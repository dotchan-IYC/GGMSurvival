package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.ggm.ggmsurvival.GGMSurvival;

import java.util.*;

public class EnchantUpgradeManager implements Listener {

    private final GGMSurvival plugin;
    private final EconomyManager economyManager;
    private final NamespacedKey upgradeKey;

    // 강화 비용 (레벨별)
    private final Map<Integer, Long> upgradeCosts = new HashMap<>();

    // 강화 성공 확률 (레벨별)
    private final Map<Integer, Integer> successRates = new HashMap<>();

    public EnchantUpgradeManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.upgradeKey = new NamespacedKey(plugin, "upgrade_level");

        // 강화 비용과 확률 초기화
        initializeUpgradeSettings();
    }

    /**
     * 강화 설정 초기화
     */
    private void initializeUpgradeSettings() {
        // 강화 비용 (G)
        upgradeCosts.put(1, 1000L);     // 1강: 1,000G
        upgradeCosts.put(2, 2000L);     // 2강: 2,000G
        upgradeCosts.put(3, 4000L);     // 3강: 4,000G
        upgradeCosts.put(4, 8000L);     // 4강: 8,000G
        upgradeCosts.put(5, 15000L);    // 5강: 15,000G
        upgradeCosts.put(6, 25000L);    // 6강: 25,000G
        upgradeCosts.put(7, 40000L);    // 7강: 40,000G
        upgradeCosts.put(8, 70000L);    // 8강: 70,000G
        upgradeCosts.put(9, 120000L);   // 9강: 120,000G
        upgradeCosts.put(10, 200000L);  // 10강: 200,000G

        // 성공 확률 (%)
        successRates.put(1, 95);   // 1강: 95%
        successRates.put(2, 90);   // 2강: 90%
        successRates.put(3, 85);   // 3강: 85%
        successRates.put(4, 80);   // 4강: 80%
        successRates.put(5, 70);   // 5강: 70%
        successRates.put(6, 60);   // 6강: 60%
        successRates.put(7, 50);   // 7강: 50%
        successRates.put(8, 40);   // 8강: 40%
        successRates.put(9, 30);   // 9강: 30%
        successRates.put(10, 20);  // 10강: 20%

        plugin.getLogger().info("강화 시스템 설정이 로드되었습니다. (최대 10강)");
    }

    /**
     * 인첸트 테이블 준비 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (item == null) return;

        // 현재 강화 레벨 확인
        int currentLevel = getUpgradeLevel(item);

        if (currentLevel >= 10) {
            event.setCancelled(true);
            player.sendMessage("§c이미 최대 강화 레벨(10강)에 도달했습니다!");
            return;
        }

        // 다음 강화 레벨과 비용 표시
        int nextLevel = currentLevel + 1;
        long cost = upgradeCosts.get(nextLevel);
        int successRate = successRates.get(nextLevel);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l강화 정보");
        player.sendMessage("§7현재 강화: §f" + currentLevel + "강");
        player.sendMessage("§7다음 강화: §f" + nextLevel + "강");
        player.sendMessage("§7필요 비용: §6" + formatMoney(cost) + "G");
        player.sendMessage("§7성공 확률: §a" + successRate + "%");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§a인첸트를 클릭하여 강화를 시도하세요!");
    }

    /**
     * 인첸트 이벤트 (실제 강화 처리)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (item == null) return;

        // 기본 인첸트 취소 (우리가 커스텀 강화로 처리)
        event.setCancelled(true);

        // 현재 강화 레벨 확인
        int currentLevel = getUpgradeLevel(item);

        if (currentLevel >= 10) {
            player.sendMessage("§c이미 최대 강화 레벨에 도달했습니다!");
            return;
        }

        int nextLevel = currentLevel + 1;
        long cost = upgradeCosts.get(nextLevel);

        // G 잔액 확인
        economyManager.getBalance(player.getUniqueId()).thenAccept(balance -> {
            if (balance < cost) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c강화 비용이 부족합니다!");
                    player.sendMessage("§7필요: §6" + formatMoney(cost) + "G §7/ 보유: §6" + formatMoney(balance) + "G");
                });
                return;
            }

            // G 차감
            economyManager.removeMoney(player.getUniqueId(), cost).thenAccept(success -> {
                if (!success) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c강화 비용 차감 중 오류가 발생했습니다!");
                    });
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 강화 시도
                    attemptUpgrade(player, item, nextLevel);
                });
            });
        });
    }

    /**
     * 실제 강화 시도
     */
    private void attemptUpgrade(Player player, ItemStack item, int targetLevel) {
        int successRate = successRates.get(targetLevel);
        boolean success = new Random().nextInt(100) < successRate;

        if (success) {
            // 강화 성공
            applyUpgrade(item, targetLevel);

            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§a§l✨ 강화 성공! ✨");
            player.sendMessage("§7아이템이 §f" + targetLevel + "강§7으로 강화되었습니다!");
            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 성공 효과음
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY,
                    player.getLocation().add(0, 1, 0), 20);

            plugin.getLogger().info(String.format("[강화성공] %s: %s %d강 달성",
                    player.getName(), item.getType(), targetLevel));

        } else {
            // 강화 실패
            int currentLevel = getUpgradeLevel(item);

            player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§c§l💥 강화 실패! 💥");
            player.sendMessage("§7강화에 실패했습니다... (" + successRate + "% 확률)");

            // 실패 시 강화 레벨 감소 (5강 이상부터)
            if (currentLevel >= 5) {
                int newLevel = Math.max(0, currentLevel - 1);
                applyUpgrade(item, newLevel);
                player.sendMessage("§c강화 레벨이 " + newLevel + "강으로 하락했습니다!");
            } else {
                player.sendMessage("§7강화 레벨은 하락하지 않습니다.");
            }

            player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 실패 효과음
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                    player.getLocation().add(0, 1, 0), 15);

            plugin.getLogger().info(String.format("[강화실패] %s: %s 강화 실패 (%d%%)",
                    player.getName(), item.getType(), successRate));
        }
    }

    /**
     * 아이템에 강화 적용
     */
    private void applyUpgrade(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // NBT에 강화 레벨 저장
        meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.INTEGER, level);

        // 아이템 이름에 강화 표시
        String originalName = meta.hasDisplayName() ? meta.getDisplayName() :
                "§f" + item.getType().name().toLowerCase().replace("_", " ");

        // 기존 강화 표시 제거
        originalName = originalName.replaceAll("§[0-9a-f]\\[\\+\\d+\\]\\s*", "");

        if (level > 0) {
            String upgradeColor = getUpgradeColor(level);
            meta.setDisplayName(upgradeColor + "[+" + level + "] " + originalName);
        } else {
            meta.setDisplayName(originalName);
        }

        // 로어에 강화 정보 추가
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        // 기존 강화 로어 제거
        lore.removeIf(line -> line.contains("강화 레벨") || line.contains("추가 능력"));

        if (level > 0) {
            lore.add("");
            lore.add("§6⚡ 강화 레벨: §f" + level + "강");
            lore.add("§7추가 능력: §a+" + (level * 10) + "%");
        }

        meta.setLore(lore);

        // 강화 레벨에 따른 실제 인첸트 적용
        applyEnchantmentsByLevel(meta, level);

        item.setItemMeta(meta);
    }

    /**
     * 강화 레벨에 따른 인첸트 적용
     */
    private void applyEnchantmentsByLevel(ItemMeta meta, int level) {
        // 기존 인첸트 제거 (강화 관련만)
        Set<Enchantment> toRemove = new HashSet<>();
        for (Enchantment ench : meta.getEnchants().keySet()) {
            if (ench == Enchantment.DAMAGE_ALL || ench == Enchantment.PROTECTION_ENVIRONMENTAL ||
                    ench == Enchantment.DIG_SPEED || ench == Enchantment.ARROW_DAMAGE) {
                toRemove.add(ench);
            }
        }
        toRemove.forEach(meta::removeEnchant);

        if (level <= 0) return;

        // 아이템 타입에 따른 인첸트 적용
        Material material = meta.hasDisplayName() ?
                Material.DIAMOND_SWORD : Material.DIAMOND_SWORD; // 임시

        String typeName = material.name();

        if (typeName.contains("SWORD")) {
            // 검 - 날카로움
            meta.addEnchant(Enchantment.DAMAGE_ALL, Math.min(level, 5), true);
        } else if (typeName.contains("PICKAXE") || typeName.contains("AXE") || typeName.contains("SHOVEL")) {
            // 도구 - 효율성
            meta.addEnchant(Enchantment.DIG_SPEED, Math.min(level, 5), true);
        } else if (typeName.contains("BOW")) {
            // 활 - 힘
            meta.addEnchant(Enchantment.ARROW_DAMAGE, Math.min(level, 5), true);
        } else if (typeName.contains("HELMET") || typeName.contains("CHESTPLATE") ||
                typeName.contains("LEGGINGS") || typeName.contains("BOOTS")) {
            // 방어구 - 보호
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, Math.min(level, 4), true);
        }

        // 높은 강화 레벨일수록 추가 인첸트
        if (level >= 5) {
            meta.addEnchant(Enchantment.DURABILITY, Math.min(level - 2, 3), true);
        }

        if (level >= 8) {
            meta.addEnchant(Enchantment.MENDING, 1, true);
        }
    }

    /**
     * 강화 레벨 색상 반환
     */
    private String getUpgradeColor(int level) {
        if (level >= 10) return "§d"; // 보라색 (10강)
        if (level >= 7) return "§6";  // 금색 (7~9강)
        if (level >= 4) return "§e";  // 노란색 (4~6강)
        if (level >= 1) return "§a";  // 초록색 (1~3강)
        return "§f";                   // 흰색 (0강)
    }

    /**
     * 아이템의 강화 레벨 조회
     */
    public int getUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().getOrDefault(upgradeKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * 강화 비용 조회
     */
    public long getUpgradeCost(int level) {
        return upgradeCosts.getOrDefault(level, 0L);
    }

    /**
     * 강화 성공 확률 조회
     */
    public int getSuccessRate(int level) {
        return successRates.getOrDefault(level, 0);
    }

    /**
     * 금액 포맷팅
     */
    private String formatMoney(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * 강화 정보 표시
     */
    public void showUpgradeInfo(Player player, ItemStack item) {
        if (item == null) {
            player.sendMessage("§c손에 아이템을 들어주세요!");
            return;
        }

        int currentLevel = getUpgradeLevel(item);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l강화 정보");
        player.sendMessage("§7아이템: §f" + item.getType().name());
        player.sendMessage("§7현재 강화: §f" + currentLevel + "강");

        if (currentLevel < 10) {
            int nextLevel = currentLevel + 1;
            long cost = getUpgradeCost(nextLevel);
            int successRate = getSuccessRate(nextLevel);

            player.sendMessage("§7다음 강화: §f" + nextLevel + "강");
            player.sendMessage("§7필요 비용: §6" + formatMoney(cost) + "G");
            player.sendMessage("§7성공 확률: §a" + successRate + "%");

            if (nextLevel > 5) {
                player.sendMessage("§c※ 실패 시 강화 레벨이 1 감소합니다!");
            }
        } else {
            player.sendMessage("§d최대 강화 레벨에 도달했습니다!");
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}