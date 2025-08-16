// 완전한 GGMSurvival.java - 이모티콘 제거 버전
package com.ggm.ggmsurvival;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.ggm.ggmsurvival.commands.*;
import com.ggm.ggmsurvival.listeners.*;
import com.ggm.ggmsurvival.managers.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class GGMSurvival extends JavaPlugin {

    private static GGMSurvival instance;

    // 매니저들 - Thread-Safe 보장
    private volatile DatabaseManager databaseManager;
    private volatile EconomyManager economyManager;
    private volatile JobManager jobManager;
    private volatile EnchantUpgradeManager enchantUpgradeManager;
    private volatile AxeSpeedManager axeSpeedManager;
    private volatile DragonRewardManager dragonRewardManager;
    private volatile NPCTradeManager npcTradeManager;
    private volatile EnderResetManager enderResetManager;

    // 스케줄러 태스크들 - 메모리 누수 방지
    private BukkitTask autoSaveTask;
    private BukkitTask memoryCleanupTask;

    // 초기화 상태 추적
    private volatile boolean initialized = false;
    private volatile boolean shutdownInProgress = false;

    @Override
    public void onEnable() {
        instance = this;

        try {
            getLogger().info("===========================================");
            getLogger().info("   GGMSurvival HikariCP + EnderReset 버전");
            getLogger().info("===========================================");

            // 설정 파일 생성 및 검증
            if (!initializeConfig()) {
                getLogger().severe("설정 파일 초기화 실패 - 플러그인 비활성화");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // 비동기로 매니저 초기화 (서버 시작 지연 방지)
            CompletableFuture.runAsync(() -> {
                try {
                    initializeManagers();

                    // 메인 스레드에서 명령어 및 리스너 등록
                    getServer().getScheduler().runTask(this, () -> {
                        try {
                            registerCommands();
                            registerListeners();
                            startSchedulers();

                            initialized = true;
                            logSuccessfulInitialization();

                        } catch (Exception e) {
                            getLogger().log(Level.SEVERE, "메인 스레드 초기화 실패", e);
                            getServer().getPluginManager().disablePlugin(this);
                        }
                    });

                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "비동기 초기화 실패", e);
                    getServer().getScheduler().runTask(this, () ->
                            getServer().getPluginManager().disablePlugin(this));
                }
            }).exceptionally(throwable -> {
                getLogger().log(Level.SEVERE, "초기화 중 예상치 못한 오류", throwable);
                return null;
            });

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "플러그인 활성화 실패", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (shutdownInProgress) return;
        shutdownInProgress = true;

        getLogger().info("GGMSurvival 안전 종료 시작...");

        try {
            // 스케줄러 태스크 정리
            cleanupSchedulers();

            // 모든 플레이어 데이터 강제 저장
            saveAllPlayerData().join(); // 동기적으로 대기

            // 매니저들 안전 종료
            shutdownManagers();

            getLogger().info("GGMSurvival 안전하게 비활성화되었습니다!");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "플러그인 종료 중 오류", e);
        } finally {
            instance = null;
        }
    }

    /**
     * 설정 파일 초기화 및 검증
     */
    private boolean initializeConfig() {
        try {
            saveDefaultConfig();

            // 필수 설정 검증
            if (!validateRequiredConfig()) {
                getLogger().severe("필수 설정이 누락되었습니다. config.yml을 확인해주세요.");
                return false;
            }

            getLogger().info("설정 파일 로드 완료");
            return true;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "설정 파일 초기화 실패", e);
            return false;
        }
    }

    /**
     * 필수 설정 검증 (엔더 리셋 설정 포함)
     */
    private boolean validateRequiredConfig() {
        String[] requiredPaths = {
                "database.host",
                "database.database",
                "database.username",
                "job_system.enabled",
                "upgrade_system.enabled",
                "ender_reset_system.enabled"
        };

        boolean allValid = true;

        for (String path : requiredPaths) {
            if (!getConfig().contains(path)) {
                getLogger().severe("필수 설정 누락: " + path);
                allValid = false;
            }
        }

        // 엔더 리셋 설정 세부 검증
        if (getConfig().getBoolean("ender_reset_system.enabled", false)) {
            String[] enderPaths = {
                    "ender_reset.hour",
                    "ender_reset.minute",
                    "ender_reset.target_server",
                    "ender_reset.block_end_cities"
            };

            for (String path : enderPaths) {
                if (!getConfig().contains(path)) {
                    getLogger().warning("엔더 리셋 설정 누락 (기본값 사용): " + path);
                }
            }
        }

        return allValid;
    }

    /**
     * 매니저 초기화 - 안전하고 순차적
     */
    private void initializeManagers() {
        try {
            // 서버 기능 감지
            loadServerFeatures();

            // 1. 데이터베이스 매니저 (최우선 - HikariCP)
            getLogger().info("데이터베이스 매니저 초기화 중...");
            databaseManager = new DatabaseManager(this);
            if (!databaseManager.testConnection()) {
                throw new RuntimeException("데이터베이스 연결 실패");
            }
            getLogger().info("데이터베이스 매니저 초기화 완료 (HikariCP)");

            // 2. 경제 매니저 (GGMCore 연동)
            getLogger().info("경제 매니저 초기화 중...");
            economyManager = new EconomyManager(this);
            getLogger().info("경제 매니저 초기화 완료");

            // 3. 직업 매니저 (활성화 체크)
            if (getConfig().getBoolean("job_system.enabled", true)) {
                getLogger().info("직업 시스템 매니저 초기화 중...");
                jobManager = new JobManager(this);
                getLogger().info("직업 시스템 매니저 초기화 완료");
            }

            // 4. 강화 매니저 (활성화 체크)
            if (getConfig().getBoolean("upgrade_system.enabled", true)) {
                getLogger().info("강화 시스템 매니저 초기화 중...");
                enchantUpgradeManager = new EnchantUpgradeManager(this);
                getLogger().info("강화 시스템 매니저 초기화 완료");
            }

            // 5. 도끼 속도 매니저 (활성화 체크)
            if (getConfig().getBoolean("axe_speed_system.enabled", true)) {
                getLogger().info("도끼 공격속도 매니저 초기화 중...");
                axeSpeedManager = new AxeSpeedManager(this);
                getLogger().info("도끼 공격속도 매니저 초기화 완료");
            }

            // 6. 드래곤 보상 매니저 (활성화 체크)
            if (getConfig().getBoolean("dragon_reward_system.enabled", true)) {
                getLogger().info("드래곤 보상 매니저 초기화 중...");
                dragonRewardManager = new DragonRewardManager(this);
                getLogger().info("드래곤 보상 매니저 초기화 완료");
            }

            // 7. NPC 교환 매니저 (활성화 체크)
            if (getConfig().getBoolean("npc_trade_system.enabled", true)) {
                getLogger().info("NPC 교환 매니저 초기화 중...");
                npcTradeManager = new NPCTradeManager(this);
                getLogger().info("NPC 교환 매니저 초기화 완료");
            }

            // 8. 엔더 리셋 매니저 (새로 추가 - 활성화 체크)
            if (getConfig().getBoolean("ender_reset_system.enabled", false)) {
                getLogger().info("엔더 리셋 매니저 초기화 중...");
                try {
                    enderResetManager = new EnderResetManager(this);
                    getLogger().info("엔더 리셋 매니저 초기화 완료");
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "엔더 리셋 매니저 초기화 실패 - 계속 진행", e);
                    enderResetManager = null;
                }
            } else {
                getLogger().info("엔더 리셋 시스템 비활성화됨 (설정에서 활성화 가능)");
            }

            getLogger().info("모든 매니저 초기화 완료!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "매니저 초기화 실패", e);
            throw new RuntimeException("매니저 초기화 실패", e);
        }
    }

    /**
     * 서버 기능 감지
     */
    private void loadServerFeatures() {
        try {
            // GGMCore 연동 체크
            boolean ggmCoreAvailable = getServer().getPluginManager().getPlugin("GGMCore") != null;
            getLogger().info("GGMCore 연동: " + (ggmCoreAvailable ? "사용 가능" : "독립 모드"));

            // Vault 체크
            boolean vaultAvailable = getServer().getPluginManager().getPlugin("Vault") != null;
            getLogger().info("Vault 지원: " + (vaultAvailable ? "사용 가능" : "비활성화"));

            // PlaceholderAPI 체크
            boolean papiAvailable = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
            getLogger().info("PlaceholderAPI 지원: " + (papiAvailable ? "사용 가능" : "비활성화"));

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "서버 기능 감지 중 오류", e);
        }
    }

    /**
     * 명령어 등록
     */
    private void registerCommands() {
        try {
            // 메인 명령어
            getCommand("survival").setExecutor(new SurvivalCommand(this));

            // 직업 시스템 명령어
            if (jobManager != null) {
                getCommand("job").setExecutor(new JobCommand(this));
            }

            // 강화 시스템 명령어
            if (enchantUpgradeManager != null) {
                getCommand("upgrade").setExecutor(new UpgradeCommand(this));
            }

            // 거래 시스템 명령어
            if (npcTradeManager != null) {
                getCommand("trade").setExecutor(new TradeCommand(this));
            }

            // 드래곤 시스템 명령어
            if (dragonRewardManager != null) {
                getCommand("dragon").setExecutor(new DragonCommand(this));
            }

            // 엔더 리셋 명령어
            if (enderResetManager != null) {
                getCommand("enderreset").setExecutor(new EnderResetCommand(this));
            }

            getLogger().info("모든 명령어 등록 완료");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "명령어 등록 실패", e);
            throw new RuntimeException("명령어 등록 실패", e);
        }
    }

    /**
     * 이벤트 리스너 등록
     */
    private void registerListeners() {
        try {
            // 플레이어 관련 리스너
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);

            // 직업 시스템 리스너
            if (jobManager != null) {
                getServer().getPluginManager().registerEvents(new JobListener(this), this);
            }

            // 강화 시스템 리스너
            if (enchantUpgradeManager != null) {
                getServer().getPluginManager().registerEvents(new UpgradeListener(this), this);
            }

            // 드래곤 보상 리스너
            if (dragonRewardManager != null) {
                getServer().getPluginManager().registerEvents(new DragonListener(this), this);
            }

            // NPC 교환 리스너
            if (npcTradeManager != null) {
                getServer().getPluginManager().registerEvents(new NPCTradeListener(this), this);
            }

            // 엔더 리셋 리스너
            if (enderResetManager != null) {
                getServer().getPluginManager().registerEvents(new EnderResetListener(this), this);
            }

            getLogger().info("모든 이벤트 리스너 등록 완료");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "리스너 등록 실패", e);
            throw new RuntimeException("리스너 등록 실패", e);
        }
    }

    /**
     * 스케줄러 시작
     */
    private void startSchedulers() {
        try {
            // 자동 저장 태스크 (5분마다)
            autoSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    saveAllPlayerData();
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "자동 저장 중 오류", e);
                }
            }, 6000L, 6000L); // 5분 = 6000틱

            // 메모리 정리 태스크 (30분마다)
            memoryCleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    cleanupMemory();
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "메모리 정리 중 오류", e);
                }
            }, 36000L, 36000L); // 30분 = 36000틱

            getLogger().info("모든 스케줄러 태스크 시작 완료");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "스케줄러 시작 실패", e);
        }
    }

    /**
     * 모든 플레이어 데이터 저장
     */
    private CompletableFuture<Void> saveAllPlayerData() {
        return CompletableFuture.runAsync(() -> {
            try {
                int savedCount = 0;

                for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                    try {
                        // 각 매니저의 플레이어 데이터 저장
                        if (jobManager != null) {
                            jobManager.savePlayerData(player).join();
                        }

                        // 추가 매니저들도 필요시 저장
                        savedCount++;

                    } catch (Exception e) {
                        getLogger().log(Level.WARNING,
                                "플레이어 데이터 저장 실패: " + player.getName(), e);
                    }
                }

                if (savedCount > 0) {
                    getLogger().info(savedCount + "명의 플레이어 데이터 저장 완료");
                }

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "전체 플레이어 데이터 저장 실패", e);
            }
        });
    }

    /**
     * 메모리 정리
     */
    private void cleanupMemory() {
        try {
            // 각 매니저의 캐시 정리
            if (jobManager != null) {
                jobManager.cleanupCache();
            }

            if (axeSpeedManager != null) {
                axeSpeedManager.cleanupCache();
            }

            // 시스템 GC 제안
            System.gc();

            getLogger().info("메모리 정리 완료");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "메모리 정리 중 오류", e);
        }
    }

    /**
     * 스케줄러 정리
     */
    private void cleanupSchedulers() {
        try {
            if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
                autoSaveTask.cancel();
            }

            if (memoryCleanupTask != null && !memoryCleanupTask.isCancelled()) {
                memoryCleanupTask.cancel();
            }

            // 모든 플러그인 태스크 취소
            getServer().getScheduler().cancelTasks(this);
            getLogger().info("모든 스케줄러 태스크 정리 완료");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "스케줄러 정리 중 오류", e);
        }
    }

    /**
     * 매니저들 안전 종료
     */
    private void shutdownManagers() {
        try {
            getLogger().info("매니저들 종료 중...");

            // 역순으로 종료 (초기화 순서의 반대)

            // 엔더 리셋 매니저 먼저 종료
            if (enderResetManager != null) {
                enderResetManager.shutdown();
                getLogger().info("EnderResetManager 종료 완료");
            }

            if (npcTradeManager != null) {
                safeShutdown(npcTradeManager, "NPCTradeManager");
            }

            if (dragonRewardManager != null) {
                safeShutdown(dragonRewardManager, "DragonRewardManager");
            }

            if (axeSpeedManager != null) {
                axeSpeedManager.onDisable();
                getLogger().info("AxeSpeedManager 종료 완료");
            }

            if (enchantUpgradeManager != null) {
                safeShutdown(enchantUpgradeManager, "EnchantUpgradeManager");
            }

            if (jobManager != null) {
                jobManager.shutdown();
                getLogger().info("JobManager 종료 완료");
            }

            if (economyManager != null) {
                safeShutdown(economyManager, "EconomyManager");
            }

            // 데이터베이스 매니저는 마지막에 종료
            if (databaseManager != null) {
                databaseManager.closeConnection();
                getLogger().info("DatabaseManager 종료 완료");
            }

            getLogger().info("모든 매니저 안전 종료 완료");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "매니저 종료 중 오류", e);
        }
    }

    /**
     * 매니저 안전 종료 헬퍼
     */
    private void safeShutdown(Object manager, String managerName) {
        try {
            manager.getClass().getMethod("shutdown").invoke(manager);
            getLogger().info(managerName + " 종료 완료");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, managerName + " 종료 중 오류", e);
        }
    }

    /**
     * 성공적인 초기화 로그
     */
    private void logSuccessfulInitialization() {
        getLogger().info("==========================================");
        getLogger().info("   GGMSurvival 초기화 완료!");
        getLogger().info("==========================================");
        getLogger().info("활성화된 시스템:");

        if (jobManager != null) {
            getLogger().info("- 직업 시스템: 활성화");
        }

        if (enchantUpgradeManager != null) {
            getLogger().info("- 강화 시스템: 활성화");
        }

        if (axeSpeedManager != null) {
            getLogger().info("- 도끼 속도 시스템: 활성화");
        }

        if (dragonRewardManager != null) {
            getLogger().info("- 드래곤 보상 시스템: 활성화");
        }

        if (npcTradeManager != null) {
            getLogger().info("- NPC 교환 시스템: 활성화");
        }

        if (enderResetManager != null) {
            getLogger().info("- 엔더 리셋 시스템: 활성화");
        }

        if (economyManager != null && economyManager.isGGMCoreConnected()) {
            getLogger().info("- GGMCore 경제 연동: 성공");
        }

        getLogger().info("서버 준비 완료! 플레이어 접속 대기 중...");
        getLogger().info("==========================================");
    }

    // Getter 메서드들
    public static GGMSurvival getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public EnchantUpgradeManager getEnchantUpgradeManager() {
        return enchantUpgradeManager;
    }

    public AxeSpeedManager getAxeSpeedManager() {
        return axeSpeedManager;
    }

    public DragonRewardManager getDragonRewardManager() {
        return dragonRewardManager;
    }

    public NPCTradeManager getNPCTradeManager() {
        return npcTradeManager;
    }

    public EnderResetManager getEnderResetManager() {
        return enderResetManager;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isShuttingDown() {
        return shutdownInProgress;
    }
}