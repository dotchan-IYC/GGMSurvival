package com.ggm.ggmsurvival.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.ggm.ggmsurvival.GGMSurvival;

public class SurvivalCommand implements CommandExecutor {

    private final GGMSurvival plugin;

    public SurvivalCommand(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showSurvivalInfo(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
            case "정보":
                showSurvivalInfo(sender);
                break;
            case "reload":
                if (!sender.hasPermission("ggm.survival.admin")) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                reloadPlugin(sender);
                break;
            case "stats":
            case "통계":
                if (!sender.hasPermission("ggm.survival.admin")) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                showServerStats(sender);
                break;
            default:
                showSurvivalInfo(sender);
                break;
        }

        return true;
    }

    private void showSurvivalInfo(CommandSender sender) {
        String serverName = getServerName();
        boolean isWilderness = plugin.isFeatureEnabled("upgrade_system");

        sender.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (isWilderness) {
            sender.sendMessage("§e§l🌲 GGM 야생 서버 정보 🌲");
            sender.sendMessage("");
            sender.sendMessage("§a§l✨ 전체 기능 이용 가능:");
            sender.sendMessage("§7• §c직업 시스템 §7- 탱커, 검사, 궁수");
            sender.sendMessage("§7• §6G 강화 시스템 §7- 인챈트 테이블로 최대 10강");
            sender.sendMessage("§7• §5드래곤 보상 §7- 일일 100,000G 보상");
            sender.sendMessage("§7• §bNPC 교환 §7- 아이템을 G로 판매");
            sender.sendMessage("");
            sender.sendMessage("§e§l📋 주요 명령어:");
            sender.sendMessage("§7/job select §f- 직업 선택");
            sender.sendMessage("§7/upgrade info §f- 강화 정보");
            sender.sendMessage("§7/dragon today §f- 드래곤 처치 현황");
            sender.sendMessage("§7/trade prices §f- 교환 가격표");
            sender.sendMessage("");
            sender.sendMessage("§6즐거운 야생 생활 되세요! 🎮");
        } else {
            sender.sendMessage("§e§l🎮 GGM " + serverName + " 정보 🎮");
            sender.sendMessage("");
            sender.sendMessage("§a§l✨ 이용 가능한 기능:");
            sender.sendMessage("§7• §c직업 시스템 §7- 탱커, 검사, 궁수 효과");
            sender.sendMessage("§7• §a모든 서버 공통 §7- 직업 특성 적용");
            sender.sendMessage("");
            sender.sendMessage("§e§l📋 주요 명령어:");
            sender.sendMessage("§7/job select §f- 직업 선택");
            sender.sendMessage("§7/job info §f- 직업 정보 확인");
            sender.sendMessage("");
            sender.sendMessage("§c§l🌲 더 많은 기능을 원한다면:");
            sender.sendMessage("§7야생 서버에서 G 강화, 드래곤 보상,");
            sender.sendMessage("§7NPC 교환 등의 특별한 기능을 이용하세요!");
            sender.sendMessage("");
            sender.sendMessage("§6" + serverName + "에서 즐거운 시간 되세요! 🎮");
        }

        sender.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private String getServerName() {
        int port = plugin.getServer().getPort();
        return switch (port) {
            case 25565 -> "로비 서버";
            case 25566 -> "건축 서버";
            case 25567 -> "야생 서버";
            case 25568 -> "마을 서버";
            default -> "서버";
        };
    }

    private void reloadPlugin(CommandSender sender) {
        try {
            plugin.reloadConfig();
            sender.sendMessage("§a[GGM야생] 설정이 리로드되었습니다!");

            // 주요 설정 상태 표시
            boolean jobSystemEnabled = plugin.getConfig().getBoolean("job_system.enabled", true);
            boolean upgradeSystemEnabled = plugin.getConfig().getBoolean("upgrade_system.enabled", true);
            boolean dragonRewardEnabled = plugin.getConfig().getBoolean("dragon_reward.enabled", true);
            boolean npcTradeEnabled = plugin.getConfig().getBoolean("npc_trade.enabled", true);

            sender.sendMessage("§7- 직업 시스템: " + (jobSystemEnabled ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7- 강화 시스템: " + (upgradeSystemEnabled ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7- 드래곤 보상: " + (dragonRewardEnabled ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7- NPC 교환: " + (npcTradeEnabled ? "§a활성화" : "§c비활성화"));

            plugin.getLogger().info(sender.getName() + "이(가) 플러그인 설정을 리로드했습니다.");

        } catch (Exception e) {
            sender.sendMessage("§c설정 리로드 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().severe("설정 리로드 실패: " + e.getMessage());
        }
    }

    private void showServerStats(CommandSender sender) {
        sender.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§l📊 야생 서버 통계");
        sender.sendMessage("");

        // 기본 서버 정보
        sender.sendMessage("§a플러그인 버전: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§a온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size() + "명");

        // 경제 시스템 상태
        boolean ggmCoreConnected = plugin.getEconomyManager().isGGMCoreConnected();
        sender.sendMessage("§aGGMCore 연동: " + (ggmCoreConnected ? "§f연결됨" : "§c연결 안됨"));

        // 시스템 상태
        sender.sendMessage("");
        sender.sendMessage("§e시스템 상태:");
        sender.sendMessage("§7• 직업 시스템: " + getSystemStatus("job_system.enabled"));
        sender.sendMessage("§7• 강화 시스템: " + getSystemStatus("upgrade_system.enabled"));
        sender.sendMessage("§7• 드래곤 보상: " + getSystemStatus("dragon_reward.enabled"));
        sender.sendMessage("§7• NPC 교환: " + getSystemStatus("npc_trade.enabled"));

        sender.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private String getSystemStatus(String configPath) {
        boolean enabled = plugin.getConfig().getBoolean(configPath, true);
        return enabled ? "§a활성화" : "§c비활성화";
    }
}