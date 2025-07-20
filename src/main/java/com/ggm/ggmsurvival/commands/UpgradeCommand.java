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
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // 손에 든 아이템의 강화 정보 표시
            showUpgradeInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
            case "정보":
                showUpgradeInfo(player);
                break;

            case "help":
            case "도움말":
                sendHelp(player);
                break;

            case "guide":
            case "가이드":
                showUpgradeGuide(player);
                break;

            case "rates":
            case "확률":
                showUpgradeRates(player);
                break;

            case "costs":
            case "비용":
                showUpgradeCosts(player);
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    /**
     * 강화 정보 표시
     */
    private void showUpgradeInfo(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            player.sendMessage("§c손에 아이템을 들어주세요!");
            player.sendMessage("§7강화 가능한 아이템: 무기, 도구, 방어구");
            return;
        }

        // 강화 가능한 아이템인지 확인
        if (!isUpgradeable(item)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            player.sendMessage("§7강화 가능한 아이템: 검, 도구, 활, 방어구 등");
            return;
        }

        upgradeManager.showUpgradeInfo(player, item);
    }

    /**
     * 강화 가이드 표시
     */
    private void showUpgradeGuide(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ G 강화 시스템 가이드 ⚡");
        player.sendMessage("");
        player.sendMessage("§a§l📝 강화 방법:");
        player.sendMessage("§71. 강화할 아이템을 손에 들기");
        player.sendMessage("§72. 인첸트 테이블에 아이템 올리기");
        player.sendMessage("§73. G를 소모하여 강화 시도");
        player.sendMessage("§74. 성공하면 강화 레벨 증가!");
        player.sendMessage("");
        player.sendMessage("§c§l⚠️ 주의사항:");
        player.sendMessage("§7• 강화는 §cG를 소모§7합니다 (레벨마다 다름)");
        player.sendMessage("§7• §c5강 이상부터 실패 시 강화 레벨 감소");
        player.sendMessage("§7• 최대 강화 레벨: §610강");
        player.sendMessage("§7• 높은 강화일수록 §c성공 확률 감소");
        player.sendMessage("");
        player.sendMessage("§e§l💡 팁:");
        player.sendMessage("§7• 중요한 아이템은 신중하게 강화하세요");
        player.sendMessage("§7• G를 충분히 모은 후 시도하는 것이 좋습니다");
        player.sendMessage("§7• 강화 성공 시 아이템 능력이 크게 향상됩니다");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 확률 표시
     */
    private void showUpgradeRates(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l📊 강화 성공 확률표");
        player.sendMessage("");

        for (int level = 1; level <= 10; level++) {
            int successRate = upgradeManager.getSuccessRate(level);
            String color = getSuccessRateColor(successRate);
            String riskWarning = level >= 5 ? " §c(실패 시 -1강)" : " §a(실패 시 레벨 유지)";

            player.sendMessage(String.format("§7%d강: %s%d%% §7성공 확률%s",
                    level, color, successRate, riskWarning));
        }

        player.sendMessage("");
        player.sendMessage("§c§l위험 구간:");
        player.sendMessage("§7• §e1~4강: §a실패해도 강화 레벨 유지");
        player.sendMessage("§7• §c5~10강: §c실패 시 강화 레벨 1 감소");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 비용 표시
     */
    private void showUpgradeCosts(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l💰 강화 비용표");
        player.sendMessage("");

        for (int level = 1; level <= 10; level++) {
            long cost = upgradeManager.getUpgradeCost(level);
            String color = getCostColor(level);

            player.sendMessage(String.format("§7%d강: %s%s G",
                    level, color, plugin.getEconomyManager().formatMoney(cost)));
        }

        player.sendMessage("");
        player.sendMessage("§e§l💡 비용 안내:");
        player.sendMessage("§7• 강화 레벨이 높을수록 §6비용 증가");
        player.sendMessage("§7• 실패해도 §c비용은 소모됩니다");
        player.sendMessage("§7• 충분한 G를 준비하고 강화하세요");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 도움말 표시
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 강화 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§7/upgrade §f- 손에 든 아이템 강화 정보");
        player.sendMessage("§7/upgrade info §f- 아이템 강화 정보 확인");
        player.sendMessage("§7/upgrade guide §f- 강화 시스템 가이드");
        player.sendMessage("§7/upgrade rates §f- 강화 성공 확률표");
        player.sendMessage("§7/upgrade costs §f- 강화 비용표");
        player.sendMessage("");
        player.sendMessage("§a§l실제 강화 방법:");
        player.sendMessage("§71. 강화할 아이템을 손에 들기");
        player.sendMessage("§72. §e인첸트 테이블§7에 아이템 올리기");
        player.sendMessage("§73. 표시되는 강화 옵션 클릭");
        player.sendMessage("§74. G 소모하여 강화 시도!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 아이템이 강화 가능한지 확인
     */
    private boolean isUpgradeable(ItemStack item) {
        String typeName = item.getType().name();

        // 무기류
        if (typeName.contains("SWORD") || typeName.contains("BOW") ||
                typeName.contains("CROSSBOW") || typeName.contains("TRIDENT")) {
            return true;
        }

        // 도구류
        if (typeName.contains("PICKAXE") || typeName.contains("AXE") ||
                typeName.contains("SHOVEL") || typeName.contains("HOE")) {
            return true;
        }

        // 방어구
        if (typeName.contains("HELMET") || typeName.contains("CHESTPLATE") ||
                typeName.contains("LEGGINGS") || typeName.contains("BOOTS")) {
            return true;
        }

        return false;
    }

    /**
     * 성공 확률에 따른 색상 반환
     */
    private String getSuccessRateColor(int rate) {
        if (rate >= 80) return "§a"; // 녹색 (높음)
        if (rate >= 60) return "§e"; // 노란색 (보통)
        if (rate >= 40) return "§6"; // 주황색 (낮음)
        return "§c";                 // 빨간색 (매우 낮음)
    }

    /**
     * 비용에 따른 색상 반환
     */
    private String getCostColor(int level) {
        if (level <= 3) return "§a";      // 녹색 (저렴)
        if (level <= 6) return "§e";      // 노란색 (보통)
        if (level <= 8) return "§6";      // 주황색 (비쌈)
        return "§c";                      // 빨간색 (매우 비쌈)
    }
}