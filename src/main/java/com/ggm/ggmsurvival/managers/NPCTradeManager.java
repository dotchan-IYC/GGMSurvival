// 완전한 NPCTradeManager.java - 수정된 EconomyManager 호출 포함
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

    // 상인 타입별 거래 가격
    public enum TradeType {
        MINING("§6광물 상인", Map.of(
                Material.DIAMOND, 1000L,
                Material.EMERALD, 800L,
                Material.GOLD_INGOT, 200L,
                Material.IRON_INGOT, 100L,
                Material.COAL, 50L,
                Material.REDSTONE, 30L,
                Material.LAPIS_LAZULI, 40L,
                Material.QUARTZ, 60L
        )),

        COMBAT("§c전투 상인", Map.of(
                Material.ROTTEN_FLESH, 10L,
                Material.BONE, 20L,
                Material.STRING, 15L,
                Material.SPIDER_EYE, 25L,
                Material.GUNPOWDER, 50L,
                Material.ENDER_PEARL, 500L,
                Material.BLAZE_ROD, 300L,
                Material.GHAST_TEAR, 800L,
                Material.NETHER_STAR, 10000L,
                Material.DRAGON_BREATH, 2000L
        )),

        FARMING("§a농업 상인", Map.of(
                Material.WHEAT, 20L,
                Material.CARROT, 15L,
                Material.POTATO, 15L,
                Material.BEETROOT, 18L,
                Material.SUGAR_CANE, 25L,
                Material.MELON_SLICE, 12L,
                Material.PUMPKIN, 30L,
                Material.BEEF, 40L,
                Material.PORKCHOP, 35L,
                Material.CHICKEN, 30L,
                Material.MUTTON, 38L,
                Material.LEATHER, 25L,
                Material.FEATHER, 20L
        )),

        RARE("§5희귀 상인", Map.of(
                Material.NETHERITE_INGOT, 50000L,
                Material.NETHERITE_SCRAP, 12000L,
                Material.ANCIENT_DEBRIS, 15000L,
                Material.WITHER_SKELETON_SKULL, 5000L,
                Material.DRAGON_HEAD, 20000L,
                Material.ELYTRA, 30000L,
                Material.SHULKER_SHELL, 3000L,
                Material.PHANTOM_MEMBRANE, 1000L,
                Material.HEART_OF_THE_SEA, 8000L,
                Material.NAUTILUS_SHELL, 2000L
        )),

        BUILDING("§b건축 상인", Map.of(
                Material.OAK_LOG, 30L,
                Material.BIRCH_LOG, 32L,
                Material.SPRUCE_LOG, 35L,
                Material.JUNGLE_LOG, 38L,
                Material.ACACIA_LOG, 33L,
                Material.DARK_OAK_LOG, 40L,
                Material.STONE, 10L,
                Material.COBBLESTONE, 8L,
                Material.SAND, 15L,
                Material.GRAVEL, 12L,
                Material.CLAY, 25L,
                Material.TERRACOTTA, 20L
        )),

        REDSTONE("§4레드스톤 상인", Map.of(
                Material.REDSTONE, 30L,
                Material.REDSTONE_BLOCK, 270L,
                Material.REPEATER, 100L,
                Material.COMPARATOR, 150L,
                Material.PISTON, 80L,
                Material.STICKY_PISTON, 120L,
                Material.OBSERVER, 200L,
                Material.DROPPER, 90L,
                Material.DISPENSER, 100L,
                Material.HOPPER, 300L
        ));

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
     * NPC 생성
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
            plugin.getLogger().info(String.format("[NPC생성] %s: %s (%s)",
                    creator.getName(), npcName, tradeType.name()));

            return true;

        } catch (Exception e) {
            creator.sendMessage("§cNPC 생성 중 오류 발생: " + e.getMessage());
            plugin.getLogger().severe("NPC 생성 오류: " + e.getMessage());
            return false;
        }
    }

    /**
     * 거래 타입에 따른 주민 타입 설정
     */
    private Villager.Type getVillagerTypeForTrade(TradeType tradeType) {
        switch (tradeType) {
            case MINING: return Villager.Type.DESERT;
            case COMBAT: return Villager.Type.SAVANNA;
            case FARMING: return Villager.Type.PLAINS;
            case RARE: return Villager.Type.JUNGLE;
            case BUILDING: return Villager.Type.TAIGA;
            case REDSTONE: return Villager.Type.SNOW;
            default: return Villager.Type.PLAINS;
        }
    }

    /**
     * NPC를 데이터베이스에 저장
     */
    private void saveNPCToDatabase(Villager npc, String npcName, TradeType tradeType, Player creator) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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
                    plugin.getLogger().info("NPC 데이터베이스 저장 완료: " + npcName);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("NPC DB 저장 실패: " + e.getMessage());
            }
        });
    }

    /**
     * NPC 제거
     */
    public boolean removeNPC(Player player, Villager npc) {
        try {
            if (npc.getCustomName() == null || !npc.getCustomName().contains("상인")) {
                player.sendMessage("§c이 NPC는 교환상이 아닙니다!");
                return false;
            }

            String npcName = npc.getCustomName();
            UUID npcUUID = npc.getUniqueId();

            // DB에서 제거
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = databaseManager.getConnection()) {
                    String sql = "DELETE FROM ggm_npcs WHERE entity_uuid = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, npcUUID.toString());
                        int deleted = stmt.executeUpdate();

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (deleted > 0) {
                                plugin.getLogger().info("NPC DB에서 제거: " + npcName);
                            }
                        });
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("NPC DB 제거 실패: " + e.getMessage());
                }
            });

            // 엔티티 제거
            npc.remove();
            player.sendMessage("§a" + npcName + "§a이(가) 제거되었습니다.");

            plugin.getLogger().info(String.format("[NPC제거] %s: %s",
                    player.getName(), npcName));

            return true;

        } catch (Exception e) {
            player.sendMessage("§cNPC 제거 중 오류 발생: " + e.getMessage());
            plugin.getLogger().severe("NPC 제거 오류: " + e.getMessage());
            return false;
        }
    }

    /**
     * NPC와 상호작용
     */
    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent event) {
        if (!plugin.isFeatureEnabled("npc_trading")) return;
        if (event.getRightClicked().getType() != EntityType.VILLAGER) return;

        Villager npc = (Villager) event.getRightClicked();
        Player player = event.getPlayer();

        // NPC가 교환상인지 확인
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

        // 아이템 제거
        for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            removeItemsFromInventory(player, material, amount);
        }

        // G 지급 - 수정된 메서드 시그니처 사용 (UUID, long)
        plugin.getEconomyManager().addMoney(player.getUniqueId(), totalPrice)
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
                            for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
                                Material material = entry.getKey();
                                int amount = entry.getValue();
                                long itemPrice = tradeType.getPrice(material);
                                long itemTotal = itemPrice * amount;

                                player.sendMessage("§f  • " + getItemDisplayName(material) +
                                        " x" + amount + " = §6" + formatMoney(itemTotal) + "G");
                            }

                            player.sendMessage("");
                            player.sendMessage("§7총 수입: §6" + formatMoney(totalPrice) + "G");
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                            // 사운드 효과
                            player.playSound(player.getLocation(),
                                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                    1.0f, 1.2f);

                            plugin.getLogger().info(String.format("[NPC거래] %s: %s에게 %dG 판매",
                                    player.getName(), tradeType.name(), totalPrice));
                        } else {
                            player.sendMessage("§c거래 처리 중 오류가 발생했습니다!");

                            // 실패 시 아이템 복구
                            for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
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
                        for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
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
            case "DRAGON_BREATH": return "드래곤의 숨결";
            case "NETHERITE_INGOT": return "네더라이트 주괴";
            case "NETHERITE_SCRAP": return "네더라이트 조각";
            case "ANCIENT_DEBRIS": return "고대 파편";
            case "WITHER_SKELETON_SKULL": return "위더 스켈레톤 머리";
            case "DRAGON_HEAD": return "드래곤 머리";
            case "ELYTRA": return "겉날개";
            case "SHULKER_SHELL": return "셜커 껍질";
            case "PHANTOM_MEMBRANE": return "팬텀 막";
            case "HEART_OF_THE_SEA": return "바다의 심장";
            case "NAUTILUS_SHELL": return "앵무조개 껍질";
            case "OAK_LOG": return "참나무 원목";
            case "BIRCH_LOG": return "자작나무 원목";
            case "SPRUCE_LOG": return "가문비나무 원목";
            case "JUNGLE_LOG": return "정글나무 원목";
            case "ACACIA_LOG": return "아카시아나무 원목";
            case "DARK_OAK_LOG": return "짙은 참나무 원목";
            case "STONE": return "돌";
            case "COBBLESTONE": return "조약돌";
            case "SAND": return "모래";
            case "GRAVEL": return "자갈";
            case "CLAY": return "점토";
            case "TERRACOTTA": return "테라코타";
            case "REDSTONE_BLOCK": return "레드스톤 블록";
            case "REPEATER": return "중계기";
            case "COMPARATOR": return "비교기";
            case "PISTON": return "피스톤";
            case "STICKY_PISTON": return "끈적이 피스톤";
            case "OBSERVER": return "관찰자";
            case "DROPPER": return "공급기";
            case "DISPENSER": return "발사기";
            case "HOPPER": return "깔때기";
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