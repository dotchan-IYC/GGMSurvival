// 완전 안정화된 AxeSpeedManager.java
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 완전 안정화된 도끼 공격속도 시스템 매니저
 * - 도끼 강화 시 공격간격 감소 (더 빨라짐)
 * - Thread-Safe 구현
 * - 메모리 누수 방지
 * - 성능 최적화
 */
public class AxeSpeedManager implements Listener {

    private final GGMSurvival plugin;
    private final EnchantUpgradeManager upgradeManager;

    // Thread-Safe 컬렉션들
    private final ConcurrentHashMap<UUID, AttributeModifier> appliedModifiers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> lastKnownUpgradeLevel = new ConcurrentHashMap<>();

    // 도끼 타입 캐시 (성능 최적화)
    private final Set<Material> axeTypes;
    private final Map<String, Boolean> axeCache = new ConcurrentHashMap<>();

    // AttributeModifier 키
    private final NamespacedKey axeSpeedKey;

    // 설정값들
    private final double speedPerLevel;
    private final double maxSpeedBonus;

    // 업데이트 큐 (성능 최적화)
    private final Set<UUID> playersNeedingUpdate = ConcurrentHashMap.newKeySet();

    public AxeSpeedManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getEnchantUpgradeManager();
        this.axeSpeedKey = new NamespacedKey(plugin, "axe_speed_bonus");

        try {
            // 도끼 타입 초기화
            this.axeTypes = initializeAxeTypes();

            // 설정값 로드
            this.speedPerLevel = plugin.getConfig().getDouble("upgrade_system.axe_speed_per_level", 0.02);
            this.maxSpeedBonus = plugin.getConfig().getDouble("upgrade_system.max_axe_speed_bonus", 0.20);

            // 주기적 업데이트 태스크 시작
            startUpdateTask();

            plugin.getLogger().info("AxeSpeedManager 안정화 초기화 완료");
            plugin.getLogger().info("도끼 타입: " + axeTypes.size() + "개, 레벨당 속도: " + (speedPerLevel * 100) + "%");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "AxeSpeedManager 초기화 실패", e);
            throw new RuntimeException("AxeSpeedManager 초기화 실패", e);
        }
    }

    /**
     * 도끼 타입 초기화
     */
    private Set<Material> initializeAxeTypes() {
        Set<Material> axes = EnumSet.noneOf(Material.class);

        try {
            for (Material material : Material.values()) {
                String name = material.name();
                if (name.contains("_AXE") && !name.equals("WOODEN_AXE")) {
                    axes.add(material);
                }
            }

            plugin.getLogger().info("도끼 타입 " + axes.size() + "개 로드됨: " + axes);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "도끼 타입 초기화 중 오류", e);
        }

        return Collections.unmodifiableSet(axes);
    }

    /**
     * 주기적 업데이트 태스크 시작
     */
    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.isShuttingDown()) {
                    cancel();
                    return;
                }

                try {
                    // 업데이트가 필요한 플레이어들 처리
                    Iterator<UUID> iterator = playersNeedingUpdate.iterator();
                    while (iterator.hasNext()) {
                        UUID uuid = iterator.next();
                        Player player = plugin.getServer().getPlayer(uuid);

                        if (player != null && player.isOnline()) {
                            updatePlayerAxeSpeedSafe(player);
                        }

                        iterator.remove();
                    }

                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "도끼 속도 업데이트 태스크 중 오류", e);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1초마다 실행
    }

    /**
     * 플레이어 접속 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            // 지연 후 도끼 속도 확인 (다른 시스템 로딩 대기)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    markForUpdate(player);
                }
            }, 40L); // 2초 후

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 접속 도끼 속도 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 플레이어 퇴장 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        try {
            // 캐시에서 제거
            appliedModifiers.remove(uuid);
            lastKnownUpgradeLevel.remove(uuid);
            playersNeedingUpdate.remove(uuid);

            // 도끼 속도 보너스 제거
            removeAxeSpeedBonus(player);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 퇴장 도끼 속도 정리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 핫바 아이템 변경 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        if (plugin.isShuttingDown()) return;

        try {
            // 1틱 후 업데이트 (아이템 변경 완료 후)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    markForUpdate(player);
                }
            }, 1L);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "핫바 변경 도끼 속도 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 인벤토리 클릭 이벤트 (핫바 관련 변경)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.isShuttingDown()) return;

        try {
            // 핫바 슬롯이 관련된 클릭인지 확인
            if (isHotbarRelatedClick(event)) {
                // 1틱 후 업데이트
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        markForUpdate(player);
                    }
                }, 1L);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "인벤토리 클릭 도끼 속도 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 핫바 관련 클릭인지 확인
     */
    private boolean isHotbarRelatedClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();

        // 핫바 슬롯 직접 클릭
        if (slot >= 0 && slot <= 8) {
            return true;
        }

        // 현재 들고 있는 슬롯과 관련된 변경
        if (slot == player.getInventory().getHeldItemSlot()) {
            return true;
        }

        // 숫자 키를 사용한 핫바 스왑
        if (event.getHotbarButton() >= 0) {
            return true;
        }

        return false;
    }

    /**
     * 업데이트 마크
     */
    private void markForUpdate(Player player) {
        playersNeedingUpdate.add(player.getUniqueId());
    }

    /**
     * 안전한 플레이어 도끼 속도 업데이트
     */
    private void updatePlayerAxeSpeedSafe(Player player) {
        try {
            if (upgradeManager == null) {
                plugin.getLogger().warning("EnchantUpgradeManager가 null - 도끼 속도 업데이트 불가");
                return;
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (isAxe(mainHand)) {
                // 도끼를 들고 있음 - 속도 보너스 적용
                int upgradeLevel = upgradeManager.getCurrentUpgradeLevel(mainHand);
                applyAxeSpeedBonus(player, upgradeLevel);
            } else {
                // 도끼를 들고 있지 않음 - 속도 보너스 제거
                removeAxeSpeedBonus(player);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "도끼 속도 업데이트 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 도끼 속도 보너스 적용
     */
    private void applyAxeSpeedBonus(Player player, int upgradeLevel) {
        if (upgradeLevel <= 0) {
            removeAxeSpeedBonus(player);
            return;
        }

        try {
            UUID uuid = player.getUniqueId();

            // 이미 같은 레벨의 보너스가 적용되어 있다면 스킵
            Integer lastLevel = lastKnownUpgradeLevel.get(uuid);
            if (lastLevel != null && lastLevel == upgradeLevel) {
                return;
            }

            AttributeInstance attackSpeedAttr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (attackSpeedAttr == null) {
                plugin.getLogger().warning("공격속도 Attribute가 null: " + player.getName());
                return;
            }

            // 기존 보너스 제거
            AttributeModifier existingModifier = appliedModifiers.get(uuid);
            if (existingModifier != null) {
                attackSpeedAttr.removeModifier(existingModifier);
            }

            // 새 보너스 계산 및 적용
            double speedBonus = Math.min(upgradeLevel * speedPerLevel, maxSpeedBonus);

            UUID modifierUUID = UUID.nameUUIDFromBytes(axeSpeedKey.toString().getBytes());
            AttributeModifier speedModifier = new AttributeModifier(
                    modifierUUID,
                    "axe_upgrade_speed_bonus",
                    speedBonus,
                    AttributeModifier.Operation.ADD_NUMBER
            );

            attackSpeedAttr.addModifier(speedModifier);

            // 캐시 업데이트
            appliedModifiers.put(uuid, speedModifier);
            lastKnownUpgradeLevel.put(uuid, upgradeLevel);

            // 디버그 로그
            if (plugin.getConfig().getBoolean("debug.log_axe_speed", false)) {
                plugin.getLogger().info(String.format(
                        "[도끼속도] %s: +%d강 -> +%.1f%% 공격속도",
                        player.getName(), upgradeLevel, speedBonus * 100));
            }

            // 플레이어에게 알림 (첫 적용시에만)
            if (lastLevel == null || lastLevel == 0) {
                player.sendActionBar(String.format("§6🔨 도끼 강화 효과: 공격속도 +%.1f%%", speedBonus * 100));
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "도끼 속도 보너스 적용 실패: " + player.getName(), e);
        }
    }

    /**
     * 도끼 속도 보너스 제거
     */
    private void removeAxeSpeedBonus(Player player) {
        try {
            UUID uuid = player.getUniqueId();
            AttributeModifier modifier = appliedModifiers.get(uuid);

            if (modifier != null) {
                AttributeInstance attackSpeedAttr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
                if (attackSpeedAttr != null) {
                    attackSpeedAttr.removeModifier(modifier);
                }

                // 캐시에서 제거
                appliedModifiers.remove(uuid);
                lastKnownUpgradeLevel.remove(uuid);

                // 디버그 로그
                if (plugin.getConfig().getBoolean("debug.log_axe_speed", false)) {
                    plugin.getLogger().info("[도끼속도] " + player.getName() + ": 보너스 제거됨");
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "도끼 속도 보너스 제거 실패: " + player.getName(), e);
        }
    }

    /**
     * 아이템이 도끼인지 확인 (캐시 사용)
     */
    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        try {
            String materialName = item.getType().name();

            // 캐시에서 확인
            Boolean cached = axeCache.get(materialName);
            if (cached != null) {
                return cached;
            }

            // 새로 계산 후 캐시에 저장
            boolean isAxe = axeTypes.contains(item.getType());
            axeCache.put(materialName, isAxe);

            return isAxe;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "도끼 확인 중 오류", e);
            return false;
        }
    }

    /**
     * 플레이어의 현재 도끼 속도 보너스 반환
     */
    public double getCurrentSpeedBonus(Player player) {
        try {
            Integer level = lastKnownUpgradeLevel.get(player.getUniqueId());
            if (level == null || level <= 0) {
                return 0.0;
            }

            return Math.min(level * speedPerLevel, maxSpeedBonus);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "도끼 속도 보너스 확인 중 오류: " + player.getName(), e);
            return 0.0;
        }
    }

    /**
     * 플레이어의 도끼 강화 정보 표시
     */
    public void showAxeSpeedInfo(Player player) {
        try {
            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (!isAxe(mainHand)) {
                player.sendMessage("§c도끼를 들고 이 명령어를 사용해주세요!");
                return;
            }

            if (upgradeManager == null) {
                player.sendMessage("§c강화 시스템이 비활성화되어 있습니다.");
                return;
            }

            int upgradeLevel = upgradeManager.getCurrentUpgradeLevel(mainHand);
            double currentBonus = getCurrentSpeedBonus(player);
            double nextLevelBonus = Math.min((upgradeLevel + 1) * speedPerLevel, maxSpeedBonus);

            player.sendMessage("§6==========================================");
            player.sendMessage("§e도끼 공격속도 정보");
            player.sendMessage("§7아이템: §f" + getAxeDisplayName(mainHand));
            player.sendMessage("§7강화 레벨: §a+" + upgradeLevel + "강");
            player.sendMessage("§7현재 속도 보너스: §a+" + String.format("%.1f%%", currentBonus * 100));

            if (upgradeLevel < 10) {
                player.sendMessage("§7다음 레벨 보너스: §e+" + String.format("%.1f%%", nextLevelBonus * 100));
                double improvement = nextLevelBonus - currentBonus;
                if (improvement > 0) {
                    player.sendMessage("§7강화 시 증가: §b+" + String.format("%.1f%%", improvement * 100));
                }
            } else {
                player.sendMessage("§6★ 최대 강화 달성!");
            }

            player.sendMessage("§7최대 보너스: §c+" + String.format("%.1f%%", maxSpeedBonus * 100));
            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "도끼 속도 정보 표시 중 오류: " + player.getName(), e);
            player.sendMessage("§c정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 도끼 표시 이름 반환
     */
    private String getAxeDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // Material 이름을 한글로 변환
        String materialName = item.getType().name();
        switch (materialName) {
            case "WOODEN_AXE": return "나무 도끼";
            case "STONE_AXE": return "돌 도끼";
            case "IRON_AXE": return "철 도끼";
            case "GOLDEN_AXE": return "금 도끼";
            case "DIAMOND_AXE": return "다이아몬드 도끼";
            case "NETHERITE_AXE": return "네더라이트 도끼";
            default: return materialName;
        }
    }

    /**
     * 모든 플레이어의 도끼 속도 강제 업데이트
     */
    public void updateAllPlayersAxeSpeed() {
        try {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                markForUpdate(player);
            }

            plugin.getLogger().info("모든 플레이어의 도끼 속도 업데이트 요청됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "전체 도끼 속도 업데이트 중 오류", e);
        }
    }

    /**
     * 캐시 정리
     */
    public void cleanupCache() {
        try {
            // 오프라인 플레이어 데이터 정리
            Set<UUID> onlineUUIDs = new HashSet<>();
            plugin.getServer().getOnlinePlayers().forEach(player -> onlineUUIDs.add(player.getUniqueId()));

            appliedModifiers.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            lastKnownUpgradeLevel.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            playersNeedingUpdate.removeIf(uuid -> !onlineUUIDs.contains(uuid));

            plugin.getLogger().info("AxeSpeedManager 캐시 정리 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "도끼 속도 캐시 정리 중 오류", e);
        }
    }

    /**
     * 설정 리로드
     */
    public void reloadConfig() {
        try {
            // 모든 플레이어의 보너스 제거 후 재적용
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                removeAxeSpeedBonus(player);
                markForUpdate(player);
            }

            plugin.getLogger().info("AxeSpeedManager 설정 리로드 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "도끼 속도 설정 리로드 중 오류", e);
        }
    }

    /**
     * 매니저 종료
     */
    public void onDisable() {
        try {
            // 모든 플레이어의 도끼 속도 보너스 제거
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                removeAxeSpeedBonus(player);
            }

            // 캐시 정리
            cleanupCache();

            // 모든 맵 정리
            appliedModifiers.clear();
            lastKnownUpgradeLevel.clear();
            playersNeedingUpdate.clear();
            axeCache.clear();

            plugin.getLogger().info("AxeSpeedManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "AxeSpeedManager 종료 중 오류", e);
        }
    }

    // Getter 메서드들
    public double getSpeedPerLevel() {
        return speedPerLevel;
    }

    public double getMaxSpeedBonus() {
        return maxSpeedBonus;
    }

    public Set<Material> getAxeTypes() {
        return axeTypes;
    }
}