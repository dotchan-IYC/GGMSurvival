// UpgradeListener.java - 강화 관련 이벤트 처리 (이모티콘 제거)
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/**
 * 강화 관련 이벤트 리스너
 * - 강화 아이템 보호
 * - 강화 UI 상호작용
 * - 강화 정보 표시
 */
public class UpgradeListener implements Listener {

    private final GGMSurvival plugin;

    public UpgradeListener(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    /**
     * 플레이어 상호작용 이벤트 - 강화 정보 표시
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 강화된 아이템 정보 표시 (Shift + 우클릭)
        if (event.getAction().name().contains("RIGHT_CLICK") &&
                player.isSneaking() &&
                item != null &&
                plugin.getEnchantUpgradeManager() != null) {

            try {
                int upgradeLevel = plugin.getEnchantUpgradeManager().getUpgradeLevel(item);

                if (upgradeLevel > 0) {
                    player.sendMessage("§6[강화 정보]");
                    player.sendMessage("§7아이템: §f" + getItemDisplayName(item));
                    player.sendMessage("§7강화 레벨: §a+" + upgradeLevel);

                    if (upgradeLevel < 10) {
                        int nextLevel = upgradeLevel + 1;
                        int successRate = plugin.getEnchantUpgradeManager().getSuccessRate(nextLevel);
                        long cost = plugin.getEnchantUpgradeManager().getUpgradeCost(nextLevel);

                        player.sendMessage("§7다음 강화: §e" + successRate + "% §7(비용: §f" +
                                plugin.getEconomyManager().formatMoneyWithSymbol(cost) + "§7)");
                    } else {
                        player.sendMessage("§c최대 강화 레벨에 도달했습니다!");
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "강화 정보 표시 중 오류: " + player.getName(), e);
            }
        }
    }

    /**
     * 인벤토리 클릭 이벤트 - 강화 아이템 보호
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        // 모루에서 강화된 아이템 수리 방지
        if (event.getInventory().getType() == InventoryType.ANVIL &&
                clickedItem != null &&
                plugin.getEnchantUpgradeManager() != null) {

            try {
                int upgradeLevel = plugin.getEnchantUpgradeManager().getUpgradeLevel(clickedItem);

                if (upgradeLevel > 0) {
                    // 강화된 아이템의 모루 사용 제한
                    event.setCancelled(true);
                    player.sendMessage("§c강화된 아이템은 모루에서 수리할 수 없습니다.");
                    player.sendMessage("§7강화 시스템을 이용해 내구도를 관리하세요.");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "강화 아이템 보호 처리 중 오류: " + player.getName(), e);
            }
        }
    }

    /**
     * 아이템 표시 이름 가져오기
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ");
    }
}