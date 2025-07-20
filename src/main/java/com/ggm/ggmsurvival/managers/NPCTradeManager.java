package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class NPCTradeManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    // 상인 타입별 거래 가격 - Map.of() 문제 해결
    public enum TradeType {
        MINING("§6광물 상인", createMiningPrices()),
        COMBAT("§c전투 상인", createCombatPrices()),
        FARMING("§a농업 상인", createFarmingPrices()),
        RARE("§5희귀 상인", createRarePrices()),
        BUILDING("§b건축 상인", createBuildingPrices()),
        REDSTONE("§4레드스톤 상인", createRedstonePrices());

        public final String displayName;
        private final Map<Material, Long> prices;

        TradeType(String displayName, Map<Material, Long> prices) {
            this.displayName = displayName;
            this.prices = new HashMap<>(prices);
        }

        public Long getPrice(Material material) {
            return prices.get(material);
        }

        public Map<Material, Long> getAllPrices() {
            return new HashMap<>(prices);
        }

        // Map.of() 대신 static 메소드로 Map 생성
        private static Map<Material, Long> createMiningPrices() {
            Map<Material, Long> prices = new HashMap<>();
            prices.put(Material.DIAMOND, 1000L);
            prices.put(Material.EMERALD, 800L);
            prices.put(Material.GOLD_INGOT, 200L);
            prices.put(Material.IRON_INGOT, 100L);
            prices.put(Material.COAL, 50L);
            prices.put(Material.REDSTONE, 30L);
            prices.put(Material.LAPIS_LAZULI, 40L);
            prices.put(Material.QUARTZ, 60L);
            return prices;
        }

        private static Map<Material, Long> createCombatPrices() {
            Map<Material, Long> prices = new HashMap<>();
            prices.put(Material.ROTTEN_FLESH, 10L);
            prices.put(Material.BONE, 20L);
            prices.put(Material.STRING, 15L);
            prices.put(Material.SPIDER_EYE, 25L);
            prices.put(Material.GUNPOWDER, 50L);
            prices.put(Material.ENDER_PEARL, 500L);
            prices.put(Material.BLAZE_ROD, 300L);
            prices.put(Material.GHAST_TEAR, 800L);
            prices.put(Material.NETHER_STAR, 10000L);
            prices.put(Material.DRAGON_BREATH, 2000L);
            return prices;
        }

        private static Map<Material, Long> createFarmingPrices() {
            Map<Material, Long> prices = new HashMap<>();
            prices.put(Material.WHEAT, 20L);
            prices.put(Material.CARROT, 15L);
            prices.put(Material.POTATO, 15L);
            prices.put(Material.BEETROOT, 18L);
            prices.put(Material.SUGAR_CANE, 25L);
            prices.put(Material.MELON_SLICE, 12L);
            prices.put(Material.PUMPKIN, 30L);
            prices.put(Material.BEEF, 40L);
            prices.put(Material.PORKCHOP, 35L);
            prices.put(Material.CHICKEN, 30L);
            prices.put(Material.MUTTON, 38L);
            prices.put(Material.LEATHER, 25L);
            prices.put(Material.FEATHER, 20L);
            return prices;
        }

        private static Map<Material, Long> createRarePrices() {
            Map<Material, Long> prices = new HashMap<>();
            prices.put(Material.NETHERITE_INGOT, 50000L);
            prices.put(Material.NETHERITE_SCRAP, 12000L);
            prices.put(Material.ANCIENT_DEBRIS, 15000L);
            prices.put(Material.WITHER_SKELETON_SKULL, 5000L);
            prices.put(Material.DRAGON_HEAD, 20000L);
            prices.put(Material.ELYTRA, 30000L);
            prices.put(Material.SHULKER_SHELL, 3000L);
            prices.put(Material.PHANTOM_MEMBRANE, 1000L);
            prices.put(Material.HEART_OF_THE_SEA, 8000L);
            prices.put(Material.NAUTILUS_SHELL, 2000L);
            return prices;
        }

        private static Map<Material, Long> createBuildingPrices() {
            Map<Material, Long> prices = new HashMap<>();
            prices.put(Material.OAK_LOG, 30L);
            prices.put(Material.BIRCH_LOG, 32L);
            prices.put(Material.SPRUCE_LOG, 35L);
            prices.put(Material.JUNGLE_LOG, 38L);
            prices.put(Material.ACACIA_LOG, 33L);
            prices.put(Material.DARK_OAK_LOG, 40L);
            prices.put(Material.STONE, 10L);
            prices.put(Material.COBBLESTONE, 8L);
            prices.put(Material.SAND, 15L);
            prices.put(Material.GRAVEL, 12L);
            prices.put(Material.CLAY, 25L);
            prices.put(Material.TERRACOTTA, 20L);
            return prices;
        }

        private static Map<Material, Long> createRedstonePrices() {
            Map<Material, Long> prices = new HashMap<>();
            prices.put(Material.REDSTONE, 30L);
            prices.put(Material.REDSTONE_BLOCK, 270L);
            prices.put(Material.REPEATER, 100L);
            prices.put(Material.COMPARATOR, 150L);
            prices.put(Material.PISTON, 80L);
            prices.put(Material.STICKY_PISTON, 120L);
            prices.put(Material.OBSERVER, 200L);
            prices.put(Material.DROPPER, 90L);
            prices.put(Material.DISPENSER, 100L);
            prices.put(Material.HOPPER, 300L);
            return prices;
        }
    }

    public NPCTradeManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();

        // 테이블 생성
        createNPCTable();

        plugin.getLogger().info("NPC 교환 시스템 초기화 완료");
    }

    /**
     * NPC 테이블 생성
     */
    private void createNPCTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ggm_npcs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                entity_uuid VARCHAR(36) NOT NULL UNIQUE,
                npc_name VARCHAR(50) NOT NULL,
                trade_type VARCHAR(20) NOT NULL,
                world VARCHAR(50) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                created_by VARCHAR(36) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_world (world),
                INDEX idx_trade_type (trade_type)
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("NPC 테이블이 준비되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("NPC 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * NPC 생성 - 수정된 메소드 시그니처
     */
    public boolean createTradeNPC(Player creator, String npcName, TradeType tradeType) {
        try {
            Location location = creator.getLocation();

            // 주민 스폰
            Villager npc = (Villager) creator.getWorld().spawnEntity(location, EntityType.VILLAGER);
            npc.setCustomName(tradeType.displayName + " " + npcName);
            npc.setCustomNameVisible(true);
            npc.setAI(false); // AI 비활성화
            npc.setInvulnerable(true); // 무적
            npc.setProfession(Villager.Profession.NONE);
            npc.setVillagerType(getVillagerTypeForTrade(tradeType));

            // DB에 저장
            saveNPCToDatabase(npc, npcName, tradeType, creator);

            creator.sendMessage("§a" + tradeType.displayName + " '" + npcName + "'§a이(가) 생성되었습니다!");
            creator.sendMessage("§7NPC를 우클릭하여 거래하세요!");

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("NPC 생성 오류: " + e.getMessage());
            creator.sendMessage("§cNPC 생성 중 오류가 발생했습니다: " + e.getMessage());
            return false;
        }
    }

    /**
     * 거래 타입에 따른 주민 타입 반환
     */
    private Villager.Type getVillagerTypeForTrade(TradeType tradeType) {
        switch (tradeType) {
            case MINING:
                return Villager.Type.PLAINS;
            case COMBAT:
                return Villager.Type.DESERT;
            case FARMING:
                return Villager.Type.PLAINS;
            case RARE:
                return Villager.Type.JUNGLE;
            case BUILDING:
                return Villager.Type.TAIGA;
            case REDSTONE:
                return Villager.Type.SAVANNA;
            default:
                return Villager.Type.PLAINS;
        }
    }

    /**
     * NPC를 데이터베이스에 저장
     */
    private void saveNPCToDatabase(Villager npc, String npcName, TradeType tradeType, Player creator) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = """
                INSERT INTO ggm_npcs (entity_uuid, npc_name, trade_type, world, x, y, z, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                Location loc = npc.getLocation();
                stmt.setString(1, npc.getUniqueId().toString());
                stmt.setString(2, npcName);
                stmt.setString(3, tradeType.name());
                stmt.setString(4, loc.getWorld().getName());
                stmt.setDouble(5, loc.getX());
                stmt.setDouble(6, loc.getY());
                stmt.setDouble(7, loc.getZ());
                stmt.setString(8, creator.getUniqueId().toString());

                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("NPC 데이터베이스 저장 실패: " + e.getMessage());
        }
    }

    /**
     * NPC 상호작용 이벤트
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        Villager villager = (Villager) event.getRightClicked();
        if (villager.getCustomName() == null) return;

        // NPC 상인인지 확인
        TradeType tradeType = getTradeTypeFromName(villager.getCustomName());
        if (tradeType == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (!plugin.isFeatureEnabled("npc_trading")) {
            player.sendMessage("§cNPC 교환은 야생 서버에서만 사용할 수 있습니다!");
            return;
        }

        // 거래 GUI 열기
        openTradeGUI(player, tradeType);
    }

    /**
     * 상인 이름에서 거래 타입 추출
     */
    private TradeType getTradeTypeFromName(String customName) {
        for (TradeType type : TradeType.values()) {
            if (customName.contains(type.displayName.replaceAll("§.", ""))) {
                return type;
            }
        }
        return null;
    }

    /**
     * 거래 GUI 열기
     */
    private void openTradeGUI(Player player, TradeType tradeType) {
        Inventory gui = Bukkit.createInventory(null, 54, tradeType.displayName + " §0거래소");

        // 상인 정보 표시
        ItemStack infoItem = new ItemStack(Material.EMERALD);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(tradeType.displayName);
        infoMeta.setLore(Arrays.asList(
                "§7아이템을 판매하여 G를 획득하세요!",
                "",
                "§e클릭하여 인벤토리의 모든 아이템 판매"
        ));
        infoItem.setItemMeta(infoMeta);
        gui.setItem(4, infoItem);

        // 거래 가능 아이템들 표시
        Map<Material, Long> prices = tradeType.getAllPrices();
        int slot = 9;

        for (Map.Entry<Material, Long> entry : prices.entrySet()) {
            if (slot >= 45) break; // GUI 공간 제한

            Material material = entry.getKey();
            Long price = entry.getValue();

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§f" + getItemDisplayName(material));
            meta.setLore(Arrays.asList(
                    "§7가격: §6" + formatMoney(price) + "G §7(개당)",
                    "",
                    "§a인벤토리에 있는 이 아이템들이 자동으로 판매됩니다!"
            ));
            item.setItemMeta(meta);

            gui.setItem(slot++, item);
        }

        // 취소 버튼
        ItemStack cancelButton = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName("§c취소");
        cancelMeta.setLore(Arrays.asList("§7클릭하여 거래를 취소합니다."));
        cancelButton.setItemMeta(cancelMeta);
        gui.setItem(49, cancelButton);

        player.openInventory(gui);
    }

    /**
     * GUI 클릭 이벤트
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // 거래 GUI 확인
        if (!title.contains("거래소")) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // 상인 타입 확인
        TradeType tradeType = null;
        for (TradeType type : TradeType.values()) {
            if (title.contains(type.displayName.replaceAll("§.", ""))) {
                tradeType = type;
                break;
            }
        }

        if (tradeType == null) return;

        if (clickedItem.getType() == Material.EMERALD) {
            // 판매 실행
            sellItems(player, tradeType);
        } else if (clickedItem.getType() == Material.BARRIER) {
            // 취소
            player.closeInventory();
            player.sendMessage("§7거래를 취소했습니다.");
        }
    }

    /**
     * 아이템 판매 처리
     */
    private void sellItems(Player player, TradeType tradeType) {
        if (!plugin.isFeatureEnabled("npc_trading")) {
            player.sendMessage("§cNPC 교환은 야생 서버에서만 사용할 수 있습니다!");
            return;
        }

        Map<Material, Integer> sellableItems = new HashMap<>();
        long totalPrice = 0;

        // 인벤토리에서 판매 가능한 아이템 찾기
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            Long price = tradeType.getPrice(item.getType());
            if (price != null && price > 0) {
                sellableItems.put(item.getType(), sellableItems.getOrDefault(item.getType(), 0) + item.getAmount());
                totalPrice += price * item.getAmount();
            }
        }

        if (sellableItems.isEmpty()) {
            player.sendMessage("§c" + tradeType.displayName + "§c에게 판매할 수 있는 아이템이 없습니다!");
            return;
        }

        final long finalTotalPrice = totalPrice;
        final Map<Material, Integer> finalSellableItems = new HashMap<>(sellableItems);

        // 아이템 제거
        for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();
            removeItemsFromInventory(player, material, amount);
        }

        // G 지급
        plugin.getEconomyManager().addMoney(player.getUniqueId(), finalTotalPrice)
                .thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            // 성공 메시지
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            player.sendMessage("§a💰 아이템 판매 완료!");
                            player.sendMessage("");
                            player.sendMessage("§7상인: " + tradeType.displayName);

                            // 판매된 아이템 목록 표시
                            player.sendMessage("§7판매 아이템:");
                            for (Map.Entry<Material, Integer> entry : finalSellableItems.entrySet()) {
                                Material material = entry.getKey();
                                int amount = entry.getValue();
                                long itemPrice = tradeType.getPrice(material);
                                long itemTotal = itemPrice * amount;

                                player.sendMessage("§f  • " + getItemDisplayName(material) +
                                        " x" + amount + " = §6" + formatMoney(itemTotal) + "G");
                            }

                            player.sendMessage("");
                            player.sendMessage("§7총 수입: §6" + formatMoney(finalTotalPrice) + "G");
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                            // 사운드 효과
                            player.playSound(player.getLocation(),
                                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                    1.0f, 1.2f);

                            plugin.getLogger().info(String.format("[NPC거래] %s: %s에게 %dG 판매",
                                    player.getName(), tradeType.name(), finalTotalPrice));
                        } else {
                            player.sendMessage("§c거래 처리 중 오류가 발생했습니다!");

                            // 실패 시 아이템 복구
                            for (Map.Entry<Material, Integer> entry : finalSellableItems.entrySet()) {
                                Material material = entry.getKey();
                                int amount = entry.getValue();
                                ItemStack restoreItem = new ItemStack(material, amount);
                                addItemSafely(player, restoreItem);
                            }

                            player.sendMessage("§7아이템이 복구되었습니다.");
                        }
                    });
                }).exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c거래 처리 중 오류 발생: " + throwable.getMessage());

                        // 오류 시 아이템 복구
                        for (Map.Entry<Material, Integer> entry : finalSellableItems.entrySet()) {
                            Material material = entry.getKey();
                            int amount = entry.getValue();
                            ItemStack restoreItem = new ItemStack(material, amount);
                            addItemSafely(player, restoreItem);
                        }

                        player.sendMessage("§7아이템이 복구되었습니다.");
                    });
                    plugin.getLogger().severe("NPC 거래 오류: " + throwable.getMessage());
                    return null;
                });

        player.closeInventory();
    }

    /**
     * 아이템을 안전하게 인벤토리에 추가
     */
    private void addItemSafely(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            // 인벤토리가 가득 찬 경우 바닥에 드롭
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage("§e인벤토리가 가득 차서 " + getItemDisplayName(item.getType()) +
                    " §ex" + item.getAmount() + "§e이(가) 바닥에 떨어졌습니다.");
        }
    }

    /**
     * 인벤토리에서 특정 아이템 제거
     */
    private void removeItemsFromInventory(Player player, Material material, int amount) {
        int remaining = amount;

        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == material) {
                int removeAmount = Math.min(remaining, item.getAmount());

                if (removeAmount >= item.getAmount()) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - removeAmount);
                }

                remaining -= removeAmount;
            }
        }
    }

    /**
     * 가격 목록 표시
     */
    public void showPrices(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l💰 NPC 거래 가격표");
        player.sendMessage("");

        for (TradeType type : TradeType.values()) {
            player.sendMessage(type.displayName + "§7:");
            Map<Material, Long> prices = type.getAllPrices();

            for (Map.Entry<Material, Long> entry : prices.entrySet()) {
                Material material = entry.getKey();
                Long price = entry.getValue();
                player.sendMessage("§f  • " + getItemDisplayName(material) +
                        ": §6" + formatMoney(price) + "G");
            }
            player.sendMessage("");
        }

        player.sendMessage("§7NPC를 우클릭하여 거래하세요!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 상인 목록 표시
     */
    public void showMerchants(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🏪 NPC 상인 안내");
        player.sendMessage("");

        for (TradeType type : TradeType.values()) {
            player.sendMessage(type.displayName);
            player.sendMessage("§7" + getMerchantDescription(type));
            player.sendMessage("");
        }

        player.sendMessage("§7/trade prices - 가격표 확인");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 상인 설명 반환
     */
    private String getMerchantDescription(TradeType type) {
        switch (type) {
            case MINING: return "모든 종류의 광물과 원석을 최고가에 구매합니다!";
            case COMBAT: return "몬스터 드롭과 전투 관련 아이템을 구매합니다!";
            case FARMING: return "농작물과 동물 재료를 구매합니다!";
            case RARE: return "매우 희귀한 아이템을 놀라운 가격에 구매합니다!";
            case BUILDING: return "모든 건축 재료를 대량으로 구매합니다!";
            case REDSTONE: return "레드스톤과 기계 부품을 구매합니다!";
            default: return "다양한 아이템을 구매합니다!";
        }
    }

    /**
     * 아이템 표시명 반환
     */
    private String getItemDisplayName(Material material) {
        // 한글 아이템명 매핑
        switch (material.name()) {
            case "DIAMOND": return "다이아몬드";
            case "EMERALD": return "에메랄드";
            case "GOLD_INGOT": return "금 주괴";
            case "IRON_INGOT": return "철 주괴";
            case "COAL": return "석탄";
            case "REDSTONE": return "레드스톤";
            case "LAPIS_LAZULI": return "청금석";
            case "QUARTZ": return "석영";
            case "WHEAT": return "밀";
            case "CARROT": return "당근";
            case "POTATO": return "감자";
            case "BEETROOT": return "비트루트";
            case "SUGAR_CANE": return "사탕수수";
            case "MELON_SLICE": return "수박 조각";
            case "PUMPKIN": return "호박";
            case "BEEF": return "소고기";
            case "PORKCHOP": return "돼지고기";
            case "CHICKEN": return "닭고기";
            case "MUTTON": return "양고기";
            case "LEATHER": return "가죽";
            case "FEATHER": return "깃털";
            case "ROTTEN_FLESH": return "썩은 살점";
            case "BONE": return "뼈";
            case "STRING": return "실";
            case "SPIDER_EYE": return "거미 눈";
            case "GUNPOWDER": return "화약";
            case "ENDER_PEARL": return "엔더 진주";
            case "BLAZE_ROD": return "블레이즈 막대";
            case "GHAST_TEAR": return "가스트 눈물";
            case "NETHER_STAR": return "네더의 별";
            case "DRAGON_BREATH": return "드래곤 브레스";
            case "NETHERITE_INGOT": return "네더라이트 주괴";
            case "NETHERITE_SCRAP": return "네더라이트 조각";
            case "ANCIENT_DEBRIS": return "고대 잔해";
            case "WITHER_SKELETON_SKULL": return "위더 스켈레톤 머리";
            case "DRAGON_HEAD": return "드래곤 머리";
            case "ELYTRA": return "겉날개";
            case "SHULKER_SHELL": return "셜커 껍질";
            case "PHANTOM_MEMBRANE": return "팬텀 막";
            case "HEART_OF_THE_SEA": return "바다의 심장";
            case "NAUTILUS_SHELL": return "앵무조개 껍질";
            default: return material.name().toLowerCase().replace("_", " ");
        }
    }

    /**
     * 금액 포맷팅
     */
    private String formatMoney(long amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }
}