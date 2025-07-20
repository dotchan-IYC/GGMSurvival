// 완전한 EnchantUpgradeManager.java - 인첸트된 아이템도 강화 가능
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
     * 인챈트 테이블 준비 이벤트 - 인첸트가 있어도 강화 옵션 표시
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

        // 인첸트가 있는 아이템이라도 강제로 인첸트 옵션 표시
        EnchantmentOffer[] offers = event.getOffers();

        // 첫 번째 슬롯에 강화 옵션 설정
        if (offers.length > 0) {
            offers[0] = new EnchantmentOffer(
                    Enchantment.DURABILITY,  // 내구성 인첸트 (가짜)
                    1,                       // 레벨 1
                    Math.min(30, nextLevel * 3)  // 경험치 비용 (실제로는 G 사용)
            );
        }

        // 두 번째 슬롯에도 옵션 설정 (선택사항)
        if (offers.length > 1) {
            offers[1] = new EnchantmentOffer(
                    Enchantment.DURABILITY,  // 내구성 인첸트 (가짜)
                    1,                       // 레벨 1
                    Math.min(30, nextLevel * 3)  // 경험치 비용
            );
        }

        // 세 번째 슬롯에도 옵션 설정 (선택사항)
        if (offers.length > 2) {
            offers[2] = new EnchantmentOffer(
                    Enchantment.DURABILITY,  // 내구성 인첸트 (가짜)
                    1,                       // 레벨 1
                    Math.min(30, nextLevel * 3)  // 경험치 비용
            );
        }

        // 강화 정보 표시 (ActionBar 또는 채팅)
        sendUpgradeActionBar(player, currentLevel, nextLevel, cost, successRate);

        // 채팅에도 강화 정보 표시
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
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
                player.sendMessage("§a인챈트 옵션을 클릭하여 강화를 시도하세요!");
                player.sendMessage("§e(표시된 인첸트는 무시되며, 강화만 적용됩니다)");
            }
        }, 5L); // 0.25초 후 표시
    }

    /**
     * ActionBar로 강화 정보 전송
     */
    private void sendUpgradeActionBar(Player player, int currentLevel, int nextLevel, long cost, int successRate) {
        String message = String.format("§e⚡ %d강→%d강 §7| §6%s G §7| §a%d%% 확률",
                currentLevel, nextLevel, formatMoney(cost), successRate);

        // ActionBar 전송 (1.20+ 방식)
        try {
            player.sendActionBar(message);
        } catch (Exception e) {
            // 구버전 대응
            plugin.getLogger().warning("ActionBar 전송 실패, 채팅으로 전환");
        }
    }

    /**
     * 인챈트 이벤트 (실제 강화 처리) - 수정된 버전
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) return;

        // 모든 기본 인챈트 취소 (우리가 커스텀 강화로 처리)
        event.setCancelled(true);

        // 인첸트 테이블 닫기 (경험치/라피스 소모 방지)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.closeInventory();
            }
        }, 1L);

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

        // 강화 시작 메시지
        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e⚡ " + nextLevel + "강 강화를 시도합니다...");
        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // G 잔액 확인 및 강화 처리
        economyManager.getBalance(player.getUniqueId()).thenAccept(balance -> {
            if (balance < cost) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§c💸 강화 비용 부족!");
                    player.sendMessage("§7필요: §6" + formatMoney(cost) + "G");
                    player.sendMessage("§7보유: §6" + formatMoney(balance) + "G");
                    player.sendMessage("§7부족: §c" + formatMoney(cost - balance) + "G");
                    player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    upgrading.remove(uuid);
                });
                return;
            }

            // G 차감 후 강화 진행 - 수정된 메서드 시그니처 사용 (UUID, long)
            economyManager.removeMoney(player.getUniqueId(), cost).thenAccept(success -> {
                if (!success) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c강화 비용 차감 중 오류가 발생했습니다!");
                        upgrading.remove(uuid);
                    });
                    return;
                }

                // 메인 스레드에서 강화 처리
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        processUpgrade(player, item, currentLevel, nextLevel);
                    } finally {
                        upgrading.remove(uuid);
                    }
                });
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§c강화 처리 중 오류 발생: " + throwable.getMessage());
                upgrading.remove(uuid);
            });
            return null;
        });
    }

    /**
     * 실제 강화 처리 로직
     */
    private void processUpgrade(Player player, ItemStack item, int currentLevel, int nextLevel) {
        int successRate = successRates.get(nextLevel);
        boolean success = random.nextInt(100) < successRate;

        if (success) {
            // 강화 성공
            applyUpgrade(item, nextLevel);

            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§a✨ " + nextLevel + "강 강화 성공! ✨");
            player.sendMessage("§7아이템이 더욱 강력해졌습니다!");
            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 성공 효과
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 20, 1, 1, 1);

            // 높은 강화 시 추가 효과
            if (nextLevel >= 5) {
                player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1, 0), 30, 1, 1, 1);
            }
            if (nextLevel >= 8) {
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 15, 0.5, 1, 0.5);
            }

            plugin.getLogger().info(String.format("[강화성공] %s: %s %d강 성공 (%d%%)",
                    player.getName(), item.getType(), nextLevel, successRate));
        } else {
            // 강화 실패
            player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§c💥 " + nextLevel + "강 강화 실패!");
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
     * 강화 레벨에 따른 인챈트 적용
     */
    private void applyEnchantmentsByLevel(ItemMeta meta, int level, Material material) {
        if (level <= 0) return;

        // 아이템 타입에 따른 인챈트 적용
        String materialName = material.name();

        if (materialName.contains("SWORD")) {
            // 검: 날카로움
            int sharpnessLevel = Math.min(5, (level + 1) / 2);
            meta.addEnchant(Enchantment.DAMAGE_ALL, sharpnessLevel, true);

            if (level >= 3) {
                meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
            }
            if (level >= 6) {
                meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            }
            if (level >= 8) {
                meta.addEnchant(Enchantment.SWEEPING_EDGE, 2, true);
            }

        } else if (materialName.contains("PICKAXE") || materialName.contains("AXE") ||
                materialName.contains("SHOVEL") || materialName.contains("HOE")) {
            // 도구: 효율성
            int efficiencyLevel = Math.min(5, (level + 1) / 2);
            meta.addEnchant(Enchantment.DIG_SPEED, efficiencyLevel, true);

            if (level >= 4) {
                meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
            }

        } else if (materialName.contains("BOW")) {
            // 활: 힘
            int powerLevel = Math.min(5, (level + 1) / 2);
            meta.addEnchant(Enchantment.ARROW_DAMAGE, powerLevel, true);

            if (level >= 3) {
                meta.addEnchant(Enchantment.ARROW_FIRE, 1, true);
            }
            if (level >= 6) {
                meta.addEnchant(Enchantment.ARROW_KNOCKBACK, 1, true);
            }
            if (level >= 8) {
                meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
            }

        } else if (materialName.contains("HELMET") || materialName.contains("CHESTPLATE") ||
                materialName.contains("LEGGINGS") || materialName.contains("BOOTS")) {
            // 방어구: 보호
            int protectionLevel = Math.min(4, (level + 2) / 3);
            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, protectionLevel, true);

            if (level >= 5) {
                meta.addEnchant(Enchantment.THORNS, 1, true);
            }
            if (level >= 7) {
                meta.addEnchant(Enchantment.MENDING, 1, true);
            }
        }

        // 모든 아이템에 내구성 부여
        if (level >= 2) {
            int unbreakingLevel = Math.min(3, level / 3);
            meta.addEnchant(Enchantment.DURABILITY, unbreakingLevel, true);
        }
    }

    /**
     * 강화 레벨에 따른 색상 반환
     */
    private String getUpgradeColor(int level) {
        if (level <= 3) return "§a";       // 초록 (1-3강)
        else if (level <= 6) return "§e";  // 노랑 (4-6강)
        else if (level <= 8) return "§c";  // 빨강 (7-8강)
        else return "§d";                  // 보라 (9-10강)
    }

    /**
     * 직접 강화 메서드 (GUI나 명령어에서 호출) - 수정된 버전
     */
    public void directUpgradeItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c강화할 아이템이 없습니다!");
            return;
        }

        // 중복 강화 방지
        UUID uuid = player.getUniqueId();
        if (upgrading.contains(uuid)) {
            player.sendMessage("§c이미 강화를 진행 중입니다!");
            return;
        }

        upgrading.add(uuid);

        try {
            // 현재 강화 레벨 확인
            int currentLevel = getUpgradeLevel(item);

            if (currentLevel >= 10) {
                player.sendMessage("§c이미 최대 강화 레벨(10강)에 도달했습니다!");
                return;
            }

            int nextLevel = currentLevel + 1;
            long cost = upgradeCosts.get(nextLevel);

            // 강화 확인 메시지
            player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e⚡ " + nextLevel + "강 강화를 시도합니다...");
            player.sendMessage("§7아이템: §f" + getItemDisplayName(item));
            player.sendMessage("§7비용: §6" + formatMoney(cost) + "G");
            player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // G 잔액 확인 및 강화 처리
            economyManager.getBalance(player.getUniqueId()).thenAccept(balance -> {
                if (balance < cost) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§c💸 강화 비용 부족!");
                        player.sendMessage("§7필요: §6" + formatMoney(cost) + "G");
                        player.sendMessage("§7보유: §6" + formatMoney(balance) + "G");
                        player.sendMessage("§7부족: §c" + formatMoney(cost - balance) + "G");
                        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    });
                    return;
                }

                // G 차감 후 강화 진행 - 수정된 메서드 시그니처 사용 (UUID, long)
                economyManager.removeMoney(player.getUniqueId(), cost).thenAccept(success -> {
                    if (!success) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§c강화 비용 차감 중 오류가 발생했습니다!");
                        });
                        return;
                    }

                    // 메인 스레드에서 강화 처리
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        processUpgrade(player, item, currentLevel, nextLevel);
                    });
                });
            }).exceptionally(throwable -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c강화 처리 중 오류 발생: " + throwable.getMessage());
                });
                return null;
            });

        } finally {
            // 잠시 후 잠금 해제
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                upgrading.remove(uuid);
            }, 40L); // 2초 후
        }
    }

    /**
     * 아이템 강화 정보 표시
     */
    public void showUpgradeInfo(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c손에 아이템을 들고 사용하세요!");
            return;
        }

        if (!canUpgrade(item)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다!");
            return;
        }

        int currentLevel = getUpgradeLevel(item);
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l📋 아이템 강화 정보");
        player.sendMessage("");
        player.sendMessage("§7아이템: §f" + getItemDisplayName(item));
        player.sendMessage("§7현재 강화: §f" + currentLevel + "강");

        if (currentLevel < 10) {
            int nextLevel = currentLevel + 1;
            long cost = upgradeCosts.get(nextLevel);
            int rate = successRates.get(nextLevel);

            player.sendMessage("§7다음 강화: §f" + nextLevel + "강");
            player.sendMessage("§7필요 비용: §6" + formatMoney(cost) + "G");
            player.sendMessage("§7성공 확률: §a" + rate + "%");

            if (nextLevel >= 5) {
                player.sendMessage("§7실패 시: §c강화 레벨 1 감소");
            } else {
                player.sendMessage("§7실패 시: §7레벨 유지");
            }

            player.sendMessage("");
            player.sendMessage("§a강화 방법:");
            player.sendMessage("§71. §e인첸트 테이블 §7- 아이템 + 라피스");
            player.sendMessage("§72. §e/upgrade gui §7- 강화 GUI");
            player.sendMessage("§73. §e/upgrade direct §7- 즉시 강화");
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
        player.sendMessage("§7• 인첸트된 아이템도 강화 가능!");
        player.sendMessage("");
        player.sendMessage("§a§l강화 방법 (3가지):");
        player.sendMessage("§71. 인첸트 테이블 2. /upgrade gui 3. /upgrade direct");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // Public 메서드들 (외부에서 호출 가능)

    /**
     * 강화 비용 조회
     */
    public long getUpgradeCost(int level) {
        return upgradeCosts.getOrDefault(level, 0L);
    }

    /**
     * 성공 확률 조회
     */
    public int getSuccessRate(int level) {
        return successRates.getOrDefault(level, 0);
    }

    /**
     * 강화 레벨 조회
     */
    public int getUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().getOrDefault(upgradeKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * 아이템 표시명 조회
     */
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
            case "GOLDEN_SWORD": return "금 검";
            case "STONE_SWORD": return "돌 검";
            case "WOODEN_SWORD": return "나무 검";
            case "NETHERITE_SWORD": return "네더라이트 검";

            case "DIAMOND_PICKAXE": return "다이아몬드 곡괭이";
            case "IRON_PICKAXE": return "철 곡괭이";
            case "GOLDEN_PICKAXE": return "금 곡괭이";
            case "STONE_PICKAXE": return "돌 곡괭이";
            case "WOODEN_PICKAXE": return "나무 곡괭이";
            case "NETHERITE_PICKAXE": return "네더라이트 곡괭이";

            case "BOW": return "활";
            case "CROSSBOW": return "쇠뇌";
            case "TRIDENT": return "삼지창";

            case "DIAMOND_HELMET": return "다이아몬드 투구";
            case "DIAMOND_CHESTPLATE": return "다이아몬드 흉갑";
            case "DIAMOND_LEGGINGS": return "다이아몬드 각반";
            case "DIAMOND_BOOTS": return "다이아몬드 부츠";

            default: return materialName.toLowerCase().replace("_", " ");
        }
    }

    /**
     * 금액 포맷팅
     */
    public String formatMoney(long amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }

    /**
     * 강화 가능 여부 확인
     */
    public boolean canUpgrade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        // 업그레이드 가능한 아이템 타입 확인
        Material type = item.getType();
        String typeName = type.name();

        return typeName.contains("SWORD") ||
                typeName.contains("PICKAXE") ||
                typeName.contains("AXE") ||
                typeName.contains("SHOVEL") ||
                typeName.contains("HOE") ||
                typeName.contains("HELMET") ||
                typeName.contains("CHESTPLATE") ||
                typeName.contains("LEGGINGS") ||
                typeName.contains("BOOTS") ||
                typeName.contains("BOW") ||
                typeName.contains("CROSSBOW") ||
                type == Material.TRIDENT ||
                type == Material.SHIELD;
    }
}