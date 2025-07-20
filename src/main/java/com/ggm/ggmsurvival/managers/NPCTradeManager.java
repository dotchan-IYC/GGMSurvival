// 수정된 NPCTradeManager.java - 문제 부분만
package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import com.ggm.ggmsurvival.GGMSurvival;

import java.util.*;

public class NPCTradeManager implements Listener {

    private final GGMSurvival plugin;
    private final Map<UUID, TradeType> npcTradeTypes = new HashMap<>();

    // 교환상 타입별 테마 - Material 이름 수정
    public enum TradeType {
        MINING("§6광물 상인", "광물과 원석을 구매합니다", Material.IRON_PICKAXE),     // PICKAXE → IRON_PICKAXE
        COMBAT("§c전투 상인", "전투 관련 아이템을 구매합니다", Material.IRON_SWORD),    // SWORD → IRON_SWORD
        FARMING("§a농업 상인", "농작물과 동물 재료를 구매합니다", Material.WHEAT),
        RARE("§5희귀 상인", "희귀하고 특별한 아이템을 구매합니다", Material.NETHER_STAR),
        BUILDING("§b건축 상인", "건축 재료를 구매합니다", Material.BRICKS),
        REDSTONE("§4레드스톤 상인", "레드스톤과 기계 부품을 구매합니다", Material.REDSTONE);

        private final String displayName;
        private final String description;
        private final Material icon;

        TradeType(String displayName, String description, Material icon) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Material getIcon() { return icon; }
    }

    public NPCTradeManager(GGMSurvival plugin) {
        this.plugin = plugin;
        initializeTradeItems();
    }

    /**
     * 교환 아이템 목록 초기화
     */
    private void initializeTradeItems() {
        plugin.getLogger().info("테마별 NPC 교환 시스템이 초기화되었습니다.");
    }

    /**
     * NPC와 상호작용 - hasCustomName() 메서드 수정
     */
    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != EntityType.VILLAGER) return;

        Villager npc = (Villager) event.getRightClicked();
        Player player = event.getPlayer();

        // NPC가 교환상인지 확인 - hasCustomName() → getCustomName() != null
        if (npc.getCustomName() == null) return;
        String customName = npc.getCustomName();
        if (!customName.contains("상인")) return;

        event.setCancelled(true);

        // 상인 타입 확인
        TradeType tradeType = getTradeTypeFromName(customName);
        if (tradeType != null) {
            openTradeGUI(player, tradeType);
        }
    }

    /**
     * 상인 이름으로 타입 확인
     */
    private TradeType getTradeTypeFromName(String name) {
        for (TradeType type : TradeType.values()) {
            if (name.contains(type.displayName.replaceAll("§.", ""))) {
                return type;
            }
        }
        return null;
    }

    /**
     * 교환 GUI 열기
     */
    public void openTradeGUI(Player player, TradeType tradeType) {
        Inventory gui = Bukkit.createInventory(null, 54, tradeType.getDisplayName() + " 교환소");

        Map<Material, Long> items = getTradeItems(tradeType);
        int slot = 0;

        for (Map.Entry<Material, Long> entry : items.entrySet()) {
            if (slot >= 45) break; // 마지막 줄은 버튼용으로 남겨둠

            Material material = entry.getKey();
            long price = entry.getValue();

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + getItemDisplayName(material));
            meta.setLore(Arrays.asList(
                    "§7판매 가격: §6" + String.format("%,d", price) + "G",
                    "§a좌클릭: 1개 판매",
                    "§a우클릭: 64개 판매",
                    "§a쉬프트+클릭: 전체 판매"
            ));
            item.setItemMeta(meta);

            gui.setItem(slot++, item);
        }

        // 닫기 버튼
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c닫기");
        closeMeta.setLore(Arrays.asList("§7교환소를 닫습니다"));
        closeItem.setItemMeta(closeMeta);
        gui.setItem(49, closeItem);

        player.openInventory(gui);
    }

    /**
     * 테마별 교환 아이템 목록 - Material 이름들 수정
     */
    private Map<Material, Long> getTradeItems(TradeType tradeType) {
        Map<Material, Long> items = new LinkedHashMap<>();

        switch (tradeType) {
            case MINING:
                // 광물 상인
                items.put(Material.COAL, 8L);
                items.put(Material.COAL_ORE, 15L);
                // Minecraft 1.17+ 전용 아이템들은 조건부로 추가
                if (materialExists("DEEPSLATE_COAL_ORE")) {
                    items.put(Material.valueOf("DEEPSLATE_COAL_ORE"), 18L);
                }
                items.put(Material.IRON_INGOT, 40L);
                items.put(Material.IRON_ORE, 60L);
                if (materialExists("DEEPSLATE_IRON_ORE")) {
                    items.put(Material.valueOf("DEEPSLATE_IRON_ORE"), 75L);
                }
                if (materialExists("RAW_IRON")) {
                    items.put(Material.valueOf("RAW_IRON"), 45L);
                }
                items.put(Material.GOLD_INGOT, 80L);
                items.put(Material.GOLD_ORE, 120L);
                if (materialExists("DEEPSLATE_GOLD_ORE")) {
                    items.put(Material.valueOf("DEEPSLATE_GOLD_ORE"), 140L);
                }
                if (materialExists("RAW_GOLD")) {
                    items.put(Material.valueOf("RAW_GOLD"), 90L);
                }
                items.put(Material.DIAMOND, 400L);
                items.put(Material.DIAMOND_ORE, 600L);
                if (materialExists("DEEPSLATE_DIAMOND_ORE")) {
                    items.put(Material.valueOf("DEEPSLATE_DIAMOND_ORE"), 700L);
                }
                items.put(Material.EMERALD, 250L);
                items.put(Material.EMERALD_ORE, 400L);
                if (materialExists("DEEPSLATE_EMERALD_ORE")) {
                    items.put(Material.valueOf("DEEPSLATE_EMERALD_ORE"), 450L);
                }
                items.put(Material.LAPIS_LAZULI, 15L);
                items.put(Material.LAPIS_ORE, 25L);
                items.put(Material.REDSTONE, 5L);
                items.put(Material.REDSTONE_ORE, 12L);
                if (materialExists("COPPER_INGOT")) {
                    items.put(Material.valueOf("COPPER_INGOT"), 20L);
                }
                if (materialExists("RAW_COPPER")) {
                    items.put(Material.valueOf("RAW_COPPER"), 18L);
                }
                if (materialExists("NETHERITE_INGOT")) {
                    items.put(Material.valueOf("NETHERITE_INGOT"), 5000L);
                }
                if (materialExists("NETHERITE_SCRAP")) {
                    items.put(Material.valueOf("NETHERITE_SCRAP"), 1200L);
                }
                if (materialExists("ANCIENT_DEBRIS")) {
                    items.put(Material.valueOf("ANCIENT_DEBRIS"), 3000L);
                }
                break;

            case COMBAT:
                // 전투 상인
                items.put(Material.GUNPOWDER, 12L);
                items.put(Material.BONE, 8L);
                items.put(Material.ROTTEN_FLESH, 3L);
                items.put(Material.STRING, 6L);
                items.put(Material.SPIDER_EYE, 15L);
                items.put(Material.ENDER_PEARL, 80L);
                items.put(Material.BLAZE_ROD, 40L);
                items.put(Material.BLAZE_POWDER, 20L);
                items.put(Material.GHAST_TEAR, 150L);
                items.put(Material.MAGMA_CREAM, 25L);
                items.put(Material.SLIME_BALL, 18L);
                items.put(Material.PRISMARINE_CRYSTALS, 30L);
                items.put(Material.PRISMARINE_SHARD, 20L);
                items.put(Material.SHULKER_SHELL, 500L);
                if (materialExists("PHANTOM_MEMBRANE")) {
                    items.put(Material.valueOf("PHANTOM_MEMBRANE"), 100L);
                }
                items.put(Material.WITHER_SKELETON_SKULL, 1000L);
                if (materialExists("ZOMBIE_HEAD")) {
                    items.put(Material.valueOf("ZOMBIE_HEAD"), 200L);
                }
                items.put(Material.SKELETON_SKULL, 150L);
                if (materialExists("CREEPER_HEAD")) {
                    items.put(Material.valueOf("CREEPER_HEAD"), 300L);
                }
                break;

            case FARMING:
                // 농업 상인
                items.put(Material.WHEAT, 5L);
                items.put(Material.WHEAT_SEEDS, 2L);
                items.put(Material.CARROT, 4L);
                items.put(Material.POTATO, 4L);
                items.put(Material.BEETROOT, 3L);
                items.put(Material.BEETROOT_SEEDS, 2L);
                items.put(Material.PUMPKIN, 8L);
                items.put(Material.MELON, 6L);
                items.put(Material.SUGAR_CANE, 7L);
                if (materialExists("BAMBOO")) {
                    items.put(Material.valueOf("BAMBOO"), 3L);
                }
                if (materialExists("KELP")) {
                    items.put(Material.valueOf("KELP"), 4L);
                }
                if (materialExists("SEA_PICKLE")) {
                    items.put(Material.valueOf("SEA_PICKLE"), 10L);
                }
                if (materialExists("SWEET_BERRIES")) {
                    items.put(Material.valueOf("SWEET_BERRIES"), 6L);
                }
                items.put(Material.COCOA_BEANS, 8L);
                items.put(Material.APPLE, 10L);
                items.put(Material.GOLDEN_APPLE, 200L);
                items.put(Material.LEATHER, 12L);
                items.put(Material.BEEF, 15L);
                items.put(Material.PORKCHOP, 15L);
                items.put(Material.MUTTON, 14L);
                items.put(Material.CHICKEN, 12L);
                items.put(Material.RABBIT, 18L);
                items.put(Material.MILK_BUCKET, 25L);
                items.put(Material.EGG, 8L);
                items.put(Material.FEATHER, 6L);
                // WOOL → WHITE_WOOL로 수정
                items.put(Material.WHITE_WOOL, 10L);
                break;

            case RARE:
                // 희귀 상인 (높은 가격)
                items.put(Material.NETHER_STAR, 8000L);
                items.put(Material.DRAGON_EGG, 50000L);
                items.put(Material.ELYTRA, 20000L);
                items.put(Material.HEART_OF_THE_SEA, 3000L);
                items.put(Material.NAUTILUS_SHELL, 500L);
                items.put(Material.CONDUIT, 10000L);
                items.put(Material.BEACON, 15000L);
                items.put(Material.TOTEM_OF_UNDYING, 5000L);
                items.put(Material.TRIDENT, 3000L);
                if (materialExists("ENCHANTED_GOLDEN_APPLE")) {
                    items.put(Material.valueOf("ENCHANTED_GOLDEN_APPLE"), 2000L);
                }
                // 음반들 - 버전별로 확인
                if (materialExists("MUSIC_DISC_13")) {
                    items.put(Material.valueOf("MUSIC_DISC_13"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_CAT"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_BLOCKS"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_CHIRP"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_FAR"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_MALL"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_MELLOHI"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_STAL"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_STRAD"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_WARD"), 800L);
                    items.put(Material.valueOf("MUSIC_DISC_11"), 1200L);
                    items.put(Material.valueOf("MUSIC_DISC_WAIT"), 800L);
                }
                break;

            case BUILDING:
                // 건축 상인
                items.put(Material.STONE, 2L);
                items.put(Material.COBBLESTONE, 1L);
                items.put(Material.GRANITE, 3L);
                items.put(Material.DIORITE, 3L);
                items.put(Material.ANDESITE, 3L);
                if (materialExists("DEEPSLATE")) {
                    items.put(Material.valueOf("DEEPSLATE"), 4L);
                }
                items.put(Material.SANDSTONE, 5L);
                items.put(Material.RED_SANDSTONE, 6L);
                items.put(Material.BRICKS, 8L);
                items.put(Material.NETHER_BRICKS, 10L);
                items.put(Material.PRISMARINE, 15L);
                items.put(Material.DARK_PRISMARINE, 20L);
                items.put(Material.QUARTZ, 25L);
                items.put(Material.QUARTZ_BLOCK, 100L);
                items.put(Material.TERRACOTTA, 8L);
                items.put(Material.WHITE_TERRACOTTA, 10L);
                items.put(Material.GLASS, 4L);
                items.put(Material.WHITE_STAINED_GLASS, 6L);
                items.put(Material.OAK_LOG, 5L);
                items.put(Material.BIRCH_LOG, 5L);
                items.put(Material.SPRUCE_LOG, 5L);
                items.put(Material.JUNGLE_LOG, 6L);
                items.put(Material.ACACIA_LOG, 6L);
                items.put(Material.DARK_OAK_LOG, 7L);
                if (materialExists("CRIMSON_STEM")) {
                    items.put(Material.valueOf("CRIMSON_STEM"), 12L);
                    items.put(Material.valueOf("WARPED_STEM"), 12L);
                }
                break;

            case REDSTONE:
                // 레드스톤 상인
                items.put(Material.REDSTONE, 5L);
                items.put(Material.REDSTONE_BLOCK, 45L);
                items.put(Material.REPEATER, 15L);
                items.put(Material.COMPARATOR, 20L);
                items.put(Material.PISTON, 30L);
                items.put(Material.STICKY_PISTON, 50L);
                items.put(Material.REDSTONE_TORCH, 8L);
                items.put(Material.LEVER, 10L);
                items.put(Material.STONE_BUTTON, 5L);
                items.put(Material.OAK_BUTTON, 5L);
                items.put(Material.STONE_PRESSURE_PLATE, 12L);
                items.put(Material.OAK_PRESSURE_PLATE, 10L);
                items.put(Material.TRIPWIRE_HOOK, 25L);
                items.put(Material.OBSERVER, 40L);
                items.put(Material.DROPPER, 35L);
                items.put(Material.DISPENSER, 40L);
                items.put(Material.HOPPER, 80L);
                items.put(Material.CHEST, 15L);
                items.put(Material.TRAPPED_CHEST, 25L);
                items.put(Material.DAYLIGHT_DETECTOR, 30L);
                items.put(Material.TNT, 60L);
                items.put(Material.SLIME_BLOCK, 90L);
                if (materialExists("HONEY_BLOCK")) {
                    items.put(Material.valueOf("HONEY_BLOCK"), 100L);
                }
                break;
        }

        return items;
    }

    /**
     * Material 존재 여부 확인 (버전 호환성)
     */
    private boolean materialExists(String materialName) {
        try {
            Material.valueOf(materialName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * GUI 클릭 이벤트
     */
    @EventHandler
    public void onTradeGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // 교환소 GUI인지 확인
        boolean isTradeGUI = false;
        TradeType currentTradeType = null;
        for (TradeType type : TradeType.values()) {
            if (title.equals(type.getDisplayName() + " 교환소")) {
                isTradeGUI = true;
                currentTradeType = type;
                break;
            }
        }

        if (!isTradeGUI) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String displayName = clickedItem.getItemMeta().getDisplayName();

        if (displayName.equals("§c닫기")) {
            player.closeInventory();
            return;
        }

        // 아이템 판매 처리
        Material material = clickedItem.getType();
        Map<Material, Long> tradeItems = getTradeItems(currentTradeType);

        if (!tradeItems.containsKey(material)) return;

        long pricePerItem = tradeItems.get(material);

        // 클릭 타입에 따른 수량 결정
        int sellAmount = 1;
        if (event.isRightClick()) {
            sellAmount = 64;
        } else if (event.isShiftClick()) {
            // 인벤토리에 있는 모든 해당 아이템
            sellAmount = countItemsInInventory(player, material);
        }

        sellItems(player, material, sellAmount, pricePerItem);
    }

    /**
     * 아이템 판매 처리
     */
    private void sellItems(Player player, Material material, int amount, long pricePerItem) {
        // 플레이어 인벤토리에서 해당 아이템 개수 확인
        int availableAmount = countItemsInInventory(player, material);

        if (availableAmount == 0) {
            player.sendMessage("§c판매할 " + getItemDisplayName(material) + "이(가) 없습니다!");
            return;
        }

        int actualSellAmount = Math.min(amount, availableAmount);
        long totalPrice = pricePerItem * actualSellAmount;

        // 아이템 제거
        removeItemsFromInventory(player, material, actualSellAmount);

        // 돈 지급
        plugin.getEconomyManager().addMoney(player.getUniqueId(), player.getName(), totalPrice)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§a" + getItemDisplayName(material) + " " + actualSellAmount + "개를 " +
                                String.format("%,d", totalPrice) + "G에 판매했습니다!");

                        plugin.getLogger().info(String.format("[아이템판매] %s: %s %d개 -> %,dG",
                                player.getName(), material.name(), actualSellAmount, totalPrice));
                    } else {
                        // 실패 시 아이템 복구
                        player.getInventory().addItem(new ItemStack(material, actualSellAmount));
                        player.sendMessage("§c판매 처리에 실패했습니다. 아이템이 복구되었습니다.");
                    }
                });
    }

    /**
     * 인벤토리에서 특정 아이템 개수 계산
     */
    private int countItemsInInventory(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * 인벤토리에서 아이템 제거
     */
    private void removeItemsFromInventory(Player player, Material material, int amount) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && remaining > 0) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    remaining -= itemAmount;
                    item.setAmount(0);
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }
    }

    /**
     * 아이템 표시 이름 가져오기
     */
    private String getItemDisplayName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
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
     * NPC 생성 (관리자용)
     */
    public void createTradeNPC(Player player, TradeType tradeType) {
        Villager npc = (Villager) player.getWorld().spawnEntity(player.getLocation(), EntityType.VILLAGER);
        npc.setCustomName(tradeType.getDisplayName());
        npc.setCustomNameVisible(true);
        npc.setAI(false);
        npc.setSilent(true);
        npc.setInvulnerable(true);

        // NPC 타입 저장
        npcTradeTypes.put(npc.getUniqueId(), tradeType);

        player.sendMessage("§a" + tradeType.getDisplayName() + "§a이(가) 생성되었습니다!");
        plugin.getLogger().info(player.getName() + "이(가) " + tradeType.getDisplayName() + " NPC를 생성했습니다.");
    }
}