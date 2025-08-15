// 완전 안정화된 EnchantUpgradeManager.java
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * 완전 안정화된 강화 시스템 매니저
 * - 검, 도끼, 활, 흉갑만 강화 가능
 * - Thread-Safe 구현
 * - 메모리 누수 방지
 * - 강력한 예외 처리
 */
public class EnchantUpgradeManager implements Listener {

    private final GGMSurvival plugin;

    // Thread-Safe 컬렉션들
    private final Set<Material> upgradableMaterials;
    private final Map<Integer, Double> successRates;
    private final Map<Integer, Double> destroyRates;
    private final ConcurrentHashMap<UUID, Long> lastUpgradeTime = new ConcurrentHashMap<>();

    // 10강 특수 효과 쿨다운 관리
    private final ConcurrentHashMap<UUID, Long> fireEffectCooldown = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> bleedEffectCooldown = new ConcurrentHashMap<>();

    // 성능 최적화를 위한 캐시
    private final Map<String, Boolean> materialCache = new ConcurrentHashMap<>();

    // 상수 정의
    private static final int MAX_UPGRADE_LEVEL = 10;
    private static final String UPGRADE_LORE_PREFIX = "§6강화: §e+";
    private static final long UPGRADE_COOLDOWN = 1000L; // 1초 쿨다운
    private static final long EFFECT_COOLDOWN = 5000L; // 5초 쿨다운

    public EnchantUpgradeManager(GGMSurvival plugin) {
        this.plugin = plugin;

        try {
            // 강화 가능한 아이템 초기화
            this.upgradableMaterials = initializeUpgradableMaterials();

            // 성공률 및 파괴율 초기화
            this.successRates = initializeSuccessRates();
            this.destroyRates = initializeDestroyRates();

            plugin.getLogger().info("EnchantUpgradeManager 안정화 초기화 완료");
            plugin.getLogger().info("강화 가능한 아이템: " + upgradableMaterials.size() + "개");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "EnchantUpgradeManager 초기화 실패", e);
            throw new RuntimeException("EnchantUpgradeManager 초기화 실패", e);
        }
    }

    /**
     * 강화 가능한 아이템 타입 초기화
     */
    private Set<Material> initializeUpgradableMaterials() {
        Set<Material> materials = EnumSet.noneOf(Material.class);

        try {
            // 검류
            for (Material material : Material.values()) {
                String name = material.name();
                if (name.contains("SWORD") && !name.equals("WOODEN_SWORD")) {
                    materials.add(material);
                }
            }

            // 도끼류
            for (Material material : Material.values()) {
                String name = material.name();
                if (name.contains("_AXE") && !name.equals("WOODEN_AXE")) {
                    materials.add(material);
                }
            }

            // 활류
            materials.add(Material.BOW);
            materials.add(Material.CROSSBOW);

            // 흉갑류
            for (Material material : Material.values()) {
                String name = material.name();
                if (name.contains("CHESTPLATE") && !name.equals("LEATHER_CHESTPLATE")) {
                    materials.add(material);
                }
            }

            plugin.getLogger().info("강화 가능한 아이템 타입 " + materials.size() + "개 로드됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "강화 가능한 아이템 초기화 중 오류", e);
        }

        return Collections.unmodifiableSet(materials);
    }

    /**
     * 강화 성공률 초기화
     */
    private Map<Integer, Double> initializeSuccessRates() {
        Map<Integer, Double> rates = new HashMap<>();

        try {
            // config.yml에서 성공률 로드, 없으면 기본값 사용
            for (int level = 1; level <= MAX_UPGRADE_LEVEL; level++) {
                String configPath = "upgrade_system.success_rates." + level;
                double rate = plugin.getConfig().getDouble(configPath, getDefaultSuccessRate(level));
                rates.put(level, Math.max(0.0, Math.min(1.0, rate))); // 0~1 사이로 제한
            }

            plugin.getLogger().info("강화 성공률 로드 완료: " + rates.size() + "개 레벨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "강화 성공률 초기화 중 오류, 기본값 사용", e);

            // 기본값으로 폴백
            for (int level = 1; level <= MAX_UPGRADE_LEVEL; level++) {
                rates.put(level, getDefaultSuccessRate(level));
            }
        }

        return Collections.unmodifiableMap(rates);
    }

    /**
     * 기본 성공률 반환
     */
    private double getDefaultSuccessRate(int level) {
        switch (level) {
            case 1: case 2: case 3: return 0.95; // 95%
            case 4: case 5: return 0.80;         // 80%
            case 6: case 7: return 0.60;         // 60%
            case 8: case 9: return 0.40;         // 40%
            case 10: return 0.20;                // 20%
            default: return 0.50;                // 50%
        }
    }

    /**
     * 파괴율 초기화
     */
    private Map<Integer, Double> initializeDestroyRates() {
        Map<Integer, Double> rates = new HashMap<>();

        try {
            int startLevel = plugin.getConfig().getInt("upgrade_system.downgrade_start_level", 5);

            for (int level = 1; level <= MAX_UPGRADE_LEVEL; level++) {
                if (level < startLevel) {
                    rates.put(level, 0.0); // 파괴되지 않음
                } else {
                    String configPath = "upgrade_system.destroy_rates." + level;
                    double rate = plugin.getConfig().getDouble(configPath, getDefaultDestroyRate(level));
                    rates.put(level, Math.max(0.0, Math.min(0.5, rate))); // 0~50% 사이로 제한
                }
            }

            plugin.getLogger().info("파괴율 로드 완료: " + startLevel + "강부터 적용");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "파괴율 초기화 중 오류, 기본값 사용", e);

            // 기본값으로 폴백
            for (int level = 1; level <= MAX_UPGRADE_LEVEL; level++) {
                rates.put(level, getDefaultDestroyRate(level));
            }
        }

        return Collections.unmodifiableMap(rates);
    }

    /**
     * 기본 파괴율 반환
     */
    private double getDefaultDestroyRate(int level) {
        if (level < 5) return 0.0;      // 4강까지는 파괴되지 않음
        if (level <= 7) return 0.10;    // 5~7강: 10%
        if (level <= 9) return 0.20;    // 8~9강: 20%
        return 0.30;                    // 10강: 30%
    }

    /**
     * 인첸트 테이블 강화 이벤트
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchantItem(EnchantItemEvent event) {
        if (plugin.isShuttingDown()) return;

        Player player = event.getEnchanter();
        ItemStack item = event.getItem();

        try {
            // 강화 가능한 아이템인지 확인
            if (!isUpgradable(item)) {
                return; // 강화 불가능한 아이템은 정상 인첸트 진행
            }

            // 쿨다운 확인
            if (isOnCooldown(player)) {
                event.setCancelled(true);
                player.sendMessage("§c잠시 후 다시 시도해주세요. (쿨다운: 1초)");
                return;
            }

            // 인첸트 테이블을 통한 강화 허용 여부 확인
            boolean allowEnchantTable = plugin.getConfig().getBoolean("upgrade_system.upgrade_methods.enchant_table", true);
            if (!allowEnchantTable) {
                return; // 인첸트 테이블 강화 비허용
            }

            // 기존 인첸트가 있어도 강화 옵션 표시 여부
            boolean forceOptions = plugin.getConfig().getBoolean("upgrade_system.upgrade_methods.force_enchant_options", true);
            if (!forceOptions && hasEnchantments(item)) {
                return; // 이미 인첸트된 아이템은 강화 안함
            }

            // 인첸트 이벤트 취소하고 강화 진행
            event.setCancelled(true);

            // 경험치 소모 (레벨에 따라)
            int currentLevel = getCurrentUpgradeLevel(item);
            int expCost = calculateExpCost(currentLevel + 1);

            if (player.getLevel() < expCost) {
                player.sendMessage("§c강화에 필요한 경험치가 부족합니다! (필요: " + expCost + "레벨)");
                return;
            }

            // 경험치 차감
            player.setLevel(player.getLevel() - expCost);

            // 강화 실행
            performUpgrade(player, item);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "인첸트 강화 처리 중 오류: " + player.getName(), e);

            // 오류 발생 시 이벤트 복원
            event.setCancelled(false);
        }
    }

    /**
     * 10강 특수 효과 - 공격 시 발동
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (plugin.isShuttingDown()) return;

        try {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (weapon == null || weapon.getType() == Material.AIR) return;

            int upgradeLevel = getCurrentUpgradeLevel(weapon);
            if (upgradeLevel < 10) return; // 10강이 아니면 효과 없음

            // 무기 타입별 특수 효과
            applyCombatEffect(player, target, weapon);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "10강 특수 효과 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 강화 실행
     */
    public boolean performUpgrade(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c강화할 아이템을 들고 있지 않습니다!");
            return false;
        }

        if (!isUpgradable(item)) {
            String message = plugin.getConfig().getString("messages.upgrade_not_available",
                    "이 아이템은 강화할 수 없습니다! (강화 가능: 검, 도끼, 활, 흉갑)");
            player.sendMessage("§c" + message);
            return false;
        }

        try {
            int currentLevel = getCurrentUpgradeLevel(item);

            if (currentLevel >= MAX_UPGRADE_LEVEL) {
                String message = plugin.getConfig().getString("messages.upgrade_max_level",
                        "이미 최대 강화 레벨(10강)에 도달했습니다!");
                player.sendMessage("§c" + message);
                return false;
            }

            // 쿨다운 설정
            lastUpgradeTime.put(player.getUniqueId(), System.currentTimeMillis());

            int targetLevel = currentLevel + 1;
            double successRate = successRates.getOrDefault(targetLevel, 0.5);
            double destroyRate = destroyRates.getOrDefault(targetLevel, 0.0);

            double random = ThreadLocalRandom.current().nextDouble();

            if (random <= successRate) {
                // 성공
                applyUpgradeSuccess(player, item, targetLevel);
                return true;

            } else if (random <= successRate + destroyRate) {
                // 파괴/하락
                applyUpgradeFailure(player, item, currentLevel);
                return false;

            } else {
                // 실패 (변화 없음)
                applyUpgradeStay(player, item, currentLevel);
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "강화 실행 중 치명적 오류: " + player.getName(), e);

            player.sendMessage("§c강화 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            return false;
        }
    }

    /**
     * 강화 성공 처리
     */
    private void applyUpgradeSuccess(Player player, ItemStack item, int newLevel) {
        try {
            // 강화 레벨 적용
            setUpgradeLevel(item, newLevel);

            // 스탯 보너스 적용
            applyStatBonus(item, newLevel);

            // 10강 달성 시 특수 효과 추가
            if (newLevel == MAX_UPGRADE_LEVEL) {
                applyMaxLevelEffect(item);
            }

            // 성공 메시지 및 효과
            String message = plugin.getConfig().getString("messages.upgrade_success",
                    "✨ {level}강 강화 성공! ✨").replace("{level}", String.valueOf(newLevel));
            player.sendMessage("§a" + message);

            // 효과음 및 파티클
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

            // 10강 달성 시 추가 축하
            if (newLevel == MAX_UPGRADE_LEVEL) {
                player.sendTitle("§6★ 10강 달성! ★", "§e특수 효과가 부여되었습니다!", 10, 40, 10);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "강화 성공 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 강화 실패 처리 (하락/파괴)
     */
    private void applyUpgradeFailure(Player player, ItemStack item, int currentLevel) {
        try {
            if (currentLevel <= 1) {
                // 1강 이하는 파괴
                player.getInventory().remove(item);
                player.sendMessage("§c💥 강화 실패! 아이템이 파괴되었습니다!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            } else {
                // 강화 레벨 하락
                int newLevel = Math.max(0, currentLevel - 1);
                setUpgradeLevel(item, newLevel);
                applyStatBonus(item, newLevel);

                String message = plugin.getConfig().getString("messages.upgrade_downgrade",
                        "강화 레벨이 {level}강으로 하락했습니다!").replace("{level}", String.valueOf(newLevel));
                player.sendMessage("§c" + message);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "강화 실패 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 강화 실패 처리 (변화 없음)
     */
    private void applyUpgradeStay(Player player, ItemStack item, int currentLevel) {
        try {
            String message = plugin.getConfig().getString("messages.upgrade_failure",
                    "💥 {level}강 강화 실패!").replace("{level}", String.valueOf(currentLevel + 1));
            player.sendMessage("§c" + message);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 유지 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 아이템에 강화 레벨 설정
     */
    private void setUpgradeLevel(ItemStack item, int level) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }

            // 기존 강화 정보 제거
            lore.removeIf(line -> line.startsWith(UPGRADE_LORE_PREFIX));

            // 새 강화 정보 추가 (0강은 표시하지 않음)
            if (level > 0) {
                lore.add(0, UPGRADE_LORE_PREFIX + level);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "강화 레벨 설정 중 오류", e);
        }
    }

    /**
     * 스탯 보너스 적용
     */
    private void applyStatBonus(ItemStack item, int level) {
        try {
            if (level <= 0) return;

            Material material = item.getType();
            String materialName = material.name();

            // 무기별 스탯 보너스
            if (materialName.contains("SWORD") || materialName.contains("BOW") || materialName.contains("CROSSBOW")) {
                // 검/활: 공격력 증가 (3% per level)
                // 실제 게임에서는 AttributeModifier 사용

            } else if (materialName.contains("AXE")) {
                // 도끼: 공격속도 증가는 AxeSpeedManager에서 처리

            } else if (materialName.contains("CHESTPLATE")) {
                // 흉갑: 방어력 증가 (3% per level)
                // 실제 게임에서는 AttributeModifier 사용
            }

            // 강화 단계별 내구도 보너스
            if (item.getType().getMaxDurability() > 0) {
                short maxDurability = item.getType().getMaxDurability();
                short currentDurability = (short) (maxDurability - item.getDurability());

                // 강화된 아이템은 내구도가 더 높음
                double durabilityBonus = 1.0 + (level * 0.05); // 5% per level
                short newMaxDurability = (short) (maxDurability * durabilityBonus);

                // 현재 내구도 비율 유지
                double durabilityRatio = (double) currentDurability / maxDurability;
                short newCurrentDurability = (short) (newMaxDurability * durabilityRatio);

                item.setDurability((short) (newMaxDurability - newCurrentDurability));
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "스탯 보너스 적용 중 오류", e);
        }
    }

    /**
     * 10강 최대 레벨 효과 적용
     */
    private void applyMaxLevelEffect(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }

            Material material = item.getType();
            String materialName = material.name();

            // 무기별 특수 효과 설명 추가
            if (materialName.contains("SWORD")) {
                lore.add("§c⚔️ 10강 효과: 출혈");
                lore.add("§7공격 시 상대방에게 출혈 효과");

            } else if (materialName.contains("AXE")) {
                lore.add("§6🔥 10강 효과: 발화");
                lore.add("§7공격 시 상대방을 불태움");

            } else if (materialName.contains("BOW") || materialName.contains("CROSSBOW")) {
                lore.add("§a🎯 10강 효과: 화염");
                lore.add("§7화살이 화염 속성을 가짐");

            } else if (materialName.contains("CHESTPLATE")) {
                lore.add("§9🛡️ 10강 효과: 가시");
                lore.add("§7공격받을 시 공격자에게 반사 피해");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "10강 효과 적용 중 오류", e);
        }
    }

    /**
     * 전투 특수 효과 적용
     */
    private void applyCombatEffect(Player attacker, LivingEntity target, ItemStack weapon) {
        try {
            Material material = weapon.getType();
            String materialName = material.name();
            UUID attackerUUID = attacker.getUniqueId();
            long currentTime = System.currentTimeMillis();

            if (materialName.contains("SWORD")) {
                // 검: 출혈 효과
                if (!isEffectOnCooldown(bleedEffectCooldown, attackerUUID, currentTime)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0)); // 5초간 위더
                    bleedEffectCooldown.put(attackerUUID, currentTime);
                    attacker.sendActionBar("§c⚔️ 출혈 효과 발동!");
                }

            } else if (materialName.contains("AXE")) {
                // 도끼: 발화 효과
                if (!isEffectOnCooldown(fireEffectCooldown, attackerUUID, currentTime)) {
                    target.setFireTicks(100); // 5초간 화염
                    fireEffectCooldown.put(attackerUUID, currentTime);
                    attacker.sendActionBar("§6🔥 발화 효과 발동!");
                }

            } else if (materialName.contains("BOW") || materialName.contains("CROSSBOW")) {
                // 활: 화염 화살 (이미 EntityShootBowEvent에서 처리)
                attacker.sendActionBar("§a🎯 화염 화살 발사!");

            } else if (materialName.contains("CHESTPLATE")) {
                // 흉갑: 가시 효과 (피격 시 발동하므로 여기서는 처리하지 않음)
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "전투 특수 효과 적용 중 오류: " + attacker.getName(), e);
        }
    }

    /**
     * 효과 쿨다운 확인
     */
    private boolean isEffectOnCooldown(Map<UUID, Long> cooldownMap, UUID playerUUID, long currentTime) {
        Long lastTime = cooldownMap.get(playerUUID);
        return lastTime != null && (currentTime - lastTime) < EFFECT_COOLDOWN;
    }

    /**
     * 현재 강화 레벨 반환
     */
    public int getCurrentUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        try {
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore();

            if (lore != null) {
                for (String line : lore) {
                    if (line.startsWith(UPGRADE_LORE_PREFIX)) {
                        String levelStr = line.substring(UPGRADE_LORE_PREFIX.length());
                        return Integer.parseInt(levelStr);
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "강화 레벨 확인 중 오류", e);
        }

        return 0;
    }

    /**
     * 강화 가능한 아이템인지 확인 (캐시 사용)
     */
    public boolean isUpgradable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        try {
            String materialName = item.getType().name();

            // 캐시에서 확인
            Boolean cached = materialCache.get(materialName);
            if (cached != null) {
                return cached;
            }

            // 새로 계산 후 캐시에 저장
            boolean upgradable = upgradableMaterials.contains(item.getType());
            materialCache.put(materialName, upgradable);

            return upgradable;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "강화 가능 여부 확인 중 오류", e);
            return false;
        }
    }

    /**
     * 쿨다운 확인
     */
    public boolean isOnCooldown(Player player) {
        Long lastTime = lastUpgradeTime.get(player.getUniqueId());
        if (lastTime == null) return false;

        return (System.currentTimeMillis() - lastTime) < UPGRADE_COOLDOWN;
    }

    /**
     * 아이템에 인첸트가 있는지 확인
     */
    private boolean hasEnchantments(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasEnchants();
    }

    /**
     * 강화 비용 계산
     */
    private int calculateExpCost(int targetLevel) {
        // 레벨에 따른 경험치 비용
        switch (targetLevel) {
            case 1: case 2: case 3: return 5;    // 1~3강: 5레벨
            case 4: case 5: return 10;           // 4~5강: 10레벨
            case 6: case 7: return 15;           // 6~7강: 15레벨
            case 8: case 9: return 20;           // 8~9강: 20레벨
            case 10: return 30;                  // 10강: 30레벨
            default: return 10;                  // 기본값
        }
    }

    /**
     * 강화 정보 표시
     */
    public void showUpgradeInfo(Player player, ItemStack item) {
        if (!isUpgradable(item)) {
            String message = plugin.getConfig().getString("messages.upgrade_not_available",
                    "이 아이템은 강화할 수 없습니다! (강화 가능: 검, 도끼, 활, 흉갑)");
            player.sendMessage("§c" + message);
            return;
        }

        try {
            int currentLevel = getCurrentUpgradeLevel(item);
            int nextLevel = currentLevel + 1;

            player.sendMessage("§6==========================================");
            player.sendMessage("§e강화 정보");
            player.sendMessage("§7아이템: §f" + getItemDisplayName(item));
            player.sendMessage("§7현재 강화: §a+" + currentLevel + "강");

            if (currentLevel < MAX_UPGRADE_LEVEL) {
                double successRate = successRates.getOrDefault(nextLevel, 0.5) * 100;
                double destroyRate = destroyRates.getOrDefault(nextLevel, 0.0) * 100;
                int expCost = calculateExpCost(nextLevel);

                player.sendMessage("§7다음 강화: §e+" + nextLevel + "강");
                player.sendMessage("§7성공률: §a" + String.format("%.1f%%", successRate));
                player.sendMessage("§7파괴/하락률: §c" + String.format("%.1f%%", destroyRate));
                player.sendMessage("§7필요 경험치: §b" + expCost + "레벨");

                if (nextLevel == MAX_UPGRADE_LEVEL) {
                    player.sendMessage("§6★ 10강 달성 시 특수 효과 부여!");
                }
            } else {
                player.sendMessage("§6★ 최대 강화 달성! 특수 효과 보유");
            }

            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 정보 표시 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 아이템 표시 이름 반환
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // Material 이름을 한글로 변환 (간단한 예시)
        String materialName = item.getType().name();
        if (materialName.contains("SWORD")) return "검";
        if (materialName.contains("AXE")) return "도끼";
        if (materialName.contains("BOW")) return "활";
        if (materialName.contains("CROSSBOW")) return "쇠뇌";
        if (materialName.contains("CHESTPLATE")) return "흉갑";

        return materialName;
    }

    /**
     * 캐시 정리
     */
    public void cleanupCache() {
        try {
            // 오래된 쿨다운 데이터 정리
            long currentTime = System.currentTimeMillis();
            long expireTime = currentTime - (EFFECT_COOLDOWN * 2); // 쿨다운의 2배 시간이 지나면 제거

            lastUpgradeTime.entrySet().removeIf(entry -> entry.getValue() < expireTime);
            fireEffectCooldown.entrySet().removeIf(entry -> entry.getValue() < expireTime);
            bleedEffectCooldown.entrySet().removeIf(entry -> entry.getValue() < expireTime);

            plugin.getLogger().info("EnchantUpgradeManager 캐시 정리 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "캐시 정리 중 오류", e);
        }
    }

    /**
     * 매니저 종료
     */
    public void onDisable() {
        try {
            // 캐시 정리
            cleanupCache();

            // 모든 맵 정리
            lastUpgradeTime.clear();
            fireEffectCooldown.clear();
            bleedEffectCooldown.clear();
            materialCache.clear();

            plugin.getLogger().info("EnchantUpgradeManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "EnchantUpgradeManager 종료 중 오류", e);
        }
    }
}