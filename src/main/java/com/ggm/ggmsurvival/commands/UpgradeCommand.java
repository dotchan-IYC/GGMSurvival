// 완전한 UpgradeCommand.java - GUI와 직접 강화 기능 포함
package com.ggm.ggmsurvival.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;

import java.util.Arrays;

public class UpgradeCommand implements CommandExecutor, Listener {

    private final GGMSurvival plugin;
    private final EnchantUpgradeManager upgradeManager;

    public UpgradeCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getEnchantUpgradeManager();

        // 이벤트 리스너 등록
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
            case "gui":
            case "GUI":
                openUpgradeGUI(player);
                break;
            case "direct":
            case "바로강화":
                directUpgrade(player);
                break;
            case "stats":
            case "통계":
                showUpgradeStats(player);
                break;
            default:
                showUpgradeGuide(player);
                break;
        }

        return true;
    }

    /**
     * 강화 GUI 열기 (인첸트 테이블 대신 사용)
     */
    private void openUpgradeGUI(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c손에 강화할 아이템을 들고 사용하세요!");
            return;
        }

        if (!upgradeManager.canUpgrade(handItem)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            player.sendMessage("§7강화 가능 아이템: 무기, 도구, 방어구, 활, 방패");
            return;
        }

        // 27칸 인벤토리 생성
        Inventory gui = Bukkit.createInventory(null, 27, "§6§l⚡ 아이템 강화");

        // 현재 아이템 정보
        int currentLevel = upgradeManager.getUpgradeLevel(handItem);

        if (currentLevel >= 10) {
            player.sendMessage("§c이미 최대 강화 레벨에 도달했습니다!");
            return;
        }

        int nextLevel = currentLevel + 1;
        long cost = upgradeManager.getUpgradeCost(nextLevel);
        int successRate = upgradeManager.getSuccessRate(nextLevel);

        // 강화할 아이템 표시 (가운데)
        gui.setItem(13, handItem.clone());

        // 강화 버튼
        ItemStack upgradeButton = new ItemStack(Material.ANVIL);
        ItemMeta upgradeMeta = upgradeButton.getItemMeta();
        upgradeMeta.setDisplayName("§6§l⚡ 강화 시도");
        upgradeMeta.setLore(Arrays.asList(
                "§7현재 강화: §f" + currentLevel + "강",
                "§7다음 강화: §f" + nextLevel + "강",
                "",
                "§7필요 비용: §6" + upgradeManager.formatMoney(cost) + "G",
                "§7성공 확률: §a" + successRate + "%",
                nextLevel >= 5 ? "§7실패 시: §c강화 레벨 1 감소" : "§7실패 시: §7레벨 유지",
                "",
                "§e클릭하여 강화 시도!",
                "§c⚠️ 비용이 즉시 차감됩니다!"
        ));
        upgradeButton.setItemMeta(upgradeMeta);
        gui.setItem(10, upgradeButton);

        // 정보 표시
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§e§l📖 강화 정보");
        infoMeta.setLore(Arrays.asList(
                "§7아이템: §f" + upgradeManager.getItemDisplayName(handItem),
                "§7재질: §f" + handItem.getType().name(),
                "",
                "§6강화 효과:",
                "§7• 레벨당 능력 +10%",
                "§7• 고급 인첸트 자동 적용",
                "§7• 아이템 이름에 강화 표시",
                "",
                "§a인첸트 테이블 대신 이 GUI를 사용하세요!",
                "§e기존 인첸트는 유지되며 강화 효과가 추가됩니다."
        ));
        infoItem.setItemMeta(infoMeta);
        gui.setItem(16, infoItem);

        // 강화 확률표 버튼
        ItemStack ratesItem = new ItemStack(Material.PAPER);
        ItemMeta ratesMeta = ratesItem.getItemMeta();
        ratesMeta.setDisplayName("§a§l📊 강화 확률표");
        ratesMeta.setLore(Arrays.asList(
                "§7클릭하여 전체 강화 정보를 확인하세요!",
                "",
                "§a1-3강: §795-85% §7(낮은 비용)",
                "§e4-6강: §780-60% §7(중간 비용)",
                "§c7-8강: §750-40% §7(높은 비용)",
                "§d9-10강: §730-20% §7(최고 비용)",
                "",
                "§c5강 이상부터 실패 시 레벨 감소!"
        ));
        ratesItem.setItemMeta(ratesMeta);
        gui.setItem(12, ratesItem);

        // 다른 강화 방법 안내
        ItemStack methodsItem = new ItemStack(Material.COMPASS);
        ItemMeta methodsMeta = methodsItem.getItemMeta();
        methodsMeta.setDisplayName("§b§l🔧 다른 강화 방법");
        methodsMeta.setLore(Arrays.asList(
                "§7강화하는 3가지 방법:",
                "",
                "§71. §e인첸트 테이블 §7- 기존 방식",
                "§7   아이템 + 라피스 라줄리 → 클릭",
                "",
                "§72. §e/upgrade gui §7- 이 GUI",
                "§7   직관적이고 안전한 방식",
                "",
                "§73. §e/upgrade direct §7- 즉시 강화",
                "§7   명령어 한 번으로 바로 강화",
                "",
                "§a모든 방법이 동일한 결과를 제공합니다!"
        ));
        methodsItem.setItemMeta(methodsMeta);
        gui.setItem(14, methodsItem);

        // 취소 버튼
        ItemStack cancelButton = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName("§c§l❌ 취소");
        cancelMeta.setLore(Arrays.asList(
                "§7클릭하여 강화 GUI를 닫습니다.",
                "",
                "§e다른 강화 방법:",
                "§7• /upgrade direct - 즉시 강화",
                "§7• 인첸트 테이블 사용",
                "",
                "§a언제든지 다시 /upgrade gui로 열 수 있습니다!"
        ));
        cancelButton.setItemMeta(cancelMeta);
        gui.setItem(22, cancelButton);

        // 장식용 유리판
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§0");
        glass.setItemMeta(glassMeta);

        // 빈 칸 채우기
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }

        player.openInventory(gui);
        player.sendMessage("§a강화 GUI가 열렸습니다!");
        player.sendMessage("§e인첸트된 아이템도 강화 가능합니다!");
    }

    /**
     * 직접 강화 (명령어로 바로 강화)
     */
    private void directUpgrade(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c손에 강화할 아이템을 들고 사용하세요!");
            return;
        }

        if (!upgradeManager.canUpgrade(handItem)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            player.sendMessage("§7강화 가능 아이템: 무기, 도구, 방어구, 활, 방패");
            return;
        }

        // EnchantUpgradeManager의 직접 강화 메서드 호출
        upgradeManager.directUpgradeItem(player, handItem);
    }

    /**
     * 강화 통계 표시
     */
    private void showUpgradeStats(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l📊 강화 시스템 통계");
        player.sendMessage("");

        // 손에 든 아이템 정보
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem != null && handItem.getType() != Material.AIR) {
            int level = upgradeManager.getUpgradeLevel(handItem);
            player.sendMessage("§7현재 아이템: §f" + upgradeManager.getItemDisplayName(handItem));
            player.sendMessage("§7강화 레벨: §f" + level + "강");

            if (level < 10) {
                int nextLevel = level + 1;
                long cost = upgradeManager.getUpgradeCost(nextLevel);
                int rate = upgradeManager.getSuccessRate(nextLevel);
                player.sendMessage("§7다음 강화: §f" + nextLevel + "강 (§6" + upgradeManager.formatMoney(cost) + "G§7, §a" + rate + "%§7)");
            } else {
                player.sendMessage("§a최대 강화 레벨 달성!");
            }
        } else {
            player.sendMessage("§c손에 아이템을 들고 확인하세요!");
        }

        player.sendMessage("");
        player.sendMessage("§a§l사용 가능한 강화 방법:");
        player.sendMessage("§71. §e인첸트 테이블§7 - 기존 방식");
        player.sendMessage("§72. §e/upgrade gui§7 - 강화 전용 GUI");
        player.sendMessage("§73. §e/upgrade direct§7 - 명령어 강화");
        player.sendMessage("");
        player.sendMessage("§6모든 방법이 인첸트된 아이템에서도 작동합니다!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * GUI 클릭 이벤트 처리
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // 강화 GUI 확인
        if (event.getView().getTitle().equals("§6§l⚡ 아이템 강화")) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            switch (clickedItem.getType()) {
                case ANVIL:
                    // 강화 시도
                    player.closeInventory();
                    ItemStack handItem = player.getInventory().getItemInMainHand();

                    if (handItem != null && handItem.getType() != Material.AIR) {
                        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§e⚡ GUI를 통한 강화를 시작합니다!");
                        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        upgradeManager.directUpgradeItem(player, handItem);
                    } else {
                        player.sendMessage("§c손에 아이템이 없습니다!");
                    }
                    break;

                case BARRIER:
                    // 취소
                    player.closeInventory();
                    player.sendMessage("§7강화를 취소했습니다.");
                    break;

                case BOOK:
                    // 정보 표시
                    player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§e§l📖 강화 시스템 안내");
                    player.sendMessage("");
                    player.sendMessage("§a인첸트된 아이템도 강화 가능!");
                    player.sendMessage("§7• 인첸트 테이블: 기존 방식");
                    player.sendMessage("§7• /upgrade gui: 이 GUI 사용");
                    player.sendMessage("§7• /upgrade direct: 즉시 강화");
                    player.sendMessage("");
                    player.sendMessage("§6모든 방법으로 동일한 결과를 얻습니다!");
                    player.sendMessage("§7기존 인첸트는 유지되며 강화 효과가 추가됩니다.");
                    player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    break;

                case PAPER:
                    // 강화 확률표 표시
                    player.closeInventory();
                    upgradeManager.showUpgradeRates(player);
                    break;

                case COMPASS:
                    // 다른 강화 방법 안내
                    player.sendMessage("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§b§l🔧 강화 방법 안내");
                    player.sendMessage("");
                    player.sendMessage("§a1. 인첸트 테이블 사용:");
                    player.sendMessage("§7• 아이템 + 라피스 라줄리 배치");
                    player.sendMessage("§7• 인첸트 옵션 클릭");
                    player.sendMessage("§7• §e인첸트된 아이템도 가능!");
                    player.sendMessage("");
                    player.sendMessage("§a2. 강화 GUI 사용:");
                    player.sendMessage("§7• /upgrade gui 명령어");
                    player.sendMessage("§7• 직관적인 인터페이스");
                    player.sendMessage("§7• 안전하고 편리함");
                    player.sendMessage("");
                    player.sendMessage("§a3. 즉시 강화:");
                    player.sendMessage("§7• /upgrade direct 명령어");
                    player.sendMessage("§7• 빠른 강화 가능");
                    player.sendMessage("§7• 한 번에 처리");
                    player.sendMessage("");
                    player.sendMessage("§6모든 방법이 동일한 결과를 제공합니다!");
                    player.sendMessage("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    break;

                default:
                    // 강화할 아이템 클릭 시 정보 표시
                    if (clickedItem.equals(player.getInventory().getItemInMainHand())) {
                        upgradeManager.showUpgradeInfo(player, clickedItem);
                    }
                    break;
            }
        }
    }

    /**
     * 강화 가이드 표시
     */
    private void showUpgradeGuide(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 강화 시스템 가이드");
        player.sendMessage("");
        player.sendMessage("§a§l강화 방법 (3가지):");
        player.sendMessage("§71. §e인첸트 테이블§7: 아이템 + 라피스 → 클릭");
        player.sendMessage("§72. §e/upgrade gui§7: 강화 전용 GUI 사용");
        player.sendMessage("§73. §e/upgrade direct§7: 명령어로 즉시 강화");
        player.sendMessage("");
        player.sendMessage("§c§l인첸트된 아이템도 강화 가능!");
        player.sendMessage("§7• 인첸트가 있어도 모든 방법 사용 가능");
        player.sendMessage("§7• 기존 인첸트는 유지되며 강화 효과 추가");
        player.sendMessage("");
        player.sendMessage("§e§l강화 특징:");
        player.sendMessage("§7• 최대 10강까지 업그레이드 가능");
        player.sendMessage("§7• G(골드)를 소모하여 강화");
        player.sendMessage("§7• 레벨이 높을수록 확률 감소");
        player.sendMessage("§7• 5강 이상 실패 시 레벨 감소");
        player.sendMessage("§7• 강화 시 추가 인챈트 자동 적용");
        player.sendMessage("");
        player.sendMessage("§a§l추천 방법:");
        player.sendMessage("§7인첸트된 아이템 → §e/upgrade gui §7사용!");
        player.sendMessage("");
        player.sendMessage("§6§l명령어:");
        player.sendMessage("§7/upgrade gui §f- 강화 GUI 열기");
        player.sendMessage("§7/upgrade direct §f- 즉시 강화");
        player.sendMessage("§7/upgrade info §f- 아이템 정보");
        player.sendMessage("§7/upgrade rates §f- 확률표");
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
        player.sendMessage("§7/upgrade gui §f- 강화 GUI 열기 (추천!)");
        player.sendMessage("§7/upgrade direct §f- 즉시 강화");
        player.sendMessage("§7/upgrade stats §f- 아이템 통계");
        player.sendMessage("");
        player.sendMessage("§e§l강화 팁:");
        player.sendMessage("§7• 낮은 레벨부터 차근차근 올리세요");
        player.sendMessage("§7• 5강 이상은 실패 시 레벨이 떨어집니다");
        player.sendMessage("§7• 중요한 아이템은 백업을 준비하세요");
        player.sendMessage("§7• §e인챈트된 아이템도 강화 가능합니다!");
        player.sendMessage("");
        player.sendMessage("§a§l인첸트된 아이템 강화 방법:");
        player.sendMessage("§7인첸트 테이블에 안 뜨면 → §e/upgrade gui §7사용!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}