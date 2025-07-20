package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showUpgradeHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
            case "정보":
                showItemUpgradeInfo(player);
                break;

            case "rates":
            case "확률":
            case "가격":
                if (upgradeManager != null) {
                    upgradeManager.showUpgradeRates(player);
                } else {
                    player.sendMessage("§c강화 시스템이 비활성화되어 있습니다.");
                }
                break;

            case "guide":
            case "가이드":
                showUpgradeGuide(player);
                break;

            case "help":
            case "도움말":
                showUpgradeHelp(player);
                break;

            default:
                showUpgradeHelp(player);
                break;
        }

        return true;
    }

    /**
     * 손에 든 아이템의 강화 정보 표시
     */
    private void showItemUpgradeInfo(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c강화할 아이템을 손에 들고 사용하세요!");
            return;
        }

        if (upgradeManager != null) {
            upgradeManager.showUpgradeInfo(player, item);
        } else {
            player.sendMessage("§c강화 시스템이 비활성화되어 있습니다.");
        }
    }

    /**
     * 강화 가이드 표시
     */
    private void showUpgradeGuide(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 강화 시스템 가이드");
        player.sendMessage("");
        player.sendMessage("§a📋 강화 방법:");
        player.sendMessage("§71. 강화할 아이템을 준비합니다");
        player.sendMessage("§72. 인챈트 테이블에 아이템을 올립니다");
        player.sendMessage("§73. 강화 정보를 확인합니다");
        player.sendMessage("§74. 인챈트 옵션을 클릭하여 강화합니다");
        player.sendMessage("");
        player.sendMessage("§c⚠ 주의사항:");
        player.sendMessage("§7• 강화 비용이 G로 차감됩니다");
        player.sendMessage("§7• 5강부터 실패 시 레벨이 하락합니다");
        player.sendMessage("§7• 최대 10강까지 가능합니다");
        player.sendMessage("§7• 높은 강화일수록 성공률이 낮습니다");
        player.sendMessage("");
        player.sendMessage("§a💡 팁:");
        player.sendMessage("§7• 중요한 아이템은 백업을 준비하세요");
        player.sendMessage("§7• G를 충분히 모아두고 시도하세요");
        player.sendMessage("§7• 낮은 강화부터 차근차근 시도하세요");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 도움말 표시
     */
    private void showUpgradeHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 강화 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§a/upgrade info §7- 손에 든 아이템 강화 정보");
        player.sendMessage("§a/upgrade rates §7- 강화 비용 및 확률표");
        player.sendMessage("§a/upgrade guide §7- 강화 시스템 가이드");
        player.sendMessage("§a/upgrade help §7- 이 도움말");
        player.sendMessage("");
        player.sendMessage("§e강화 방법:");
        player.sendMessage("§7인챈트 테이블에 아이템을 올리고 클릭!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}