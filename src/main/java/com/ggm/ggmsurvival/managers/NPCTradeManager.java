package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import com.ggm.ggmsurvival.GGMSurvival;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class NPCTradeManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    // NPC 위치 저장
    private final Map<UUID, NPCData> npcMap = new HashMap<>();

    // 교환 가격표
    private final Map<Material, Long> itemPrices = new HashMap<>();

    public NPCTradeManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();

        // 테이블 생성
        createNPCTable();

        // 아이템 가격 초기화
        initializeItemPrices();

        // 기존 NPC 로드
        loadExistingNPCs();
    }

    /**
     * NPC 테이블 생성
     */
    private void createNPCTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ggm_npc_traders (
                id INT AUTO_INCREMENT PRIMARY KEY,
                npc_uuid VARCHAR(36) NOT NULL UNIQUE,
                npc_name VARCHAR(50) NOT NULL,
                world_name VARCHAR(50) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                npc_type VARCHAR(20) NOT NULL DEFAULT 'ITEM_BUYER',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("NPC 교환 테이블이 준비되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("NPC 교환 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 아이템 가격 초기화
     */
    private void initializeItemPrices() {
        // 광물류
        itemPrices.put(Material.COAL, 10L);
        itemPrices.put(Material.IRON_INGOT, 50L);
        itemPrices.put(Material.GOLD_INGOT, 100L);
        itemPrices.put(Material.DIAMOND, 500L);
        itemPrices.put(Material.EMERALD, 300L);
        itemPrices.put(Material.NETHERITE_INGOT, 2000L);

        // 원석
        itemPrices.put(Material.COAL_ORE, 15L);
        itemPrices.put(Material.IRON_ORE, 75L);
        itemPrices.put(Material.GOLD_ORE, 150L);
        itemPrices.put(Material.DIAMOND_ORE, 750L);
        itemPrices.put(Material.EMERALD_ORE, 450L);
        itemPrices.put(Material.ANCIENT_DEBRIS, 3000L);

        // 심층 광석
        itemPrices.put(Material.DEEPSLATE_COAL_ORE, 20L);
        itemPrices.put(Material.DEEPSLATE_IRON_ORE, 100L);
        itemPrices.put(Material.DEEPSLATE_GOLD_ORE, 200L);
        itemPrices.put(Material.DEEPSLATE_DIAMOND_ORE, 1000L);
        itemPrices.put(Material.DEEPSLATE_EMERALD_ORE, 600L);

        // 음식류
        itemPrices.put(Material.WHEAT, 5L);
        itemPrices.put(Material.CARROT, 5L);
        itemPrices.put(Material.POTATO, 5L);
        itemPrices.put(Material.BEETROOT, 5L);
        itemPrices.put(Material.APPLE, 10L);
        itemPrices.put(Material.GOLDEN_APPLE, 500L);
        itemPrices.put(Material.ENCHANTED_GOLDEN_APPLE, 5000L);

        // 몹 드롭
        itemPrices.put(Material.ROTTEN_FLESH, 2L);
        itemPrices.put(Material.BONE, 5L);
        itemPrices.put(Material.STRING, 3L);
        itemPrices.put(Material.SPIDER_EYE, 10L);
        itemPrices.put(Material.GUNPOWDER, 15L);
        itemPrices.put(Material.ENDER_PEARL, 100L);
        itemPrices.put(Material.BLAZE_ROD, 50L);
        itemPrices.put(Material.GHAST_TEAR, 200L);

        // 나무류
        itemPrices.put(Material.OAK_LOG, 3L);
        itemPrices.put(Material.BIRCH_LOG, 3L);
        itemPrices.put(Material.SPRUCE_LOG, 3L);
        itemPrices.put(Material.JUNGLE_LOG, 4L);
        itemPrices.put(Material.ACACIA_LOG, 4L);
        itemPrices.put(Material.DARK_OAK_LOG, 4L);

        // 희귀 아이템
        itemPrices.put(Material.NETHER_STAR, 10000L);
        itemPrices.put(Material.DRAGON_EGG, 50000L);
        itemPrices.put(Material.ELYTRA, 25000L);
        itemPrices.put(Material.HEART_OF_THE_SEA, 5000L);
        itemPrices.put(Material.CONDUIT, 7500L);

        plugin.getLogger().info("아이템 가격표가 로드되었습니다. (총 " + itemPrices.size() + "개 아이템)");
    }

    /**
     * 기존 NPC 로드
     */
    private void loadExistingNPCs() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = "SELECT * FROM ggm_npc_traders";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        String npcUuid = rs.getString("npc_uuid");
                        String npcName = rs.getString("npc_name");
                        String worldName = rs.getString("world_name");
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        String npcType = rs.getString("npc_type");

                        // 메인 스레드에서 NPC 생성
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            createNPCAtLocation(worldName, x, y, z, npcName, npcType, UUID.fromString(npcUuid));
                        });
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("기존 NPC 로드 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 새 교환 NPC 생성
     */
    public boolean createTradeNPC(Location location, String npcName) {
        if (location.getWorld() == null) return false;

        // 빌리저 NPC 생성
        Villager npc = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        npc.setCustomName("§6§l[교환상] " + npcName);
        npc.setCustomNameVisible(true);
        npc.setAI(false); // AI 비활성화
        npc.setSilent(true);
        npc.setInvulnerable(true);
        npc.setProfession(Villager.Profession.LIBRARIAN);

        // NPC 데이터 저장
        NPCData npcData = new NPCData(npc.getUniqueId(), npcName, location, "ITEM_BUYER");
        npcMap.put(npc.getUniqueId(), npcData);

        // 데이터베이스에 저장
        saveNPCToDatabase(npcData);

        plugin.getLogger().info(String.format("교환 NPC 생성: %s (위치: %.1f, %.1f, %.1f)",
                npcName, location.getX(), location.getY(), location.getZ()));

        return true;
    }

    /**
     * 위치에 NPC 생성
     */
    private void createNPCAtLocation(String worldName, double x, double y, double z, String npcName, String npcType, UUID npcUuid) {
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("월드를 찾을 수 없습니다: " + worldName);
            return;
        }

        Location location = new Location(world, x, y, z);

        // 빌리저 NPC 생성
        Villager npc = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
        npc.setCustomName("§6§l[교환상] " + npcName);
        npc.setCustomNameVisible(true);
        npc.setAI(false);
        npc.setSilent(true);
        npc.setInvulnerable(true);
        npc.setProfession(Villager.Profession.LIBRARIAN);

        // UUID 설정이 불가능하므로 새 UUID로 매핑
        NPCData npcData = new NPCData(npc.getUniqueId(), npcName, location, npcType);
        npcMap.put(npc.getUniqueId(), npcData);

        plugin.getLogger().info("기존 NPC 복원: " + npcName);
    }

    /**
     * NPC와 상호작용 이벤트
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        Villager npc = (Villager) event.getRightClicked();

        // 우리가 만든 교환 NPC인지 확인
        if (!npcMap.containsKey(npc.getUniqueId())) return;

        event.setCancelled(true); // 기본 거래 GUI 차단

        Player player = event.getPlayer();
        NPCData npcData = npcMap.get(npc.getUniqueId());

        // 교환 GUI 열기
        openTradeGUI(player, npcData);
    }

    /**
     * 교환 GUI 열기
     */
    private void openTradeGUI(Player player, NPCData npcData) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6§l[교환상] " + npcData.getName());

        // 교환 안내 아이템
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§e§l아이템 교환 안내");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7아이템을 클릭하여 인벤토리의");
        infoLore.add("§7해당 아이템을 모두 판매합니다.");
        infoLore.add("");
        infoLore.add("§a좌클릭: 1개 판매");
        infoLore.add("§e우클릭: 스택 판매");
        infoLore.add("§6Shift+클릭: 모두 판매");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(4, infoItem);

        // 교환 가능한 아이템들 표시
        int slot = 9;
        for (Map.Entry<Material, Long> entry : itemPrices.entrySet()) {
            if (slot >= 45) break; // 인벤토리 공간 제한

            Material material = entry.getKey();
            long price = entry.getValue();

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§f" + getMaterialName(material));

            List<String> lore = new ArrayList<>();
            lore.add("§7판매 가격: §6" + formatMoney(price) + "G");
            lore.add("");
            lore.add("§a좌클릭: 1개 판매");
            lore.add("§e우클릭: 스택 판매");
            lore.add("§6Shift+클릭: 모두 판매");
            meta.setLore(lore);

            item.setItemMeta(meta);
            gui.setItem(slot, item);
            slot++;
        }

        // 닫기 버튼
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c닫기");
        closeItem.setItemMeta(closeMeta);
        gui.setItem(49, closeItem);

        player.openInventory(gui);
    }

    /**
     * GUI 클릭 이벤트
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getView().getTitle();
        if (!title.contains("[교환상]")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null) return;

        Material material = clickedItem.getType();

        // 닫기 버튼
        if (material == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // 안내 아이템
        if (material == Material.PAPER) return;

        // 판매 가능한 아이템인지 확인
        if (!itemPrices.containsKey(material)) return;

        // 클릭 타입에 따른 판매 처리
        if (event.isLeftClick()) {
            sellItems(player, material, 1); // 1개 판매
        } else if (event.isRightClick()) {
            sellItems(player, material, 64); // 스택 판매
        } else if (event.isShiftClick()) {
            sellAllItems(player, material); // 모두 판매
        }
    }

    /**
     * 아이템 판매 (개수 지정)
     */
    private void sellItems(Player player, Material material, int maxAmount) {
        long pricePerItem = itemPrices.get(material);

        // 플레이어 인벤토리에서 해당 아이템 찾기
        int totalCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                totalCount += item.getAmount();
            }
        }

        if (totalCount == 0) {
            player.sendMessage("§c판매할 " + getMaterialName(material) + "이(가) 없습니다!");
            return;
        }

        // 실제 판매할 개수
        int sellAmount = Math.min(totalCount, maxAmount);
        long totalPrice = sellAmount * pricePerItem;

        // 아이템 제거
        int remaining = sellAmount;
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == material) {
                int removeAmount = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - removeAmount);
                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(i, null);
                }
                remaining -= removeAmount;
            }
        }

        // G 지급
        economyManager.addMoney(player.getUniqueId(), totalPrice).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§a" + getMaterialName(material) + " §f" + sellAmount + "개§a를 §6" +
                            formatMoney(totalPrice) + "G§a에 판매했습니다!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                } else {
                    player.sendMessage("§c판매 중 오류가 발생했습니다!");
                }
            });
        });
    }

    /**
     * 아이템 모두 판매
     */
    private void sellAllItems(Player player, Material material) {
        long pricePerItem = itemPrices.get(material);

        // 플레이어 인벤토리에서 해당 아이템 모두 찾기
        int totalCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                totalCount += item.getAmount();
            }
        }

        if (totalCount == 0) {
            player.sendMessage("§c판매할 " + getMaterialName(material) + "이(가) 없습니다!");
            return;
        }

        // final 변수로 복사 (람다에서 사용하기 위해)
        final int finalTotalCount = totalCount;

        // 모든 아이템 제거
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == material) {
                player.getInventory().setItem(i, null);
            }
        }

        long totalPrice = finalTotalCount * pricePerItem;

        // G 지급
        economyManager.addMoney(player.getUniqueId(), totalPrice).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§a" + getMaterialName(material) + " §f" + finalTotalCount + "개§a를 모두 §6" +
                            formatMoney(totalPrice) + "G§a에 판매했습니다!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                } else {
                    player.sendMessage("§c판매 중 오류가 발생했습니다!");
                }
            });
        });
    }

    /**
     * NPC 데이터베이스 저장
     */
    private void saveNPCToDatabase(NPCData npcData) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    INSERT INTO ggm_npc_traders 
                    (npc_uuid, npc_name, world_name, x, y, z, npc_type) 
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, npcData.getUuid().toString());
                    stmt.setString(2, npcData.getName());
                    stmt.setString(3, npcData.getLocation().getWorld().getName());
                    stmt.setDouble(4, npcData.getLocation().getX());
                    stmt.setDouble(5, npcData.getLocation().getY());
                    stmt.setDouble(6, npcData.getLocation().getZ());
                    stmt.setString(7, npcData.getType());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("NPC 저장 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 아이템 이름 가져오기
     */
    private String getMaterialName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    /**
     * 금액 포맷팅
     */
    private String formatMoney(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * 아이템 가격 조회
     */
    public Long getItemPrice(Material material) {
        return itemPrices.get(material);
    }

    /**
     * NPC 데이터 클래스
     */
    public static class NPCData {
        private final UUID uuid;
        private final String name;
        private final Location location;
        private final String type;

        public NPCData(UUID uuid, String name, Location location, String type) {
            this.uuid = uuid;
            this.name = name;
            this.location = location;
            this.type = type;
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        public String getType() { return type; }
    }
}