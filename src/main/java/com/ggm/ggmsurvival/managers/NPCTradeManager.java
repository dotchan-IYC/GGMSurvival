// 완전한 NPCTradeManager.java - NPC 교환 시스템 (이모티콘 제거)
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 완전한 NPC 교환 시스템 매니저
 * - 커스텀 NPC 생성 및 관리
 * - 교환 아이템 설정
 * - 교환 쿨다운 관리
 * - 교환 기록 저장
 * - 동적 가격 조정
 */
public class NPCTradeManager {

    private final GGMSurvival plugin;

    // NPC 데이터 저장
    private final Map<UUID, TradeNPC> activeNPCs = new ConcurrentHashMap<>();

    // 플레이어별 교환 쿨다운
    private final Map<UUID, Map<String, Long>> tradeCooldowns = new ConcurrentHashMap<>();

    // 플레이어별 일일 교환 횟수
    private final Map<UUID, Map<String, Integer>> dailyTradeCounts = new ConcurrentHashMap<>();

    // 교환 설정
    private final int tradeCooldownSeconds;
    private final int maxTradesPerDay;

    // 기본 교환 상점들
    private final Map<String, List<TradeRecipe>> defaultTrades = new HashMap<>();

    public NPCTradeManager(GGMSurvival plugin) {
        this.plugin = plugin;

        // 설정 로드
        this.tradeCooldownSeconds = plugin.getConfig().getInt("npc_trade_system.trade_cooldown", 300);
        this.maxTradesPerDay = plugin.getConfig().getInt("npc_trade_system.max_trades_per_day", 10);

        try {
            initializeTradeSystem();
            loadDefaultTrades();
            spawnDefaultNPCs();

            plugin.getLogger().info("=== NPC 교환 시스템 초기화 완료 ===");
            plugin.getLogger().info("교환 쿨다운: " + tradeCooldownSeconds + "초");
            plugin.getLogger().info("일일 최대 교환: " + maxTradesPerDay + "회");
            plugin.getLogger().info("기본 상점: " + defaultTrades.size() + "개");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "NPCTradeManager 초기화 실패", e);
            throw new RuntimeException("NPCTradeManager 초기화 실패", e);
        }
    }

    /**
     * 교환 시스템 초기화
     */
    private void initializeTradeSystem() {
        plugin.getLogger().info("NPC 교환 시스템 데이터베이스 연결 확인 완료");
    }

    /**
     * 기본 교환 설정 로드
     */
    private void loadDefaultTrades() {
        // 음식 상인
        List<TradeRecipe> foodTrades = new ArrayList<>();
        foodTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 1),
                new ItemStack(Material.COOKED_BEEF, 16),
                "익힌 소고기 16개"
        ));
        foodTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 2),
                new ItemStack(Material.GOLDEN_APPLE, 1),
                "황금 사과 1개"
        ));
        foodTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 5),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1),
                "마법이 부여된 황금 사과 1개"
        ));
        defaultTrades.put("food_merchant", foodTrades);

        // 도구 상인
        List<TradeRecipe> toolTrades = new ArrayList<>();
        toolTrades.add(new TradeRecipe(
                new ItemStack(Material.DIAMOND, 3),
                new ItemStack(Material.DIAMOND_PICKAXE, 1),
                "다이아몬드 곡괭이 1개"
        ));
        toolTrades.add(new TradeRecipe(
                new ItemStack(Material.IRON_INGOT, 10),
                new ItemStack(Material.IRON_SWORD, 1),
                "철 검 1개"
        ));
        toolTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 8),
                new ItemStack(Material.DIAMOND_AXE, 1),
                "다이아몬드 도끼 1개"
        ));
        defaultTrades.put("tool_merchant", toolTrades);

        // 블록 상인
        List<TradeRecipe> blockTrades = new ArrayList<>();
        blockTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 1),
                new ItemStack(Material.STONE, 64),
                "돌 64개"
        ));
        blockTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 2),
                new ItemStack(Material.OAK_LOG, 32),
                "참나무 원목 32개"
        ));
        blockTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 4),
                new ItemStack(Material.OBSIDIAN, 16),
                "흑요석 16개"
        ));
        defaultTrades.put("block_merchant", blockTrades);

        // 마법 상인
        List<TradeRecipe> magicTrades = new ArrayList<>();
        magicTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 10),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 16),
                "경험치 병 16개"
        ));
        magicTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 15),
                new ItemStack(Material.ENDER_PEARL, 8),
                "엔더 진주 8개"
        ));
        magicTrades.add(new TradeRecipe(
                new ItemStack(Material.EMERALD, 20),
                new ItemStack(Material.TOTEM_OF_UNDYING, 1),
                "불사의 토템 1개"
        ));
        defaultTrades.put("magic_merchant", magicTrades);

        plugin.getLogger().info("기본 교환 설정 로드 완료: " + defaultTrades.size() + "개 상점");
    }

    /**
     * 기본 NPC 스폰
     */
    private void spawnDefaultNPCs() {
        try {
            // 설정에서 스폰 위치 로드
            spawnMerchantNPC("food_merchant", "음식 상인", parseLocation("world:0:70:0"));
            spawnMerchantNPC("tool_merchant", "도구 상인", parseLocation("world:10:70:0"));
            spawnMerchantNPC("block_merchant", "블록 상인", parseLocation("world:20:70:0"));
            spawnMerchantNPC("magic_merchant", "마법 상인", parseLocation("world:30:70:0"));

            plugin.getLogger().info("기본 NPC " + activeNPCs.size() + "개 스폰 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "기본 NPC 스폰 실패", e);
        }
    }

    /**
     * 상인 NPC 스폰
     */
    public void spawnMerchantNPC(String npcId, String displayName, Location location) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("유효하지 않은 위치: " + npcId);
            return;
        }

        try {
            // 기존 NPC가 있다면 제거
            removeMerchantNPC(npcId);

            // 새 NPC 스폰
            Villager npc = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
            npc.setCustomName("§6[상인] §f" + displayName);
            npc.setCustomNameVisible(true);
            npc.setProfession(Villager.Profession.MERCHANT);
            npc.setVillagerLevel(5); // 최고 레벨
            npc.setAI(false); // AI 비활성화 (움직이지 않음)

            // 교환 설정
            setupNPCTrades(npc, npcId);

            // NPC 등록
            TradeNPC tradeNPC = new TradeNPC(npcId, displayName, npc, location);
            activeNPCs.put(npc.getUniqueId(), tradeNPC);

            plugin.getLogger().info("NPC 스폰 완료: " + displayName + " (" + npcId + ")");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "NPC 스폰 실패: " + npcId, e);
        }
    }

    /**
     * NPC 교환 설정
     */
    private void setupNPCTrades(Villager npc, String npcId) {
        List<MerchantRecipe> recipes = new ArrayList<>();
        List<TradeRecipe> trades = defaultTrades.get(npcId);

        if (trades != null) {
            for (TradeRecipe trade : trades) {
                MerchantRecipe recipe = new MerchantRecipe(trade.getResult(), 999); // 최대 사용 횟수
                recipe.addIngredient(trade.getInput());
                recipes.add(recipe);
            }
        }

        npc.setRecipes(recipes);
    }

    /**
     * NPC 제거
     */
    public void removeMerchantNPC(String npcId) {
        Iterator<Map.Entry<UUID, TradeNPC>> iterator = activeNPCs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, TradeNPC> entry = iterator.next();
            TradeNPC tradeNPC = entry.getValue();

            if (tradeNPC.getId().equals(npcId)) {
                if (tradeNPC.getVillager() != null && !tradeNPC.getVillager().isDead()) {
                    tradeNPC.getVillager().remove();
                }
                iterator.remove();
                plugin.getLogger().info("NPC 제거됨: " + npcId);
                break;
            }
        }
    }

    /**
     * 플레이어 교환 시도
     */
    public boolean attemptTrade(Player player, Villager npc, ItemStack input, ItemStack output) {
        TradeNPC tradeNPC = activeNPCs.get(npc.getUniqueId());
        if (tradeNPC == null) {
            return false;
        }

        try {
            // 쿨다운 확인
            if (isOnCooldown(player, tradeNPC.getId())) {
                long remainingTime = getCooldownRemaining(player, tradeNPC.getId());
                player.sendMessage("§c교환 쿨다운 중입니다. 남은 시간: " + formatTime(remainingTime));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }

            // 일일 교환 횟수 확인
            if (isMaxTradesReached(player, tradeNPC.getId())) {
                player.sendMessage("§c오늘의 교환 횟수를 모두 사용했습니다. (최대: " + maxTradesPerDay + "회)");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }

            // 교환 실행
            executeTradeTransaction(player, tradeNPC, input, output);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "교환 시도 중 오류: " + player.getName(), e);
            return false;
        }
    }

    /**
     * 교환 트랜잭션 실행
     */
    private void executeTradeTransaction(Player player, TradeNPC tradeNPC, ItemStack input, ItemStack output) {
        // 쿨다운 설정
        setCooldown(player, tradeNPC.getId());

        // 일일 교환 횟수 증가
        incrementDailyTradeCount(player, tradeNPC.getId());

        // 교환 기록 저장
        saveTradeRecord(player, tradeNPC.getId(), input, output);

        // 성공 메시지
        player.sendMessage("§a교환이 완료되었습니다!");
        player.sendMessage("§7받은 아이템: §f" + getItemDisplayName(output));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);

        // 성공 효과
        player.spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * 쿨다운 확인
     */
    private boolean isOnCooldown(Player player, String npcId) {
        Map<String, Long> playerCooldowns = tradeCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return false;
        }

        Long cooldownEnd = playerCooldowns.get(npcId);
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    /**
     * 쿨다운 남은 시간
     */
    private long getCooldownRemaining(Player player, String npcId) {
        Map<String, Long> playerCooldowns = tradeCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0;
        }

        Long cooldownEnd = playerCooldowns.get(npcId);
        if (cooldownEnd == null) {
            return 0;
        }

        return Math.max(0, cooldownEnd - System.currentTimeMillis());
    }

    /**
     * 쿨다운 설정
     */
    private void setCooldown(Player player, String npcId) {
        tradeCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(npcId, System.currentTimeMillis() + (tradeCooldownSeconds * 1000L));
    }

    /**
     * 일일 최대 교환 횟수 도달 확인
     */
    private boolean isMaxTradesReached(Player player, String npcId) {
        Map<String, Integer> playerTrades = dailyTradeCounts.get(player.getUniqueId());
        if (playerTrades == null) {
            return false;
        }

        Integer count = playerTrades.get(npcId);
        return count != null && count >= maxTradesPerDay;
    }

    /**
     * 일일 교환 횟수 증가
     */
    private void incrementDailyTradeCount(Player player, String npcId) {
        dailyTradeCounts.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .merge(npcId, 1, Integer::sum);
    }

    /**
     * 플레이어 교환 정보 조회
     */
    public void showPlayerTradeInfo(Player player) {
        player.sendMessage("§6==== 교환 정보 ====");

        Map<String, Long> playerCooldowns = tradeCooldowns.get(player.getUniqueId());
        Map<String, Integer> playerTrades = dailyTradeCounts.get(player.getUniqueId());

        boolean hasActiveCooldowns = false;

        for (TradeNPC tradeNPC : activeNPCs.values()) {
            String npcId = tradeNPC.getId();
            String displayName = tradeNPC.getDisplayName();

            // 쿨다운 정보
            if (playerCooldowns != null && isOnCooldown(player, npcId)) {
                long remaining = getCooldownRemaining(player, npcId);
                player.sendMessage("§c" + displayName + ": 쿨다운 " + formatTime(remaining));
                hasActiveCooldowns = true;
            } else {
                // 일일 교환 횟수
                int tradeCount = playerTrades != null ? playerTrades.getOrDefault(npcId, 0) : 0;
                player.sendMessage("§a" + displayName + ": " + tradeCount + "/" + maxTradesPerDay + "회");
            }
        }

        if (!hasActiveCooldowns) {
            player.sendMessage("§7모든 상인과 교환 가능합니다!");
        }

        player.sendMessage("§6================");
    }

    /**
     * 상인 목록 표시
     */
    public void showMerchantList(Player player) {
        player.sendMessage("§6===========================================");
        player.sendMessage("§e§l        상인 목록");
        player.sendMessage("");

        for (TradeNPC tradeNPC : activeNPCs.values()) {
            Location loc = tradeNPC.getLocation();
            String coords = String.format("§7(%d, %d, %d)",
                    (int) loc.getX(), (int) loc.getY(), (int) loc.getZ());

            player.sendMessage("§a" + tradeNPC.getDisplayName() + " " + coords);

            // 교환 아이템 미리보기
            List<TradeRecipe> trades = defaultTrades.get(tradeNPC.getId());
            if (trades != null && !trades.isEmpty()) {
                TradeRecipe firstTrade = trades.get(0);
                player.sendMessage("§7  예시: " + getItemDisplayName(firstTrade.getInput()) +
                        " → " + getItemDisplayName(firstTrade.getResult()));
            }
        }

        player.sendMessage("");
        player.sendMessage("§7상인 NPC와 상호작용하여 교환하세요!");
        player.sendMessage("§6===========================================");
    }

    /**
     * 교환 기록 저장
     */
    private void saveTradeRecord(Player player, String npcId, ItemStack input, ItemStack output) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO npc_trades (player_uuid, npc_id, trade_item, trade_amount) VALUES (?, ?, ?, ?)")) {

                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, npcId);
                statement.setString(3, output.getType().name());
                statement.setInt(4, output.getAmount());

                statement.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "교환 기록 저장 실패: " + player.getName(), e);
            }
        });
    }

    /**
     * 플레이어 교환 통계 조회
     */
    public CompletableFuture<TradeStats> getPlayerTradeStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) as total_trades, COUNT(DISTINCT npc_id) as unique_merchants, " +
                                 "MAX(trade_time) as last_trade FROM npc_trades WHERE player_uuid = ?")) {

                statement.setString(1, uuid.toString());

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return new TradeStats(
                                rs.getInt("total_trades"),
                                rs.getInt("unique_merchants"),
                                rs.getTimestamp("last_trade")
                        );
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "교환 통계 조회 실패: " + uuid, e);
            }

            return new TradeStats(0, 0, null);
        });
    }

    /**
     * 위치 파싱
     */
    private Location parseLocation(String locationString) {
        try {
            String[] parts = locationString.split(":");
            if (parts.length >= 4) {
                String worldName = parts[0];
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);

                org.bukkit.World world = plugin.getServer().getWorld(worldName);
                if (world != null) {
                    return new Location(world, x, y, z);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "위치 파싱 실패: " + locationString, e);
        }

        return null;
    }

    /**
     * 시간 포맷팅
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "분 " + seconds + "초";
        } else {
            return seconds + "초";
        }
    }

    /**
     * 아이템 표시 이름
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        String name = item.getType().name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1) + " x" + item.getAmount();
    }

    /**
     * 일일 데이터 초기화 (매일 자정)
     */
    public void resetDailyData() {
        dailyTradeCounts.clear();
        plugin.getLogger().info("일일 교환 데이터가 초기화되었습니다.");
    }

    /**
     * 캐시 정리
     */
    public void cleanupCache() {
        try {
            // 오프라인 플레이어 데이터 정리
            Set<UUID> onlineUUIDs = new HashSet<>();
            plugin.getServer().getOnlinePlayers().forEach(player -> onlineUUIDs.add(player.getUniqueId()));

            tradeCooldowns.entrySet().removeIf(entry -> !onlineUUIDs.contains(entry.getKey()));
            dailyTradeCounts.entrySet().removeIf(entry -> !onlineUUIDs.contains(entry.getKey()));

            plugin.getLogger().info("NPCTradeManager 캐시 정리 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 교환 시스템 캐시 정리 중 오류", e);
        }
    }

    /**
     * 시스템 종료
     */
    public void shutdown() {
        try {
            // 모든 NPC 제거
            for (TradeNPC tradeNPC : activeNPCs.values()) {
                if (tradeNPC.getVillager() != null && !tradeNPC.getVillager().isDead()) {
                    tradeNPC.getVillager().remove();
                }
            }

            activeNPCs.clear();
            tradeCooldowns.clear();
            dailyTradeCounts.clear();

            plugin.getLogger().info("NPCTradeManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPCTradeManager 종료 중 오류", e);
        }
    }

    // Getter 메서드들
    public Map<UUID, TradeNPC> getActiveNPCs() {
        return new HashMap<>(activeNPCs);
    }

    public int getTradeCooldownSeconds() {
        return tradeCooldownSeconds;
    }

    public int getMaxTradesPerDay() {
        return maxTradesPerDay;
    }

    /**
     * 교환 NPC 클래스
     */
    public static class TradeNPC {
        private final String id;
        private final String displayName;
        private final Villager villager;
        private final Location location;

        public TradeNPC(String id, String displayName, Villager villager, Location location) {
            this.id = id;
            this.displayName = displayName;
            this.villager = villager;
            this.location = location;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public Villager getVillager() { return villager; }
        public Location getLocation() { return location; }
    }

    /**
     * 교환 레시피 클래스
     */
    public static class TradeRecipe {
        private final ItemStack input;
        private final ItemStack result;
        private final String description;

        public TradeRecipe(ItemStack input, ItemStack result, String description) {
            this.input = input;
            this.result = result;
            this.description = description;
        }

        public ItemStack getInput() { return input; }
        public ItemStack getResult() { return result; }
        public String getDescription() { return description; }
    }

    /**
     * 교환 통계 클래스
     */
    public static class TradeStats {
        private final int totalTrades;
        private final int uniqueMerchants;
        private final java.sql.Timestamp lastTrade;

        public TradeStats(int totalTrades, int uniqueMerchants, java.sql.Timestamp lastTrade) {
            this.totalTrades = totalTrades;
            this.uniqueMerchants = uniqueMerchants;
            this.lastTrade = lastTrade;
        }

        public int getTotalTrades() { return totalTrades; }
        public int getUniqueMerchants() { return uniqueMerchants; }
        public java.sql.Timestamp getLastTrade() { return lastTrade; }
    }
}