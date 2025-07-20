// 강화 GUI 클릭 이벤트 처리
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class UpgradeGUIListener implements Listener {

    private final GGMSurvival plugin;
    private final EnchantUpgradeManager upgradeManager;

    public UpgradeGUIListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getEnchantUpgradeManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // 강화 GUI가 아니면 무시
        if (!title.contains("강화 시스템")) return;

        event.setCancelled(true); // 아이템 이동 방지

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // 강화 버튼 클릭 (네더의 별)
        if (clickedItem.getType() == Material.NETHER_STAR) {
            performUpgradeFromGUI(player);
            return;
        }

        // 아이템 슬롯 클릭 시 정보 표시
        if (event.getSlot() == 13) {
            showItemInfo(player, clickedItem);
        }
    }

    /**
     * GUI에서 강화 수행
     */
    private void performUpgradeFromGUI(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            player.closeInventory();
            player.sendMessage("§c손에 아이템을 들고 사용하세요!");
            return;
        }

        if (!upgradeManager.isUpgradeable(item)) {
            player.closeInventory();
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            return;
        }

        int currentLevel = upgradeManager.getUpgradeLevel(item);
        if (currentLevel >= 10) {
            player.closeInventory();
            player.sendMessage("§c이미 최대 강화 레벨입니다!");
            return;
        }

        // 확인 메시지
        int nextLevel = currentLevel + 1;
        long cost = upgradeManager.getUpgradeCost(nextLevel);
        int successRate = upgradeManager.getSuccessRate(nextLevel);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e⚡ GUI 강화 시도!");
        player.sendMessage("§7아이템: §f" + upgradeManager.getItemDisplayName(item));
        player.sendMessage("§7목표: §f" + nextLevel + "강");
        player.sendMessage("§7비용: §6" + formatMoney(cost) + "G");
        player.sendMessage("§7확률: §a" + successRate + "%");

        if (nextLevel >= 5) {
            player.sendMessage("§c실패 시 강화 레벨이 감소합니다!");
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // GUI 닫고 강화 시도
        player.closeInventory();

        // 잠시 후 강화 진행
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            upgradeManager.processUpgrade(player, item);
        }, 10L);

        // 사운드 효과
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * 아이템 정보 표시
     */
    private void showItemInfo(Player player, ItemStack item) {
        if (!upgradeManager.isUpgradeable(item)) return;

        int currentLevel = upgradeManager.getUpgradeLevel(item);
        EnchantUpgradeManager.UpgradeItemType type = upgradeManager.getItemType(item.getType());

        if (type == null) return;

        // 액션바로 간단한 정보 표시
        String typeInfo = "";
        switch (type) {
            case SWORD:
                typeInfo = "검 위력 +" + (currentLevel * 3) + "%";
                if (currentLevel == 10) typeInfo += " + 발화";
                break;
            case AXE:
                typeInfo = "공격속도 +" + (currentLevel * 2) + "%";
                if (currentLevel == 10) typeInfo += " + 출혈";
                break;
            case BOW:
                typeInfo = "활 위력 +" + (currentLevel * 3) + "%";
                if (currentLevel == 10) typeInfo += " + 화염";
                break;
            case CHESTPLATE:
                typeInfo = "방어력 +" + (currentLevel * 3) + "%";
                if (currentLevel == 10) typeInfo += " + 가시";
                break;
        }

        player.sendActionBar("§e" + upgradeManager.getItemDisplayName(item) + " §7- " + typeInfo);

        // 클릭 사운드
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
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
}