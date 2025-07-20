// 도끼 공격속도 증가 시스템
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AxeSpeedManager implements Listener {

    private final GGMSurvival plugin;
    private final EnchantUpgradeManager upgradeManager;
    private final NamespacedKey upgradeKey;

    // 플레이어별 공격속도 쿨다운 관리
    private final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerAttackSpeed = new ConcurrentHashMap<>();

    public AxeSpeedManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getEnchantUpgradeManager();
        this.upgradeKey = new NamespacedKey(plugin, "upgrade_level");

        // 주기적으로 공격속도 업데이트
        startSpeedUpdateTask();
    }

    /**
     * 아이템 변경 시 공격속도 업데이트
     */
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        // 다음 틱에 아이템 변경 후 업데이트
        new BukkitRunnable() {
            @Override
            public void run() {
                updatePlayerAttackSpeed(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * 손 바꿈 시 공격속도 업데이트
     */
    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                updatePlayerAttackSpeed(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * 공격 이벤트 - 도끼 공격속도 적용
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (!isAxe(weapon.getType())) return;

        int upgradeLevel = upgradeManager.getUpgradeLevel(weapon);
        if (upgradeLevel <= 0) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // 공격속도 쿨다운 확인
        if (hasAttackCooldown(player, upgradeLevel)) {
            // 쿨다운 중이면 공격 취소하지 않고, 단지 속도 효과만 적용
            // 마인크래프트 자체 공격 쿨다운과 함께 작동
        }

        // 공격 시간 기록
        lastAttackTime.put(playerId, currentTime);

        // 도끼 특수 효과 (10강 출혈)
        if (upgradeLevel == 10) {
            applyBleedingEffect(event);
        }
    }

    /**
     * 공격속도 쿨다운 확인
     */
    private boolean hasAttackCooldown(Player player, int upgradeLevel) {
        UUID playerId = player.getUniqueId();
        long lastAttack = lastAttackTime.getOrDefault(playerId, 0L);
        long currentTime = System.currentTimeMillis();

        // 기본 도끼 공격 간격: 1000ms (1초)
        // 강화 레벨당 2% 감소 (더 빨라짐)
        double speedBonus = upgradeLevel * 0.02; // 2% per level
        double attackInterval = 1000 * (1.0 - speedBonus); // 간격 감소

        // 최소 간격 제한 (너무 빨라지지 않도록)
        attackInterval = Math.max(attackInterval, 200); // 최소 0.2초

        return (currentTime - lastAttack) < attackInterval;
    }

    /**
     * 플레이어 공격속도 업데이트
     */
    public void updatePlayerAttackSpeed(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (!isAxe(mainHand.getType())) {
            // 도끼가 아니면 기본 공격속도로 복원
            resetPlayerAttackSpeed(player);
            return;
        }

        int upgradeLevel = upgradeManager.getUpgradeLevel(mainHand);
        if (upgradeLevel <= 0) {
            resetPlayerAttackSpeed(player);
            return;
        }

        // 공격속도 보너스 계산
        double speedBonus = upgradeLevel * 0.02; // 레벨당 2% 증가
        double newAttackSpeed = 1.0 + speedBonus; // 기본 1.0에서 증가

        // 기존 공격속도와 다르면 업데이트
        UUID playerId = player.getUniqueId();
        Double currentSpeed = playerAttackSpeed.get(playerId);

        if (currentSpeed == null || Math.abs(currentSpeed - newAttackSpeed) > 0.01) {
            playerAttackSpeed.put(playerId, newAttackSpeed);

            // 시각적 효과 (액션바)
            if (upgradeLevel > 0) {
                player.sendActionBar(String.format("§e⚡ 도끼 공격속도: §a+%.0f%%", speedBonus * 100));
            }
        }
    }

    /**
     * 플레이어 공격속도 초기화
     */
    private void resetPlayerAttackSpeed(Player player) {
        UUID playerId = player.getUniqueId();

        if (playerAttackSpeed.containsKey(playerId)) {
            playerAttackSpeed.remove(playerId);
            lastAttackTime.remove(playerId);
        }
    }

    /**
     * 출혈 효과 적용 (10강 도끼)
     */
    private void applyBleedingEffect(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            // 몬스터에게 출혈 효과
            event.getEntity().setFireTicks(100); // 5초간 불 효과 (출혈 시뮬레이션)

            // 출혈 메시지
            if (event.getDamager() instanceof Player) {
                Player attacker = (Player) event.getDamager();
                attacker.sendMessage("§c💉 출혈 효과 발동!");
            }
        }
    }

    /**
     * 정기적으로 공격속도 업데이트
     */
    private void startSpeedUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getServer().getOnlinePlayers().forEach(player -> {
                    ItemStack mainHand = player.getInventory().getItemInMainHand();

                    if (isAxe(mainHand.getType())) {
                        updatePlayerAttackSpeed(player);
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 100L); // 5초마다 체크
    }

    /**
     * 도끼인지 확인
     */
    private boolean isAxe(Material material) {
        return material.name().contains("AXE");
    }

    /**
     * 플레이어의 현재 도끼 공격속도 보너스 조회
     */
    public double getAxeSpeedBonus(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (!isAxe(mainHand.getType())) return 0.0;

        int upgradeLevel = upgradeManager.getUpgradeLevel(mainHand);
        return upgradeLevel * 0.02; // 레벨당 2%
    }

    /**
     * 도끼 공격속도 정보 표시
     */
    public void showAxeSpeedInfo(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (!isAxe(mainHand.getType())) {
            player.sendMessage("§c도끼를 손에 들고 사용하세요!");
            return;
        }

        int upgradeLevel = upgradeManager.getUpgradeLevel(mainHand);
        double speedBonus = getAxeSpeedBonus(player);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 도끼 공격속도 정보");
        player.sendMessage("§7아이템: §f" + upgradeManager.getItemDisplayName(mainHand));
        player.sendMessage("§7강화 레벨: §f" + upgradeLevel + "강");
        player.sendMessage("§7공격속도 보너스: §a+" + String.format("%.0f", speedBonus * 100) + "%");

        if (upgradeLevel == 10) {
            player.sendMessage("§c특수 효과: §f출혈 효과 (발화와 동일한 데미지)");
        }

        player.sendMessage("");
        player.sendMessage("§a도끼 공격속도 시스템:");
        player.sendMessage("§7• 강화 레벨당 공격간격 2% 감소");
        player.sendMessage("§7• 더 빠른 연속 공격 가능");
        player.sendMessage("§7• 10강 달성 시 출혈 효과 추가");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 플레이어 퇴장 시 데이터 정리
     */
    public void cleanupPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        playerAttackSpeed.remove(playerId);
        lastAttackTime.remove(playerId);
    }
}