// 완전 안정화된 GGMSurvival.java
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
            getLogger().info("GGMSurvival 안정화 버전 시작...");

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
     * 필수 설정 검증
     */
    private boolean validateRequiredConfig() {
        String[] requiredPaths = {
                "database.host",
                "database.database",
                "database.username",
                "job_system.enabled",
                "upgrade_system.enabled"
        };

        for (String path : requiredPaths) {
            if (!getConfig().contains(path)) {
                getLogger().severe("필수 설정 누락: " + path);
                return false;
            }
        }

        return true;
    }

    /**
     * 매니저 초기화 - 안전하고 순차적
     */
    private void initializeManagers() {
        try {
            // 서버 기능 감지
            loadServerFeatures();

            // 1. 데이터베이스 매니저 (최우선)
            databaseManager = new DatabaseManager(this);
            if (!databaseManager.testConnection()) {
                throw new RuntimeException("데이터베이스 연결 실패");
            }
            getLogger().info("데이터베이스 매니저 초기화 완료");

            // 2. 경제 매니저 (GGMCore 연동)
            economyManager = new EconomyManager(this);
            getLogger().info("경제 매니저 초기화 완료");

            // 3. 직업 시스템 매니저 (모든 서버)
            if (isFeatureEnabled("job_system")) {
                jobManager = new JobManager(this);
                getLogger().info("직업 시스템 매니저 초기화 완료");
            }

            // 4. 강화 시스템 매니저 (야생 서버)
            if (isFeatureEnabled("upgrade_system")) {
                enchantUpgradeManager = new EnchantUpgradeManager(this);
                axeSpeedManager = new AxeSpeedManager(this);
                getLogger().info("강화 시스템 매니저 초기화 완료");
            }

            // 5. 드래곤 보상 매니저
            if (isFeatureEnabled("dragon_reward")) {
                dragonRewardManager = new DragonRewardManager(this);
                getLogger().info("드래곤 보상 매니저 초기화 완료");
            }

            // 6. NPC 교환 매니저
            if (isFeatureEnabled("npc_trading")) {
                npcTradeManager = new NPCTradeManager(this);
                getLogger().info("NPC 교환 매니저 초기화 완료");
            }

        } catch (Exception e) {
            throw new RuntimeException("매니저 초기화 실패", e);
        }
    }

    /**
     * 명령어 등록 - 안전한 등록
     */
    private void registerCommands() {
        try {
            // 직업 명령어
            if (jobManager != null) {
                JobCommand jobCommand = new JobCommand(this);
                safeRegisterCommand("job", jobCommand, jobCommand);
            }

            // 강화 명령어
            if (enchantUpgradeManager != null) {
                UpgradeCommand upgradeCommand = new UpgradeCommand(this);
                safeRegisterCommand("upgrade", upgradeCommand, upgradeCommand);
            }

            // NPC 명령어
            if (npcTradeManager != null) {
                safeRegisterCommand("npc", new NPCCommand(this), null);
                safeRegisterCommand("trade", new TradeCommand(this), null);
            }

            // 드래곤 명령어
            if (dragonRewardManager != null) {
                safeRegisterCommand("dragon", new DragonCommand(this), null);
            }

            // 메인 명령어
            safeRegisterCommand("survival", new SurvivalCommand(this), null);

            getLogger().info("명령어 등록 완료");

        } catch (Exception e) {
            throw new RuntimeException("명령어 등록 실패", e);
        }
    }

    /**
     * 안전한 명령어 등록
     */
    private void safeRegisterCommand(String name, org.bukkit.command.CommandExecutor executor,
                                     org.bukkit.command.TabCompleter tabCompleter) {
        try {
            org.bukkit.command.PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(executor);
                if (tabCompleter != null) {
                    command.setTabCompleter(tabCompleter);
                }
                getLogger().info("명령어 등록: /" + name);
            } else {
                getLogger().warning("명령어 등록 실패: /" + name + " (plugin.yml 확인 필요)");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "명령어 등록 실패: /" + name, e);
        }
    }

    /**
     * 리스너 등록 - 안전한 등록
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

            getLogger().info("이벤트 리스너 등록 완료");

        } catch (Exception e) {
            throw new RuntimeException("리스너 등록 실패", e);
        }
    }

    /**
     * 스케줄러 시작 - 성능 최적화
     */
    private void startSchedulers() {
        try {
            // 자동 저장 (5분마다)
            autoSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (!shutdownInProgress && initialized) {
                    saveAllPlayerData();
                }
            }, 6000L, 6000L); // 5분 간격

            // 메모리 정리 (30분마다)
            memoryCleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (!shutdownInProgress && initialized) {
                    performMemoryCleanup();
                }
            }, 36000L, 36000L); // 30분 간격

            getLogger().info("스케줄러 시작 완료");

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "스케줄러 시작 실패", e);
        }
    }

    /**
     * 모든 플레이어 데이터 저장
     */
    private CompletableFuture<Void> saveAllPlayerData() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (jobManager != null) {
                    getServer().getOnlinePlayers().forEach(player -> {
                        try {
                            jobManager.savePlayerJobData(player);
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING,
                                    "플레이어 데이터 저장 실패: " + player.getName(), e);
                        }
                    });
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "전체 데이터 저장 중 오류", e);
            }
        });
    }

    /**
     * 메모리 정리
     */
    private void performMemoryCleanup() {
        try {
            if (jobManager != null) {
                jobManager.cleanupCache();
            }

            // 강제 가비지 컬렉션 (과도하지 않게)
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

            // 모든 태스크 정리
            getServer().getScheduler().cancelTasks(this);

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "스케줄러 정리 중 오류", e);
        }
    }

    /**
     * 매니저들 안전 종료
     */
    private void shutdownManagers() {
        try {
            if (jobManager != null) {
                jobManager.onDisable();
                jobManager = null;
            }

            if (databaseManager != null) {
                databaseManager.closeConnection();
                databaseManager = null;
            }

            // 다른 매니저들도 null로 설정
            economyManager = null;
            enchantUpgradeManager = null;
            axeSpeedManager = null;
            dragonRewardManager = null;
            npcTradeManager = null;

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "매니저 종료 중 오류", e);
        }
    }

    /**
     * 서버 기능 로드
     */
    private void loadServerFeatures() {
        try {
            int port = getServer().getPort();
            getLogger().info("서버 포트 감지: " + port);

            switch (port) {
                case 25565: // 로비
                case 25566: // 건축
                case 25568: // 마을
                    getLogger().info("제한된 기능 서버로 감지됨 - 직업 시스템만 활성화");
                    break;
                case 25567: // 야생
                    getLogger().info("야생 서버로 감지됨 - 모든 기능 활성화");
                    break;
                default:
                    getLogger().info("기본 서버로 설정됨 - config.yml 설정 사용");
                    break;
            }

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "서버 기능 로드 중 오류", e);
        }
    }

    /**
     * 기능 활성화 여부 확인
     */
    public boolean isFeatureEnabled(String feature) {
        try {
            int port = getServer().getPort();

            // 포트별 자동 설정
            switch (port) {
                case 25565: // 로비
                case 25566: // 건축
                case 25568: // 마을
                    return "job_system".equals(feature);

                case 25567: // 야생
                    return true; // 모든 기능 활성화

                default:
                    // config.yml 설정 사용
                    return getConfig().getBoolean("server_features.features." + feature, false);
            }

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "기능 확인 중 오류: " + feature, e);
            return false;
        }
    }

    /**
     * 초기화 성공 로그
     */
    private void logSuccessfulInitialization() {
        getLogger().info("==========================================");
        getLogger().info("GGMSurvival 안정화 버전 활성화 완료!");
        getLogger().info("==========================================");
        getLogger().info("새로운 강화 시스템: 검, 도끼, 활, 흉갑만 강화 가능");
        getLogger().info("새로운 직업레벨: 몬스터 처치로 성장하는 직업");
        getLogger().info("경험치바 UI: 실시간 레벨 & 경험치 표시");
        getLogger().info("만렙 10 효과: 탱커(체력+4칸), 검사(크리티컬), 궁수(화살3발)");
        getLogger().info("도끼 공격속도: 강화 시 공격간격 감소");
        getLogger().info("10강 특수 효과: 발화, 출혈, 화염, 가시");
        getLogger().info("==========================================");
        getLogger().info("모든 새로운 기능이 안전하게 로드되었습니다!");
    }

    // Thread-Safe Getter 메서드들
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

    public ScoreboardIntegration getScoreboardIntegration() {
        return jobManager != null ? jobManager.getScoreboardIntegration() : null;
    }

    /**
     * 플러그인 상태 확인
     */
    public boolean isInitialized() {
        return initialized;
    }

    public boolean isShuttingDown() {
        return shutdownInProgress;
    }
}