// 완성된 EnchantUpgradeManager.java - 새로운 강화 시스템 (검, 도끼, 활, 흉갑만)
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class EnchantUpgradeManager implements Listener {

    private final GGMSurvival plugin;
    private final EconomyManager economyManager;
    private final NamespacedKey upgradeKey;
    private final Random random = new Random();

    // 강화 비용 (레벨별)
    private final Map<Integer, Long> upgradeCosts = new HashMap<>();

    // 강화 성공 확률 (레벨별)
    private final Map<Integer, Integer> successRates = new HashMap<>();

    // 강화 시도 중인 플레이어 (중복 방지)
    private final Set<UUID> upgrading = new HashSet<>();

    // 강화 가능한 아이템 타입 (새 패치)
    private final Set<Material> upgradeableItems = new HashSet<>();

    public EnchantUpgradeManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.upgradeKey = new NamespacedKey(plugin, "upgrade_level");

        // 강화 비용과 확률 초기화
        initializeUpgradeSettings();

        // 강화 가능한 아이템 설정 (새 패치)
        initializeUpgradeableItems();

        plugin.getLogger().info("새로운 강화 시스템 초기화 완료 - 검, 도끼, 활, 흉갑만 강화 가능");
    }

    /**
     * 강화 가능한 아이템 설정 (새 패치)
     */
    private void initializeUpgradeableItems() {
        // 검 (모든 종류)
        upgradeableItems.add(Material.WOODEN_SWORD);
        upgradeableItems.add(Material.STONE_SWORD);
        upgradeableItems.add(Material.IRON_SWORD);
        upgradeableItems.add(Material.GOLDEN_SWORD);
        upgradeableItems.add(Material.DIAMOND_SWORD);
        upgradeableItems.add(Material.NETHERITE_SWORD);

        // 도끼 (모든 종류)
        upgradeableItems.add(Material.WOODEN_AXE);
        upgradeableItems.add(Material.STONE_AXE);
        upgradeableItems.add(Material.IRON_AXE);
        upgradeableItems.add(Material.GOLDEN_AXE);
        upgradeableItems.add(Material.DIAMOND_AXE);
        upgradeableItems.add(Material.NETHERITE_AXE);

        // 활
        upgradeableItems.add(Material.BOW);
        upgradeableItems.add(Material.CROSSBOW);

        // 흉갑 (모든 종류)
        upgradeableItems.add(Material.LEATHER_CHESTPLATE);
        upgradeableItems.add(Material.CHAINMAIL_CHESTPLATE);
        upgradeableItems.add(Material.IRON_CHESTPLATE);
        upgradeableItems.add(Material.GOLDEN_CHESTPLATE);
        upgradeableItems.add(Material.DIAMOND_CHESTPLATE);
        upgradeableItems.add(Material.NETHERITE_CHESTPLATE);

        plugin.getLogger().info("강화 가능 아이템: " + upgradeableItems.size() + "개 타입");
    }

    /**
     * 강화 설정 초기화
     */
    private void initializeUpgradeSettings() {
        // 강화 비용 (G)
        upgradeCosts.put(1, 1000L);
        upgradeCosts.put(2, 2000L);
        upgradeCosts.put(3, 4000L);
        upgradeCosts.put(4, 8000L);
        upgradeCosts.put(5, 15000L);
        upgradeCosts.put(6, 25000L);
        upgradeCosts.put(7, 40000L);
        upgradeCosts.put(8, 70000L);
        upgradeCosts.put(9, 120000L);
        upgradeCosts.put(10, 200000L);

        // 성공 확률 (%)
        successRates.put(1, 95);
        successRates.put(2, 90);
        successRates.put(3, 85);
        successRates.put(4, 80);
        successRates.put(5, 70);
        successRates.put(6, 60);
        successRates.put(7, 50);
        successRates.put(8, 40);
        successRates.put(9, 30);
        successRates.put(10, 20);
    }

    /**
     * 아이템이 강화 가능한지 확인 (새 패치)
     */
    public boolean isUpgradeable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return upgradeableItems.contains(item.getType());
    }

    /**
     * 아이템 타입 분류 (새 패치)
     */
    public enum UpgradeItemType {
        SWORD, AXE, BOW, CHESTPLATE
    }

    /**
     * 아이템 타입 가져오기
     */
    public UpgradeItemType getItemType(Material material) {
        String name = material.name();

        if (name.contains("SWORD")) return UpgradeItemType.SWORD;
        if (name.contains("AXE")) return UpgradeItemType.AXE;
        if (name.equals("BOW") || name.equals("CROSSBOW")) return UpgradeItemType.BOW;
        if (name.contains("CHESTPLATE")) return UpgradeItemType.CHESTPLATE;

        return null;
    }

    /**
     * 인챈트 테이블 준비 이벤트 (새 패치 - 강화 가능 아이템만)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemEnchant(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (!isUpgradeable(item)) {
            // 강화 불가능한 아이템
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            player.sendMessage("§7강화 가능: §e검, 도끼, 활, 흉갑");
            event.setCancelled(true);
            return;
        }

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

        // 강화 옵션 설정
        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            offers[i] = new EnchantmentOffer(
                    Enchantment.DURABILITY,
                    1,
                    Math.min(30, nextLevel * 3)
            );
        }

        // 강화 정보 표시
        sendUpgradeInfo(player, item, currentLevel, nextLevel, cost, successRate);
    }

    /**
     * 인챈트 이벤트 (새 패치 - 실제 강화 처리)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (!isUpgradeable(item)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true); // 실제 인챈트 취소

        // 강화 처리
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500); // 잠시 대기
                Bukkit.getScheduler().runTask(plugin, () -> {
                    processUpgrade(player, item);
                    player.closeInventory();
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 강화 처리 (새 패치)
     */
    public void processUpgrade(Player player, ItemStack item) {
        if (upgrading.contains(player.getUniqueId())) {
            player.sendMessage("§c이미 강화가 진행 중입니다!");
            return;
        }

        if (!isUpgradeable(item)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            return;
        }

        int currentLevel = getUpgradeLevel(item);
        if (currentLevel >= 10) {
            player.sendMessage("§c이미 최대 강화 레벨입니다!");
            return;
        }

        int nextLevel = currentLevel + 1;
        long cost = upgradeCosts.get(nextLevel);
        int successRate = successRates.get(nextLevel);

        upgrading.add(player.getUniqueId());

        // 돈 확인 및 차감 (수정된 부분)
        economyManager.getBalance(player.getUniqueId()).thenAccept(balance -> {
            if (balance < cost) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    upgrading.remove(player.getUniqueId());
                    player.sendMessage("§c강화에 필요한 G가 부족합니다! (필요: " + formatMoney(cost) + "G)");
                });
                return;
            }

            // G 차감
            economyManager.removeMoney(player.getUniqueId(), cost).thenAccept(success -> {
                if (!success) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        upgrading.remove(player.getUniqueId());
                        player.sendMessage("§cG 차감 중 오류가 발생했습니다!");
                    });
                    return;
                }

                // 강화 시도 처리
                Bukkit.getScheduler().runTask(plugin, () -> {
                    processUpgradeAttempt(player, item, currentLevel, nextLevel, successRate, cost);
                });
            });
        });
    }

    /**
     * 강화 시도 처리
     */
    private void processUpgradeAttempt(Player player, ItemStack item, int currentLevel, int nextLevel, int successRate, long cost) {
        boolean success = random.nextInt(100) < successRate;

        upgrading.remove(player.getUniqueId());

        if (success) {
            // 강화 성공
            applyUpgrade(item, nextLevel);
            showUpgradeSuccess(player, item, nextLevel, successRate);
        } else {
            // 강화 실패
            if (currentLevel >= 5) {
                int newLevel = Math.max(0, currentLevel - 1);
                applyUpgrade(item, newLevel);
            }
            showUpgradeFailure(player, item, nextLevel, successRate, currentLevel >= 5);
        }
    }

    /**
     * 아이템에 강화 적용 (새 패치)
     */
    private void applyUpgrade(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // NBT에 강화 레벨 저장
        meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.INTEGER, level);

        // 아이템 이름 업데이트
        updateItemDisplayName(meta, item, level);

        // 로어 업데이트 (새 패치 효과 설명)
        updateItemLore(meta, item, level);

        // 10강 특수 효과 적용
        if (level == 10) {
            applyMaxLevelEffects(meta, item);
        }

        item.setItemMeta(meta);
    }

    /**
     * 아이템 이름 업데이트
     */
    private void updateItemDisplayName(ItemMeta meta, ItemStack item, int level) {
        String originalName = meta.hasDisplayName() ? meta.getDisplayName() : "§f" + getItemDisplayName(item);
        originalName = originalName.replaceAll("§[0-9a-f]\\[\\+\\d+\\]\\s*", "");

        if (level > 0) {
            String upgradeColor = getUpgradeColor(level);
            meta.setDisplayName(upgradeColor + "[+" + level + "] " + originalName);
        } else {
            meta.setDisplayName(originalName);
        }
    }

    /**
     * 아이템 로어 업데이트 (새 패치 효과)
     */
    private void updateItemLore(ItemMeta meta, ItemStack item, int level) {
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // 기존 강화 로어 제거
        lore.removeIf(line -> line.contains("강화 레벨") || line.contains("추가 효과") || line.contains("⚡"));

        if (level > 0) {
            lore.add("");
            lore.add("§6⚡ 강화 레벨: §f" + level + "강");

            UpgradeItemType type = getItemType(item.getType());
            if (type != null) {
                switch (type) {
                    case SWORD:
                        lore.add("§7검 위력: §a+" + (level * 3) + "%");
                        if (level == 10) lore.add("§c▸ 발화 효과");
                        break;
                    case AXE:
                        lore.add("§7공격 속도: §a+" + (level * 2) + "%");
                        if (level == 10) lore.add("§c▸ 출혈 효과");
                        break;
                    case BOW:
                        lore.add("§7활 위력: §a+" + (level * 3) + "%");
                        if (level == 10) lore.add("§c▸ 화염 효과");
                        break;
                    case CHESTPLATE:
                        lore.add("§7방어력: §a+" + (level * 3) + "%");
                        if (level == 10) lore.add("§c▸ 가시 효과");
                        break;
                }
            }

            lore.add("§7새로운 강화 시스템으로 업그레이드됨");
        }

        meta.setLore(lore);
    }

    /**
     * 10강 특수 효과 적용
     */
    private void applyMaxLevelEffects(ItemMeta meta, ItemStack item) {
        UpgradeItemType type = getItemType(item.getType());
        if (type == null) return;

        switch (type) {
            case SWORD:
                meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
                break;
            case AXE:
                // 출혈 효과는 공격 시 이벤트로 처리
                break;
            case BOW:
                meta.addEnchant(Enchantment.ARROW_FIRE, 1, true);
                break;
            case CHESTPLATE:
                meta.addEnchant(Enchantment.THORNS, 3, true);
                break;
        }
    }

    /**
     * 공격 이벤트 처리 (새 패치 - 강화 효과 적용)
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player player = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (!isUpgradeable(weapon)) return;

        int level = getUpgradeLevel(weapon);
        if (level <= 0) return;

        UpgradeItemType type = getItemType(weapon.getType());
        if (type == null) return;

        double damage = event.getDamage();

        switch (type) {
            case SWORD:
                // 검 위력 3% 증가
                double swordBonus = damage * (level * 0.03);
                event.setDamage(damage + swordBonus);

                // 10강 발화 효과 (이미 인챈트로 적용됨)
                break;

            case AXE:
                // 도끼는 공격속도 증가 (별도 매니저에서 처리)
                if (level == 10) {
                    // 10강 출혈 효과
                    applyBleedingEffect(target);
                }
                break;

            case BOW:
                // 활 위력 3% 증가 (투사체 공격은 별도 처리 필요)
                double bowBonus = damage * (level * 0.03);
                event.setDamage(damage + bowBonus);
                break;
        }
    }

    /**
     * 출혈 효과 적용 (발화와 동일한 데미지)
     */
    private void applyBleedingEffect(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0)); // 5초간 독 효과

        // 출혈 파티클 효과
        target.getWorld().spawnParticle(Particle.BLOCK_CRACK,
                target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5,
                Material.REDSTONE_BLOCK.createBlockData());
    }

    /**
     * 강화 성공 메시지
     */
    private void showUpgradeSuccess(Player player, ItemStack item, int level, int rate) {
        player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§a✨ " + level + "강 강화 성공! ✨");
        player.sendMessage("§7강화에 성공했습니다! (" + rate + "% 확률)");

        if (level == 10) {
            player.sendMessage("§6🎉 최고 강화 달성! 특수 효과가 추가되었습니다!");
        }

        player.sendMessage("§7아이템이 더욱 강력해졌습니다!");
        player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 성공 효과
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 1, 0), 20, 1, 1, 1);
    }

    /**
     * 강화 실패 메시지
     */
    private void showUpgradeFailure(Player player, ItemStack item, int targetLevel, int rate, boolean downgraded) {
        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§c💥 " + targetLevel + "강 강화 실패!");
        player.sendMessage("§7강화에 실패했습니다... (" + rate + "% 확률)");

        if (downgraded) {
            player.sendMessage("§c강화 레벨이 1 감소했습니다!");
        } else {
            player.sendMessage("§7강화 레벨은 하락하지 않습니다.");
        }

        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 실패 효과
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.SMOKE_LARGE,
                player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5);
    }

    /**
     * 강화 정보 표시
     */
    private void sendUpgradeInfo(Player player, ItemStack item, int current, int next, long cost, int rate) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l⚡ 새로운 강화 시스템");
                player.sendMessage("§7아이템: §f" + getItemDisplayName(item));
                player.sendMessage("§7현재 강화: §f" + current + "강");
                player.sendMessage("§7다음 강화: §f" + next + "강");
                player.sendMessage("§7필요 비용: §6" + formatMoney(cost) + "G");
                player.sendMessage("§7성공 확률: §a" + rate + "%");

                UpgradeItemType type = getItemType(item.getType());
                if (type != null) {
                    switch (type) {
                        case SWORD:
                            player.sendMessage("§7효과: §a검 위력 +" + (next * 3) + "%");
                            break;
                        case AXE:
                            player.sendMessage("§7효과: §a공격 속도 +" + (next * 2) + "%");
                            break;
                        case BOW:
                            player.sendMessage("§7효과: §a활 위력 +" + (next * 3) + "%");
                            break;
                        case CHESTPLATE:
                            player.sendMessage("§7효과: §a방어력 +" + (next * 3) + "%");
                            break;
                    }
                }

                if (next == 10) {
                    player.sendMessage("§6▸ 10강 달성시 특수 효과 추가!");
                }
                if (next >= 5) {
                    player.sendMessage("§7실패 시: §c강화 레벨 1 감소");
                }
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§a인챈트 옵션을 클릭하여 강화를 시도하세요!");
            }
        }, 1L);
    }

    // 유틸리티 메서드들...

    public int getUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().getOrDefault(upgradeKey, PersistentDataType.INTEGER, 0);
    }

    private String getUpgradeColor(int level) {
        if (level <= 3) return "§a";
        if (level <= 6) return "§e";
        if (level <= 8) return "§c";
        return "§4";
    }

    private String formatMoney(long amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }

    public String getItemDisplayName(ItemStack item) {
        if (item == null) return "없음";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // 한글 아이템명 매핑
        String materialName = item.getType().name();
        switch (materialName) {
            case "DIAMOND_SWORD": return "다이아몬드 검";
            case "IRON_SWORD": return "철 검";
            case "DIAMOND_AXE": return "다이아몬드 도끼";
            case "IRON_AXE": return "철 도끼";
            case "BOW": return "활";
            case "DIAMOND_CHESTPLATE": return "다이아몬드 흉갑";
            case "IRON_CHESTPLATE": return "철 흉갑";
            default: return materialName.toLowerCase().replace("_", " ");
        }
    }

    // Getter 메서드들...
    public long getUpgradeCost(int level) {
        return upgradeCosts.getOrDefault(level, 0L);
    }

    public int getSuccessRate(int level) {
        return successRates.getOrDefault(level, 0);
    }
}