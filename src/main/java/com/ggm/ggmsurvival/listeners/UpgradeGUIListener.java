// 완전 안정화된 UpgradeGUIListener.java
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/**
 * 완전 안정화된 강화 GUI 리스너
 * - 강화 GUI 처리
 * - 강력한 예외 처리
 * - 무효한 클릭 방지
 */
public class UpgradeGUIListener implements Listener {

    private final GGMSurvival plugin;
    private final EnchantUpgradeManager upgradeManager;

    public UpgradeGUIListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getEnchantUpgradeManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        // 기본 검증
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.isShuttingDown()) return;
        if (upgradeManager == null) return;

        try {
            String title = event.getView().getTitle();

            // 강화 GUI인지 확인
            if (!"§6§l강화 시스템".equals(title)) return;

            // 모든 클릭 취소
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // 클릭된 아이템에 따른 동작 처리
            switch (clickedItem.getType()) {
                case ANVIL:
                    // 강화 실행
                    handleUpgradeClick(player);
                    break;

                case PAPER:
                    // 성공률 확인
                    handleRateCheck(player);
                    break;

                case ENCHANTED_BOOK:
                    // 강화 가이드
                    handleGuideClick(player);
                    break;

                case BOOK:
                    // 정보 확인 (아무 동작 없음)
                    break;

                default:
                    // 기타 아이템 (장식용) - 아무 동작 없음
                    break;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 GUI 처리 중 오류: " + player.getName(), e);

            player.closeInventory();
            player.sendMessage("§c강화 GUI 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 강화 실행 클릭 처리
     */
    private void handleUpgradeClick(Player player) {
        try {
            player.closeInventory();

            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (mainHand == null || mainHand.getType() == Material.AIR) {
                player.sendMessage("§c강화할 아이템을 들고 사용해주세요!");
                return;
            }

            // 쿨다운 확인
            if (upgradeManager.isOnCooldown(player)) {
                player.sendMessage("§c잠시 후 다시 시도해주세요. (쿨다운: 1초)");
                return;
            }

            // 강화 실행
            boolean success = upgradeManager.performUpgrade(player, mainHand);

            if (success) {
                plugin.getLogger().info(String.format("[GUI강화성공] %s이(가) %s을(를) 강화했습니다.",
                        player.getName(), mainHand.getType().name()));
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "GUI 강화 실행 중 오류: " + player.getName(), e);
            player.sendMessage("§c강화 중 오류가 발생했습니다.");
        }
    }

    /**
     * 성공률 확인 클릭 처리
     */
    private void handleRateCheck(Player player) {
        try {
            player.closeInventory();

            // 성공률 정보 표시
            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l강화 성공률 & 파괴율");
            player.sendMessage("");
            player.sendMessage("§a성공률:");
            player.sendMessage("§7• 1~3강: §a95%");
            player.sendMessage("§7• 4~5강: §e80%");
            player.sendMessage("§7• 6~7강: §660%");
            player.sendMessage("§7• 8~9강: §c40%");
            player.sendMessage("§7• 10강: §4§l20%");
            player.sendMessage("");
            player.sendMessage("§c파괴/하락률:");
            player.sendMessage("§7• 1~4강: §a파괴되지 않음");
            player.sendMessage("§7• 5~7강: §e10% (1강 하락)");
            player.sendMessage("§7• 8~9강: §c20% (1강 하락)");
            player.sendMessage("§7• 10강: §4§l30% (1강 하락)");
            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "성공률 확인 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 가이드 클릭 처리
     */
    private void handleGuideClick(Player player) {
        try {
            player.closeInventory();

            // 강화 가이드 표시
            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l강화 시스템 가이드");
            player.sendMessage("");
            player.sendMessage("§a강화 가능한 아이템:");
            player.sendMessage("§7• 검류 (모든 티어)");
            player.sendMessage("§7• 도끼류 (모든 티어)");
            player.sendMessage("§7• 활/쇠뇌");
            player.sendMessage("§7• 흉갑류 (모든 티어)");
            player.sendMessage("");
            player.sendMessage("§a강화 효과:");
            player.sendMessage("§7• 검/활: §a공격력 +3%/레벨");
            player.sendMessage("§7• 도끼: §a공격속도 +2%/레벨");
            player.sendMessage("§7• 흉갑: §a방어력 +3%/레벨");
            player.sendMessage("");
            player.sendMessage("§6★ 10강 특수 효과:");
            player.sendMessage("§7• 검: §c출혈 효과");
            player.sendMessage("§7• 도끼: §6발화 효과");
            player.sendMessage("§7• 활: §a화염 화살");
            player.sendMessage("§7• 흉갑: §9가시 효과");
            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "가이드 표시 중 오류: " + player.getName(), e);
        }
    }
}