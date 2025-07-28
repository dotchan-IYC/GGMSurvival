// === DragonRewardManager.java 수정 - getTitle() 의존성 완전 제거 ===

package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DragonRewardManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    // 드래곤 시스템 설정
    private final Map<UUID, Double> dragonDamage = new HashMap<>();
    private EnderDragon currentDragon = null;
    private final double DRAGON_MAX_HEALTH = 10000.0;
    private final double MAX_CONTRIBUTION = 100.0;
    private final long MAX_REWARD = 100000L;

    // GUI 관리 (타이틀 의존성 제거)
    private final Map<UUID, Inventory> openGuis = new HashMap<>();
    private final Map<UUID, GuiType> guiTypes = new HashMap<>(); // GUI 타입 추가 추적
    private BukkitRunnable guiUpdateTask;

    // GUI 타입 식별용 enum
    public enum GuiType {
        WELCOME,
        DRAGON_STATUS
    }

    // 특별한 식별자 아이템들 (getTitle() 대신 사용)
    private static final String WELCOME_GUI_IDENTIFIER = "§d§l🐉 엔더드래곤 처치 안내";
    private static final String STATUS_GUI_IDENTIFIER = "§c§l🐉 엔더드래곤";
    private static final String REFRESH_BUTTON_NAME = "§a§l🔄 새로고침";
    private static final String MY_RECORD_BUTTON_NAME = "§b§l📋 나의 오늘 기록";

    public DragonRewardManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();

        // 테이블 생성
        createDragonRewardTable();
        createDailyVisitorTable();

        // GUI 자동 업데이트 시작
        startGuiUpdateTask();

        // 서버 시작시 기존 드래곤 찾기
        findExistingDragon();

        plugin.getLogger().info("엔더드래곤 GUI 시스템 초기화 완료 (체력: " + DRAGON_MAX_HEALTH + ")");
    }

    /**
     * 기존 드래곤 찾기 (서버 재시작시)
     */
    private void findExistingDragon() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                    if (!dragon.isDead()) {
                        setCurrentDragon(dragon);
                        plugin.getLogger().info("기존 엔더드래곤을 찾았습니다!");
                        break;
                    }
                }
                break;
            }
        }
    }

    /**
     * 드래곤 보상 테이블 생성
     */
    private void createDragonRewardTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ggm_dragon_rewards (
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16) NOT NULL,
                reward_date DATE NOT NULL,
                contribution DOUBLE NOT NULL,
                reward_amount BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (uuid, reward_date),
                INDEX idx_reward_date (reward_date),
                INDEX idx_player (uuid),
                INDEX idx_contribution (contribution DESC)
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("드래곤 보상 테이블이 준비되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("드래곤 보상 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 일일 방문자 테이블 생성
     */
    private void createDailyVisitorTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ggm_daily_end_visitors (
                uuid VARCHAR(36) NOT NULL,
                visit_date DATE NOT NULL,
                first_visit_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (uuid, visit_date),
                INDEX idx_visit_date (visit_date)
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("일일 방문자 테이블이 준비되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("일일 방문자 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 엔드 월드 진입 이벤트
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();

        // 엔드 월드로 들어온 경우
        if (toWorld.getEnvironment() == World.Environment.THE_END) {
            // 침대폭발 방지 안내
            player.sendMessage("§c⚠ 엔드에서는 침대 사용이 금지되어 있습니다!");
            player.sendMessage("§7§l🐉 드래곤 GUI는 접속시 1회만 자동으로 표시됩니다.");  // 추가!
            player.sendMessage("§e이후에는 /dragon gui 명령어로 다시 열 수 있습니다.");  // 추가!
            player.sendActionBar("§d🐉 엔더드래곤 GUI를 준비하는 중...");

            // 1초 후 GUI 표시 (월드 로딩 완료 대기)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                checkDailyFirstVisit(player).thenAccept(isFirstVisit -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (isFirstVisit) {
                            showWelcomeGui(player);
                        } else {
                            showDragonStatusGui(player);
                        }
                    });
                });
            }, 20L);
        }
        // 엔드에서 나간 경우 GUI 정리
        else if (event.getFrom().getEnvironment() == World.Environment.THE_END) {
            closeGui(player);
        }
    }

    /**
     * 침대폭발 방지
     */
    @EventHandler
    public void onBedInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getPlayer().getWorld().getEnvironment() != World.Environment.THE_END) return;

        Material blockType = event.getClickedBlock().getType();
        if (blockType.name().contains("BED")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c엔드에서는 침대를 사용할 수 없습니다!");
            event.getPlayer().sendMessage("§7침대 폭발로 인한 피해를 방지하기 위함입니다.");
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }

    /**
     * 일일 최초 방문 확인
     */
    private CompletableFuture<Boolean> checkDailyFirstVisit(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 오늘 방문 기록 확인
                String checkSql = "SELECT COUNT(*) FROM ggm_daily_end_visitors WHERE uuid = ? AND visit_date = CURDATE()";
                try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                    stmt.setString(1, player.getUniqueId().toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            return false; // 이미 방문함
                        }
                    }
                }

                // 최초 방문 기록
                String insertSql = "INSERT INTO ggm_daily_end_visitors (uuid, visit_date) VALUES (?, CURDATE())";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, player.getUniqueId().toString());
                    stmt.executeUpdate();
                }

                plugin.getLogger().info(player.getName() + "님이 오늘 처음 엔드에 방문했습니다.");
                return true; // 최초 방문
            } catch (SQLException e) {
                plugin.getLogger().severe("일일 방문 확인 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 환영 GUI (최초 방문자용) - getTitle() 사용 안함
     */
    private void showWelcomeGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "Welcome Dragon GUI");

        // 환영 메시지 (식별자 역할)
        ItemStack welcomeItem = new ItemStack(Material.DRAGON_HEAD);
        ItemMeta welcomeMeta = welcomeItem.getItemMeta();
        welcomeMeta.setDisplayName(WELCOME_GUI_IDENTIFIER); // 식별자로 사용
        welcomeMeta.setLore(Arrays.asList(
                "§7엔더 차원에 오신 것을 환영합니다!",
                "",
                "§e§l드래곤 정보:",
                "§7• 체력: §c" + String.format("%.0f", DRAGON_MAX_HEALTH) + " HP",
                "§7• 최대 기여도: §6" + String.format("%.0f", MAX_CONTRIBUTION) + "%",
                "§7• 최대 보상: §6" + String.format("%,d", MAX_REWARD) + "G",
                "",
                "§a§l보상 계산:",
                "§7기여도에 정확히 비례하여 보상 지급",
                "§7• 기여도 100% = 100,000G",
                "§7• 기여도 50% = 50,000G",
                "§7• 기여도 25% = 25,000G",
                "§7• 기여도 1% = 1,000G (최소)",
                "",
                "§c§l주의사항:",
                "§7• 침대 사용 금지 (폭발 방지)",
                "§7• 하루 1회만 보상 가능",
                "§7• 최소 기여도 1% 이상 필요",
                "",
                "§e클릭하여 드래곤 상태 확인!"
        ));
        welcomeItem.setItemMeta(welcomeMeta);
        gui.setItem(13, welcomeItem);

        // 드래곤 상태 확인 버튼
        ItemStack statusItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta statusMeta = statusItem.getItemMeta();
        statusMeta.setDisplayName("§a§l드래곤 상태 확인");
        statusMeta.setLore(Arrays.asList(
                "§7현재 드래곤 상태와",
                "§7플레이어별 기여도를 확인합니다",
                "",
                "§a실시간 업데이트 지원!",
                "",
                "§e클릭하여 확인!"
        ));
        statusItem.setItemMeta(statusMeta);
        gui.setItem(11, statusItem);

        // 나의 오늘 기록
        ItemStack myRecordItem = new ItemStack(Material.BOOK);
        ItemMeta myRecordMeta = myRecordItem.getItemMeta();
        myRecordMeta.setDisplayName(MY_RECORD_BUTTON_NAME);
        myRecordMeta.setLore(Arrays.asList(
                "§7오늘의 드래곤 처치 기록을",
                "§7확인할 수 있습니다",
                "",
                "§7아직 기록이 없다면",
                "§7현재 기여도를 확인해보세요!",
                "",
                "§e클릭하여 확인!"
        ));
        myRecordItem.setItemMeta(myRecordMeta);
        gui.setItem(15, myRecordItem);

        // 장식 아이템들
        ItemStack glassPane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.setDisplayName("§7");
        glassPane.setItemMeta(glassMeta);

        int[] decorSlots = {0, 1, 2, 6, 7, 8, 9, 17, 18, 19, 20, 24, 25, 26};
        for (int slot : decorSlots) {
            gui.setItem(slot, glassPane);
        }

        // GUI 추적 정보 저장
        openGuis.put(player.getUniqueId(), gui);
        guiTypes.put(player.getUniqueId(), GuiType.WELCOME);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.5f, 1.0f);
    }

    /**
     * 드래곤 상태 GUI - getTitle() 사용 안함
     */
    public void showDragonStatusGui(Player player) {
        // 엔드에 있는지 확인
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
            player.sendMessage("§c드래곤 GUI는 엔드에서만 사용할 수 있습니다!");
            return;
        }

        // 기존 GUI가 열려있다면 닫기
        if (openGuis.containsKey(player.getUniqueId())) {
            player.closeInventory();
        }

        // GUI 생성 및 표시
        Inventory gui = Bukkit.createInventory(null, 54, "Dragon Status GUI");
        updateDragonStatusGui(gui, player);

        // GUI 추적 정보 저장
        openGuis.put(player.getUniqueId(), gui);
        guiTypes.put(player.getUniqueId(), GuiType.DRAGON_STATUS);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * 드래곤 상태 GUI 업데이트
     */
    private void updateDragonStatusGui(Inventory gui, Player viewer) {
        gui.clear();

        // 드래곤 상태 확인
        EnderDragon dragon = getCurrentDragon();
        boolean dragonExists = dragon != null && !dragon.isDead();

        if (dragonExists) {
            // 드래곤이 살아있는 경우
            double currentHealth = dragon.getHealth();
            double healthPercentage = (currentHealth / DRAGON_MAX_HEALTH) * 100;

            // 드래곤 정보 (식별자 역할)
            ItemStack dragonInfo = new ItemStack(Material.DRAGON_HEAD);
            ItemMeta dragonMeta = dragonInfo.getItemMeta();
            dragonMeta.setDisplayName(STATUS_GUI_IDENTIFIER + " (생존)"); // 식별자로 사용
            dragonMeta.setLore(Arrays.asList(
                    "§7현재 체력: §c" + String.format("%.1f", currentHealth) + " / " + String.format("%.0f", DRAGON_MAX_HEALTH),
                    "§7체력 비율: §e" + String.format("%.1f", healthPercentage) + "%",
                    "",
                    "§a참여 중인 플레이어: §f" + dragonDamage.size() + "명",
                    "§7총 입힌 피해: §f" + String.format("%.1f", getTotalDamageDealt()),
                    "",
                    "§e아래에서 플레이어별 기여도를 확인하세요!",
                    "§7(기여도 순으로 정렬됨)"
            ));
            dragonInfo.setItemMeta(dragonMeta);
            gui.setItem(4, dragonInfo);

            // 참여자 목록 (기여도 순으로 정렬)
            List<Map.Entry<UUID, Double>> sortedParticipants = dragonDamage.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                    .toList();

            int slot = 9;
            int rank = 1;
            for (int i = 0; i < Math.min(sortedParticipants.size(), 36); i++) {
                Map.Entry<UUID, Double> entry = sortedParticipants.get(i);
                UUID uuid = entry.getKey();
                double damage = entry.getValue();
                double contribution = Math.min((damage / DRAGON_MAX_HEALTH) * MAX_CONTRIBUTION, MAX_CONTRIBUTION);

                Player participant = Bukkit.getPlayer(uuid);
                String playerName = participant != null ? participant.getName() : "알 수 없음";

                // 순위별 아이템 타입 결정
                Material itemType = switch (rank) {
                    case 1 -> Material.GOLDEN_HELMET;
                    case 2 -> Material.IRON_HELMET;
                    case 3 -> Material.LEATHER_HELMET;
                    default -> Material.PLAYER_HEAD;
                };

                ItemStack playerItem = new ItemStack(itemType);
                ItemMeta playerMeta = playerItem.getItemMeta();

                String rankPrefix = switch (rank) {
                    case 1 -> "§6🥇 ";
                    case 2 -> "§7🥈 ";
                    case 3 -> "§c🥉 ";
                    default -> "§f" + rank + "위 ";
                };

                playerMeta.setDisplayName(rankPrefix + playerName);
                playerMeta.setLore(Arrays.asList(
                        "§7기여도: §6" + String.format("%.2f", contribution) + "%",
                        "§7예상 보상: §a" + String.format("%,d", calculateReward(contribution)) + "G",
                        "§7총 피해량: §f" + String.format("%.1f", damage),
                        "",
                        participant != null ? "§a● 온라인" : "§7● 오프라인",
                        "",
                        "§7실시간으로 업데이트됩니다!"
                ));
                playerItem.setItemMeta(playerMeta);
                gui.setItem(slot++, playerItem);
                rank++;
            }

        } else {
            // 드래곤이 죽었거나 없는 경우
            ItemStack dragonInfo = new ItemStack(Material.SKELETON_SKULL);
            ItemMeta dragonMeta = dragonInfo.getItemMeta();
            dragonMeta.setDisplayName(STATUS_GUI_IDENTIFIER + " (처치됨)"); // 식별자로 사용
            dragonMeta.setLore(Arrays.asList(
                    "§7드래곤이 처치되었습니다!",
                    "",
                    "§e새로운 드래곤은 내일 12시에 부활합니다.",
                    "§7또는 관리자가 수동으로 리셋할 수 있습니다.",
                    "",
                    "§a오늘의 참여자들에게 보상이 지급되었습니다!",
                    "",
                    "§7아래에서 오늘의 보상 기록을 확인하세요."
            ));
            dragonInfo.setItemMeta(dragonMeta);
            gui.setItem(4, dragonInfo);

            // 오늘의 보상 기록 표시
            showTodayRewards(gui);
        }

        // 새로고침 버튼
        ItemStack refreshItem = new ItemStack(Material.LIME_DYE);
        ItemMeta refreshMeta = refreshItem.getItemMeta();
        refreshMeta.setDisplayName(REFRESH_BUTTON_NAME); // 식별자로 사용
        refreshMeta.setLore(Arrays.asList(
                "§7클릭하여 최신 정보로 업데이트",
                "",
                "§a자동 업데이트: 매 3초마다",
                "§7수동 새로고침도 가능합니다!",
                "",
                "§e클릭!"
        ));
        refreshItem.setItemMeta(refreshMeta);
        gui.setItem(49, refreshItem);

        // 나의 기록 버튼
        ItemStack myRecordItem = new ItemStack(Material.BOOK);
        ItemMeta myRecordMeta = myRecordItem.getItemMeta();
        myRecordMeta.setDisplayName(MY_RECORD_BUTTON_NAME); // 식별자로 사용

        // 비동기로 나의 기록 정보 가져오기
        getTodayRewardInfo(viewer.getUniqueId()).thenAccept(info -> {
            List<String> lore = new ArrayList<>();
            if (info.hasReceived) {
                lore.addAll(Arrays.asList(
                        "§a✅ 오늘 드래곤 처치에 성공했습니다!",
                        "",
                        "§7기여도: §6" + String.format("%.2f", info.contribution) + "%",
                        "§7받은 보상: §a" + String.format("%,d", info.rewardAmount) + "G",
                        "§7처치 시간: §f" + (info.receivedAt != null ?
                                info.receivedAt.toString().substring(11, 19) : "알 수 없음"),
                        "",
                        "§e오늘은 더 이상 보상을 받을 수 없습니다.",
                        "§7내일 다시 도전해보세요!"
                ));
            } else {
                double currentDamage = dragonDamage.getOrDefault(viewer.getUniqueId(), 0.0);
                double currentContribution = Math.min((currentDamage / DRAGON_MAX_HEALTH) * MAX_CONTRIBUTION, MAX_CONTRIBUTION);

                lore.addAll(Arrays.asList(
                        "§7아직 드래곤을 처치하지 않았습니다.",
                        "",
                        "§7현재 기여도: §6" + String.format("%.2f", currentContribution) + "%",
                        "§7예상 보상: §a" + String.format("%,d", calculateReward(currentContribution)) + "G",
                        "§7총 피해량: §f" + String.format("%.1f", currentDamage),
                        "",
                        currentContribution >= 1.0 ? "§a✅ 최소 기여도 달성!" : "§c❌ 최소 기여도 1% 필요",
                        "",
                        "§e드래곤을 공격하여 기여도를 높이세요!"
                ));
            }
            myRecordMeta.setLore(lore);
            myRecordItem.setItemMeta(myRecordMeta);
        });
        gui.setItem(45, myRecordItem);

        // 안내 정보
        ItemStack infoItem = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§e§l📚 도움말");
        infoMeta.setLore(Arrays.asList(
                "§7🐉 §e엔더드래곤 시스템 안내",
                "",
                "§a▸ §7기여도는 드래곤에게 입힌 피해에 비례",
                "§a▸ §7보상은 기여도에 정확히 비례하여 지급",
                "§a▸ §7하루에 1번만 보상 받을 수 있음",
                "§a▸ §7최소 기여도 1% 이상 필요",
                "",
                "§c⚠ §7침대 사용 금지 (폭발 방지)"
        ));
        infoItem.setItemMeta(infoMeta);
        gui.setItem(53, infoItem);
    }

    /**
     * 오늘의 보상 기록 표시
     */
    private void showTodayRewards(Inventory gui) {
        CompletableFuture.supplyAsync(() -> {
            List<DragonRewardInfo> rewards = new ArrayList<>();
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    SELECT dr.uuid, dr.player_name, dr.contribution, dr.reward_amount, dr.created_at
                    FROM ggm_dragon_rewards dr
                    WHERE dr.reward_date = CURDATE()
                    ORDER BY dr.contribution DESC
                    LIMIT 36
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            rewards.add(new DragonRewardInfo(
                                    true,
                                    rs.getString("player_name"),
                                    rs.getDouble("contribution"),
                                    rs.getLong("reward_amount"),
                                    rs.getTimestamp("created_at")
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("오늘 보상 기록 조회 실패: " + e.getMessage());
            }
            return rewards;
        }).thenAccept(rewards -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int slot = 9;
                int rank = 1;
                for (DragonRewardInfo reward : rewards) {
                    Material itemType = switch (rank) {
                        case 1 -> Material.GOLD_INGOT;
                        case 2 -> Material.IRON_INGOT;
                        case 3 -> {
                            try {
                                yield Material.valueOf("COPPER_INGOT");
                            } catch (IllegalArgumentException e) {
                                yield Material.BRICK; // 1.16 이하 호환
                            }
                        }
                        default -> Material.EMERALD;
                    };

                    ItemStack rewardItem = new ItemStack(itemType);
                    ItemMeta rewardMeta = rewardItem.getItemMeta();

                    String rankPrefix = switch (rank) {
                        case 1 -> "§6🏆 ";
                        case 2 -> "§7🥈 ";
                        case 3 -> "§c🥉 ";
                        default -> "§a" + rank + "위 ";
                    };

                    rewardMeta.setDisplayName(rankPrefix + reward.playerName);
                    rewardMeta.setLore(Arrays.asList(
                            "§7기여도: §6" + String.format("%.2f", reward.contribution) + "%",
                            "§7받은 보상: §a" + String.format("%,d", reward.rewardAmount) + "G",
                            "§7처치 시간: §f" + reward.receivedAt.toString().substring(11, 19),
                            "",
                            "§a✅ 성공적으로 드래곤을 처치했습니다!"
                    ));
                    rewardItem.setItemMeta(rewardMeta);
                    gui.setItem(slot++, rewardItem);
                    rank++;

                    if (slot >= 45) break;
                }

                // 오늘 보상 받은 사람이 없는 경우
                if (rewards.isEmpty()) {
                    ItemStack noRewardItem = new ItemStack(Material.BARRIER);
                    ItemMeta noRewardMeta = noRewardItem.getItemMeta();
                    noRewardMeta.setDisplayName("§7오늘은 아직 처치 기록이 없습니다");
                    noRewardMeta.setLore(Arrays.asList(
                            "§7아직 오늘 드래곤을 처치한",
                            "§7플레이어가 없습니다.",
                            "",
                            "§e첫 번째 처치자가 되어보세요!"
                    ));
                    noRewardItem.setItemMeta(noRewardMeta);
                    gui.setItem(22, noRewardItem);
                }
            });
        });
    }

    /**
     * GUI 클릭 이벤트 - getTitle() 완전 제거, 아이템 이름으로만 식별
     */
    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInv = event.getClickedInventory();

        if (!openGuis.containsKey(player.getUniqueId())) return;
        if (clickedInv == null || !clickedInv.equals(openGuis.get(player.getUniqueId()))) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String itemName = clickedItem.getItemMeta().getDisplayName();
        GuiType currentGuiType = guiTypes.get(player.getUniqueId());

        // 공통 버튼들 처리
        if (itemName.equals(REFRESH_BUTTON_NAME)) {
            showDragonStatusGui(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.sendMessage("§a정보가 새로고침되었습니다!");
            return;
        }

        if (itemName.equals(MY_RECORD_BUTTON_NAME)) {
            showMyRecordDetail(player);
            return;
        }

        // GUI 타입별 특별 처리
        if (currentGuiType == GuiType.WELCOME) {
            // 환영 GUI에서의 클릭
            if (itemName.equals(WELCOME_GUI_IDENTIFIER) || itemName.contains("드래곤 상태 확인")) {
                showDragonStatusGui(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        } else if (currentGuiType == GuiType.DRAGON_STATUS) {
            // 드래곤 상태 GUI에서의 클릭 - 필요한 경우 추가 처리
            // 현재는 공통 버튼들로 충분함
        }
    }

    /**
     * 나의 상세 기록 표시
     */
    private void showMyRecordDetail(Player player) {
        getTodayRewardInfo(player.getUniqueId()).thenAccept(info -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§b§l📋 " + player.getName() + "님의 오늘 기록");
                player.sendMessage("");

                if (info.hasReceived) {
                    player.sendMessage("§a✅ 드래곤 처치 성공!");
                    player.sendMessage("§7• 기여도: §6" + String.format("%.2f", info.contribution) + "%");
                    player.sendMessage("§7• 받은 보상: §a" + String.format("%,d", info.rewardAmount) + "G");
                    player.sendMessage("§7• 처치 시간: §f" + info.receivedAt.toString().substring(11, 19));
                    player.sendMessage("");
                    player.sendMessage("§e오늘은 더 이상 보상을 받을 수 없습니다.");
                } else {
                    double currentDamage = dragonDamage.getOrDefault(player.getUniqueId(), 0.0);
                    double currentContribution = Math.min((currentDamage / DRAGON_MAX_HEALTH) * MAX_CONTRIBUTION, MAX_CONTRIBUTION);

                    if (currentDamage > 0) {
                        player.sendMessage("§e⚡ 현재 참여 중!");
                        player.sendMessage("§7• 현재 기여도: §6" + String.format("%.2f", currentContribution) + "%");
                        player.sendMessage("§7• 예상 보상: §a" + String.format("%,d", calculateReward(currentContribution)) + "G");
                        player.sendMessage("§7• 총 피해량: §f" + String.format("%.1f", currentDamage));
                        player.sendMessage("");
                        player.sendMessage(currentContribution >= 1.0 ? "§a✅ 최소 기여도 달성!" : "§c❌ 최소 기여도 1% 필요");
                    } else {
                        player.sendMessage("§7아직 드래곤을 공격하지 않았습니다.");
                        player.sendMessage("§e드래곤을 공격하여 기여도를 쌓아보세요!");
                    }
                }

                player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
            });
        });
    }

    /**
     * GUI 닫기 이벤트
     */
    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();

        // GUI 추적 정보만 정리 (자동으로 다시 열지 않음)
        if (openGuis.containsKey(uuid)) {
            openGuis.remove(uuid);
            guiTypes.remove(uuid);

            // GUI가 닫혔다는 것을 플레이어에게 알림
            player.sendActionBar("§7GUI를 닫았습니다. §e/dragon gui§7로 다시 열 수 있습니다.");
        }
    }

    /**
     * GUI 자동 업데이트 태스크 - getTitle() 사용 안함
     */
    private void startGuiUpdateTask() {
        guiUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Inventory> entry : new HashMap<>(openGuis).entrySet()) {
                    UUID uuid = entry.getKey();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player != null && player.isOnline() &&
                            player.getWorld().getEnvironment() == World.Environment.THE_END) {

                        GuiType guiType = guiTypes.get(uuid);
                        if (guiType == GuiType.DRAGON_STATUS) {
                            Inventory gui = entry.getValue();
                            updateDragonStatusGui(gui, player);
                        }
                    } else {
                        // 플레이어가 오프라인이거나 엔드에 없으면 GUI 제거
                        openGuis.remove(uuid);
                        guiTypes.remove(uuid);
                    }
                }
            }
        };
        guiUpdateTask.runTaskTimer(plugin, 60L, 60L); // 3초마다 업데이트
    }

    // 나머지 이벤트 핸들러들과 메서드들은 이전과 동일...
    // (onDragonSpawn, onDragonDamage, onDragonDeath, 보상 처리 등)

    /**
     * 엔더드래곤 스폰 이벤트
     */
    @EventHandler
    public void onDragonSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!plugin.isFeatureEnabled("dragon_reward")) return;

        EnderDragon dragon = (EnderDragon) event.getEntity();

        // 드래곤 체력 설정
        dragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(DRAGON_MAX_HEALTH);
        dragon.setHealth(DRAGON_MAX_HEALTH);

        setCurrentDragon(dragon);

        plugin.getLogger().info("새로운 엔더드래곤 생성! 체력: " + DRAGON_MAX_HEALTH);

        // 엔드에 있는 모든 플레이어에게 알림
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
                player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§d🐉 새로운 엔더드래곤이 나타났습니다!");
                player.sendMessage("§e체력: " + String.format("%.0f", DRAGON_MAX_HEALTH) + " HP");
                player.sendMessage("§a기여도를 쌓아 보상을 획득하세요!");
                player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }
    }

    /**
     * 드래곤 데미지 이벤트
     */
    @EventHandler
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!plugin.isFeatureEnabled("dragon_reward")) return;

        EnderDragon dragon = (EnderDragon) event.getEntity();
        Player player = null;
        double damage = event.getFinalDamage();

        // 플레이어 식별
        if (event.getDamager() instanceof Player) {
            player = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                player = (Player) projectile.getShooter();
            }
        }

        if (player != null) {
            // 데미지 기록
            recordDragonDamage(player.getUniqueId(), damage);

            // 기여도 계산
            double totalDamage = dragonDamage.get(player.getUniqueId());
            double contribution = Math.min((totalDamage / DRAGON_MAX_HEALTH) * MAX_CONTRIBUTION, MAX_CONTRIBUTION);

            // ActionBar로 실시간 피드백
            player.sendActionBar(String.format("§6🐉 기여도: §f%.2f%% §7(예상 보상: %,dG)",
                    contribution, calculateReward(contribution)));
        }
    }

    /**
     * 드래곤 사망 이벤트
     */
    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!plugin.isFeatureEnabled("dragon_reward")) return;

        plugin.getLogger().info("엔더드래곤 처치! 보상 지급을 시작합니다.");

        // 보상 처리
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::processAllRewards);

        // 엔드에 있는 모든 플레이어에게 알림
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
                player.sendMessage("§a🐉 엔더드래곤이 처치되었습니다!");

                // GUI 업데이트
                if (openGuis.containsKey(player.getUniqueId())) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        showDragonStatusGui(player);
                    }, 40L);
                }
            }
        }
    }

    /**
     * 보상 처리
     */
    private void processAllRewards() {
        int rewardCount = 0;
        for (Map.Entry<UUID, Double> entry : dragonDamage.entrySet()) {
            UUID uuid = entry.getKey();
            double totalDamage = entry.getValue();
            double contribution = Math.min((totalDamage / DRAGON_MAX_HEALTH) * MAX_CONTRIBUTION, MAX_CONTRIBUTION);

            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            // 최소 기여도 체크 (1% 이상)
            if (contribution >= 1.0) {
                processDragonReward(player, contribution);
                rewardCount++;
            }
        }

        plugin.getLogger().info("보상 지급 완료. 대상자: " + rewardCount + "명");

        // 데미지 기록 초기화
        dragonDamage.clear();
        currentDragon = null;
    }

    /**
     * 개별 플레이어 보상 처리
     */
    private void processDragonReward(Player player, double contribution) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // 오늘 이미 보상을 받았는지 확인
        hasReceivedTodayReward(uuid).thenAccept(hasReceived -> {
            if (hasReceived) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c오늘 이미 드래곤 보상을 받으셨습니다!");
                });
                return;
            }

            // 보상 계산
            long rewardAmount = calculateReward(contribution);

            // 보상 지급
            economyManager.addMoney(uuid, rewardAmount).thenAccept(success -> {
                if (success) {
                    // 보상 기록 저장
                    saveRewardRecord(uuid, playerName, contribution, rewardAmount).thenAccept(saveSuccess -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (saveSuccess) {
                                // 성공 메시지
                                player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                player.sendMessage("§d🐉 엔더드래곤 처치 보상!");
                                player.sendMessage("");
                                player.sendMessage("§7기여도: §6" + String.format("%.2f", contribution) + "%");
                                player.sendMessage("§7보상: §a+" + String.format("%,d", rewardAmount) + "G");
                                player.sendMessage("");
                                player.sendMessage("§a훌륭한 전투였습니다!");
                                player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.2f);
                            }
                        });
                    });
                }
            });
        });
    }

    /**
     * 보상 계산 (기여도 기반)
     */
    private long calculateReward(double contribution) {
        long reward = Math.round((contribution / MAX_CONTRIBUTION) * MAX_REWARD);
        if (contribution >= 1.0) {
            reward = Math.max(1000L, reward);
        }
        return Math.min(MAX_REWARD, reward);
    }

    // 데이터베이스 관련 메서드들...
    private CompletableFuture<Boolean> hasReceivedTodayReward(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = "SELECT COUNT(*) FROM ggm_dragon_rewards WHERE uuid = ? AND reward_date = CURDATE()";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1) > 0;
                        }
                    }
                }
                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("드래곤 보상 확인 실패: " + e.getMessage());
                return true;
            }
        });
    }

    private CompletableFuture<Boolean> saveRewardRecord(UUID uuid, String playerName, double contribution, long reward) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    INSERT INTO ggm_dragon_rewards (uuid, player_name, reward_date, contribution, reward_amount)
                    VALUES (?, ?, CURDATE(), ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setDouble(3, contribution);
                    stmt.setLong(4, reward);
                    return stmt.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("드래곤 보상 기록 저장 실패: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<DragonRewardInfo> getTodayRewardInfo(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    SELECT player_name, contribution, reward_amount, created_at
                    FROM ggm_dragon_rewards 
                    WHERE uuid = ? AND reward_date = CURDATE()
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return new DragonRewardInfo(
                                    true,
                                    rs.getString("player_name"),
                                    rs.getDouble("contribution"),
                                    rs.getLong("reward_amount"),
                                    rs.getTimestamp("created_at")
                            );
                        }
                    }
                }
                return new DragonRewardInfo(false, null, 0.0, 0L, null);
            } catch (SQLException e) {
                plugin.getLogger().severe("드래곤 보상 정보 조회 실패: " + e.getMessage());
                return new DragonRewardInfo(false, null, 0.0, 0L, null);
            }
        });
    }

    // 유틸리티 메서드들
    public void recordDragonDamage(UUID uuid, double damage) {
        dragonDamage.merge(uuid, damage, Double::sum);
    }

    public void setCurrentDragon(EnderDragon dragon) {
        this.currentDragon = dragon;
        dragonDamage.clear();
    }

    public EnderDragon getCurrentDragon() {
        return currentDragon;
    }

    public void closeGui(Player player) {
        openGuis.remove(player.getUniqueId());
        guiTypes.remove(player.getUniqueId());
    }

    public int getParticipantCount() {
        return dragonDamage.size();
    }

    public double getTotalDamageDealt() {
        return dragonDamage.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getCurrentPlayerContribution(UUID uuid) {
        double damage = dragonDamage.getOrDefault(uuid, 0.0);
        return Math.min((damage / DRAGON_MAX_HEALTH) * MAX_CONTRIBUTION, MAX_CONTRIBUTION);
    }

    public boolean isDragonAlive() {
        return currentDragon != null && !currentDragon.isDead();
    }

    public double getDragonHealthPercentage() {
        if (currentDragon == null || currentDragon.isDead()) {
            return 0.0;
        }
        return (currentDragon.getHealth() / DRAGON_MAX_HEALTH) * 100.0;
    }

    public void shutdown() {
        if (guiUpdateTask != null) {
            guiUpdateTask.cancel();
        }
        openGuis.clear();
        guiTypes.clear();
        plugin.getLogger().info("드래곤 보상 시스템 종료");
    }

    public static class DragonRewardInfo {
        public final boolean hasReceived;
        public final String playerName;
        public final double contribution;
        public final long rewardAmount;
        public final java.sql.Timestamp receivedAt;

        public DragonRewardInfo(boolean hasReceived, String playerName, double contribution, long rewardAmount, java.sql.Timestamp receivedAt) {
            this.hasReceived = hasReceived;
            this.playerName = playerName;
            this.contribution = contribution;
            this.rewardAmount = rewardAmount;
            this.receivedAt = receivedAt;
        }
    }
}