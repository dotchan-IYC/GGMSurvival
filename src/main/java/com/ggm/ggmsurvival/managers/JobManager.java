package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.enums.JobType;
import com.ggm.ggmsurvival.gui.JobSelectionGUI;
import com.ggm.ggmsurvival.gui.JobAchievementGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 향상된 직업 관리 시스템 (Java 8+ 호환)
 * UI 강화 및 새로운 기능들이 통합된 JobManager
 */
public class JobManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final JobAchievementGUI achievementGUI;

    // 캐시 시스템
    private final Map<UUID, JobType> jobTypes = new HashMap<>();
    private final Map<UUID, Integer> jobLevels = new HashMap<>();
    private final Map<UUID, Integer> jobExperience = new HashMap<>();
    private final Map<UUID, Long> lastExpGain = new HashMap<>();
    private final Map<UUID, Integer> dailyExp = new HashMap<>();

    // 쿨다운 시스템
    private final Map<UUID, Long> shieldCooldowns = new HashMap<>();
    private final Map<UUID, Long> ultimateCooldowns = new HashMap<>();
    private final Map<UUID, Integer> comboStacks = new HashMap<>();

    // 통계 시스템
    private final Map<UUID, JobStats> playerStats = new HashMap<>();

    // 경험치 요구량
    private final Map<Integer, Integer> expRequirements = new HashMap<>();

    public JobManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();
        this.achievementGUI = new JobAchievementGUI(plugin);

        // 초기화
        setupExpRequirements();
        createTables();
        startPeriodicTasks();

        plugin.getLogger().info("향상된 직업 시스템이 초기화되었습니다!");
        printSystemInfo();
    }

    /**
     * 시스템 정보 출력
     */
    private void printSystemInfo() {
        plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        plugin.getLogger().info("직업 시스템 v2.0 - Enhanced UI");
        plugin.getLogger().info("새로운 기능:");
        plugin.getLogger().info("• 스킬 관리 대시보드");
        plugin.getLogger().info("• 상세 통계 시스템");
        plugin.getLogger().info("• 업적 시스템");
        plugin.getLogger().info("• 실시간 진행도 추적");
        plugin.getLogger().info("• 향상된 시각화");
        plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 데이터베이스 테이블 생성
     */
    private void createTables() {
        createJobTable();
        createStatsTable();
        createAchievementTable();
        createDailyStatsTable();
    }

    /**
     * 직업 테이블 생성 (기존 + 확장)
     */
    private void createJobTable() {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_jobs (
                    uuid VARCHAR(36) PRIMARY KEY,
                    job_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
                    job_level INT NOT NULL DEFAULT 1,
                    job_experience INT NOT NULL DEFAULT 0,
                    total_experience INT NOT NULL DEFAULT 0,
                    job_selected_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_level_up TIMESTAMP NULL,
                    prestige_level INT DEFAULT 0,
                    skill_points INT DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("직업 테이블 초기화 완료");

        } catch (SQLException e) {
            plugin.getLogger().severe("직업 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 통계 테이블 생성
     */
    private void createStatsTable() {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_job_stats (
                    uuid VARCHAR(36) PRIMARY KEY,
                    monster_kills INT DEFAULT 0,
                    boss_kills INT DEFAULT 0,
                    pvp_kills INT DEFAULT 0,
                    pvp_deaths INT DEFAULT 0,
                    damage_dealt BIGINT DEFAULT 0,
                    damage_received BIGINT DEFAULT 0,
                    healing_done BIGINT DEFAULT 0,
                    skill_uses TEXT,
                    play_time_minutes INT DEFAULT 0,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("통계 테이블 초기화 완료");

        } catch (SQLException e) {
            plugin.getLogger().severe("통계 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 업적 테이블 생성
     */
    private void createAchievementTable() {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_achievements (
                    uuid VARCHAR(36),
                    achievement_id VARCHAR(50),
                    completed BOOLEAN DEFAULT FALSE,
                    completion_date TIMESTAMP NULL,
                    progress_data TEXT,
                    PRIMARY KEY (uuid, achievement_id)
                )
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

            // 업적 점수 테이블
            String scoreSQL = """
                CREATE TABLE IF NOT EXISTS player_achievement_scores (
                    uuid VARCHAR(36) PRIMARY KEY,
                    total_score INT DEFAULT 0,
                    achievements_completed INT DEFAULT 0,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

            try (PreparedStatement stmt = conn.prepareStatement(scoreSQL)) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("업적 테이블 초기화 완료");

        } catch (SQLException e) {
            plugin.getLogger().severe("업적 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 일일 통계 테이블 생성
     */
    private void createDailyStatsTable() {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS daily_job_stats (
                    uuid VARCHAR(36),
                    date DATE,
                    experience_gained INT DEFAULT 0,
                    monsters_killed INT DEFAULT 0,
                    play_time_minutes INT DEFAULT 0,
                    skills_used INT DEFAULT 0,
                    PRIMARY KEY (uuid, date)
                )
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("일일 통계 테이블 초기화 완료");

        } catch (SQLException e) {
            plugin.getLogger().severe("일일 통계 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 주기적 작업 시작
     */
    private void startPeriodicTasks() {
        // 5분마다 데이터 저장
        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllPlayerData();
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // 5분

        // 1분마다 통계 업데이트
        new BukkitRunnable() {
            @Override
            public void run() {
                updatePlayTime();
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L); // 1분

        // 자정에 일일 통계 초기화
        new BukkitRunnable() {
            @Override
            public void run() {
                resetDailyStats();
            }
        }.runTaskTimerAsynchronously(plugin, getTicksUntilMidnight(), 24 * 60 * 60 * 20L); // 24시간
    }

    // === 플레이어 이벤트 처리 ===

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadPlayerJobData(player).thenRun(() -> {
            // 접속 시 직업 정보 표시
            showWelcomeMessage(player);

            // 업적 진행도 체크
            checkLoginAchievements(player);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // 데이터 저장
        savePlayerData(event.getPlayer());

        // 캐시 정리
        jobTypes.remove(uuid);
        jobLevels.remove(uuid);
        jobExperience.remove(uuid);
        shieldCooldowns.remove(uuid);
        ultimateCooldowns.remove(uuid);
        comboStacks.remove(uuid);
        playerStats.remove(uuid);
        dailyExp.remove(uuid);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Java 8 호환 - instanceof pattern matching 제거
        if (event.getEntity().getKiller() != null && event.getEntity().getKiller() instanceof Player) {
            Player player = (Player) event.getEntity().getKiller();
            handleMonsterKill(player, event);
        }
    }

    // === 핵심 기능 메소드들 ===

    /**
     * 직업 선택 GUI 열기
     */
    public void openJobSelectionGUI(Player player) {
        JobSelectionGUI gui = new JobSelectionGUI(plugin);
        gui.openGUI(player);
    }

    /**
     * 직업 설정
     */
    public CompletableFuture<Boolean> setJobType(Player player, JobType jobType) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    INSERT INTO player_jobs (uuid, job_type, job_selected_date) 
                    VALUES (?, ?, NOW())
                    ON DUPLICATE KEY UPDATE 
                    job_type = VALUES(job_type),
                    job_selected_date = NOW()
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, player.getUniqueId().toString());
                    stmt.setString(2, jobType.name());
                    stmt.executeUpdate();
                }

                // 캐시 업데이트
                jobTypes.put(player.getUniqueId(), jobType);
                jobLevels.put(player.getUniqueId(), 1);
                jobExperience.put(player.getUniqueId(), 0);

                // 업적 체크
                achievementGUI.checkAchievementProgress(player, "job_select", 1);

                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("직업 설정 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 경험치 추가 (향상된 버전)
     */
    public void addJobExperience(Player player, int amount, String source) {
        UUID uuid = player.getUniqueId();
        JobType jobType = getJobType(player);

        if (jobType == JobType.NONE) return;

        int currentExp = getJobExperience(player);
        int currentLevel = getJobLevel(player);
        int newExp = currentExp + amount;

        // 경험치 업데이트
        jobExperience.put(uuid, newExp);

        // 일일 경험치 업데이트
        dailyExp.put(uuid, dailyExp.getOrDefault(uuid, 0) + amount);

        // 레벨업 체크
        int newLevel = calculateLevel(newExp);
        if (newLevel > currentLevel) {
            handleLevelUp(player, currentLevel, newLevel);
        }

        // 경험치 표시
        showExpGain(player, amount, source);

        // 통계 업데이트
        updateExpStats(player, amount, source);

        // 업적 체크
        achievementGUI.checkAchievementProgress(player, "experience", newExp);

        // 마지막 경험치 획득 시간 업데이트
        lastExpGain.put(uuid, System.currentTimeMillis());
    }

    /**
     * 레벨업 처리 (향상된 버전)
     */
    private void handleLevelUp(Player player, int oldLevel, int newLevel) {
        UUID uuid = player.getUniqueId();

        // 레벨 업데이트
        jobLevels.put(uuid, newLevel);

        // 레벨업 효과
        showLevelUpEffects(player, oldLevel, newLevel);

        // 새로운 능력 해금 알림
        showNewAbilities(player, newLevel);

        // 업적 체크
        achievementGUI.checkAchievementProgress(player, "level", newLevel);

        // 스킬 포인트 지급 (레벨 5 이상부터)
        if (newLevel >= 5) {
            giveSkillPoints(player, newLevel - oldLevel);
        }

        // 데이터베이스 업데이트
        updateLevelInDatabase(player, newLevel);
    }

    /**
     * 몬스터 처치 처리
     */
    private void handleMonsterKill(Player player, EntityDeathEvent event) {
        JobType jobType = getJobType(player);
        if (jobType == JobType.NONE) return;

        String entityType = event.getEntity().getType().name();
        int baseExp = getMonsterExp(entityType);

        // 직업별 보너스 적용
        int finalExp = applyJobBonus(player, baseExp, "MONSTER_KILL");

        // 경험치 지급
        addJobExperience(player, finalExp, "몬스터 처치");

        // 통계 업데이트
        updateKillStats(player, entityType);

        // 업적 체크
        achievementGUI.checkAchievementProgress(player, "monster_kill",
                getMonsterKillCount(player) + 1);
    }

    /**
     * 스킬 사용 처리
     */
    public boolean useSkill(Player player, String skillId) {
        UUID uuid = player.getUniqueId();
        JobType jobType = getJobType(player);
        int level = getJobLevel(player);

        // 스킬 사용 가능 여부 체크
        if (!canUseSkill(player, skillId, level)) {
            return false;
        }

        // 쿨다운 체크
        if (isOnCooldown(player, skillId)) {
            long remainingCooldown = getRemainingCooldown(player, skillId);
            player.sendMessage("§c스킬 쿨다운: " + (remainingCooldown / 1000) + "초 남음");
            return false;
        }

        // 스킬 실행
        boolean success = executeSkill(player, skillId);

        if (success) {
            // 쿨다운 설정
            setSkillCooldown(player, skillId);

            // 통계 업데이트
            updateSkillStats(player, skillId);

            // 업적 체크
            checkSkillAchievements(player, skillId);
        }

        return success;
    }

    // === GUI 관련 메소드들 ===

    /**
     * 스코어보드용 직업 표시 정보
     */
    public String getJobDisplayForScoreboard(Player player) {
        JobType jobType = getJobType(player);
        if (jobType == JobType.NONE) {
            return "§7직업 없음";
        }

        int level = getJobLevel(player);
        return jobType.getColor() + jobType.getDisplayName() + " §7Lv." + level;
    }

    /**
     * 상세 진행도 정보
     */
    public String getDetailedProgress(Player player) {
        JobType jobType = getJobType(player);
        if (jobType == JobType.NONE) return "§7직업을 선택하세요";

        int level = getJobLevel(player);
        int exp = getJobExperience(player);
        int currentLevelExp = exp - getRequiredExpForLevel(level);
        int requiredExp = getRequiredExpForLevel(level + 1) - getRequiredExpForLevel(level);

        if (level >= 10) {
            return jobType.getColor() + jobType.getDisplayName() + " §6§lMAX";
        }

        double progress = (double) currentLevelExp / requiredExp * 100;
        return String.format("%s%s §7Lv.%d §f%d/%d §7(%.1f%%)",
                jobType.getColor(), jobType.getDisplayName(), level,
                currentLevelExp, requiredExp, progress);
    }

    // === 데이터 관리 메소드들 ===

    /**
     * 플레이어 데이터 로드
     */
    private CompletableFuture<Void> loadPlayerJobData(Player player) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = player.getUniqueId();

            try (Connection conn = databaseManager.getConnection()) {
                String sql = "SELECT * FROM player_jobs WHERE uuid = ?";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        jobTypes.put(uuid, JobType.valueOf(rs.getString("job_type")));
                        jobLevels.put(uuid, rs.getInt("job_level"));
                        jobExperience.put(uuid, rs.getInt("job_experience"));
                    } else {
                        // 새 플레이어 초기화
                        jobTypes.put(uuid, JobType.NONE);
                        jobLevels.put(uuid, 1);
                        jobExperience.put(uuid, 0);
                    }
                }

                // 통계 데이터 로드
                loadPlayerStats(player);

            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 데이터 로드 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 플레이어 통계 로드
     */
    private void loadPlayerStats(Player player) {
        // TODO: 통계 데이터 로드 구현
        playerStats.put(player.getUniqueId(), new JobStats());
    }

    /**
     * 플레이어 데이터 저장
     */
    private void savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();

        if (!jobTypes.containsKey(uuid)) return;

        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    INSERT INTO player_jobs (uuid, job_type, job_level, job_experience, total_experience) 
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE 
                    job_level = VALUES(job_level),
                    job_experience = VALUES(job_experience),
                    total_experience = VALUES(total_experience)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, jobTypes.get(uuid).name());
                    stmt.setInt(3, jobLevels.getOrDefault(uuid, 1));
                    stmt.setInt(4, jobExperience.getOrDefault(uuid, 0));
                    stmt.setInt(5, jobExperience.getOrDefault(uuid, 0)); // 총 경험치
                    stmt.executeUpdate();
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 데이터 저장 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 모든 플레이어 데이터 저장
     */
    private void saveAllPlayerData() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerData(player);
        }
    }

    // === 기존 메소드들 (호환성 유지) ===

    public JobType getJobType(Player player) {
        return jobTypes.getOrDefault(player.getUniqueId(), JobType.NONE);
    }

    public int getJobLevel(Player player) {
        return jobLevels.getOrDefault(player.getUniqueId(), 1);
    }

    public int getJobExperience(Player player) {
        return jobExperience.getOrDefault(player.getUniqueId(), 0);
    }

    public void setJobLevel(Player player, int level) {
        jobLevels.put(player.getUniqueId(), level);
        savePlayerData(player);
    }

    // === 헬퍼 메소드들 ===

    private void setupExpRequirements() {
        expRequirements.put(1, 0);
        expRequirements.put(2, 100);
        expRequirements.put(3, 250);
        expRequirements.put(4, 500);
        expRequirements.put(5, 800);
        expRequirements.put(6, 1200);
        expRequirements.put(7, 1700);
        expRequirements.put(8, 2300);
        expRequirements.put(9, 3000);
        expRequirements.put(10, 3800);
    }

    private int calculateLevel(int experience) {
        for (int level = 10; level >= 1; level--) {
            if (experience >= expRequirements.get(level)) {
                return level;
            }
        }
        return 1;
    }

    private int getRequiredExpForLevel(int level) {
        return expRequirements.getOrDefault(level, expRequirements.get(10));
    }

    private int getMonsterExp(String entityType) {
        // Java 8 호환 - switch expression을 if-else로 변경
        if (entityType.equals("ZOMBIE") || entityType.equals("SKELETON") || entityType.equals("SPIDER")) {
            return 10;
        } else if (entityType.equals("CREEPER") || entityType.equals("WITCH")) {
            return 15;
        } else if (entityType.equals("ENDERMAN") || entityType.equals("BLAZE")) {
            return 25;
        } else if (entityType.equals("WITHER_SKELETON")) {
            return 40;
        } else if (entityType.equals("ENDER_DRAGON")) {
            return 1000;
        } else if (entityType.equals("WITHER")) {
            return 500;
        } else {
            return 5;
        }
    }

    private int applyJobBonus(Player player, int baseExp, String action) {
        JobType jobType = getJobType(player);
        // TODO: 직업별 보너스 로직 구현
        return baseExp;
    }

    private void showWelcomeMessage(Player player) {
        JobType jobType = getJobType(player);
        if (jobType == JobType.NONE) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§e§l직업을 선택하세요! §7(/job select)");
                    player.sendMessage("§7새로운 UI로 더욱 편리해진 직업 시스템!");
                    player.sendMessage("§a• §f/job skills §7- 스킬 관리 대시보드");
                    player.sendMessage("§a• §f/job stats §7- 상세 통계 확인");
                    player.sendMessage("§a• §f/job achievements §7- 업적 시스템");
                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                }
            }, 60L);
        }
    }

    private void showExpGain(Player player, int amount, String source) {
        // 액션바에 경험치 획득 표시
        String message = String.format("§a+%d EXP §7(%s)", amount, source);
        player.sendActionBar(message);
    }

    private void showLevelUpEffects(Player player, int oldLevel, int newLevel) {
        // 레벨업 효과
        player.sendTitle("§6§l레벨 업!",
                "§f" + oldLevel + " §7→ §a" + newLevel, 10, 40, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // 파티클 효과 (호환성 문제로 제거)
        // Java 8 호환 - FIREWORK 파티클 대신 다른 파티클 사용
        /*
        player.getWorld().spawnParticle(
            org.bukkit.Particle.VILLAGER_HAPPY,
            player.getLocation().add(0, 1, 0),
            20, 0.5, 0.5, 0.5, 0.1
        );
        */
    }

    private void showNewAbilities(Player player, int level) {
        JobType jobType = getJobType(player);
        String ability = getNewAbilityAtLevel(jobType, level);

        if (ability != null) {
            player.sendMessage("§6§l새로운 능력 해금!");
            player.sendMessage("§f" + ability);
            player.sendMessage("§7/job skills 명령어로 확인하세요!");
        }
    }

    private String getNewAbilityAtLevel(JobType jobType, int level) {
        // Java 8 호환 - switch expression을 if-else로 변경
        if (jobType == JobType.TANK) {
            if (level == 3) return "§9방패 회복";
            else if (level == 5) return "§6불굴의 의지 (특수)";
            else if (level == 7) return "§9도발";
            else if (level == 10) return "§6무적 방벽 (궁극기)";
            else return null;
        } else if (jobType == JobType.WARRIOR) {
            if (level == 3) return "§c연속 베기";
            else if (level == 5) return "§6치명타 숙련 (특수)";
            else if (level == 7) return "§c돌진 베기";
            else if (level == 10) return "§6광풍 베기 (궁극기)";
            else return null;
        } else if (jobType == JobType.ARCHER) {
            if (level == 3) return "§e경량화";
            else if (level == 5) return "§6정밀 사격 (특수)";
            else if (level == 7) return "§e관통 화살";
            else if (level == 10) return "§6화살 폭풍 (궁극기)";
            else return null;
        } else {
            return null;
        }
    }

    // === 임시 구현 메소드들 ===

    private void updateExpStats(Player player, int amount, String source) {}
    private void updateKillStats(Player player, String entityType) {}
    private void updateSkillStats(Player player, String skillId) {}
    private void updatePlayTime() {}
    private void resetDailyStats() {}
    private void updateLevelInDatabase(Player player, int level) {}
    private void giveSkillPoints(Player player, int points) {}
    private void checkLoginAchievements(Player player) {}
    private void checkSkillAchievements(Player player, String skillId) {}
    private boolean canUseSkill(Player player, String skillId, int level) { return true; }
    private boolean isOnCooldown(Player player, String skillId) { return false; }
    private long getRemainingCooldown(Player player, String skillId) { return 0; }
    private boolean executeSkill(Player player, String skillId) { return true; }
    private void setSkillCooldown(Player player, String skillId) {}
    private int getMonsterKillCount(Player player) { return 0; }
    private long getTicksUntilMidnight() { return 20L; }

    public void reloadConfig() {
        // TODO: 설정 리로드 구현
    }

    public void onDisable() {
        saveAllPlayerData();
    }

    // === 내부 클래스 ===

    private static class JobStats {
        // 통계 데이터 구조
    }
}