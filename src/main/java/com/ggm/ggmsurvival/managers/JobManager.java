// 완전한 JobManager.java - 직업 시스템 (이모티콘 제거)
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 완전한 직업 시스템 매니저
 * - 3개 직업: 탱커, 전사, 궁수
 * - 직업별 고유 능력
 * - 레벨링 시스템
 * - 직업 변경 시스템
 * - 실시간 능력치 적용
 */
public class JobManager {

    private final GGMSurvival plugin;

    // 플레이어 직업 데이터 캐시
    private final Map<UUID, PlayerJobData> jobDataCache = new ConcurrentHashMap<>();

    // 직업 변경 쿨다운 (플레이어별)
    private final Map<UUID, Long> jobChangeCooldown = new ConcurrentHashMap<>();

    // 직업 정의
    public enum JobType {
        NONE("없음", "직업이 없습니다"),
        TANK("탱커", "방어에 특화된 직업"),
        WARRIOR("전사", "근접 공격에 특화된 직업"),
        ARCHER("궁수", "원거리 공격에 특화된 직업");

        private final String displayName;
        private final String description;

        JobType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public JobManager(GGMSurvival plugin) {
        this.plugin = plugin;

        try {
            initializeJobSystem();
            plugin.getLogger().info("=== 직업 시스템 초기화 완료 ===");
            plugin.getLogger().info("사용 가능한 직업: 탱커, 전사, 궁수");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "JobManager 초기화 실패", e);
            throw new RuntimeException("JobManager 초기화 실패", e);
        }
    }

    /**
     * 직업 시스템 초기화
     */
    private void initializeJobSystem() {
        // 데이터베이스 테이블이 이미 DatabaseManager에서 생성됨
        plugin.getLogger().info("직업 시스템 데이터베이스 연결 확인 완료");
    }

    /**
     * 플레이어 직업 데이터 로드
     */
    public CompletableFuture<PlayerJobData> loadPlayerJobData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerJobData jobData = plugin.getDatabaseManager().executeQuerySafe(
                        "SELECT job, job_level, job_exp FROM player_data WHERE uuid = ?",
                        rs -> {
                            if (rs.next()) {
                                String jobString = rs.getString("job");
                                int jobLevel = rs.getInt("job_level");
                                long jobExp = rs.getLong("job_exp");

                                JobType jobType = parseJobType(jobString);
                                return new PlayerJobData(uuid, jobType, jobLevel, jobExp);
                            } else {
                                // 새 플레이어 - 기본 데이터 생성
                                PlayerJobData defaultData = new PlayerJobData(uuid, JobType.NONE, 1, 0L);
                                createPlayerJobData(uuid, defaultData);
                                return defaultData;
                            }
                        },
                        uuid.toString()
                );

                // 캐시에 저장
                jobDataCache.put(uuid, jobData);
                return jobData;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "플레이어 직업 데이터 로드 실패: " + uuid, e);

                // 기본 데이터 반환
                PlayerJobData defaultData = new PlayerJobData(uuid, JobType.NONE, 1, 0L);
                jobDataCache.put(uuid, defaultData);
                return defaultData;
            }
        });
    }

    /**
     * 플레이어 직업 데이터 저장
     */
    public CompletableFuture<Boolean> savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerJobData jobData = jobDataCache.get(uuid);

        if (jobData == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE player_data SET job = ?, job_level = ?, job_exp = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?")) {

                statement.setString(1, jobData.getJobType().name().toLowerCase());
                statement.setInt(2, jobData.getLevel());
                statement.setLong(3, jobData.getExperience());
                statement.setString(4, uuid.toString());

                return statement.executeUpdate() > 0;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "플레이어 직업 데이터 저장 실패: " + uuid, e);
                return false;
            }
        });
    }

    /**
     * 새 플레이어 직업 데이터 생성
     */
    private void createPlayerJobData(UUID uuid, PlayerJobData jobData) {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO player_data (uuid, username, job, job_level, job_exp) VALUES (?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE job = ?, job_level = ?, job_exp = ?")) {

            Player player = plugin.getServer().getPlayer(uuid);
            String username = player != null ? player.getName() : "Unknown";

            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setString(3, jobData.getJobType().name().toLowerCase());
            statement.setInt(4, jobData.getLevel());
            statement.setLong(5, jobData.getExperience());
            statement.setString(6, jobData.getJobType().name().toLowerCase());
            statement.setInt(7, jobData.getLevel());
            statement.setLong(8, jobData.getExperience());

            statement.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "새 플레이어 직업 데이터 생성 실패: " + uuid, e);
        }
    }

    /**
     * 플레이어 직업 변경
     */
    public CompletableFuture<JobChangeResult> changePlayerJob(Player player, JobType newJob) {
        UUID uuid = player.getUniqueId();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 쿨다운 확인
                if (isJobChangeCooldown(uuid)) {
                    long remainingTime = getJobChangeCooldownRemaining(uuid);
                    return new JobChangeResult(false, "직업 변경 쿨다운 중입니다. 남은 시간: " + formatTime(remainingTime));
                }

                PlayerJobData currentJobData = jobDataCache.get(uuid);
                if (currentJobData == null) {
                    currentJobData = loadPlayerJobData(uuid).join();
                }

                // 현재 직업과 같은지 확인
                if (currentJobData.getJobType() == newJob) {
                    return new JobChangeResult(false, "이미 " + newJob.getDisplayName() + " 직업입니다.");
                }

                // 직업 변경 비용 확인
                long changeCost = getJobChangeCost(currentJobData.getJobType(), newJob);
                if (changeCost > 0) {
                    boolean hasEnoughMoney = plugin.getEconomyManager().hasEnoughMoney(uuid, changeCost).join();
                    if (!hasEnoughMoney) {
                        return new JobChangeResult(false, "직업 변경 비용이 부족합니다. 필요: " +
                                plugin.getEconomyManager().formatMoneyWithSymbol(changeCost));
                    }

                    // 비용 차감
                    boolean deducted = plugin.getEconomyManager().removeMoney(uuid, changeCost).join();
                    if (!deducted) {
                        return new JobChangeResult(false, "직업 변경 비용 차감에 실패했습니다.");
                    }
                }

                // 직업 변경 실행
                currentJobData.setJobType(newJob);
                currentJobData.setLevel(1);
                currentJobData.setExperience(0L);

                // 데이터베이스 저장
                boolean saved = savePlayerData(player).join();
                if (!saved) {
                    return new JobChangeResult(false, "직업 변경 저장에 실패했습니다.");
                }

                // 쿨다운 설정
                setJobChangeCooldown(uuid);

                // 플레이어 능력치 적용
                applyJobAbilities(player, newJob);

                return new JobChangeResult(true, "직업이 " + newJob.getDisplayName() + "(으)로 변경되었습니다!");

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "직업 변경 실패: " + player.getName(), e);
                return new JobChangeResult(false, "직업 변경 중 오류가 발생했습니다.");
            }
        });
    }

    /**
     * 플레이어에게 직업 능력 적용
     */
    public void applyJobAbilities(Player player, JobType jobType) {
        try {
            // 기존 효과 제거
            removeJobEffects(player);

            switch (jobType) {
                case TANK:
                    applyTankAbilities(player);
                    break;
                case WARRIOR:
                    applyWarriorAbilities(player);
                    break;
                case ARCHER:
                    applyArcherAbilities(player);
                    break;
                case NONE:
                    // 기본 능력치로 리셋
                    resetToDefaultAbilities(player);
                    break;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "직업 능력 적용 실패: " + player.getName(), e);
        }
    }

    /**
     * 탱커 능력 적용
     */
    private void applyTankAbilities(Player player) {
        // 체력 증가 (하트 2개 = 4.0)
        AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            double healthBonus = plugin.getConfig().getDouble("job_system.jobs.tank.health_bonus", 4.0);
            healthAttr.setBaseValue(20.0 + healthBonus);
        }

        // 이동속도 감소
        AttributeInstance speedAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            double speedPenalty = plugin.getConfig().getDouble("job_system.jobs.tank.speed_penalty", -0.02);
            speedAttr.setBaseValue(0.10 + speedPenalty);
        }

        // 저항 효과 (약한 저항)
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        player.sendMessage("§a[탱커] 방어 능력이 강화되었습니다!");
    }

    /**
     * 전사 능력 적용
     */
    private void applyWarriorAbilities(Player player) {
        // 공격력 증가 (근접무기)
        AttributeInstance attackAttr = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackAttr != null) {
            double attackBonus = plugin.getConfig().getDouble("job_system.jobs.warrior.attack_bonus", 0.20);
            attackAttr.setBaseValue(1.0 + attackBonus);
        }

        // 힘 효과 (약한 힘)
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 0, false, false));

        player.sendMessage("§c[전사] 근접 공격력이 강화되었습니다!");
    }

    /**
     * 궁수 능력 적용
     */
    private void applyArcherAbilities(Player player) {
        // 이동속도 증가
        AttributeInstance speedAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            double speedBonus = plugin.getConfig().getDouble("job_system.jobs.archer.speed_bonus", 0.05);
            speedAttr.setBaseValue(0.10 + speedBonus);
        }

        // 점프력 증가 (약한 점프 강화)
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 0, false, false));

        player.sendMessage("§e[궁수] 기동력이 강화되었습니다!");
    }

    /**
     * 기본 능력치로 리셋
     */
    private void resetToDefaultAbilities(Player player) {
        // 기본 체력
        AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(20.0);
        }

        // 기본 이동속도
        AttributeInstance speedAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.10);
        }

        // 기본 공격력
        AttributeInstance attackAttr = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(1.0);
        }

        player.sendMessage("§7직업 능력이 기본 상태로 리셋되었습니다.");
    }

    /**
     * 직업 효과 제거
     */
    private void removeJobEffects(Player player) {
        // 포션 효과 제거
        player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        player.removePotionEffect(PotionEffectType.JUMP);
    }

    /**
     * 경험치 추가
     */
    public CompletableFuture<Boolean> addExperience(UUID uuid, long exp) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerJobData jobData = jobDataCache.get(uuid);
            if (jobData == null || jobData.getJobType() == JobType.NONE) {
                return false;
            }

            long currentExp = jobData.getExperience();
            long newExp = currentExp + exp;

            // 레벨업 확인
            int currentLevel = jobData.getLevel();
            int newLevel = calculateLevel(newExp);

            jobData.setExperience(newExp);

            if (newLevel > currentLevel) {
                jobData.setLevel(newLevel);

                // 플레이어에게 레벨업 알림
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null) {
                    player.sendMessage("§6[레벨업!] " + jobData.getJobType().getDisplayName() +
                            " 레벨이 " + newLevel + "이 되었습니다!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    // 능력치 재적용 (레벨에 따른 보너스)
                    applyJobAbilities(player, jobData.getJobType());
                }
            }

            return true;
        });
    }

    /**
     * 레벨 계산
     */
    private int calculateLevel(long experience) {
        // 레벨 공식: level = sqrt(exp / 100) + 1
        return (int) Math.floor(Math.sqrt(experience / 100.0)) + 1;
    }

    /**
     * 다음 레벨까지 필요한 경험치 계산
     */
    public long getExpToNextLevel(int currentLevel) {
        long nextLevelExp = (long) Math.pow(currentLevel, 2) * 100;
        long currentLevelExp = (long) Math.pow(currentLevel - 1, 2) * 100;
        return nextLevelExp - currentLevelExp;
    }

    /**
     * 직업 변경 비용 계산
     */
    private long getJobChangeCost(JobType fromJob, JobType toJob) {
        if (fromJob == JobType.NONE) {
            return 0L; // 첫 직업 선택은 무료
        }

        return plugin.getConfig().getLong("job_system.job_change.cost", 5000L);
    }

    /**
     * 직업 변경 쿨다운 확인
     */
    private boolean isJobChangeCooldown(UUID uuid) {
        Long cooldownEnd = jobChangeCooldown.get(uuid);
        if (cooldownEnd == null) {
            return false;
        }

        return System.currentTimeMillis() < cooldownEnd;
    }

    /**
     * 직업 변경 쿨다운 남은 시간
     */
    private long getJobChangeCooldownRemaining(UUID uuid) {
        Long cooldownEnd = jobChangeCooldown.get(uuid);
        if (cooldownEnd == null) {
            return 0L;
        }

        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    /**
     * 직업 변경 쿨다운 설정
     */
    private void setJobChangeCooldown(UUID uuid) {
        long cooldownHours = plugin.getConfig().getLong("job_system.job_change.cooldown", 86400L); // 기본 24시간
        long cooldownEnd = System.currentTimeMillis() + (cooldownHours * 1000);
        jobChangeCooldown.put(uuid, cooldownEnd);
    }

    /**
     * 시간 포맷팅
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            return hours + "시간 " + minutes + "분";
        } else {
            return minutes + "분";
        }
    }

    /**
     * 문자열을 JobType으로 파싱
     */
    private JobType parseJobType(String jobString) {
        try {
            return JobType.valueOf(jobString.toUpperCase());
        } catch (Exception e) {
            return JobType.NONE;
        }
    }

    /**
     * 플레이어 직업 정보 조회
     */
    public PlayerJobData getPlayerJobData(UUID uuid) {
        return jobDataCache.get(uuid);
    }

    /**
     * 플레이어 직업 정보 조회 (비동기)
     */
    public CompletableFuture<PlayerJobData> getPlayerJobDataAsync(UUID uuid) {
        PlayerJobData cached = jobDataCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return loadPlayerJobData(uuid);
    }

    /**
     * 캐시 정리
     */
    public void cleanupCache() {
        try {
            Set<UUID> onlineUUIDs = new HashSet<>();
            plugin.getServer().getOnlinePlayers().forEach(player -> onlineUUIDs.add(player.getUniqueId()));

            jobDataCache.entrySet().removeIf(entry -> !onlineUUIDs.contains(entry.getKey()));
            jobChangeCooldown.entrySet().removeIf(entry -> !onlineUUIDs.contains(entry.getKey()));

            plugin.getLogger().info("JobManager 캐시 정리 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "JobManager 캐시 정리 중 오류", e);
        }
    }

    /**
     * 시스템 종료
     */
    public void shutdown() {
        try {
            // 모든 온라인 플레이어 데이터 저장
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                savePlayerData(player).join();
                removeJobEffects(player);
            }

            // 캐시 정리
            jobDataCache.clear();
            jobChangeCooldown.clear();

            plugin.getLogger().info("JobManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "JobManager 종료 중 오류", e);
        }
    }

    /**
     * 플레이어 직업 데이터 클래스
     */
    public static class PlayerJobData {
        private final UUID uuid;
        private JobType jobType;
        private int level;
        private long experience;

        public PlayerJobData(UUID uuid, JobType jobType, int level, long experience) {
            this.uuid = uuid;
            this.jobType = jobType;
            this.level = level;
            this.experience = experience;
        }

        // Getter 및 Setter 메서드들
        public UUID getUuid() { return uuid; }
        public JobType getJobType() { return jobType; }
        public void setJobType(JobType jobType) { this.jobType = jobType; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public long getExperience() { return experience; }
        public void setExperience(long experience) { this.experience = experience; }
    }

    /**
     * 직업 변경 결과 클래스
     */
    public static class JobChangeResult {
        private final boolean success;
        private final String message;

        public JobChangeResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}