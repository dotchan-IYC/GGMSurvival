// 수정된 DragonRewardManager.java - EconomyManager 메서드 시그니처 수정
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DragonRewardManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    // 드래곤 데미지 추적
    private final Map<UUID, Double> dragonDamage = new HashMap<>();
    private EnderDragon currentDragon = null;

    // 보상 설정
    private final long BASE_REWARD = 100000L; // 기본 보상 100,000G
    private final long MIN_REWARD = 10000L;   // 최소 보상 10,000G
    private final double MIN_DAMAGE_THRESHOLD = 50.0; // 최소 기여도 50

    public DragonRewardManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();

        // 테이블 생성
        createDragonRewardTable();

        plugin.getLogger().info("드래곤 보상 시스템 초기화 완료");
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
                damage_dealt DOUBLE NOT NULL,
                reward_amount BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (uuid, reward_date),
                INDEX idx_reward_date (reward_date),
                INDEX idx_player (uuid)
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
    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!plugin.isFeatureEnabled("dragon_reward")) return;

        EnderDragon dragon = (EnderDragon) event.getEntity();

        plugin.getLogger().info("엔더드래곤이 처치되었습니다! 보상을 계산합니다.");

        // 모든 플레이어에게 보상 지급
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            processAllRewards();
        });

        // 데미지 기록 초기화
        dragonDamage.clear();
        currentDragon = null;
    }

    /**
     * 모든 플레이어 보상 처리
     */
    private void processAllRewards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            double damage = dragonDamage.getOrDefault(uuid, 0.0);

            if (damage >= MIN_DAMAGE_THRESHOLD) {
                processDragonReward(player, damage);
            }
        }
    }

    /**
     * 드래곤 보상 처리
     */
    public void processDragonReward(Player player, double damageDealt) {
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

            // 최소 기여도 확인
            if (damageDealt < MIN_DAMAGE_THRESHOLD) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c드래곤 처치 기여도가 부족합니다. (최소: " + (int)MIN_DAMAGE_THRESHOLD + ")");
                });
                return;
            }

            // 보상 금액 계산
            long rewardAmount = calculateReward(damageDealt);

            // 보상 지급 - 수정된 메서드 시그니처 사용 (UUID, long)
            plugin.getEconomyManager().addMoney(player.getUniqueId(), rewardAmount)
                    .thenAccept(success -> {
                        if (success) {
                            // DB에 보상 기록 저장
                            saveRewardRecord(uuid, playerName, damageDealt, rewardAmount).thenAccept(saveSuccess -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (saveSuccess) {
                                        // 성공 메시지
                                        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                        player.sendMessage("§d🐉 엔더드래곤 처치 보상!");
                                        player.sendMessage("");
                                        player.sendMessage("§7기여도: §f" + String.format("%.1f", damageDealt));
                                        player.sendMessage("§7보상: §6" + formatMoney(rewardAmount) + "G");
                                        player.sendMessage("");
                                        player.sendMessage("§a훌륭한 전투였습니다!");
                                        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                                        // 사운드 효과
                                        player.playSound(player.getLocation(),
                                                org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH,
                                                0.5f, 1.2f);

                                        plugin.getLogger().info(String.format("[드래곤보상] %s: 기여도 %.1f, 보상 %dG",
                                                playerName, damageDealt, rewardAmount));
                                    } else {
                                        player.sendMessage("§c보상 기록 저장에 실패했습니다.");
                                    }
                                });
                            });
                        } else {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§c보상 지급에 실패했습니다. 관리자에게 문의하세요.");
                            });
                        }
                    });
        });
    }

    /**
     * 보상 금액 계산
     */
    private long calculateReward(double damageDealt) {
        // 기여도에 따른 보상 계산 (최소 10,000G, 최대 100,000G)
        double ratio = Math.min(1.0, damageDealt / 1000.0); // 1000 데미지를 기준으로 비율 계산
        long reward = Math.round(MIN_REWARD + (BASE_REWARD - MIN_REWARD) * ratio);

        return Math.max(MIN_REWARD, Math.min(BASE_REWARD, reward));
    }

    /**
     * 오늘 보상 받았는지 확인
     */
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
                return true; // 오류 시 보상 지급 차단
            }
        });
    }

    /**
     * 보상 기록 저장
     */
    private CompletableFuture<Boolean> saveRewardRecord(UUID uuid, String playerName, double damage, long reward) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    INSERT INTO ggm_dragon_rewards (uuid, player_name, reward_date, damage_dealt, reward_amount)
                    VALUES (?, ?, CURDATE(), ?, ?)
                    ON DUPLICATE KEY UPDATE
                    damage_dealt = damage_dealt + VALUES(damage_dealt),
                    reward_amount = reward_amount + VALUES(reward_amount)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setDouble(3, damage);
                    stmt.setLong(4, reward);

                    return stmt.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("드래곤 보상 기록 저장 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 플레이어의 오늘 드래곤 보상 정보 조회
     */
    public CompletableFuture<DragonRewardInfo> getTodayRewardInfo(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    SELECT damage_dealt, reward_amount, created_at
                    FROM ggm_dragon_rewards 
                    WHERE uuid = ? AND reward_date = CURDATE()
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return new DragonRewardInfo(
                                    true,
                                    rs.getDouble("damage_dealt"),
                                    rs.getLong("reward_amount"),
                                    rs.getTimestamp("created_at")
                            );
                        }
                    }
                }
                return new DragonRewardInfo(false, 0.0, 0L, null);
            } catch (SQLException e) {
                plugin.getLogger().severe("드래곤 보상 정보 조회 실패: " + e.getMessage());
                return new DragonRewardInfo(false, 0.0, 0L, null);
            }
        });
    }

    /**
     * 드래곤 데미지 기록
     */
    public void recordDragonDamage(UUID uuid, double damage) {
        dragonDamage.merge(uuid, damage, Double::sum);
    }

    /**
     * 현재 드래곤 설정
     */
    public void setCurrentDragon(EnderDragon dragon) {
        this.currentDragon = dragon;
        dragonDamage.clear(); // 새 드래곤이 나타나면 데미지 기록 초기화
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

    /**
     * 드래곤 보상 정보 클래스
     */
    public static class DragonRewardInfo {
        public final boolean hasReceived;
        public final double damageDealt;
        public final long rewardAmount;
        public final java.sql.Timestamp receivedAt;

        public DragonRewardInfo(boolean hasReceived, double damageDealt, long rewardAmount, java.sql.Timestamp receivedAt) {
            this.hasReceived = hasReceived;
            this.damageDealt = damageDealt;
            this.rewardAmount = rewardAmount;
            this.receivedAt = receivedAt;
        }
    }
}