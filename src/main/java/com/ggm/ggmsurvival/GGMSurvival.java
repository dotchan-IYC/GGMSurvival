// 최종 GGMSurvival.java - EnderResetManager 완전 통합 버전
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
    private volatile EnderResetManager enderResetManager; // 새로 추가

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
                "ender_reset_system.enabled" // 새로 추가
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
     * 매니저 초기화 - 안전하고 순차적 (엔더 리셋 매니저 포함)
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
            getLogger().info("✅ 데이터베이스 매니저 초기화 완료 (HikariCP)");

            // 2. 경제 매니저 (GGMCore 연동)
            getLogger().info("경제 매니저 초기화 중...");
            economyManager = new EconomyManager(this);
            getLogger().info("✅ 경제 매니저 초기화 완료");

            // 3. 직업 매니저 (활성화 체크)
            if (getConfig().getBoolean("job_system.enabled", true)) {
                getLogger().info("직업 시스템 매니저 초기화 중...");
                jobManager = new JobManager(this);
                getLogger().info("✅ 직업 시스템 매니저 초기화 완료");
            }

            // 4. 강화 매니저 (활성화 체크)
            if (getConfig().getBoolean("upgrade_system.enabled", true)) {
                getLogger().info("강화 시스템 매니저 초기화 중...");
                enchantUpgradeManager = new EnchantUpgradeManager(this);
                getLogger().info("✅ 강화 시스템 매니저 초기화 완료");
            }

            // 5. 도끼 속도 매니저 (활성화 체크)
            if (getConfig().getBoolean("axe_speed_system.enabled", true)) {
                getLogger().info("도끼 공격속도 매니저 초기화 중...");
                axeSpeedManager = new AxeSpeedManager(this);
                getLogger().info("✅ 도끼 공격속도 매니저 초기화 완료");
            }

            // 6. 드래곤 보상 매니저 (활성화 체크)
            if (getConfig().getBoolean("dragon_reward_system.enabled", true)) {
                getLogger().info("드래곤 보상 매니저 초기화 중...");
                dragonRewardManager = new DragonRewardManager(this);
                getLogger().info("✅ 드래곤 보상 매니저 초기화 완료");
            }

            // 7. NPC 교환 매니저 (활성화 체크)
            if (getConfig().getBoolean("npc_trade_system.enabled", true)) {
                getLogger().info("NPC 교환 매니저 초기화 중...");
                npcTradeManager = new NPCTradeManager(this);
                getLogger().info("✅ NPC 교환 매니저 초기화 완료");
            }

            // 8. 엔더 리셋 매니저 (새로 추가 - 활성화 체크)
            if (getConfig().getBoolean("ender_reset_system.enabled", false)) {
                getLogger().info("엔더 리셋 매니저 초기화 중...");
                try {
                    enderResetManager = new EnderResetManager(this);
                    getLogger().info("✅ 엔더 리셋 매니저 초기화 완료");
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "엔더 리셋 매니저 초기화 실패 - 계속 진행", e);
                    enderResetManager = null;
                }
            } else {
                getLogger().info("⚠️ 엔더 리셋 시스템 비활성화됨 (설정에서 활성화 가능)");
            }

            getLogger().info("🎉 모든 매니저 초기화 완료!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "매니저 초기화 실패", e);
            throw new RuntimeException("매니저 초기화 실패", e);
        }
    }

    /**
     * 서버 기능 감지 (향상됨)
     */
    private void loadServerFeatures() {
        try {
            // GGMCore 연동 체크
            boolean ggmCoreAvailable = getServer().getPluginManager().getPlugin("GGMCore") != null;
            getLogger().info("GGMCore 연동: " + (ggmCoreAvailable ? "✅ 가능" : "❌ 독립 모드"));

            // Vault 연동 체크 (옵션)
            boolean vaultAvailable = getServer().getPluginManager().getPlugin("Vault") != null;
            getLogger().info("Vault 연동: " + (vaultAvailable ? "✅ 가능" : "⚠️ 비활성화"));

            // BungeeCord 환경 체크
            try {
                String serverName = getServer().getMotd();
                boolean bungeeCordMode = getConfig().getBoolean("ender_reset.target_server", "").length() > 0;
                getLogger().info("BungeeCord 모드: " + (bungeeCordMode ? "✅ 활성화" : "⚠️ 단일 서버"));
            } catch (Exception e) {
                getLogger().info("BungeeCord 모드: ⚠️ 확인 불가");
            }

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "서버 기능 감지 중 오류", e);
        }
    }

    /**
     * 명령어 등록 - 안전한 등록 (엔더 리셋 명령어 포함)
     */
    private void registerCommands() {
        try {
            // 기본 명령어들
            registerCommand("job", new JobCommand(this));
            registerCommand("강화", new UpgradeCommand(this));
            registerCommand("upgrade", new UpgradeCommand(this));
            registerCommand("trade", new TradeCommand(this));
            registerCommand("dragon", new DragonCommand(this));
            registerCommand("survival", new SurvivalCommand(this));
            registerCommand("ggmsurvival", new SurvivalCommand(this));

            // 엔더 리셋 명령어 (활성화 시)
            if (enderResetManager != null) {
                registerCommand("enderreset", new EnderResetCommand(this));
                getLogger().info("✅ 엔더 리셋 명령어 등록 완료");
            } else {
                getLogger().info("⚠️ 엔더 리셋 명령어 비활성화됨");
            }

            getLogger().info("✅ 모든 명령어 등록 완료");

        } catch (Exception e) {
            throw new RuntimeException("명령어 등록 실패", e);
        }
    }

    /**
     * 개별 명령어 등록 헬퍼
     */
    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        try {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(executor);

                // TabCompleter 설정 (지원하는 경우)
                if (executor instanceof org.bukkit.command.TabCompleter) {
                    getCommand(name).setTabCompleter((org.bukkit.command.TabCompleter) executor);
                }

                getLogger().fine("명령어 등록: /" + name);
            } else {
                getLogger().warning("명령어 등록 실패: /" + name + " (plugin.yml 확인 필요)");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "명령어 등록 실패: /" + name, e);
        }
    }

    /**
     * 리스너 등록 - 안전한 등록 (엔더 리스너 포함)
     */
    private void registerListeners() {
        try {
            // 기본 플레이어 리스너
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

            // 직업 시스템 리스너
            if (jobManager != null) {
                getServer().getPluginManager().registerEvents(jobManager, this);
                getServer().getPluginManager().registerEvents(new JobGUIListener(this), this);
            }

            // 강화 시스템 리스너
            if (enchantUpgradeManager != null) {
                getServer().getPluginManager().registerEvents(enchantUpgradeManager, this);
                getServer().getPluginManager().registerEvents(new UpgradeGUIListener(this), this);
            }

            // 도끼 공격속도 리스너
            if (axeSpeedManager != null) {
                getServer().getPluginManager().registerEvents(axeSpeedManager, this);
            }

            // 드래곤 보상 리스너
            if (dragonRewardManager != null) {
                getServer().getPluginManager().registerEvents(dragonRewardManager, this);
            }

            // NPC 교환 리스너
            if (npcTradeManager != null) {
                getServer().getPluginManager().registerEvents(npcTradeManager, this);
            }

            // 엔더 리스너 (새로 추가)
            if (enderResetManager != null) {
                getServer().getPluginManager().registerEvents(new EnderListener(this), this);
                getLogger().info("✅ 엔더 리스너 등록 완료");
            }

            getLogger().info("✅ 모든 이벤트 리스너 등록 완료");

        } catch (Exception e) {
            throw new RuntimeException("리스너 등록 실패", e);
        }
    }

    /**
     * 스코어보드 통합 반환 (JobManager 호환성)
     */
    public ScoreboardIntegration getScoreboardIntegration() {
        if (jobManager != null) {
            return jobManager.getScoreboardIntegration();
        }
        return null;
    }

    /**
     * EnderResetManager 재초기화 (리로드용)
     */
    public void reinitializeEnderResetManager() {
        try {
            if (getConfig().getBoolean("ender_reset_system.enabled", false)) {
                getLogger().info("EnderResetManager 재초기화 중...");
                enderResetManager = new EnderResetManager(this);

                // 새 리스너 등록
                getServer().getPluginManager().registerEvents(new EnderListener(this), this);

                getLogger().info("✅ EnderResetManager 재초기화 완료");
            } else {
                enderResetManager = null;
                getLogger().info("⚠️ EnderResetManager 비활성화됨");
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "EnderResetManager 재초기화 실패", e);
            enderResetManager = null;
        }
    }

    /**
     * 스케줄러 시작 - 성능 최적화 및 오류 수정
     */
    private void startSchedulers() {
        try {
            // 자동 저장 (5분마다)
            autoSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (!shutdownInProgress && initialized) {
                    try {
                        saveAllPlayerData();
                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "자동 저장 중 오류", e);
                    }
                }
            }, 6000L, 6000L); // 5분 간격

            // 메모리 정리 (30분마다) - 오타 수정: getSchedu -> getScheduler()
            memoryCleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (!shutdownInProgress && initialized) {
                    try {
                        performMemoryCleanup();
                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "메모리 정리 중 오류", e);
                    }
                }
            }, 36000L, 36000L); // 30분 간격

            getLogger().info("✅ 스케줄러 시작 완료");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "스케줄러 시작 실패", e);
            throw new RuntimeException("스케줄러 시작 실패", e);
        }
    }

    /**
     * 모든 플레이어 데이터 저장
     */
    private CompletableFuture<Void> saveAllPlayerData() {
        return CompletableFuture.runAsync(() -> {
            try {
                int savedCount = 0;

                if (jobManager != null) {
                    for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                        try {
                            jobManager.savePlayerJobData(player);
                            savedCount++;
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING,
                                    "플레이어 데이터 저장 실패: " + player.getName(), e);
                        }
                    }
                }

                getLogger().fine("플레이어 데이터 자동 저장 완료 (" + savedCount + "명)");

            } catch (Exception e) {
                getLogger().log(Level.WARNING, "전체 데이터 저장 중 오류", e);
            }
        });
    }

    /**
     * 메모리 정리 개선 (엔더 시스템 포함)
     */
    private void performMemoryCleanup() {
        try {
            long beforeMemory = getUsedMemory();

            // 매니저들의 캐시 정리
            if (jobManager != null) {
                jobManager.cleanupCache();
            }

            // 엔더 시스템 캐시 정리 (새로 추가)
            if (enderResetManager != null) {
                enderResetManager.cleanupCache();
            }

            // 리플렉션을 통한 안전한 메소드 호출
            cleanupManagerSafely(enchantUpgradeManager, "EnchantUpgradeManager");
            cleanupManagerSafely(dragonRewardManager, "DragonRewardManager");

            // 데이터베이스 매니저 유지보수
            if (databaseManager != null) {
                try {
                    databaseManager.getClass().getMethod("performMaintenance").invoke(databaseManager);
                } catch (Exception ignored) {
                    // performMaintenance 메소드가 없으면 무시
                }
            }

            // 가비지 컬렉션 권고 (강제 X)
            System.gc();

            long afterMemory = getUsedMemory();
            long cleanedMemory = beforeMemory - afterMemory;

            getLogger().info(String.format("✅ 메모리 정리 완료 (정리량: %.2f MB)",
                    cleanedMemory / 1024.0 / 1024.0));

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "메모리 정리 중 오류", e);
        }
    }

    /**
     * 매니저 안전 캐시 정리
     */
    private void cleanupManagerSafely(Object manager, String managerName) {
        if (manager == null) return;

        try {
            manager.getClass().getMethod("cleanupCache").invoke(manager);
            getLogger().fine(managerName + " 캐시 정리 완료");
        } catch (Exception ignored) {
            // cleanupCache 메소드가 없으면 무시
        }
    }

    /**
     * 현재 사용 중인 메모리 계산
     */
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * 스케줄러 정리 개선
     */
    private void cleanupSchedulers() {
        try {
            getLogger().info("스케줄러 정리 중...");

            if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
                autoSaveTask.cancel();
                getLogger().info("✅ 자동 저장 태스크 취소됨");
            }

            if (memoryCleanupTask != null && !memoryCleanupTask.isCancelled()) {
                memoryCleanupTask.cancel();
                getLogger().info("✅ 메모리 정리 태스크 취소됨");
            }

            // 모든 태스크 정리
            getServer().getScheduler().cancelTasks(this);
            getLogger().info("✅ 모든 스케줄러 태스크 정리 완료");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "스케줄러 정리 중 오류", e);
        }
    }

    /**
     * 매니저들 안전 종료 (엔더 시스템 포함)
     */
    private void shutdownManagers() {
        try {
            getLogger().info("매니저들 종료 중...");

            // 역순으로 종료 (초기화 순서의 반대)

            // 엔더 리셋 매니저 먼저 종료 (다른 시스템에 영향 줄 수 있으므로)
            if (enderResetManager != null) {
                enderResetManager.shutdown();
                getLogger().info("✅ EnderResetManager 종료 완료");
            }

            if (npcTradeManager != null) {
                safeShutdown(npcTradeManager, "NPCTradeManager");
            }

            if (dragonRewardManager != null) {
                safeShutdown(dragonRewardManager, "DragonRewardManager");
            }

            if (axeSpeedManager != null) {
                safeShutdown(axeSpeedManager, "AxeSpeedManager");
            }

            if (enchantUpgradeManager != null) {
                safeShutdown(enchantUpgradeManager, "EnchantUpgradeManager");
            }

            if (jobManager != null) {
                jobManager.shutdown();
                getLogger().info("✅ JobManager 종료 완료");
            }

            if (economyManager != null) {
                safeShutdown(economyManager, "EconomyManager");
            }

            // 데이터베이스 매니저는 마지막에 종료
            if (databaseManager != null) {
                databaseManager.closeConnection();
                getLogger().info("✅ DatabaseManager 종료 완료");
            }

            getLogger().info("✅ 모든 매니저 안전 종료 완료");

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
            getLogger().info("✅ " + managerName + " 종료 완료");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, managerName + " 종료 중 오류", e);
        }
    }

    /**
     * 성공적인 초기화 로그 (향상됨)
     */
    private void logSuccessfulInitialization() {
        getLogger().info("==========================================");
        getLogger().info("   🎉 GGMSurvival 초기화 완료! 🎉");
        getLogger().info("==========================================");
        getLogger().info("🔧 활성화된 시스템:");

        if (jobManager != null) {
            getLogger().info("  ✅ 직업 시스템 (탱커, 전사, 궁수)");
        }

        if (enchantUpgradeManager != null) {
            getLogger().info("  ✅ 강화 시스템 (1~10강)");
        }

        if (axeSpeedManager != null) {
            getLogger().info("  ✅ 도끼 공격속도 시스템");
        }

        if (dragonRewardManager != null) {
            getLogger().info("  ✅ 드래곤 보상 시스템");
        }

        if (npcTradeManager != null) {
            getLogger().info("  ✅ NPC 교환 시스템");
        }

        if (enderResetManager != null) {
            getLogger().info("  ✅ 엔더 자동 리셋 시스템");
            getLogger().info("    └ 리셋 시간: 매일 " +
                    String.format("%02d:%02d", enderResetManager.getResetHour(), enderResetManager.getResetMinute()));
            getLogger().info("    └ 엔드시티 차단: " +
                    (enderResetManager.isEndCityBlockingEnabled() ? "활성화" : "비활성화"));
        }

        getLogger().info("");
        getLogger().info("🗄️ 데이터베이스: " + databaseManager.getDatabaseInfo() + " (HikariCP)");

        if (economyManager != null) {
            try {
                boolean ggmCoreConnected = (Boolean) economyManager.getClass()
                        .getMethod("isGGMCoreConnected").invoke(economyManager);
                getLogger().info("💰 GGMCore 연동: " + (ggmCoreConnected ? "활성화" : "독립 모드"));
            } catch (Exception e) {
                getLogger().info("💰 GGMCore 연동: 독립 모드");
            }
        }

        getLogger().info("📊 온라인 플레이어: " + getServer().getOnlinePlayers().size() + "명");
        getLogger().info("🔧 서버 버전: " + getServer().getVersion());
        getLogger().info("==========================================");
    }

    // === Getter 메서드들 - Thread-Safe ===

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

    public NPCTradeManager getNpcTradeManager() {
        return npcTradeManager;
    }

    // 새로 추가된 Getter
    public EnderResetManager getEnderResetManager() {
        return enderResetManager;
    }

    public boolean isInitialized() {
        return initialized && !shutdownInProgress;
    }

    public boolean isShuttingDown() {
        return shutdownInProgress;
    }

    /**
     * 시스템 상태 요약
     */
    public String getSystemStatus() {
        StringBuilder status = new StringBuilder();
        status.append("GGMSurvival 상태:\n");
        status.append("- 초기화됨: ").append(initialized).append("\n");
        status.append("- 직업 시스템: ").append(jobManager != null ? "활성화" : "비활성화").append("\n");
        status.append("- 강화 시스템: ").append(enchantUpgradeManager != null ? "활성화" : "비활성화").append("\n");
        status.append("- 엔더 리셋: ").append(enderResetManager != null ? "활성화" : "비활성화").append("\n");
        status.append("- 데이터베이스: ").append(databaseManager != null && databaseManager.isInitialized() ? "연결됨" : "연결 안됨");

        return status.toString();
    }
}