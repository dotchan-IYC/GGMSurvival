// 수정된 EnchantUpgradeManager.java - 인챈트 상수 오류 수정
package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
import org.bukkit.inventory.Inventory;
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

    // 강화 시도 중인 플레이어 (중복 방지)
    private final Set<UUID> upgrading = new HashSet<>();

    public EnchantUpgradeManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.upgradeKey = new NamespacedKey(plugin, "upgrade_level");

        // 강화 비용과 확률 초기화
        initializeUpgradeSettings();

        plugin.getLogger().info("강화 시스템 초기화 완료 - 최대 10강까지 업그레이드 가능");
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

        plugin.getLogger().info("강화 시스템 설정이 로드되었습니다 (최대 10강)");
    }

    /**
     * 인챈트 테이블 준비 이벤트 - 강화 정보 표시
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) return;

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

        // 기본 인챈트 비용 표시 대신 강화 정보 표시
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 강화 정보");
        player.sendMessage("§7아이템: §f" + getItemDisplayName(item));
        player.sendMessage("§7현재 강화: §f" + currentLevel + "강");
        player.sendMessage("§7다음 강화: §f" + nextLevel + "강");
        player.sendMessage("§7필요 비용: §6" + formatMoney(cost) + "G");
        player.sendMessage("§7성공 확률: §a" + successRate + "%");
        if (nextLevel >= 5) {
            player.sendMessage("§7실패 시: §c강화 레벨 1 감소");
        }
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§a인챈트를 클릭하여 강화를 시도하세요!");
    }

    /**
     * 인챈트 이벤트 (실제 강화 처리)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) return;

        // 기본 인챈트 취소 (우리가 커스텀 강화로 처리)
        event.setCancelled(true);

        // 중복 강화 방지
        UUID uuid = player.getUniqueId();
        if (upgrading.contains(uuid)) {
            player.sendMessage("§c이미 강화를 진행 중입니다!");
            return;
        }

        upgrading.add(uuid);

        // 현재 강화 레벨 확인
        int currentLevel = getUpgradeLevel(item);

        if (currentLevel >= 10) {
            player.sendMessage("§c이미 최대 강화 레벨에 도달했습니다!");
            upgrading.remove(uuid);
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
                    upgrading.remove(uuid);
                });
                return;
            }

            // G 차감
            economyManager.removeMoney(player.getUniqueId(), player.getName(), cost).thenAccept(success -> {
                if (!success) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c강화 비용 차감 중 오류가 발생했습니다!");
                        upgrading.remove(uuid);
                    });
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 강화 시도
                    attemptUpgrade(player, item, nextLevel);
                    upgrading.remove(uuid);
                });
            }).exceptionally(throwable -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c강화 처리 중 오류 발생: " + throwable.getMessage());
                    upgrading.remove(uuid);
                });
                return null;
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§c잔액 확인 중 오류 발생: " + throwable.getMessage());
                upgrading.remove(uuid);
            });
            return null;
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
            player.sendMessage("§a능력이 §6" + (targetLevel * 10) + "%§a 향상되었습니다!");
            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 성공 효과
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5);

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

            // 실패 효과
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            player.getWorld().spawnParticle(Particle.SMOKE_LARGE, player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5);

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
                "§f" + getItemDisplayName(item);

        // 기존 강화 표시 제거
        originalName = originalName.replaceAll("§[0-9a-f]\\[\\+\\d+\\]\\s*", "");

        if (level > 0) {
            String upgradeColor = getUpgradeColor(level);
            meta.setDisplayName(upgradeColor + "[+" + level + "] " + originalName);
        } else {
            meta.setDisplayName(originalName);
        }

        // 로어에 강화 정보 추가
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // 기존 강화 로어 제거
        lore.removeIf(line -> line.contains("강화 레벨") || line.contains("추가 능력") || line.contains("⚡"));

        if (level > 0) {
            lore.add("");
            lore.add("§6⚡ 강화 레벨: §f" + level + "강");
            lore.add("§7추가 능력: §a+" + (level * 10) + "%");
            lore.add("§7강화 시스템으로 업그레이드됨");
        }

        meta.setLore(lore);

        // 강화 레벨에 따른 실제 인챈트 적용
        applyEnchantmentsByLevel(meta, level, item.getType());

        item.setItemMeta(meta);
    }

    /**
     * 강화 레벨에 따른 인챈트 적용 - 수정된 버전 (올바른 상수 사용)
     */
    private void applyEnchantmentsByLevel(ItemMeta meta, int level, Material material) {
        // 기존 강화 관련 인챈트 제거
        Set<Enchantment> toRemove = new HashSet<>();
        for (Enchantment ench : meta.getEnchants().keySet()) {
            // 올바른 인챈트 상수 사용
            if (ench == Enchantment.DAMAGE_ALL || ench == Enchantment.PROTECTION_ENVIRONMENTAL ||
                    ench == Enchantment.DIG_SPEED || ench == Enchantment.ARROW_DAMAGE ||
                    ench == Enchantment.FIRE_ASPECT || ench == Enchantment.ARROW_FIRE ||
                    ench == Enchantment.DURABILITY) {
                toRemove.add(ench);
            }
        }
        toRemove.forEach(meta::removeEnchant);

        if (level <= 0) return;

        // 아이템 타입에 따른 인챈트 적용
        String materialName = material.name();

        if (materialName.contains("SWORD")) {
            // 검: 날카로움 (DAMAGE_ALL = SHARPNESS)
            int enchantLevel = Math.min(level, 5); // 최대 5레벨
            meta.addEnchant(Enchantment.DAMAGE_ALL, enchantLevel, true);
        } else if (materialName.contains("PICKAXE") || materialName.contains("AXE") ||
                materialName.contains("SHOVEL") || materialName.contains("HOE")) {
            // 도구: 효율성 (DIG_SPEED = EFFICIENCY)
            int enchantLevel = Math.min(level, 5);
            meta.addEnchant(Enchantment.DIG_SPEED, enchantLevel, true);
        } else if (materialName.contains("BOW")) {
            // 활: 힘 (ARROW_DAMAGE = POWER)
            int enchantLevel = Math.min(level, 5);
            meta.addEnchant(Enchantment.ARROW_DAMAGE, enchantLevel, true);
        } else if (materialName.contains("HELMET") || materialName.contains("CHESTPLATE") ||
                materialName.contains("LEGGINGS") || materialName.contains("BOOTS")) {
            // 방어구: 보호
            int enchantLevel = Math.min(level, 4);
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, enchantLevel, true);
        }

        // 높은 강화 레벨에서 추가 인챈트
        if (level >= 5) {
            if (materialName.contains("SWORD")) {
                meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
            } else if (materialName.contains("BOW")) {
                meta.addEnchant(Enchantment.ARROW_FIRE, 1, true);
            }
        }

        if (level >= 8) {
            // 내구성 (DURABILITY = UNBREAKING)
            meta.addEnchant(Enchantment.DURABILITY, 3, true);
        }
    }

    /**
     * 강화 레벨에 따른 색상
     */
    private String getUpgradeColor(int level) {
        if (level <= 3) return "§a"; // 초록
        else if (level <= 6) return "§b"; // 하늘색
        else if (level <= 8) return "§d"; // 분홍
        else return "§6"; // 금색
    }

    /**
     * 강화 레벨 가져오기
     */
    private int getUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        return meta.getPersistentDataContainer().getOrDefault(upgradeKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * 아이템 표시 이름 가져오기
     */
    private String getItemDisplayName(ItemStack item) {
        String name = item.getType().name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.toString();
    }

    /**
     * 돈 포맷팅
     */
    private String formatMoney(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * 강화 정보 명령어 (추가 기능)
     */
    public void showUpgradeInfo(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c강화할 아이템을 손에 들고 사용하세요!");
            return;
        }

        int currentLevel = getUpgradeLevel(item);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 아이템 강화 정보");
        player.sendMessage("");
        player.sendMessage("§7아이템: §f" + getItemDisplayName(item));
        player.sendMessage("§7현재 강화: §f" + currentLevel + "강");

        if (currentLevel < 10) {
            int nextLevel = currentLevel + 1;
            long cost = upgradeCosts.get(nextLevel);
            int successRate = successRates.get(nextLevel);

            player.sendMessage("§7다음 강화: §f" + nextLevel + "강");
            player.sendMessage("§7필요 비용: §6" + formatMoney(cost) + "G");
            player.sendMessage("§7성공 확률: §a" + successRate + "%");

            if (nextLevel >= 5) {
                player.sendMessage("§7실패 시: §c강화 레벨 1 감소");
            }

            player.sendMessage("");
            player.sendMessage("§a인챈트 테이블에서 강화하세요!");
        } else {
            player.sendMessage("§a이미 최대 강화 레벨입니다!");
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 강화 가격표 표시
     */
    public void showUpgradeRates(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚡ 강화 시스템 정보");
        player.sendMessage("");
        player.sendMessage("§a강화 비용 및 확률:");

        for (int i = 1; i <= 10; i++) {
            long cost = upgradeCosts.get(i);
            int rate = successRates.get(i);
            String color = i <= 3 ? "§a" : i <= 6 ? "§e" : i <= 8 ? "§c" : "§4";

            player.sendMessage(color + i + "강§7: " + formatMoney(cost) + "G (§a" + rate + "%§7)");
        }

        player.sendMessage("");
        player.sendMessage("§c주의사항:");
        player.sendMessage("§7• 5강 이상 실패 시 강화 레벨 감소");
        player.sendMessage("§7• 강화 레벨에 따라 인챈트 자동 적용");
        player.sendMessage("§7• 인챈트 테이블에 아이템 올리고 클릭!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}