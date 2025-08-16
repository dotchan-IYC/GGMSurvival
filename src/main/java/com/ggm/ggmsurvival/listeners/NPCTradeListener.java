package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

import java.util.logging.Level;

/**
 * NPC 교환 관련 이벤트 리스너
 * - NPC 상호작용 처리
 * - 교환 검증 및 실행
 * - 교환 쿨다운 관리
 */
public class NPCTradeListener implements Listener {

    private final GGMSurvival plugin;

    public NPCTradeListener(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    /**
     * NPC 상호작용 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) {
            return;
        }

        Player player = event.getPlayer();
        Villager villager = (Villager) event.getRightClicked();

        try {
            // 커스텀 NPC인지 확인
            if (plugin.getNPCTradeManager() != null) {
                NPCTradeManager.TradeNPC tradeNPC = plugin.getNPCTradeManager()
                        .getActiveNPCs().get(villager.getUniqueId());

                if (tradeNPC != null) {
                    // 환영 메시지
                    player.sendMessage("§6[" + tradeNPC.getDisplayName() + "] §f어서오세요!");
                    player.sendMessage("§7원하는 아이템을 선택해주세요.");

                    // 교환 정보 표시 (Shift + 우클릭 시)
                    if (player.isSneaking()) {
                        event.setCancelled(true);
                        showTradeInfo(player, tradeNPC);
                        return;
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 상호작용 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 상인 인벤토리 클릭 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMerchantInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        MerchantInventory merchantInventory = (MerchantInventory) event.getInventory();

        try {
            // 결과 슬롯 클릭 시 (교환 시도)
            if (event.getSlot() == 2 && event.getInventory().getType() == InventoryType.MERCHANT) {
                ItemStack result = event.getCurrentItem();

                if (result != null && plugin.getNPCTradeManager() != null) {
                    Villager merchant = (Villager) merchantInventory.getHolder();

                    if (merchant != null) {
                        // 입력 아이템 확인
                        ItemStack input = merchantInventory.getItem(0);

                        if (input != null) {
                            // 커스텀 교환 처리
                            boolean success = plugin.getNPCTradeManager()
                                    .attemptTrade(player, merchant, input, result);

                            if (!success) {
                                event.setCancelled(true);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "상인 교환 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 교환 정보 표시
     */
    private void showTradeInfo(Player player, NPCTradeManager.TradeNPC tradeNPC) {
        player.sendMessage("§6=== " + tradeNPC.getDisplayName() + " 정보 ===");
        player.sendMessage("§7위치: " + formatLocation(tradeNPC.getLocation()));

        // 쿨다운 및 교환 횟수 정보
        if (plugin.getNPCTradeManager() != null) {
            player.sendMessage("§7교환 쿨다운: " +
                    plugin.getNPCTradeManager().getTradeCooldownSeconds() + "초");
            player.sendMessage("§7일일 최대 교환: " +
                    plugin.getNPCTradeManager().getMaxTradesPerDay() + "회");
        }

        player.sendMessage("§7좌클릭하여 교환하세요!");
        player.sendMessage("§6=====================");
    }

    /**
     * 위치 포맷팅
     */
    private String formatLocation(org.bukkit.Location location) {
        return String.format("(%d, %d, %d)",
                (int) location.getX(),
                (int) location.getY(),
                (int) location.getZ());
    }
}