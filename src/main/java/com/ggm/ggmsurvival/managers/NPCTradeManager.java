// 완전 안정화된 NPCTradeManager.java
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 완전 안정화된 NPC 교환 시스템 매니저
 * - NPC 생성 및 관리
 * - 아이템 교환 처리
 * - Thread-Safe 구현
 * - 데이터베이스 연동
 */
public class NPCTradeManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    // NPC 관리
    private final ConcurrentHashMap<UUID, NPCData> managedNPCs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TradeOffer> tradeOffers = new ConcurrentHashMap<>();

    // 네임스페이스 키들
    private final NamespacedKey npcTypeKey;
    private final NamespacedKey npcIdKey;

    // 설정값들
    private final int maxNPCs;
    private final boolean npcProtection;
    private final boolean npcAIDisabled;

    // NPC 타입 정의
    public enum NPCType {
        ITEM_TRADER("아이템 상인", "아이템을 사고팝니다"),
        RESOURCE_TRADER("자원 상인", "자원을 교환합니다"),
        SPECIAL_TRADER("특수 상인", "특별한 아이템을 거래합니다");

        private final String displayName;
        private final String description;

        NPCType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public NPCTradeManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();

        try {
            // 네임스페이스 키 초기화
            this.npcTypeKey = new NamespacedKey(plugin, "npc_type");
            this.npcIdKey = new NamespacedKey(plugin, "npc_id");

            // 설정값 로드
            this.maxNPCs = plugin.getConfig().getInt("npc_system.max_npcs", 50);
            this.npcProtection = plugin.getConfig().getBoolean("npc_system.npc_protection", true);
            this.npcAIDisabled = plugin.getConfig().getBoolean("npc_system.npc_ai_disabled", true);

            // 데이터베이스 테이블 생성
            createNPCTables();

            // 기본 교환 오퍼 초기화
            initializeDefaultTrades();

            // 저장된 NPC 로드
            loadNPCsFromDatabase();

            plugin.getLogger().info("NPCTradeManager 안정화 초기화 완료");
            plugin.getLogger().info("최대 NPC 수: " + maxNPCs + "개");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "NPCTradeManager 초기화 실패", e);
            throw new RuntimeException("NPCTradeManager 초기화 실패", e);
        }
    }

    /**
     * NPC 관련 테이블 생성
     */
    private void createNPCTables() {
        try (Connection connection = databaseManager.getConnection()) {

            String npcDataSQL = """
                CREATE TABLE IF NOT EXISTS npc_data (
                    id VARCHAR(36) PRIMARY KEY,
                    npc_type VARCHAR(50) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    world VARCHAR(50) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT DEFAULT 0,
                    pitch FLOAT DEFAULT 0,
                    created_by VARCHAR(36) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_world (world),
                    INDEX idx_npc_type (npc_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            String tradeLogSQL = """
                CREATE TABLE IF NOT EXISTS npc_trade_log (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    npc_id VARCHAR(36) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    trade_type VARCHAR(50) NOT NULL,
                    item_given VARCHAR(100) NOT NULL,
                    item_received VARCHAR(100) NOT NULL,
                    trade_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_npc_id (npc_id),
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_trade_time (trade_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            try (PreparedStatement stmt1 = connection.prepareStatement(npcDataSQL);
                 PreparedStatement stmt2 = connection.prepareStatement(tradeLogSQL)) {

                stmt1.executeUpdate();
                stmt2.executeUpdate();
            }

            plugin.getLogger().info("NPC 데이터베이스 테이블 생성 완료");

        } catch (SQLException e) {
            throw new RuntimeException("NPC 테이블 생성 실패", e);
        }
    }

    /**
     * 기본 교환 오퍼 초기화
     */
    private void initializeDefaultTrades() {
        try {
            // 아이템 상인 교환
            addTradeOffer("item_trader_1", new TradeOffer(
                    "다이아몬드 → 에메랄드",
                    new ItemStack(Material.DIAMOND, 2),
                    new ItemStack(Material.EMERALD, 1),
                    0, NPCType.ITEM_TRADER
            ));

            addTradeOffer("item_trader_2", new TradeOffer(
                    "철괴 → 금괴",
                    new ItemStack(Material.IRON_INGOT, 4),
                    new ItemStack(Material.GOLD_INGOT, 1),
                    0, NPCType.ITEM_TRADER
            ));

            // 자원 상인 교환
            addTradeOffer("resource_trader_1", new TradeOffer(
                    "원목 구매",
                    null, null, 100,
                    NPCType.RESOURCE_TRADER
            ));

            addTradeOffer("resource_trader_2", new TradeOffer(
                    "조약돌 판매",
                    new ItemStack(Material.COBBLESTONE, 64),
                    null, 500,
                    NPCType.RESOURCE_TRADER
            ));

            // 특수 상인 교환
            addTradeOffer("special_trader_1", new TradeOffer(
                    "경험치 병 구매",
                    null,
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 10),
                    5000, NPCType.SPECIAL_TRADER
            ));

            plugin.getLogger().info("기본 교환 오퍼 " + tradeOffers.size() + "개 로드됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "기본 교환 오퍼 초기화 중 오류", e);
        }
    }

    /**
     * 데이터베이스에서 NPC 로드
     */
    private void loadNPCsFromDatabase() {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT * FROM npc_data");
                 ResultSet rs = stmt.executeQuery()) {

                int loadedCount = 0;

                while (rs.next()) {
                    try {
                        String npcId = rs.getString("id");
                        NPCType npcType = NPCType.valueOf(rs.getString("npc_type"));
                        String name = rs.getString("name");
                        String worldName = rs.getString("world");
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        float yaw = rs.getFloat("yaw");
                        float pitch = rs.getFloat("pitch");
                        UUID createdBy = UUID.fromString(rs.getString("created_by"));

                        // 메인 스레드에서 NPC 생성
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                Location location = new Location(
                                        Bukkit.getWorld(worldName), x, y, z, yaw, pitch);

                                if (location.getWorld() != null) {
                                    createNPCAtLocation(npcId, npcType, name, location, createdBy);
                                }
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.WARNING,
                                        "NPC 로드 실패: " + npcId, e);
                            }
                        });

                        loadedCount++;

                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "NPC 데이터 파싱 실패", e);
                    }
                }

                final int finalCount = loadedCount;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().info("데이터베이스에서 " + finalCount + "개 NPC 로드 완료");
                });

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "NPC 로드 실패", e);
            }
        });
    }

    /**
     * NPC 생성
     */
    public boolean createNPC(NPCType type, String name, Location location, Player creator) {
        try {
            if (managedNPCs.size() >= maxNPCs) {
                creator.sendMessage("§c최대 NPC 수(" + maxNPCs + "개)에 도달했습니다.");
                return false;
            }

            String npcId = UUID.randomUUID().toString();

            // NPC 엔티티 생성
            if (createNPCAtLocation(npcId, type, name, location, creator.getUniqueId())) {

                // 데이터베이스에 저장
                saveNPCToDatabase(npcId, type, name, location, creator.getUniqueId());

                creator.sendMessage("§a" + type.getDisplayName() + " '" + name + "'이(가) 생성되었습니다!");

                plugin.getLogger().info(String.format("[NPC생성] %s이(가) %s를 생성했습니다. (위치: %s)",
                        creator.getName(), name, locationToString(location)));

                return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "NPC 생성 실패: " + creator.getName(), e);
            creator.sendMessage("§cNPC 생성 중 오류가 발생했습니다.");
        }

        return false;
    }

    /**
     * 위치에 NPC 생성
     */
    private boolean createNPCAtLocation(String npcId, NPCType type, String name,
                                        Location location, UUID createdBy) {
        try {
            // 주민 스폰
            Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);

            // NPC 설정
            villager.setCustomName("§e" + name);
            villager.setCustomNameVisible(true);
            villager.setInvulnerable(npcProtection);
            villager.setRemoveWhenFarAway(false);

            // AI 비활성화 (설정에 따라)
            if (npcAIDisabled) {
                villager.setAI(false);
            }

            // 직업 설정
            villager.setProfession(getProfessionForType(type));
            villager.setVillagerLevel(5); // 마스터 레벨

            // PersistentData 설정
            villager.getPersistentDataContainer().set(npcTypeKey, PersistentDataType.STRING, type.name());
            villager.getPersistentDataContainer().set(npcIdKey, PersistentDataType.STRING, npcId);

            // 관리 목록에 추가
            NPCData npcData = new NPCData(npcId, type, name, location, createdBy, villager);
            managedNPCs.put(villager.getUniqueId(), npcData);

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 엔티티 생성 실패", e);
            return false;
        }
    }

    /**
     * NPC 타입에 따른 직업 반환
     */
    private Villager.Profession getProfessionForType(NPCType type) {
        switch (type) {
            case ITEM_TRADER:
                return Villager.Profession.TOOLSMITH;
            case RESOURCE_TRADER:
                return Villager.Profession.FARMER;
            case SPECIAL_TRADER:
                return Villager.Profession.LIBRARIAN;
            default:
                return Villager.Profession.NITWIT;
        }
    }

    /**
     * 데이터베이스에 NPC 저장
     */
    private void saveNPCToDatabase(String npcId, NPCType type, String name,
                                   Location location, UUID createdBy) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "INSERT INTO npc_data (id, npc_type, name, world, x, y, z, yaw, pitch, created_by) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                stmt.setString(1, npcId);
                stmt.setString(2, type.name());
                stmt.setString(3, name);
                stmt.setString(4, location.getWorld().getName());
                stmt.setDouble(5, location.getX());
                stmt.setDouble(6, location.getY());
                stmt.setDouble(7, location.getZ());
                stmt.setFloat(8, location.getYaw());
                stmt.setFloat(9, location.getPitch());
                stmt.setString(10, createdBy.toString());

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "NPC 데이터 저장 실패: " + npcId, e);
            }
        });
    }

    /**
     * NPC 제거
     */
    public boolean removeNPC(UUID npcEntityId, Player remover) {
        try {
            NPCData npcData = managedNPCs.get(npcEntityId);
            if (npcData == null) {
                remover.sendMessage("§c관리되지 않는 NPC입니다.");
                return false;
            }

            // 권한 확인 (생성자 또는 관리자)
            if (!remover.hasPermission("ggm.npc.admin") &&
                    !npcData.createdBy.equals(remover.getUniqueId())) {
                remover.sendMessage("§c이 NPC를 제거할 권한이 없습니다.");
                return false;
            }

            // 엔티티 제거
            if (npcData.entity != null && npcData.entity.isValid()) {
                npcData.entity.remove();
            }

            // 관리 목록에서 제거
            managedNPCs.remove(npcEntityId);

            // 데이터베이스에서 제거
            removeNPCFromDatabase(npcData.npcId);

            remover.sendMessage("§aNPC '" + npcData.name + "'이(가) 제거되었습니다.");

            plugin.getLogger().info(String.format("[NPC제거] %s이(가) %s를 제거했습니다.",
                    remover.getName(), npcData.name));

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "NPC 제거 실패: " + remover.getName(), e);
            remover.sendMessage("§cNPC 제거 중 오류가 발생했습니다.");
            return false;
        }
    }

    /**
     * 데이터베이스에서 NPC 제거
     */
    private void removeNPCFromDatabase(String npcId) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "DELETE FROM npc_data WHERE id = ?")) {

                stmt.setString(1, npcId);
                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "NPC 데이터 삭제 실패: " + npcId, e);
            }
        });
    }

    /**
     * NPC 상호작용 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onNPCInteract(PlayerInteractEntityEvent event) {
        if (plugin.isShuttingDown()) return;
        if (!(event.getRightClicked() instanceof Villager villager)) return;

        try {
            // 관리되는 NPC인지 확인
            NPCData npcData = managedNPCs.get(villager.getUniqueId());
            if (npcData == null) return;

            // 기본 상호작용 취소
            event.setCancelled(true);

            Player player = event.getPlayer();

            // 교환 메뉴 열기
            openTradeMenu(player, npcData);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "NPC 상호작용 처리 중 오류: " + event.getPlayer().getName(), e);
        }
    }

    /**
     * NPC 피해 이벤트 (보호)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNPCDamage(EntityDamageEvent event) {
        if (!npcProtection) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        try {
            // 관리되는 NPC인지 확인
            if (managedNPCs.containsKey(villager.getUniqueId())) {
                event.setCancelled(true);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 보호 처리 중 오류", e);
        }
    }

    /**
     * 교환 메뉴 열기
     */
    private void openTradeMenu(Player player, NPCData npcData) {
        try {
            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l" + npcData.name + " §7(" + npcData.type.getDisplayName() + ")");
            player.sendMessage("§7" + npcData.type.getDescription());
            player.sendMessage("");
            player.sendMessage("§a사용 가능한 교환:");

            // 해당 타입의 교환 오퍼 표시
            int offerCount = 0;
            for (Map.Entry<String, TradeOffer> entry : tradeOffers.entrySet()) {
                TradeOffer offer = entry.getValue();
                if (offer.npcType == npcData.type) {
                    player.sendMessage("§7• " + offer.displayName);
                    offerCount++;
                }
            }

            if (offerCount == 0) {
                player.sendMessage("§c현재 사용 가능한 교환이 없습니다.");
            } else {
                player.sendMessage("");
                player.sendMessage("§e/trade §7명령어를 사용하여 교환하세요!");
            }

            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "교환 메뉴 열기 실패: " + player.getName(), e);
            player.sendMessage("§c교환 메뉴를 열 수 없습니다.");
        }
    }

    /**
     * 교환 실행
     */
    public boolean executeTrade(Player player, String tradeId) {
        try {
            TradeOffer offer = tradeOffers.get(tradeId);
            if (offer == null) {
                player.sendMessage("§c존재하지 않는 교환입니다: " + tradeId);
                return false;
            }

            // 교환 조건 확인
            if (offer.requiredItem != null) {
                if (!hasRequiredItem(player, offer.requiredItem)) {
                    player.sendMessage("§c필요한 아이템이 부족합니다: " +
                            getItemDisplayName(offer.requiredItem));
                    return false;
                }
            }

            if (offer.requiredMoney > 0) {
                CompletableFuture<Boolean> hasMoneyFuture = economyManager.hasEnoughMoney(
                        player.getUniqueId(), offer.requiredMoney);

                if (!hasMoneyFuture.join()) {
                    player.sendMessage("§c돈이 부족합니다: " +
                            economyManager.formatMoney(offer.requiredMoney) + "G");
                    return false;
                }
            }

            // 교환 실행
            boolean success = performTrade(player, offer);

            if (success) {
                // 교환 로그 기록
                logTrade(player, offer, tradeId);

                player.sendMessage("§a교환이 완료되었습니다: " + offer.displayName);
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            }

            return success;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "교환 실행 중 오류: " + player.getName(), e);
            player.sendMessage("§c교환 중 오류가 발생했습니다.");
            return false;
        }
    }

    /**
     * 실제 교환 수행
     */
    private boolean performTrade(Player player, TradeOffer offer) {
        try {
            // 아이템 차감
            if (offer.requiredItem != null) {
                player.getInventory().removeItem(offer.requiredItem);
            }

            // 돈 차감
            if (offer.requiredMoney > 0) {
                economyManager.removeMoney(player.getUniqueId(), offer.requiredMoney);
            }

            // 아이템 지급
            if (offer.rewardItem != null) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(offer.rewardItem);

                // 인벤토리가 가득 찬 경우 바닥에 드롭
                for (ItemStack item : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }

            // 돈 지급
            if (offer.rewardMoney > 0) {
                economyManager.addMoney(player.getUniqueId(), offer.rewardMoney);
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "교환 수행 중 오류", e);
            return false;
        }
    }

    /**
     * 필요 아이템 확인
     */
    private boolean hasRequiredItem(Player player, ItemStack required) {
        return player.getInventory().containsAtLeast(required, required.getAmount());
    }

    /**
     * 교환 로그 기록
     */
    private void logTrade(Player player, TradeOffer offer, String tradeId) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "INSERT INTO npc_trade_log (npc_id, player_uuid, player_name, trade_type, " +
                                 "item_given, item_received) VALUES (?, ?, ?, ?, ?, ?)")) {

                stmt.setString(1, tradeId);
                stmt.setString(2, player.getUniqueId().toString());
                stmt.setString(3, player.getName());
                stmt.setString(4, offer.npcType.name());
                stmt.setString(5, offer.requiredItem != null ?
                        getItemDisplayName(offer.requiredItem) : offer.requiredMoney + "G");
                stmt.setString(6, offer.rewardItem != null ?
                        getItemDisplayName(offer.rewardItem) : offer.rewardMoney + "G");

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "교환 로그 기록 실패", e);
            }
        });
    }

    /**
     * 교환 오퍼 추가
     */
    public void addTradeOffer(String id, TradeOffer offer) {
        tradeOffers.put(id, offer);
    }

    /**
     * 사용 가능한 교환 목록 표시
     */
    public void showAvailableTrades(Player player, NPCType type) {
        try {
            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l" + type.getDisplayName() + " 교환 목록");
            player.sendMessage("");

            int count = 0;
            for (Map.Entry<String, TradeOffer> entry : tradeOffers.entrySet()) {
                TradeOffer offer = entry.getValue();
                if (offer.npcType == type) {
                    player.sendMessage("§a" + (++count) + ". " + offer.displayName);

                    if (offer.requiredItem != null) {
                        player.sendMessage("§7   필요: " + getItemDisplayName(offer.requiredItem));
                    }
                    if (offer.requiredMoney > 0) {
                        player.sendMessage("§7   비용: " + economyManager.formatMoney(offer.requiredMoney) + "G");
                    }

                    if (offer.rewardItem != null) {
                        player.sendMessage("§7   보상: " + getItemDisplayName(offer.rewardItem));
                    }
                    if (offer.rewardMoney > 0) {
                        player.sendMessage("§7   보상: " + economyManager.formatMoney(offer.rewardMoney) + "G");
                    }

                    player.sendMessage("");
                }
            }

            if (count == 0) {
                player.sendMessage("§c사용 가능한 교환이 없습니다.");
            }

            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "교환 목록 표시 실패: " + player.getName(), e);
        }
    }

    /**
     * NPC 목록 표시
     */
    public void showNPCList(Player player) {
        try {
            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l현재 활성화된 NPC 목록");
            player.sendMessage("");

            if (managedNPCs.isEmpty()) {
                player.sendMessage("§c현재 활성화된 NPC가 없습니다.");
            } else {
                int count = 0;
                for (NPCData npcData : managedNPCs.values()) {
                    player.sendMessage(String.format("§a%d. %s §7(%s)",
                            ++count, npcData.name, npcData.type.getDisplayName()));
                    player.sendMessage("§7   위치: " + locationToString(npcData.location));
                }

                player.sendMessage("");
                player.sendMessage("§7총 " + count + "개의 NPC가 활성화되어 있습니다.");
            }

            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "NPC 목록 표시 실패: " + player.getName(), e);
        }
    }

    /**
     * 아이템 표시 이름 반환
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name() + " x" + item.getAmount();
    }

    /**
     * 위치를 문자열로 변환
     */
    private String locationToString(Location location) {
        return String.format("%s (%.0f, %.0f, %.0f)",
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
    }

    /**
     * NPC 통계 정보
     */
    public String getNPCStats() {
        return String.format("활성 NPC: %d개 | 최대: %d개 | 교환 종류: %d개",
                managedNPCs.size(), maxNPCs, tradeOffers.size());
    }

    /**
     * 매니저 종료
     */
    public void onDisable() {
        try {
            // 모든 NPC 제거 (필요한 경우)
            for (NPCData npcData : managedNPCs.values()) {
                if (npcData.entity != null && npcData.entity.isValid()) {
                    // 일반적으로 서버 종료 시에는 엔티티를 제거하지 않음
                    // npcData.entity.remove();
                }
            }

            // 캐시 정리
            managedNPCs.clear();
            tradeOffers.clear();

            plugin.getLogger().info("NPCTradeManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPCTradeManager 종료 중 오류", e);
        }
    }

    // 데이터 클래스들
    public static class NPCData {
        public final String npcId;
        public final NPCType type;
        public final String name;
        public final Location location;
        public final UUID createdBy;
        public final Villager entity;

        public NPCData(String npcId, NPCType type, String name, Location location,
                       UUID createdBy, Villager entity) {
            this.npcId = npcId;
            this.type = type;
            this.name = name;
            this.location = location;
            this.createdBy = createdBy;
            this.entity = entity;
        }
    }

    public static class TradeOffer {
        public final String displayName;
        public final ItemStack requiredItem;
        public final ItemStack rewardItem;
        public final long requiredMoney;
        public final long rewardMoney;
        public final NPCType npcType;

        public TradeOffer(String displayName, ItemStack requiredItem, ItemStack rewardItem,
                          long money, NPCType npcType) {
            this.displayName = displayName;
            this.requiredItem = requiredItem;
            this.rewardItem = rewardItem;

            if (money > 0) {
                this.requiredMoney = 0;
                this.rewardMoney = money;
            } else {
                this.requiredMoney = Math.abs(money);
                this.rewardMoney = 0;
            }

            this.npcType = npcType;
        }
    }

    // Getter 메서드들
    public int getMaxNPCs() {
        return maxNPCs;
    }

    public int getCurrentNPCCount() {
        return managedNPCs.size();
    }

    public Set<NPCType> getAvailableTypes() {
        return EnumSet.allOf(NPCType.class);
    }
}