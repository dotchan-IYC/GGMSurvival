// 완전 안정화된 SurvivalCommand.java
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * 완전 안정화된 야생 서버 메인 명령어 처리기
 * - 서버 정보 및 상태 확인
 * - 관리자 명령어 지원
 * - 강력한 예외 처리
 */
public class SurvivalCommand implements CommandExecutor {

    private final GGMSurvival plugin;

    public SurvivalCommand(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // 기본 정보 표시
            if (args.length == 0) {
                showServerInfo(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "info":
                case "정보":
                    showDetailedInfo(sender);
                    break;

                case "reload":
                case "리로드":
                    handleReloadCommand(sender);
                    break;

                case "stats":
                case "통계":
                    showServerStats(sender);
                    break;

                case "patch":
                case "패치":
                    showPatchInfo(sender);
                    break;

                case "help":
                case "도움말":
                    showHelpCommand(sender);
                    break;

                default:
                    sender.sendMessage("§c알 수 없는 명령어입니다. §7/survival help §c를 참고하세요.");
                    break;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "야생 명령어 처리 중 오류: " + sender.getName() + " - " + String.join(" ", args), e);
            sender.sendMessage("§c명령어 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            return true;
        }
    }

    /**
     * 기본 서버 정보 표시
     */
    private void showServerInfo(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§lGGM 야생 서버 정보");
            sender.sendMessage("");
            sender.sendMessage("§7플러그인 버전: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§7서버 포트: §f" + plugin.getServer().getPort());
            sender.sendMessage("§7온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size() + "명");

            // 초기화 상태 표시
            if (plugin.isInitialized()) {
                sender.sendMessage("§7플러그인 상태: §a정상 작동");
            } else {
                sender.sendMessage("§7플러그인 상태: §c초기화 중...");
            }

            sender.sendMessage("");
            sender.sendMessage("§a활성화된 시스템:");

            // 직업 시스템
            if (plugin.isFeatureEnabled("job_system")) {
                sender.sendMessage("§7• §a직업 시스템 §7- 몬스터 처치로 성장");
            }

            // 강화 시스템
            if (plugin.isFeatureEnabled("upgrade_system")) {
                sender.sendMessage("§7• §a강화 시스템 §7- 검/도끼/활/흉갑만");
            }

            // 드래곤 보상
            if (plugin.isFeatureEnabled("dragon_reward")) {
                sender.sendMessage("§7• §a드래곤 보상 §7- 드래곤 처치 보상");
            }

            // NPC 교환
            if (plugin.isFeatureEnabled("npc_trading")) {
                sender.sendMessage("§7• §aNPC 교환 §7- 아이템 교환");
            }

            sender.sendMessage("");
            sender.sendMessage("§7상세 정보: §e/survival info");
            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "서버 정보 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 상세 정보 표시
     */
    private void showDetailedInfo(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§lGGM 야생 서버 상세 정보");
            sender.sendMessage("");

            // 플러그인 정보
            sender.sendMessage("§a플러그인 정보:");
            sender.sendMessage("§7• 이름: §f" + plugin.getDescription().getName());
            sender.sendMessage("§7• 버전: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§7• 제작자: §f" + String.join(", ", plugin.getDescription().getAuthors()));
            sender.sendMessage("§7• 웹사이트: §f" + plugin.getDescription().getWebsite());

            // 서버 정보
            sender.sendMessage("");
            sender.sendMessage("§a서버 정보:");
            sender.sendMessage("§7• 버킷 버전: §f" + Bukkit.getVersion());
            sender.sendMessage("§7• API 버전: §f" + Bukkit.getBukkitVersion());
            sender.sendMessage("§7• 온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());

            // 시스템 상태
            sender.sendMessage("");
            sender.sendMessage("§a시스템 상태:");

            // 데이터베이스 상태
            if (plugin.getDatabaseManager() != null) {
                boolean dbConnected = plugin.getDatabaseManager().testConnection();
                sender.sendMessage("§7• 데이터베이스: " + (dbConnected ? "§a연결됨" : "§c연결 안됨"));
            }

            // 경제 시스템 상태
            if (plugin.getEconomyManager() != null) {
                boolean ggmCoreConnected = plugin.getEconomyManager().isGGMCoreConnected();
                sender.sendMessage("§7• GGMCore 연동: " + (ggmCoreConnected ? "§a연결됨" : "§c독립 모드"));
            }

            // 각 매니저 상태
            sender.sendMessage("§7• 직업 매니저: " + (plugin.getJobManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• 강화 매니저: " + (plugin.getEnchantUpgradeManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• 도끼속도 매니저: " + (plugin.getAxeSpeedManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• 드래곤보상 매니저: " + (plugin.getDragonRewardManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• NPC교환 매니저: " + (plugin.getNPCTradeManager() != null ? "§a활성화" : "§c비활성화"));

            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "상세 정보 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c상세 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 리로드 명령어 처리 (관리자 전용)
     */
    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("ggm.survival.admin")) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
            return;
        }

        try {
            sender.sendMessage("§e설정 파일을 리로드하고 있습니다...");

            // 설정 파일 리로드
            plugin.reloadConfig();

            // 각 시스템별 상태 확인
            sender.sendMessage("");
            sender.sendMessage("§a리로드 완료! 시스템 상태:");

            // 직업 시스템
            boolean jobSystemEnabled = plugin.getConfig().getBoolean("job_system.enabled", true);
            sender.sendMessage("§7- 직업 시스템: " + (jobSystemEnabled ? "§a활성화" : "§c비활성화"));

            // 강화 시스템
            boolean upgradeSystemEnabled = plugin.getConfig().getBoolean("upgrade_system.enabled", true);
            sender.sendMessage("§7- 강화 시스템: " + (upgradeSystemEnabled ? "§a활성화" : "§c비활성화"));

            // 드래곤 보상
            boolean dragonRewardEnabled = plugin.getConfig().getBoolean("dragon_reward.enabled", true);
            sender.sendMessage("§7- 드래곤 보상: " + (dragonRewardEnabled ? "§a활성화" : "§c비활성화"));

            // NPC 교환
            boolean npcTradeEnabled = plugin.getConfig().getBoolean("npc_system.enabled", true);
            sender.sendMessage("§7- NPC 교환: " + (npcTradeEnabled ? "§a활성화" : "§c비활성화"));

            plugin.getLogger().info(sender.getName() + "이(가) 플러그인 설정을 리로드했습니다.");

        } catch (Exception e) {
            sender.sendMessage("§c설정 리로드 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "설정 리로드 실패: " + sender.getName(), e);
        }
    }

    /**
     * 서버 통계 표시
     */
    private void showServerStats(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l야생 서버 통계");
            sender.sendMessage("");

            // 기본 서버 정보
            sender.sendMessage("§a기본 정보:");
            sender.sendMessage("§7• 플러그인 버전: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§7• 온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size() + "명");

            // 경제 시스템 상태
            if (plugin.getEconomyManager() != null) {
                boolean ggmCoreConnected = plugin.getEconomyManager().isGGMCoreConnected();
                sender.sendMessage("§7• GGMCore 연동: " + (ggmCoreConnected ? "§a연결됨" : "§c연결 안됨"));

                if (sender.hasPermission("ggm.survival.admin")) {
                    // 관리자에게만 추가 정보 표시
                    sender.sendMessage("§7• 경제 캐시: §f" + plugin.getEconomyManager().getCacheSize() + "개");
                    sender.sendMessage("§7• 총 거래: §f" + plugin.getEconomyManager().getTotalTransactions() + "회");
                }
            }

            // 시스템별 상태
            sender.sendMessage("");
            sender.sendMessage("§a시스템 상태:");
            sender.sendMessage("§7• 직업 시스템: " + getSystemStatus("job_system.enabled"));
            sender.sendMessage("§7• 강화 시스템: " + getSystemStatus("upgrade_system.enabled"));
            sender.sendMessage("§7• 드래곤 보상: " + getSystemStatus("dragon_reward.enabled"));
            sender.sendMessage("§7• NPC 교환: " + getSystemStatus("npc_system.enabled"));

            // 서버 성능 정보 (관리자만)
            if (sender.hasPermission("ggm.survival.admin")) {
                sender.sendMessage("");
                sender.sendMessage("§a성능 정보:");

                Runtime runtime = Runtime.getRuntime();
                long maxMemory = runtime.maxMemory() / 1024 / 1024;  // MB
                long totalMemory = runtime.totalMemory() / 1024 / 1024;
                long freeMemory = runtime.freeMemory() / 1024 / 1024;
                long usedMemory = totalMemory - freeMemory;

                sender.sendMessage("§7• 메모리 사용량: §f" + usedMemory + "MB / " + maxMemory + "MB");
                sender.sendMessage("§7• 플러그인 상태: " + (plugin.isInitialized() ? "§a정상" : "§e초기화 중"));

                // 데이터베이스 상태
                if (plugin.getDatabaseManager() != null) {
                    var dbStats = plugin.getDatabaseManager().getStats();
                    sender.sendMessage("§7• DB 연결: §f" + dbStats.activeConnections + "/" + dbStats.totalConnections);
                }
            }

            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "서버 통계 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c통계 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 패치 정보 표시
     */
    private void showPatchInfo(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§lGGM 야생 서버 패치 노트");
            sender.sendMessage("");
            sender.sendMessage("§a새로운 기능:");
            sender.sendMessage("§7• §6새로운 강화 시스템");
            sender.sendMessage("§7  - 검, 도끼, 활, 흉갑만 강화 가능");
            sender.sendMessage("§7  - 10강 달성 시 특수 효과 부여");
            sender.sendMessage("§7  - 도끼 강화 시 공격속도 증가");
            sender.sendMessage("");
            sender.sendMessage("§7• §e새로운 직업레벨 시스템");
            sender.sendMessage("§7  - 몬스터 처치로 경험치 획득");
            sender.sendMessage("§7  - 최대 10레벨까지 성장 가능");
            sender.sendMessage("§7  - 레벨 5/10에서 특수 능력 해제");
            sender.sendMessage("");
            sender.sendMessage("§7• §a경험치바 UI");
            sender.sendMessage("§7  - 실시간 레벨 & 경험치 표시");
            sender.sendMessage("§7  - 직업별 색상 구분");
            sender.sendMessage("");
            sender.sendMessage("§a직업별 특수 능력:");
            sender.sendMessage("§7• §9탱커 §7- 체력 증가, 방패 회복");
            sender.sendMessage("§7• §c검사 §7- 크리티컬 공격, 공격속도 증가");
            sender.sendMessage("§7• §a궁수 §7- 이동속도 증가, 트리플 샷");
            sender.sendMessage("");
            sender.sendMessage("§a10강 특수 효과:");
            sender.sendMessage("§7• §c검 §7- 출혈 효과 (지속 피해)");
            sender.sendMessage("§7• §6도끼 §7- 발화 효과 (화염 피해)");
            sender.sendMessage("§7• §a활 §7- 화염 화살 (화염 속성)");
            sender.sendMessage("§7• §9흉갑 §7- 가시 효과 (반사 피해)");
            sender.sendMessage("");
            sender.sendMessage("§6업데이트 일자: §f2025년 8월");
            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "패치 정보 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c패치 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 도움말 명령어 표시
     */
    private void showHelpCommand(CommandSender sender) {
        try {
            boolean isAdmin = sender.hasPermission("ggm.survival.admin");

            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l야생 서버 도움말");
            sender.sendMessage("");
            sender.sendMessage("§a일반 명령어:");
            sender.sendMessage("§7• §e/survival §7- 서버 기본 정보");
            sender.sendMessage("§7• §e/survival info §7- 상세 정보");
            sender.sendMessage("§7• §e/survival stats §7- 서버 통계");
            sender.sendMessage("§7• §e/survival patch §7- 패치 노트");

            if (isAdmin) {
                sender.sendMessage("");
                sender.sendMessage("§c관리자 명령어:");
                sender.sendMessage("§7• §e/survival reload §7- 설정 리로드");
            }

            sender.sendMessage("");
            sender.sendMessage("§a주요 시스템:");
            sender.sendMessage("§7• §e/job §7- 직업 시스템");
            sender.sendMessage("§7• §e/upgrade §7- 강화 시스템");

            if (plugin.isFeatureEnabled("dragon_reward")) {
                sender.sendMessage("§7• §e/dragon §7- 드래곤 보상");
            }

            if (plugin.isFeatureEnabled("npc_trading")) {
                sender.sendMessage("§7• §e/trade §7- NPC 교환");
            }

            sender.sendMessage("");
            sender.sendMessage("§7각 시스템의 상세 명령어는 §e<명령어> help §7를 참고하세요!");
            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "도움말 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c도움말 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 시스템 상태 확인
     */
    private String getSystemStatus(String configPath) {
        try {
            boolean enabled = plugin.getConfig().getBoolean(configPath, true);
            return enabled ? "§a활성화" : "§c비활성화";
        } catch (Exception e) {
            return "§7알 수 없음";
        }
    }
}