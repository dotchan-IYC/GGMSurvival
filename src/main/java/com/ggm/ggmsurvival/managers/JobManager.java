// 수정된 JobManager.java - ScoreboardIntegration 타입 충돌 해결
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class JobManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;

    // Thread-Safe 컬렉션들
    private final ConcurrentHashMap<UUID, JobType> jobTypes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> jobLevels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> jobExperience = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> shieldDefenseCooldown = new ConcurrentHashMap<>();

    // 경험치 및 몬스터 설정 (불변 맵)
    private final Map<Integer, Integer> expRequirements;
    private final Map<EntityType, Integer> monsterExp;

    // 스케줄러 관리
    private BukkitTask expBarUpdateTask;
    private final Set<UUID> playersNeedingExpUpdate = ConcurrentHashMap.newKeySet();

    // 스코어보드 통합 - 타입 충돌 해결
    private ScoreboardIntegration scoreboardIntegration;

    // 성능 최적화를 위한 캐시
    private final Map<UUID, Long> lastExpGainTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> cachedActionBars = new ConcurrentHashMap<>();

    // 키 상수들
    private final NamespacedKey archerSpeedKey;

    public JobManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.archerSpeedKey = new NamespacedKey(plugin, "archer_speed");

        // 불변 맵 초기화
        this.expRequirements = initializeExpRequirements();
        this.monsterExp = initializeMonsterExp();

        try {
            // 데이터베이스 테이블 생성
            createJobTables();

            // 스코어보드 통합 초기화
            initializeScoreboardIntegration();

            // 경험치바 업데이트 태스크 시작
            startExpBarUpdateTask();

            plugin.getLogger().info("JobManager 안정화 초기화 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "JobManager 초기화 실패", e);
            throw new RuntimeException("JobManager 초기화 실패", e);
        }
    }

    /**
     * 직업 타입 열거형
     */
    public enum JobType {
        NONE("직업 없음", "7"),
        TANK("탱커", "9"),
        WARRIOR("검사", "c"),
        ARCHER("궁수", "a");

        private final String displayName;
        private final String colorCode;

        JobType(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return "§" + colorCode;
        }
    }

    /**
     * 경험치 요구량 초기화 (불변)
     */
    private Map<Integer, Integer> initializeExpRequirements() {
        Map<Integer, Integer> requirements = new HashMap<>();
        requirements.put(1, 100);     // 1레벨: 100exp
        requirements.put(2, 250);     // 2레벨: 250exp
        requirements.put(3, 500);     // 3레벨: 500exp
        requirements.put(4, 800);     // 4레벨: 800exp
        requirements.put(5, 1200);    // 5레벨: 1200exp (특수 효과)
        requirements.put(6, 1700);    // 6레벨: 1700exp
        requirements.put(7, 2300);    // 7레벨: 2300exp
        requirements.put(8, 3000);    // 8레벨: 3000exp
        requirements.put(9, 3800);    // 9레벨: 3800exp
        requirements.put(10, 4700);   // 10레벨: 4700exp (만렙)
        return Collections.unmodifiableMap(requirements);
    }

    /**
     * 몬스터별 경험치 초기화 (불변)
     */
    private Map<EntityType, Integer> initializeMonsterExp() {
        Map<EntityType, Integer> exp = new HashMap<>();

        // 일반 몬스터
        exp.put(EntityType.ZOMBIE, 10);
        exp.put(EntityType.SKELETON, 10);
        exp.put(EntityType.SPIDER, 8);
        exp.put(EntityType.CREEPER, 12);
        exp.put(EntityType.SLIME, 5);

        // 중간 몬스터
        exp.put(EntityType.ENDERMAN, 25);
        exp.put(EntityType.WITCH, 20);
        exp.put(EntityType.PILLAGER, 15);
        exp.put(EntityType.VINDICATOR, 18);

        // 강한 몬스터
        exp.put(EntityType.WITHER_SKELETON, 40);
        exp.put(EntityType.BLAZE, 30);
        exp.put(EntityType.GHAST, 35);

        // 보스 몬스터
        exp.put(EntityType.ENDER_DRAGON, 1000);
        exp.put(EntityType.WITHER, 500);
        exp.put(EntityType.ELDER_GUARDIAN, 200);

        return Collections.unmodifiableMap(exp);
    }

    /**
     * 데이터베이스 테이블 생성
     */
    private void createJobTables() {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_jobs (
                    uuid VARCHAR(36) PRIMARY KEY,
                    job_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
                    job_level INT NOT NULL DEFAULT 1,
                    job_experience INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_job_type (job_type),
                    INDEX idx_job_level (job_level)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("직업 데이터베이스 테이블 생성 완료");

        } catch (SQLException e) {
            throw new RuntimeException("직업 테이블 생성 실패", e);
        }
    }

    /**
     * 스코어보드 통합 초기화 - 타입 충돌 해결
     */
    private void initializeScoreboardIntegration() {
        try {
            // 별도의 ScoreboardIntegration 클래스 사용
            this.scoreboardIntegration = new ScoreboardIntegration(plugin);
            plugin.getLogger().info("스코어보드 통합 초기화 완료");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "스코어보드 통합 초기화 실패", e);
            this.scoreboardIntegration = null;
        }
    }

    /**
     * 경험치바 업데이트 태스크 시작
     */
    private void startExpBarUpdateTask() {
        expBarUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!playersNeedingExpUpdate.isEmpty()) {
                Set<UUID> toUpdate = new HashSet<>(playersNeedingExpUpdate);
                playersNeedingExpUpdate.clear();

                for (UUID uuid : toUpdate) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        updateExpBar(player);
                    }
                }
            }
        }, 20L, 20L); // 1초마다
    }

    /**
     * 플레이어 접속 처리
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 비동기로 데이터 로드
        CompletableFuture.runAsync(() -> loadPlayerJobData(player))
                .thenRun(() -> {
                    // 메인 스레드에서 효과 적용
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        applyJobEffects(player);
                        markForExpUpdate(player);
                    });
                });
    }

    /**
     * 플레이어 퇴장 처리
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 비동기로 데이터 저장
        CompletableFuture.runAsync(() -> savePlayerJobData(player));

        // 캐시에서 제거
        playersNeedingExpUpdate.remove(uuid);
        cachedActionBars.remove(uuid);
        lastExpGainTime.remove(uuid);

        // 궁수 효과 제거
        removeArcherSpeedBonus(player);
    }

    /**
     * 몬스터 처치 경험치 획득
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player)) return;

        Player killer = event.getEntity().getKiller();
        EntityType entityType = event.getEntity().getType();

        // 직업이 있는 경우에만 경험치 지급
        if (getJobType(killer) != JobType.NONE) {
            int expGain = monsterExp.getOrDefault(entityType, 5);
            addJobExperience(killer, expGain, entityType);
        }
    }

    /**
     * 방패 방어 (탱커 특수 효과)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onShieldDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        if (getJobType(player) != JobType.TANK) return;
        if (getJobLevel(player) < 5) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // 방패를 들고 있는지 확인
        boolean hasShield = (mainHand.getType() == Material.SHIELD) ||
                (offHand.getType() == Material.SHIELD);

        if (!hasShield) return;

        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // 쿨다운 체크 (3초)
        if (shieldDefenseCooldown.containsKey(uuid)) {
            long lastUse = shieldDefenseCooldown.get(uuid);
            if (currentTime - lastUse < 3000) return;
        }

        // 30% 확률로 방어 성공
        if (ThreadLocalRandom.current().nextInt(100) < 30) {
            event.setCancelled(true);

            // 체력 회복 (2하트)
            double currentHealth = player.getHealth();
            double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double newHealth = Math.min(currentHealth + 4.0, maxHealth);
            player.setHealth(newHealth);

            // 효과 및 메시지
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 5);
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
            player.sendMessage(plugin.getConfig().getString("messages.tank_shield_heal", "§9방패 방어! §a+4 체력 회복"));

            // 쿨다운 설정
            shieldDefenseCooldown.put(uuid, currentTime);
        }
    }

    // === Getter/Setter 메서드들 ===

    public JobType getJobType(Player player) {
        return jobTypes.getOrDefault(player.getUniqueId(), JobType.NONE);
    }

    public int getJobLevel(Player player) {
        return jobLevels.getOrDefault(player.getUniqueId(), 1);
    }

    public int getJobExperience(Player player) {
        return jobExperience.getOrDefault(player.getUniqueId(), 0);
    }

    // === 직업 관리 메서드들 ===

    public boolean setJobType(Player player, JobType jobType) {
        UUID uuid = player.getUniqueId();

        // 기존 효과 제거
        removeArcherSpeedBonus(player);

        jobTypes.put(uuid, jobType);
        jobLevels.put(uuid, 1);
        jobExperience.put(uuid, 0);

        // 비동기로 저장
        CompletableFuture.runAsync(() -> savePlayerJobData(player));

        // 메인 스레드에서 효과 적용
        Bukkit.getScheduler().runTask(plugin, () -> {
            applyJobEffects(player);
            markForExpUpdate(player);

            if (scoreboardIntegration != null) {
                scoreboardIntegration.notifyJobChange(player);
            }
        });

        return true;
    }

    public void addJobExperience(Player player, int exp, EntityType source) {
        if (getJobType(player) == JobType.NONE) return;

        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // 경험치 중복 지급 방지 (1초 쿨다운)
        if (lastExpGainTime.containsKey(uuid)) {
            long lastGain = lastExpGainTime.get(uuid);
            if (currentTime - lastGain < 1000) return;
        }

        int currentExp = getJobExperience(player);
        int currentLevel = getJobLevel(player);

        if (currentLevel >= 10) return; // 만렙

        int newExp = currentExp + exp;
        jobExperience.put(uuid, newExp);
        lastExpGainTime.put(uuid, currentTime);

        // 레벨업 체크
        int requiredExp = expRequirements.getOrDefault(currentLevel + 1, Integer.MAX_VALUE);
        if (newExp >= requiredExp && currentLevel < 10) {
            levelUp(player);
        }

        // 경험치바 업데이트 예약
        markForExpUpdate(player);

        // ActionBar 표시
        String entityName = getEntityDisplayName(source);
        String message = String.format("§a+%d 경험치 §7(§e%s§7)", exp, entityName);
        player.sendActionBar(message);
    }

    private void levelUp(Player player) {
        UUID uuid = player.getUniqueId();
        int newLevel = getJobLevel(player) + 1;

        jobLevels.put(uuid, newLevel);
        jobExperience.put(uuid, 0); // 경험치 초기화

        // 효과 적용
        applyJobEffects(player);

        // 레벨업 메시지 및 효과
        player.sendMessage("§6★ 레벨 업! §e" + getJobType(player).getDisplayName() +
                " §f" + newLevel + "레벨이 되었습니다!");
        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK,
                player.getLocation().add(0, 1, 0), 20);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // 5레벨, 10레벨 특수 메시지
        if (newLevel == 5) {
            player.sendMessage("§d✨ 특수 능력이 해제되었습니다!");
        } else if (newLevel == 10) {
            player.sendMessage("§6★★★ 만렙 달성! 최고의 " +
                    getJobType(player).getDisplayName() + "가 되었습니다! ★★★");
        }

        // 스코어보드 알림
        if (scoreboardIntegration != null) {
            scoreboardIntegration.notifyLevelUp(player);
        }

        // 비동기로 저장
        CompletableFuture.runAsync(() -> savePlayerJobData(player));
    }

    private void applyJobEffects(Player player) {
        JobType job = getJobType(player);
        int level = getJobLevel(player);

        try {
            // 기본 체력 초기화
            AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(20.0);
            }

            // 기존 효과 제거
            removeArcherSpeedBonus(player);

            // 직업별 효과 적용
            switch (job) {
                case TANK:
                    // 탱커: 추가 체력
                    if (healthAttr != null) {
                        double bonusHealth = level * 2.0; // 레벨당 1하트씩
                        healthAttr.setBaseValue(20.0 + bonusHealth);

                        if (player.getHealth() < healthAttr.getValue()) {
                            player.setHealth(healthAttr.getValue());
                        }
                    }
                    break;

                case ARCHER:
                    // 궁수: 가죽 신발 착용 시 속도 증가
                    applyArcherSpeedBonus(player);
                    break;

                case WARRIOR:
                    // 전사: 기본 효과 (검 공격 시 적용)
                    break;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 효과 적용 실패: " + player.getName(), e);
        }
    }

    private void applyArcherSpeedBonus(Player player) {
        ItemStack boots = player.getInventory().getBoots();

        if (boots != null && boots.getType() == Material.LEATHER_BOOTS) {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            pdc.set(archerSpeedKey, PersistentDataType.BYTE, (byte) 1);

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    Integer.MAX_VALUE,
                    0,
                    false,
                    false,
                    false
            ));

            player.sendActionBar(plugin.getConfig().getString("messages.archer_speed_boost",
                    "§a🏃 가죽장화 패시브: 이동속도 +20%"));
        }
    }

    private void removeArcherSpeedBonus(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        if (pdc.has(archerSpeedKey, PersistentDataType.BYTE)) {
            pdc.remove(archerSpeedKey);
            player.removePotionEffect(PotionEffectType.SPEED);

            if (player.isOnline()) {
                player.sendActionBar(plugin.getConfig().getString("messages.archer_speed_removed",
                        "§7가죽장화 패시브 효과 해제"));
            }
        }
    }

    private void updateExpBar(Player player) {
        int level = getJobLevel(player);
        int currentExp = getJobExperience(player);

        if (level >= 10) {
            player.setLevel(10);
            player.setExp(1.0f);
            return;
        }

        int requiredExp = expRequirements.getOrDefault(level + 1, 1000);
        float progress = (float) currentExp / requiredExp;

        player.setLevel(level);
        player.setExp(Math.min(progress, 1.0f));
    }

    private void markForExpUpdate(Player player) {
        playersNeedingExpUpdate.add(player.getUniqueId());
    }

    private String getEntityDisplayName(EntityType type) {
        return switch (type) {
            case ZOMBIE -> "좀비";
            case SKELETON -> "스켈레톤";
            case SPIDER -> "거미";
            case CREEPER -> "크리퍼";
            case ENDERMAN -> "엔더맨";
            case ENDER_DRAGON -> "엔더 드래곤";
            case WITHER -> "위더";
            default -> type.name();
        };
    }

    // === 데이터베이스 관련 메서드들 ===

    private void loadPlayerJobData(Player player) {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = "SELECT job_type, job_level, job_experience FROM player_jobs WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, player.getUniqueId().toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        JobType jobType = JobType.valueOf(rs.getString("job_type"));
                        int level = rs.getInt("job_level");
                        int experience = rs.getInt("job_experience");

                        UUID uuid = player.getUniqueId();
                        jobTypes.put(uuid, jobType);
                        jobLevels.put(uuid, level);
                        jobExperience.put(uuid, experience);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 직업 데이터 로드 실패: " + player.getName(), e);
        }
    }

    public void savePlayerJobData(Player player) {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = """
                INSERT INTO player_jobs (uuid, job_type, job_level, job_experience) 
                VALUES (?, ?, ?, ?) 
                ON DUPLICATE KEY UPDATE 
                job_type = VALUES(job_type), 
                job_level = VALUES(job_level), 
                job_experience = VALUES(job_experience),
                updated_at = CURRENT_TIMESTAMP
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                UUID uuid = player.getUniqueId();
                stmt.setString(1, uuid.toString());
                stmt.setString(2, getJobType(player).name());
                stmt.setInt(3, getJobLevel(player));
                stmt.setInt(4, getJobExperience(player));

                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 직업 데이터 저장 실패: " + player.getName(), e);
        }
    }

    // === 유틸리티 메서드들 ===

    public void cleanupCache() {
        try {
            // 오프라인 플레이어 데이터 정리
            Set<UUID> onlineUUIDs = new HashSet<>();
            Bukkit.getOnlinePlayers().forEach(player -> onlineUUIDs.add(player.getUniqueId()));

            jobTypes.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            jobLevels.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            jobExperience.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            shieldDefenseCooldown.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            lastExpGainTime.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            cachedActionBars.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            playersNeedingExpUpdate.removeIf(uuid -> !onlineUUIDs.contains(uuid));

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "캐시 정리 중 오류", e);
        }
    }

    public void shutdown() {
        try {
            // 경험치바 업데이트 태스크 중지
            if (expBarUpdateTask != null && !expBarUpdateTask.isCancelled()) {
                expBarUpdateTask.cancel();
            }

            // 모든 온라인 플레이어 데이터 저장
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    savePlayerJobData(player);
                    removeArcherSpeedBonus(player);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "플레이어 종료 처리 실패: " + player.getName(), e);
                }
            }

            // 캐시 정리
            cleanupCache();

            plugin.getLogger().info("JobManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "JobManager 종료 중 오류", e);
        }
    }

    // === Getter 메서드들 ===

    public ScoreboardIntegration getScoreboardIntegration() {
        return scoreboardIntegration;
    }
}