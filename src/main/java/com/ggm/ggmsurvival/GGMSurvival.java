// 완전한 GGMSurvival.java - 개선된 직업 시스템과 강화 시스템
package com.ggm.ggmsurvival;

import org.bukkit.plugin.java.JavaPlugin;
import com.ggm.ggmsurvival.commands.*;
import com.ggm.ggmsurvival.listeners.*;
import com.ggm.ggmsurvival.managers.*;

public class GGMSurvival extends JavaPlugin {

    private static GGMSurvival instance;

    // 매니저들
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private JobManager jobManager;
    private EnchantUpgradeManager enchantUpgradeManager;
    private DragonRewardManager dragonRewardManager;
    private NPCTradeManager npcTradeManager;

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
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("§a✓ 직업 시스템: 직업이 없을 때만 변경 가능");
            getLogger().info("§a✓ 강화 시스템: 인첸트된 아이템도 강화 가능");
            getLogger().info("§a✓ GUI 강화: /upgrade gui 명령어 추가");
            getLogger().info("§a✓ 즉시 강화: /upgrade direct 명령어 추가");
            getLogger().info("§a✓ 검증 강화: 중복 직업 선택 방지");
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("모든 기능이 성공적으로 로드되었습니다!");

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
            getLogger().info("✓ 데이터베이스 매니저 초기화 완료");

            // 경제 매니저 (항상 필요 - GGMCore 연동)
            economyManager = new EconomyManager(this);
            getLogger().info("✓ 경제 매니저 초기화 완료");

            // 직업 시스템 매니저 (모든 서버에서 활성화)
            if (isFeatureEnabled("job_system")) {
                jobManager = new JobManager(this);
                getLogger().info("✓ 직업 시스템 매니저 초기화 완료 (강화된 검증)");
            }

            // 야생 서버 전용 기능들
            if (isFeatureEnabled("upgrade_system")) {
                enchantUpgradeManager = new EnchantUpgradeManager(this);
                getLogger().info("✓ 강화 시스템 매니저 초기화 완료 (인첸트된 아이템 지원)");
            }

            if (isFeatureEnabled("dragon_reward")) {
                dragonRewardManager = new DragonRewardManager(this);
                getLogger().info("✓ 드래곤 보상 매니저 초기화 완료");
            }

            if (isFeatureEnabled("npc_trading")) {
                npcTradeManager = new NPCTradeManager(this);
                getLogger().info("✓ NPC 교환 매니저 초기화 완료");
            }

        } catch (Exception e) {
            getLogger().severe("매니저 초기화 중 오류: " + e.getMessage());
            throw e;
        }
    }

    private void registerCommands() {
        try {
            // 직업 명령어 (개선된 버전)
            if (jobManager != null) {
                JobCommand jobCommand = new JobCommand(this);
                getCommand("job").setExecutor(jobCommand);
                getLogger().info("✓ 직업 명령어 등록 완료 (/job)");
            }

            // 강화 명령어 (GUI 및 직접 강화 포함)
            if (enchantUpgradeManager != null) {
                UpgradeCommand upgradeCommand = new UpgradeCommand(this);
                getCommand("upgrade").setExecutor(upgradeCommand);
                getLogger().info("✓ 강화 명령어 등록 완료 (/upgrade gui, /upgrade direct)");
            }

            // 드래곤 명령어
            if (dragonRewardManager != null) {
                DragonCommand dragonCommand = new DragonCommand(this);
                getCommand("dragon").setExecutor(dragonCommand);
                getLogger().info("✓ 드래곤 명령어 등록 완료 (/dragon)");
            }

            // NPC 명령어
            if (npcTradeManager != null) {
                NPCCommand npcCommand = new NPCCommand(this);
                TradeCommand tradeCommand = new TradeCommand(this);
                getCommand("npc").setExecutor(npcCommand);
                getCommand("trade").setExecutor(tradeCommand);
                getLogger().info("✓ NPC 명령어 등록 완료 (/npc, /trade)");
            }

            // 서버 정보 명령어
            SurvivalCommand survivalCommand = new SurvivalCommand(this);
            getCommand("survival").setExecutor(survivalCommand);
            getLogger().info("✓ 서버 명령어 등록 완료 (/survival)");

        } catch (Exception e) {
            getLogger().severe("명령어 등록 중 오류: " + e.getMessage());
            throw e;
        }
    }

    private void registerListeners() {
        try {
            // 플레이어 기본 리스너
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
            getLogger().info("✓ 플레이어 리스너 등록 완료");

            // 직업 시스템 리스너 (JobManager에 포함)
            if (jobManager != null) {
                getServer().getPluginManager().registerEvents(jobManager, this);
                getLogger().info("✓ 직업 시스템 리스너 등록 완료");
            }

            // 강화 시스템 리스너 (EnchantUpgradeManager에 포함)
            if (enchantUpgradeManager != null) {
                getServer().getPluginManager().registerEvents(enchantUpgradeManager, this);
                getLogger().info("✓ 강화 시스템 리스너 등록 완료");
            }

            // 드래곤 보상 리스너
            if (dragonRewardManager != null) {
                getServer().getPluginManager().registerEvents(dragonRewardManager, this);
                getLogger().info("✓ 드래곤 보상 리스너 등록 완료");
            }

            // NPC 교환 리스너
            if (npcTradeManager != null) {
                getServer().getPluginManager().registerEvents(npcTradeManager, this);
                getLogger().info("✓ NPC 교환 리스너 등록 완료");
            }

        } catch (Exception e) {
            getLogger().severe("리스너 등록 중 오류: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 서버 기능 설정 로드
     */
    private void loadServerFeatures() {
        int port = getServer().getPort();
        getLogger().info("서버 포트: " + port);

        // 포트 기반 자동 설정
        String configKey = "server_features.server_configs." + port;
        if (getConfig().contains(configKey)) {
            getLogger().info("포트 기반 자동 설정 적용: " + port);
        } else {
            getLogger().info("기본 설정 사용");
        }
    }

    /**
     * 기능 활성화 여부 확인
     */
    public boolean isFeatureEnabled(String feature) {
        int port = getServer().getPort();
        String portConfigPath = "server_features.server_configs." + port + "." + feature;

        // 포트별 설정이 있으면 사용
        if (getConfig().contains(portConfigPath)) {
            return getConfig().getBoolean(portConfigPath);
        }

        // 기본 설정 사용
        return getConfig().getBoolean("server_features.features." + feature, false);
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

    public DragonRewardManager getDragonRewardManager() {
        return dragonRewardManager;
    }

    public NPCTradeManager getNPCTradeManager() {
        return npcTradeManager;
    }

    /**
     * 플러그인 리로드
     */
    public void reloadPlugin() {
        try {
            reloadConfig();
            getLogger().info("설정 파일이 리로드되었습니다.");
        } catch (Exception e) {
            getLogger().severe("플러그인 리로드 중 오류: " + e.getMessage());
        }
    }

    /**
     * 시스템 상태 확인
     */
    public void checkSystemStatus() {
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("GGMSurvival 시스템 상태:");
        getLogger().info("데이터베이스: " + (databaseManager != null ? "§a정상" : "§c비활성"));
        getLogger().info("경제 시스템: " + (economyManager != null ? "§a정상" : "§c비활성"));
        getLogger().info("직업 시스템: " + (jobManager != null ? "§a정상" : "§c비활성"));
        getLogger().info("강화 시스템: " + (enchantUpgradeManager != null ? "§a정상" : "§c비활성"));
        getLogger().info("드래곤 보상: " + (dragonRewardManager != null ? "§a정상" : "§c비활성"));
        getLogger().info("NPC 교환: " + (npcTradeManager != null ? "§a정상" : "§c비활성"));
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}