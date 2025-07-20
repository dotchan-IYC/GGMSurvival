// UpgradeCommand.java - 강화 시스템 명령어
package com.ggm.ggmsurvival.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;

public class UpgradeCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final EnchantUpgradeManager upgradeManager;

    public UpgradeCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getEnchantUpgradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.isFeatureEnabled("upgrade_system")) {
            player.sendMessage("§c강화 시스템은 야생 서버에서만 사용할 수 있습니다!");
            return true;
        }

        if (upgradeManager == null) {
            player.sendMessage("§c강화 시스템이 초기화되지 않았습니다!");
            return true;
        }

        if (args.length == 0) {
            showUpgradeGuide(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
            case "정보":
                ItemStack item = player.getInventory().getItemInMainHand();
                upgradeManager.showUpgradeInfo(player, item);
                break;
            case "guide":
            case "가이드":
                showUpgradeGuide(player);
                break;
            case "rates":
            case "확률":
            case "costs":
            case "비용":
                upgradeManager.showUpgradeRates(player);
                break;
            case "help":
            case "도움말":
                showUpgradeHelp(player);
                break;
            default:
                showUpgradeGuide(player);
                break;
        }

        return true;
    }

    /**
     * 강화 가이드 표시
     */
    private void showUpgradeGuide(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 강화 시스템 가이드");
        player.sendMessage("");
        player.sendMessage("§a§l강화 방법:");
        player.sendMessage("§71. 강화할 아이템 준비");
        player.sendMessage("§72. 인챈트 테이블 설치");
        player.sendMessage("§73. 아이템 + 라피스 라줄리 배치");
        player.sendMessage("§74. 인챈트 옵션 클릭!");
        player.sendMessage("");
        player.sendMessage("§e§l강화 특징:");
        player.sendMessage("§7• 최대 10강까지 업그레이드 가능");
        player.sendMessage("§7• G(골드)를 소모하여 강화");
        player.sendMessage("§7• 레벨이 높을수록 확률 감소");
        player.sendMessage("§7• 5강 이상 실패 시 레벨 감소");
        player.sendMessage("§7• 강화 시 인챈트 자동 적용");
        player.sendMessage("");
        player.sendMessage("§a§l명령어:");
        player.sendMessage("§7/upgrade info §f- 손에 든 아이템 정보");
        player.sendMessage("§7/upgrade rates §f- 강화 확률 및 비용");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 도움말 표시
     */
    private void showUpgradeHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l강화 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§7/upgrade §f- 강화 가이드");
        player.sendMessage("§7/upgrade info §f- 손에 든 아이템 강화 정보");
        player.sendMessage("§7/upgrade guide §f- 강화 방법 안내");
        player.sendMessage("§7/upgrade rates §f- 강화 확률 및 비용표");
        player.sendMessage("");
        player.sendMessage("§e§l강화 팁:");
        player.sendMessage("§7• 낮은 레벨부터 차근차근 올리세요");
        player.sendMessage("§7• 5강 이상은 실패 시 레벨이 떨어집니다");
        player.sendMessage("§7• 중요한 아이템은 백업을 준비하세요");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}