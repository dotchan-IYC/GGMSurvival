// 업데이트된 업그레이드 명령어 - 새로운 강화 시스템
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UpgradeCommand implements CommandExecutor, TabCompleter {

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

        if (args.length == 0) {
            showUpgradeHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
            case "정보":
                showItemUpgradeInfo(player);
                break;

            case "guide":
            case "가이드":
                showUpgradeGuide(player);
                break;

            case "rates":
            case "확률":
                showUpgradeRates(player);
                break;

            case "gui":
                openUpgradeGUI(player);
                break;

            case "direct":
            case "즉시":
                directUpgrade(player);
                break;

            case "items":
            case "아이템":
                showUpgradeableItems(player);
                break;

            default:
                showUpgradeHelp(player);
                break;
        }

        return true;
    }

    /**
     * 도움말 표시
     */
    private void showUpgradeHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 새로운 강화 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§a/upgrade info §7- 손에 든 아이템 강화 정보");
        player.sendMessage("§a/upgrade guide §7- 강화 시스템 가이드");
        player.sendMessage("§a/upgrade rates §7- 강화 확률 및 비용");
        player.sendMessage("§a/upgrade gui §7- 강화 GUI 열기");
        player.sendMessage("§a/upgrade direct §7- 즉시 강화 시도");
        player.sendMessage("§a/upgrade items §7- 강화 가능한 아이템");
        player.sendMessage("");
        player.sendMessage("§c§l새로운 패치 특징:");
        player.sendMessage("§7• 강화 가능: §e검, 도끼, 활, 흉갑만");
        player.sendMessage("§7• 검/활: 위력 3% 증가");
        player.sendMessage("§7• 도끼: 공격속도 2% 증가");
        player.sendMessage("§7• 흉갑: 방어력 3% 증가");
        player.sendMessage("§7• 10강: 발화, 출혈, 화염, 가시 효과");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 손에 든 아이템 강화 정보
     */
    private void showItemUpgradeInfo(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c손에 아이템을 들고 사용하세요!");
            return;
        }

        if (!upgradeManager.isUpgradeable(item)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            player.sendMessage("§7강화 가능: §e검, 도끼, 활, 흉갑");
            return;
        }

        int currentLevel = upgradeManager.getUpgradeLevel(item);
        String itemName = upgradeManager.getItemDisplayName(item);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 아이템 강화 정보");
        player.sendMessage("§7아이템: §f" + itemName);
        player.sendMessage("§7현재 강화: §f" + currentLevel + "강");

        if (currentLevel < 10) {
            int nextLevel = currentLevel + 1;
            long cost = upgradeManager.getUpgradeCost(nextLevel);
            int successRate = upgradeManager.getSuccessRate(nextLevel);

            player.sendMessage("§7다음 강화: §f" + nextLevel + "강");
            player.sendMessage("§7필요 비용: §6" + formatMoney(cost) + "G");
            player.sendMessage("§7성공 확률: §a" + successRate + "%");

            if (nextLevel >= 5) {
                player.sendMessage("§7실패 시: §c강화 레벨 1 감소");
            }

            // 아이템별 효과 안내
            EnchantUpgradeManager.UpgradeItemType type = upgradeManager.getItemType(item.getType());
            if (type != null) {
                switch (type) {
                    case SWORD:
                        player.sendMessage("§7효과: §a검 위력 +" + (nextLevel * 3) + "%");
                        if (nextLevel == 10) player.sendMessage("§c▸ 10강: 발화 효과 추가");
                        break;
                    case AXE:
                        player.sendMessage("§7효과: §a공격속도 +" + (nextLevel * 2) + "%");
                        if (nextLevel == 10) player.sendMessage("§c▸ 10강: 출혈 효과 추가");
                        break;
                    case BOW:
                        player.sendMessage("§7효과: §a활 위력 +" + (nextLevel * 3) + "%");
                        if (nextLevel == 10) player.sendMessage("§c▸ 10강: 화염 효과 추가");
                        break;
                    case CHESTPLATE:
                        player.sendMessage("§7효과: §a방어력 +" + (nextLevel * 3) + "%");
                        if (nextLevel == 10) player.sendMessage("§c▸ 10강: 가시 효과 추가");
                        break;
                }
            }

        } else {
            player.sendMessage("§6이미 최대 강화 레벨입니다!");
        }

        player.sendMessage("");
        player.sendMessage("§a강화 방법:");
        player.sendMessage("§71. §e인첸트 테이블 §7- 아이템 + 라피스");
        player.sendMessage("§72. §e/upgrade gui §7- 강화 GUI");
        player.sendMessage("§73. §e/upgrade direct §7- 즉시 강화");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 시스템 가이드
     */
    private void showUpgradeGuide(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l📖 새로운 강화 시스템 가이드");
        player.sendMessage("");
        player.sendMessage("§a§l강화 가능한 아이템:");
        player.sendMessage("§7• §e검 §7- 위력 3% 증가 (10강: 발화)");
        player.sendMessage("§7• §e도끼 §7- 공격속도 2% 증가 (10강: 출혈)");
        player.sendMessage("§7• §e활 §7- 위력 3% 증가 (10강: 화염)");
        player.sendMessage("§7• §e흉갑 §7- 방어력 3% 증가 (10강: 가시)");
        player.sendMessage("");
        player.sendMessage("§c§l주의사항:");
        player.sendMessage("§7• 5강 이상 실패 시 강화 레벨 1 감소");
        player.sendMessage("§7• 10강 달성 시 특수 효과 자동 적용");
        player.sendMessage("§7• 인첸트된 아이템도 강화 가능");
        player.sendMessage("");
        player.sendMessage("§a§l강화 방법 (3가지):");
        player.sendMessage("§71. §6인첸트 테이블 §7- 가장 일반적인 방법");
        player.sendMessage("§72. §6/upgrade gui §7- GUI로 편리하게");
        player.sendMessage("§73. §6/upgrade direct §7- 즉시 강화");
        player.sendMessage("");
        player.sendMessage("§e§l새로운 특징:");
        player.sendMessage("§7• 기존 모든 아이템 강화 제한");
        player.sendMessage("§7• 아이템별 차별화된 효과");
        player.sendMessage("§7• 10강 특수 효과로 더욱 강력함");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 확률 및 비용 표시
     */
    private void showUpgradeRates(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l💰 강화 비용 및 성공 확률");
        player.sendMessage("");

        for (int i = 1; i <= 10; i++) {
            long cost = upgradeManager.getUpgradeCost(i);
            int rate = upgradeManager.getSuccessRate(i);
            String color = i <= 3 ? "§a" : i <= 6 ? "§e" : i <= 8 ? "§c" : "§4";
            String special = "";

            if (i == 5) special = " §7(실패시 레벨 감소)";
            if (i == 10) special = " §6(특수 효과!)";

            player.sendMessage(color + i + "강§7: " + formatMoney(cost) + "G §7(§a" + rate + "%§7)" + special);
        }

        player.sendMessage("");
        player.sendMessage("§c§l위험 구간:");
        player.sendMessage("§7• 5강 이상: 실패 시 강화 레벨 감소");
        player.sendMessage("§7• 8~10강: 낮은 성공률 (40%, 30%, 20%)");
        player.sendMessage("");
        player.sendMessage("§a§l추천 전략:");
        player.sendMessage("§7• 1~4강: 안전하게 도전");
        player.sendMessage("§7• 5~7강: 신중하게 도전");
        player.sendMessage("§7• 8~10강: 충분한 자금 준비 후");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 가능한 아이템 목록
     */
    private void showUpgradeableItems(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚔️ 강화 가능한 아이템 목록");
        player.sendMessage("");
        player.sendMessage("§c§l검류 (모든 재질):");
        player.sendMessage("§7• 나무검, 돌검, 철검, 금검, 다이아검, 네더라이트검");
        player.sendMessage("§7• 효과: 위력 3% 증가, 10강시 발화 효과");
        player.sendMessage("");
        player.sendMessage("§6§l도끼류 (모든 재질):");
        player.sendMessage("§7• 나무도끼, 돌도끼, 철도끼, 금도끼, 다이아도끼, 네더라이트도끼");
        player.sendMessage("§7• 효과: 공격속도 2% 증가, 10강시 출혈 효과");
        player.sendMessage("");
        player.sendMessage("§a§l활류:");
        player.sendMessage("§7• 활, 쇠뇌");
        player.sendMessage("§7• 효과: 위력 3% 증가, 10강시 화염 효과");
        player.sendMessage("");
        player.sendMessage("§9§l흉갑류 (모든 재질):");
        player.sendMessage("§7• 가죽흉갑, 사슬흉갑, 철흉갑, 금흉갑, 다이아흉갑, 네더라이트흉갑");
        player.sendMessage("§7• 효과: 방어력 3% 증가, 10강시 가시 효과");
        player.sendMessage("");
        player.sendMessage("§c§l주의: 다른 모든 아이템은 강화 불가능!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 GUI 열기
     */
    private void openUpgradeGUI(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c손에 아이템을 들고 사용하세요!");
            return;
        }

        if (!upgradeManager.isUpgradeable(item)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            player.sendMessage("§7강화 가능: §e검, 도끼, 활, 흉갑");
            return;
        }

        int currentLevel = upgradeManager.getUpgradeLevel(item);
        if (currentLevel >= 10) {
            player.sendMessage("§c이미 최대 강화 레벨입니다!");
            return;
        }

        // 강화 GUI 생성
        Inventory gui = Bukkit.createInventory(null, 27, "§6§l⚡ 새로운 강화 시스템");

        // 아이템 슬롯
        gui.setItem(13, item.clone());

        // 정보 아이템
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§e§l강화 정보");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7아이템: §f" + upgradeManager.getItemDisplayName(item));
        infoLore.add("§7현재 강화: §f" + currentLevel + "강");

        if (currentLevel < 10) {
            int nextLevel = currentLevel + 1;
            long cost = upgradeManager.getUpgradeCost(nextLevel);
            int rate = upgradeManager.getSuccessRate(nextLevel);

            infoLore.add("§7다음 강화: §f" + nextLevel + "강");
            infoLore.add("§7필요 비용: §6" + formatMoney(cost) + "G");
            infoLore.add("§7성공 확률: §a" + rate + "%");

            if (nextLevel >= 5) {
                infoLore.add("§c실패 시 레벨 감소!");
            }
        }

        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(4, infoItem);

        // 강화 버튼
        ItemStack upgradeButton = new ItemStack(Material.NETHER_STAR);
        ItemMeta upgradeMeta = upgradeButton.getItemMeta();
        upgradeMeta.setDisplayName("§a§l강화 시도!");
        upgradeMeta.setLore(Arrays.asList("§7클릭하여 강화를 시도합니다."));
        upgradeButton.setItemMeta(upgradeMeta);
        gui.setItem(22, upgradeButton);

        player.openInventory(gui);
    }

    /**
     * 즉시 강화
     */
    private void directUpgrade(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c손에 아이템을 들고 사용하세요!");
            return;
        }

        if (!upgradeManager.isUpgradeable(item)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            player.sendMessage("§7강화 가능: §e검, 도끼, 활, 흉갑");
            return;
        }

        upgradeManager.processUpgrade(player, item);
    }

    /**
     * 금액 포맷
     */
    private String formatMoney(long amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("info", "guide", "rates", "gui", "direct", "items");

            for (String subcommand : subcommands) {
                if (subcommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subcommand);
                }
            }
        }

        return completions;
    }
}