package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import com.ggm.ggmsurvival.GGMSurvival;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DragonRewardManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    // 보상 설정
    private final long DRAGON_REWARD = 100000L; // 100,000G
    private final int DAMAGE_THRESHOLD = 1000; // 최소 1000 피해량

    public DragonRewardManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();

        // 테이블 생성
        createDragonTable();
    }

    /**
     * 드래곤 보상 테이블 생성
     */
    private void createDragonTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ggm_dragon_rewards (
                id INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16) NOT NULL,
                reward_amount BIGINT NOT NULL,
                damage_dealt INT NOT NULL,
                reward_date DATE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_uuid_date (uuid, reward_date),
                INDEX idx_reward_date (reward_date)
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
     * 엔더드래곤 사망 이벤트
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // 엔더드래곤인지 확인
        if (!(entity instanceof EnderDragon)) return;

        EnderDragon dragon = (EnderDragon) entity;

        plugin.getLogger().info("엔더드래곤이 처치되었습니다! 보상 계산 중...");

        // 드래곤에게 피해를 준 플레이어들 찾기
        Map<Player, Double> damageMap = calculatePlayerDamage(dragon);

        if (damageMap.isEmpty()) {
            plugin.getLogger().warning("드래곤 처치에 기여한 플레이어를 찾을 수 없습니다.");
            return;
        }

        // 보상 지급 처리
        processDragonRewards(damageMap);
    }

    /**
     * 플레이어별 피해량 계산 (간단한 방식)
     */
    private Map<Player, Double> calculatePlayerDamage(EnderDragon dragon) {
        Map<Player, Double> damageMap = new HashMap<>();

        // 드래곤 주변 플레이어들을 찾아서 기여도 계산
        // 실제로는 더 정교한 피해량 추적이 필요하지만, 간단하게 구현
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(dragon.getWorld())) {
                double distance = player.getLocation().distance(dragon.getLocation());

                // 드래곤 근처에 있던 플레이어들에게 기여도 부여
                if (distance <= 100) { // 100블록 내
                    // 거리에 따른 기여도 (가까울수록 높음)
                    double contribution = Math.max(0, 100 - distance) * 10;
                    damageMap.put(player, contribution);
                }
            }
        }

        return damageMap;
    }

    /**
     * 드래곤 보상 처리
     */
    private void processDragonRewards(Map<Player, Double> damageMap) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 총 기여도 계산
        double totalDamage = damageMap.values().stream().mapToDouble(Double::doubleValue).sum();

        plugin.getLogger().info(String.format("드래곤 처치 기여자 %d명, 총 기여도: %.1f",
                damageMap.size(), totalDamage));

        // 각 플레이어에게 보상 지급
        for (Map.Entry<Player, Double> entry : damageMap.entrySet()) {
            Player player = entry.getKey();
            double damage = entry.getValue();

            // 최소 기여도 확인
            if (damage < DAMAGE_THRESHOLD) {
                player.sendMessage("§c드래곤 처치 기여도가 부족하여 보상을 받을 수 없습니다.");
                player.sendMessage("§7(최소 기여도: " + DAMAGE_THRESHOLD + ", 현재: " + (int)damage + ")");
                continue;
            }

            // 오늘 이미 보상을 받았는지 확인
            hasReceivedRewardToday(player.getUniqueId()).thenAccept(alreadyReceived -> {
                if (alreadyReceived) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c오늘 이미 드래곤 보상을 받으셨습니다!");
                        player.sendMessage("§7보상은 하루에 한 번만 받을 수 있습니다.");
                    });
                    return;
                }

                // 기여도에 따른 보상 계산
                double contribution = damage / totalDamage;
                long reward = Math.max(10000L, (long)(DRAGON_REWARD * contribution)); // 최소 10,000G

                // 보상 지급
                economyManager.addMoney(player.getUniqueId(), reward).thenAccept(success -> {
                    if (success) {
                        // 보상 기록 저장
                        recordDragonReward(player.getUniqueId(), player.getName(), reward, (int)damage)
                                .thenRun(() -> {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        // 보상 성공 메시지
                                        announceDragonReward(player, reward, damage, totalDamage);
                                    });
                                });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("§c보상 지급 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
                        });
                    }
                });
            });
        }

        // 전체 서버 공지
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.broadcastMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Bukkit.broadcastMessage("§d§l🐉 엔더드래곤이 처치되었습니다! 🐉");
            Bukkit.broadcastMessage("§7기여한 용사들에게 보상이 지급되었습니다!");
            Bukkit.broadcastMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 전체 효과음
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }, 60L); // 3초 후 공지
    }

    /**
     * 드래곤 보상 공지
     */
    private void announceDragonReward(Player player, long reward, double damage, double totalDamage) {
        double contribution = (damage / totalDamage) * 100;

        player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§d§l🐉 드래곤 처치 보상! 🐉");
        player.sendMessage("");
        player.sendMessage("§7기여도: §f" + String.format("%.1f%%", contribution));
        player.sendMessage("§7보상: §6" + formatMoney(reward) + "G");
        player.sendMessage("");
        player.sendMessage("§a축하합니다! 용감한 용사여!");
        player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 특별 효과음과 파티클
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.2f);
        player.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH,
                player.getLocation().add(0, 1, 0), 30);
        player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK,
                player.getLocation().add(0, 2, 0), 50);

        plugin.getLogger().info(String.format("[드래곤보상] %s: %dG (기여도: %.1f%%)",
                player.getName(), reward, contribution));
    }

    /**
     * 오늘 보상을 받았는지 확인
     */
    private CompletableFuture<Boolean> hasReceivedRewardToday(UUID uuid) {
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
                return false;
            }
        });
    }

    /**
     * 드래곤 보상 기록 저장
     */
    private CompletableFuture<Void> recordDragonReward(UUID uuid, String playerName, long reward, int damage) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    INSERT INTO ggm_dragon_rewards 
                    (uuid, player_name, reward_amount, damage_dealt, reward_date) 
                    VALUES (?, ?, ?, ?, CURDATE())
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setLong(3, reward);
                    stmt.setInt(4, damage);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("드래곤 보상 기록 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 플레이어의 드래곤 처치 기록 조회
     */
    public CompletableFuture<List<DragonRecord>> getPlayerDragonHistory(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<DragonRecord> records = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    SELECT reward_amount, damage_dealt, reward_date 
                    FROM ggm_dragon_rewards 
                    WHERE uuid = ? 
                    ORDER BY reward_date DESC 
                    LIMIT 10
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            records.add(new DragonRecord(
                                    rs.getLong("reward_amount"),
                                    rs.getInt("damage_dealt"),
                                    rs.getDate("reward_date").toLocalDate()
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("드래곤 기록 조회 실패: " + e.getMessage());
            }

            return records;
        });
    }

    /**
     * 오늘의 드래곤 처치 현황 조회
     */
    public CompletableFuture<List<TodayDragonRecord>> getTodayDragonKills() {
        return CompletableFuture.supplyAsync(() -> {
            List<TodayDragonRecord> records = new ArrayList<>();

            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    SELECT player_name, reward_amount, damage_dealt 
                    FROM ggm_dragon_rewards 
                    WHERE reward_date = CURDATE() 
                    ORDER BY reward_amount DESC
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            records.add(new TodayDragonRecord(
                                    rs.getString("player_name"),
                                    rs.getLong("reward_amount"),
                                    rs.getInt("damage_dealt")
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("오늘 드래곤 기록 조회 실패: " + e.getMessage());
            }

            return records;
        });
    }

    /**
     * 금액 포맷팅
     */
    private String formatMoney(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * 드래곤 처치 기록 클래스
     */
    public static class DragonRecord {
        private final long rewardAmount;
        private final int damageDealt;
        private final LocalDate date;

        public DragonRecord(long rewardAmount, int damageDealt, LocalDate date) {
            this.rewardAmount = rewardAmount;
            this.damageDealt = damageDealt;
            this.date = date;
        }

        public long getRewardAmount() { return rewardAmount; }
        public int getDamageDealt() { return damageDealt; }
        public LocalDate getDate() { return date; }
    }

    /**
     * 오늘 드래곤 처치 기록 클래스
     */
    public static class TodayDragonRecord {
        private final String playerName;
        private final long rewardAmount;
        private final int damageDealt;

        public TodayDragonRecord(String playerName, long rewardAmount, int damageDealt) {
            this.playerName = playerName;
            this.rewardAmount = rewardAmount;
            this.damageDealt = damageDealt;
        }

        public String getPlayerName() { return playerName; }
        public long getRewardAmount() { return rewardAmount; }
        public int getDamageDealt() { return damageDealt; }
    }
}