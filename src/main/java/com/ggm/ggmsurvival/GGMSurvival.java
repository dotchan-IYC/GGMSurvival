package com.ggm.ggmsurvival;

import org.bukkit.plugin.java.JavaPlugin;
import com.ggm.ggmsurvival.commands.*;
import com.ggm.ggmsurvival.listeners.*;
import com.ggm.ggmsurvival.managers.*;

public class GGMSurvival extends JavaPlugin {

    private static GGMSurvival instance;

    // 매니저들
    private DatabaseManager databaseManager;
    private JobManager jobManager;
    private EnchantUpgradeManager enchantUpgradeManager;
    private DragonRewardManager dragonRewardManager;
    private NPCTradeManager npcTradeManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        instance = this;

        // 설정 파일 생성
        saveDefaultConfig();

        try {
            // 매니저 초기화
            initializeManagers();

            // 명령어 등록
            registerCommands();

            // 이벤트 리스너 등록
            registerListeners();

            getLogger().info("GGMSurvival 플러그인이 활성화되었습니다!");
            getLogger().info("야생 서버 전용 기능들이 로드되었습니다!");

        } catch (Exception e) {
            getLogger().severe("플러그인 초기화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        try {
            // 데이터베이스 연결 종료
            if (databaseManager != null) {
                databaseManager.closeConnection();
            }

            getLogger().info("GGMSurvival 플러그인이 비활성화되었습니다!");

        } catch (Exception e) {
            getLogger().warning("플러그인 종료 중 오류: " + e.getMessage());
        }
    }

    private void initializeManagers() {
        try {
            // 서버 타입 감지 및 기능 설정 로드
            loadServerFeatures();

            // 데이터베이스 매니저 (항상 필요)
            databaseManager = new DatabaseManager(this);
            getLogger().info("데이터베이스 매니저 초기화 완료");

            // 경제 매니저 (항상 필요 - GGMCore 연동)
            economyManager = new EconomyManager(this);
            getLogger().info("경제 매니저 초기화 완료");

            // 직업 시스템 매니저 (모든 서버에서 활성화)
            if (isFeatureEnabled("job_system")) {
                jobManager = new JobManager(this);
                getLogger().info("직업 시스템 매니저 초기화 완료 (모든 서버 적용)");
            }

            // 야생 서버 전용 기능들
            if (isFeatureEnabled("upgrade_system")) {
                enchantUpgradeManager = new EnchantUpgradeManager(this);
                getLogger().info("강화 시스템 매니저 초기화 완료 (야생 서버 전용)");
            }

            if (isFeatureEnabled("dragon_reward")) {
                dragonRewardManager = new DragonRewardManager(this);
                getLogger().info("드래곤 보상 매니저 초기화 완료 (야생 서버 전용)");
            }

            if (isFeatureEnabled("npc_trading")) {
                npcTradeManager = new NPCTradeManager(this);
                getLogger().info("NPC 교환 매니저 초기화 완료 (야생 서버 전용)");
            }

            // 현재 서버 설정 정보 출력
            printServerConfiguration();

        } catch (Exception e) {
            getLogger().severe("매니저 초기화 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 서버 기능 설정 로드
     */
    private void loadServerFeatures() {
        boolean autoDetect = getConfig().getBoolean("server_features.auto_detect_server", true);

        if (autoDetect) {
            int port = getServer().getPort();
            String configPath = "server_features.server_configs." + port;

            if (getConfig().contains(configPath)) {
                getLogger().info("포트 " + port + "를 기반으로 서버 설정을 자동 감지했습니다.");

                // 해당 포트의 설정을 features에 복사
                for (String feature : getConfig().getConfigurationSection(configPath).getKeys(false)) {
                    boolean enabled = getConfig().getBoolean(configPath + "." + feature);
                    getConfig().set("server_features.features." + feature, enabled);
                }
            } else {
                getLogger().warning("포트 " + port + "에 대한 설정이 없습니다. 기본 설정을 사용합니다.");
            }
        }
    }

    /**
     * 기능이 활성화되어 있는지 확인
     */
    public boolean isFeatureEnabled(String feature) {
        return getConfig().getBoolean("server_features.features." + feature, false);
    }

    /**
     * 현재 서버 설정 정보 출력
     */
    private void printServerConfiguration() {
        getLogger().info("=== GGMSurvival 서버 설정 ===");
        getLogger().info("서버 포트: " + getServer().getPort());
        getLogger().info("직업 시스템: " + (isFeatureEnabled("job_system") ? "활성화" : "비활성화"));
        getLogger().info("직업 선택: " + (isFeatureEnabled("job_selection") ? "활성화" : "비활성화"));
        getLogger().info("강화 시스템: " + (isFeatureEnabled("upgrade_system") ? "활성화 (야생 전용)" : "비활성화"));
        getLogger().info("드래곤 보상: " + (isFeatureEnabled("dragon_reward") ? "활성화 (야생 전용)" : "비활성화"));
        getLogger().info("NPC 교환: " + (isFeatureEnabled("npc_trading") ? "활성화 (야생 전용)" : "비활성화"));
        getLogger().info("========================");
    }

    private void registerCommands() {
        try {
            // 직업 관련 명령어 (모든 서버)
            if (isFeatureEnabled("job_system")) {
                safeRegisterCommand("job", new JobCommand(this));
                safeRegisterCommand("jobs", new JobCommand(this));
            }

            // 야생 서버 전용 명령어들
            if (isFeatureEnabled("upgrade_system")) {
                safeRegisterCommand("upgrade", new UpgradeCommand(this));
                safeRegisterCommand("강화", new UpgradeCommand(this));
            }

            if (isFeatureEnabled("npc_trading")) {
                safeRegisterCommand("npc", new NPCCommand(this));
                safeRegisterCommand("trade", new TradeCommand(this));
            }

            if (isFeatureEnabled("dragon_reward")) {
                safeRegisterCommand("dragon", new DragonCommand(this));
            }

            // 기본 명령어 (모든 서버)
            safeRegisterCommand("survival", new SurvivalCommand(this));

            getLogger().info("명령어 등록 완료 (활성화된 기능에 따라)");

        } catch (Exception e) {
            getLogger().warning("명령어 등록 중 오류: " + e.getMessage());
        }
    }

    private void safeRegisterCommand(String commandName, Object executor) {
        try {
            if (getCommand(commandName) != null) {
                getCommand(commandName).setExecutor((org.bukkit.command.CommandExecutor) executor);
                getLogger().info(commandName + " 명령어 등록 완료");
            } else {
                getLogger().warning(commandName + " 명령어 등록 실패 - plugin.yml 확인 필요");
            }
        } catch (Exception e) {
            getLogger().warning(commandName + " 명령어 등록 중 오류: " + e.getMessage());
        }
    }

    private void registerListeners() {
        try {
            // 플레이어 관련 리스너 (모든 서버)
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

            // 직업 시스템 리스너 (모든 서버)
            if (jobManager != null) {
                getServer().getPluginManager().registerEvents(jobManager, this);
                getLogger().info("직업 시스템 리스너 등록 완료 (모든 서버)");
            }

            // 야생 서버 전용 리스너들
            if (enchantUpgradeManager != null) {
                getServer().getPluginManager().registerEvents(enchantUpgradeManager, this);
                getLogger().info("강화 시스템 리스너 등록 완료 (야생 전용)");
            }

            if (dragonRewardManager != null) {
                getServer().getPluginManager().registerEvents(dragonRewardManager, this);
                getLogger().info("드래곤 보상 리스너 등록 완료 (야생 전용)");
            }

            if (npcTradeManager != null) {
                getServer().getPluginManager().registerEvents(npcTradeManager, this);
                getLogger().info("NPC 교환 리스너 등록 완료 (야생 전용)");
            }

            getLogger().info("이벤트 리스너 등록 완료 (활성화된 기능에 따라)");

        } catch (Exception e) {
            getLogger().warning("리스너 등록 중 오류: " + e.getMessage());
        }
    }

    // Getter 메소드들
    public static GGMSurvival getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public EnchantUpgradeManager getEnchantUpgradeManager() {
        return enchantUpgradeManager;
    }

    public DragonRewardManager getDragonRewardManager() {
        return dragonRewardManager;
    }

    public NPCTradeManager getNPCTradeManager() {
        return npcTradeManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    // 설정 관련 유틸리티 메소드들
    public String getSafeConfigString(String path, String defaultValue) {
        try {
            return getConfig().getString(path, defaultValue);
        } catch (Exception e) {
            getLogger().warning("설정 읽기 실패: " + path + ", 기본값 사용: " + defaultValue);
            return defaultValue;
        }
    }

    public int getSafeConfigInt(String path, int defaultValue) {
        try {
            return getConfig().getInt(path, defaultValue);
        } catch (Exception e) {
            getLogger().warning("설정 읽기 실패: " + path + ", 기본값 사용: " + defaultValue);
            return defaultValue;
        }
    }

    public long getSafeConfigLong(String path, long defaultValue) {
        try {
            return getConfig().getLong(path, defaultValue);
        } catch (Exception e) {
            getLogger().warning("설정 읽기 실패: " + path + ", 기본값 사용: " + defaultValue);
            return defaultValue;
        }
    }

    public boolean getSafeConfigBoolean(String path, boolean defaultValue) {
        try {
            return getConfig().getBoolean(path, defaultValue);
        } catch (Exception e) {
            getLogger().warning("설정 읽기 실패: " + path + ", 기본값 사용: " + defaultValue);
            return defaultValue;
        }
    }
}