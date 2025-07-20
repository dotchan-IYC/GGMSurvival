// 업데이트된 GGMSurvival.java - 새로운 패치 시스템 적용
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
    private AxeSpeedManager axeSpeedManager;
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

            getLogger().info("GGMSurvival 새로운 패치가 활성화되었습니다!");
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("§6🎯 새로운 강화 시스템: 검, 도끼, 활, 흉갑만 강화 가능");
            getLogger().info("§e⚔️ 새로운 직업레벨: 몬스터 처치로 성장하는 직업");
            getLogger().info("§a✨ 경험치바 UI: 실시간 레벨 & 경험치 표시");
            getLogger().info("§c🔥 만렙 10 효과: 탱커(체력+4칸), 검사(크리티컬), 궁수(화살3발)");
            getLogger().info("§9🛡️ 도끼 공격속도: 강화 시 공격간격 감소");
            getLogger().info("§6🎖️ 10강 특수 효과: 발화, 출혈, 화염, 가시");
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("모든 새로운 기능이 성공적으로 로드되었습니다!");

        } catch (Exception e) {
            getLogger().severe("플러그인 초기화 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        try {
            // 모든 플레이어 데이터 저장
            if (jobManager != null) {
                getLogger().info("플레이어 직업 데이터 저장 중...");
                // 온라인 플레이어들의 데이터 강제 저장
                getServer().getOnlinePlayers().forEach(player -> {
                    // 저장 로직은 JobManager에서 처리
                });

                // JobManager 정리 (경험치바 태스크 중지)
                jobManager.onDisable();
            }

            // 데이터베이스 연결 종료
            if (databaseManager != null) {
                databaseManager.closeConnection();
            }

            getLogger().info("GGMSurvival 새로운 패치가 안전하게 비활성화되었습니다!");

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

            // 새로운 직업레벨 시스템 매니저 (모든 서버에서 활성화)
            if (isFeatureEnabled("job_system")) {
                jobManager = new JobManager(this);
                getLogger().info("✓ 새로운 직업레벨 시스템 매니저 초기화 완료");
            }

            // 새로운 강화 시스템 매니저 (야생 서버 전용)
            if (isFeatureEnabled("upgrade_system")) {
                enchantUpgradeManager = new EnchantUpgradeManager(this);
                getLogger().info("✓ 새로운 강화 시스템 매니저 초기화 완료 (검,도끼,활,흉갑만)");

                // 도끼 공격속도 시스템 매니저
                axeSpeedManager = new AxeSpeedManager(this);
                getLogger().info("✓ 도끼 공격속도 시스템 매니저 초기화 완료");
            }

            // 드래곤 보상 매니저
            if (isFeatureEnabled("dragon_reward")) {
                dragonRewardManager = new DragonRewardManager(this);
                getLogger().info("✓ 드래곤 보상 매니저 초기화 완료");
            }

            // NPC 교환 매니저
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
            // 새로운 직업 명령어 (레벨 관리 기능 포함)
            if (jobManager != null) {
                JobCommand jobCommand = new JobCommand(this);
                getCommand("job").setExecutor(jobCommand);
                getCommand("job").setTabCompleter(jobCommand);
                getLogger().info("✓ 새로운 직업 명령어 등록 완료");
            }

            // 새로운 강화 명령어
            if (enchantUpgradeManager != null) {
                UpgradeCommand upgradeCommand = new UpgradeCommand(this);
                getCommand("upgrade").setExecutor(upgradeCommand);
                getCommand("upgrade").setTabCompleter(upgradeCommand);
                getLogger().info("✓ 새로운 강화 명령어 등록 완료");
            }

            // NPC 명령어
            if (npcTradeManager != null) {
                getCommand("npc").setExecutor(new NPCCommand(this));
                getCommand("trade").setExecutor(new TradeCommand(this));
                getLogger().info("✓ NPC 교환 명령어 등록 완료");
            }

            // 드래곤 명령어
            if (dragonRewardManager != null) {
                getCommand("dragon").setExecutor(new DragonCommand(this));
                getLogger().info("✓ 드래곤 명령어 등록 완료");
            }

            // 메인 명령어
            getCommand("survival").setExecutor(new SurvivalCommand(this));
            getLogger().info("✓ 메인 명령어 등록 완료");

        } catch (Exception e) {
            getLogger().severe("명령어 등록 중 오류: " + e.getMessage());
            throw e;
        }
    }

    private void registerListeners() {
        try {
            // 수정된 플레이어 기본 리스너
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
            getLogger().info("✓ 수정된 플레이어 리스너 등록 완료");

            // 새로운 직업레벨 시스템 리스너
            if (jobManager != null) {
                getServer().getPluginManager().registerEvents(jobManager, this);

                // 새로운 직업 선택 GUI 리스너
                getServer().getPluginManager().registerEvents(new JobGUIListener(this), this);
                getLogger().info("✓ 새로운 직업 시스템 리스너 등록 완료");
            }

            // 새로운 강화 시스템 리스너
            if (enchantUpgradeManager != null) {
                getServer().getPluginManager().registerEvents(enchantUpgradeManager, this);

                // 강화 GUI 리스너
                getServer().getPluginManager().registerEvents(new UpgradeGUIListener(this), this);
                getLogger().info("✓ 새로운 강화 시스템 리스너 등록 완료");
            }

            // 도끼 공격속도 시스템 리스너
            if (axeSpeedManager != null) {
                getServer().getPluginManager().registerEvents(axeSpeedManager, this);
                getLogger().info("✓ 도끼 공격속도 시스템 리스너 등록 완료");
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
     * 서버 기능 로드
     */
    private void loadServerFeatures() {
        try {
            // 현재 서버 포트 확인
            int port = getServer().getPort();
            getLogger().info("서버 포트 감지: " + port);

            // 포트별 기능 자동 설정
            switch (port) {
                case 25565: // 로비 서버
                    getLogger().info("로비 서버로 감지됨 - 직업 시스템만 활성화");
                    break;
                case 25566: // 건축 서버
                    getLogger().info("건축 서버로 감지됨 - 직업 시스템만 활성화");
                    break;
                case 25567: // 야생 서버
                    getLogger().info("야생 서버로 감지됨 - 모든 기능 활성화");
                    break;
                case 25568: // 마을 서버
                    getLogger().info("마을 서버로 감지됨 - 직업 시스템만 활성화");
                    break;
                default:
                    getLogger().info("기본 서버로 설정됨 - config.yml 설정 사용");
                    break;
            }

        } catch (Exception e) {
            getLogger().warning("서버 기능 로드 중 오류: " + e.getMessage());
        }
    }

    /**
     * 기능 활성화 여부 확인
     */
    public boolean isFeatureEnabled(String feature) {
        int port = getServer().getPort();

        // 포트별 자동 설정
        switch (port) {
            case 25565: // 로비
            case 25566: // 건축
            case 25568: // 마을
                return feature.equals("job_system");

            case 25567: // 야생
                return true; // 모든 기능 활성화

            default:
                // config.yml 설정 사용
                return getConfig().getBoolean("server_features.features." + feature, false);
        }
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

    public ScoreboardIntegration getScoreboardIntegration() {
        return jobManager != null ? jobManager.getScoreboardIntegration() : null;
    }

    public DragonRewardManager getDragonRewardManager() {
        return dragonRewardManager;
    }

    public NPCTradeManager getNPCTradeManager() {
        return npcTradeManager;
    }

    /**
     * 플러그인 정보 표시
     */
    public void showPluginInfo() {
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("GGMSurvival 새로운 패치 정보:");
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("🔥 새로운 강화 시스템:");
        getLogger().info("   • 강화 가능: 검, 도끼, 활, 흉갑만");
        getLogger().info("   • 검/활: 위력 3% 증가");
        getLogger().info("   • 도끼: 공격간격 2% 감소 (더 빨라짐)");
        getLogger().info("   • 흉갑: 방어력 3% 증가");
        getLogger().info("   • 10강: 발화, 출혈, 화염, 가시 효과");
        getLogger().info("");
        getLogger().info("⚔️ 새로운 직업레벨 시스템:");
        getLogger().info("   • 몬스터 처치로 경험치 획득");
        getLogger().info("   • 최대 레벨 10까지 성장");
        getLogger().info("   • 레벨 5: 각 직업별 특수 능력");
        getLogger().info("   • 탱커: 흉갑 착용시 체력 +2칸");
        getLogger().info("   • 검사: 검 사용시 공격속도 증가");
        getLogger().info("   • 궁수: 가죽장화 착용시 이동속도 +20%");
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 설정 리로드
     */
    public void reloadPluginConfig() {
        reloadConfig();
        getLogger().info("설정 파일이 리로드되었습니다.");

        // 설정 변경사항 적용
        if (jobManager != null) {
            // 직업 시스템 설정 재적용
        }

        if (enchantUpgradeManager != null) {
            // 강화 시스템 설정 재적용
        }
    }
}