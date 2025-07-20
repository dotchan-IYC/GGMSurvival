// DragonRewardManager.java - 엔더드래곤 보상 시스템
package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import com.ggm.ggmsurvival.GGMSurvival;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DragonRewardManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;

    // 드래곤별 플레이어 기여도 저장
    private final Map<UUID, Map<UUID, Double>> dragonDamage = new HashMap<>();

    public DragonRewardManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        createDragonRewardTable();
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
                PRIMARY KEY (uuid, reward_date)
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("드래곤 보상 테이블이 준비되었습니다.");
        } catch (Exception e) {
            plugin.getLogger().severe("드래곤 보상 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 드래곤 피해 기록
     */
    @EventHandler
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!(event.getDamager() instanceof Player)) return;

        EnderDragon dragon = (EnderDragon) event.getEntity();
        Player player = (Player) event.getDamager();
        double damage = event.getFinalDamage();

        UUID dragonId = dragon.getUniqueId();
        UUID playerId = player.getUniqueId();

        // 드래곤별 피해량 기록
        dragonDamage.computeIfAbsent(dragonId, k -> new HashMap<>())
                .merge(playerId, damage, Double::sum);

        plugin.getLogger().info(String.format("[드래곤피해] %s이(가) 드래곤에게 %.1f 피해를 입혔습니다.",
                player.getName(), damage));
    }

    /**
     * 드래곤 처치 시 보상 지급
     */
    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;

        EnderDragon dragon = (EnderDragon) event.getEntity();
        UUID dragonId = dragon.getUniqueId();

        // 해당 드래곤에게 피해를 입힌 플레이어들 확인
        Map<UUID, Double> damageMap = dragonDamage.remove(dragonId);
        if (damageMap == null || damageMap.isEmpty()) {
            plugin.getLogger().info("드래곤이 처치되었지만 기여한 플레이어가 없습니다.");
            return;
        }

        plugin.getLogger().info("§d🐉 엔더드래곤이 처치되었습니다! 보상을 계산 중...");

        // 서버에 알림
        Bukkit.broadcastMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.broadcastMessage("§e§l🐉 엔더드래곤이 처치되었습니다!");
        Bukkit.broadcastMessage("§a기여한 플레이어들에게 보상을 지급합니다...");
        Bukkit.broadcastMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 각 플레이어에게 보상 지급
        damageMap.forEach((playerId, damage) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                processDragonReward(player, damage);
            }
        });
    }

    /**
     * 드래곤 보상 처리
     */
    private void processDragonReward(Player player, double damageDealt) {
        // 최소 공적치 확인 (50으로 변경)
        double minDamage = plugin.getConfig().getDouble("dragon_reward.min_damage_threshold", 50.0);
        if (damageDealt < minDamage) {
            player.sendMessage("§c드래곤 처치 기여도가 부족합니다. (최소: " + minDamage + ", 현재: " + String.format("%.1f", damageDealt) + ")");
            return;
        }

        // 오늘 이미 보상을 받았는지 확인
        hasReceivedTodayReward(player.getUniqueId()).thenAccept(hasReceived -> {
            if (hasReceived) {
                player.sendMessage("§c오늘 이미 드래곤 보상을 받으셨습니다!");
                return;
            }

            // 보상 계산 (기여도에 따라)
            long baseReward = plugin.getConfig().getLong("dragon_reward.base_reward", 100000);
            long minReward = plugin.getConfig().getLong("dragon_reward.min_reward", 10000);

            // 기여도 비율 계산 (최소 10%, 최대 100%)
            double contributionRatio = Math.min(1.0, Math.max(0.1, damageDealt / 500.0));
            long rewardAmount = Math.max(minReward, (long) (baseReward * contributionRatio));

            // 보상 지급
            plugin.getEconomyManager().addMoney(player.getUniqueId(), player.getName(), rewardAmount)
                    .thenAccept(success -> {
                        if (success) {
                            // 데이터베이스에 보상 기록
                            recordDragonReward(player.getUniqueId(), player.getName(), damageDealt, rewardAmount);

                            // 플레이어에게 알림
                            player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            player.sendMessage("§e§l🐉 드래곤 처치 보상!");
                            player.sendMessage("");
                            player.sendMessage("§a기여도: §f" + String.format("%.1f", damageDealt));
                            player.sendMessage("§a보상 금액: §6" + String.format("%,d", rewardAmount) + "G");
                            player.sendMessage("§7기여도 비율: " + String.format("%.1f", contributionRatio * 100) + "%");
                            player.sendMessage("");
                            player.sendMessage("§d수고하셨습니다! 🎉");
                            player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                            // 서버 공지
                            Bukkit.broadcastMessage(String.format("§a%s님이 드래곤 보상으로 §6%,dG§a를 받았습니다!",
                                    player.getName(), rewardAmount));

                            plugin.getLogger().info(String.format("[드래곤보상] %s: %.1f 피해, %,dG 지급",
                                    player.getName(), damageDealt, rewardAmount));
                        } else {
                            player.sendMessage("§c보상 지급에 실패했습니다. 관리자에게 문의하세요.");
                        }
                    });
        });
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
            } catch (Exception e) {
                plugin.getLogger().severe("드래곤 보상 확인 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 드래곤 보상 기록
     */
    private void recordDragonReward(UUID uuid, String playerName, double damage, long rewardAmount) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    INSERT INTO ggm_dragon_rewards (uuid, player_name, reward_date, damage_dealt, reward_amount)
                    VALUES (?, ?, CURDATE(), ?, ?)
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setDouble(3, damage);
                    stmt.setLong(4, rewardAmount);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("드래곤 보상 기록 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 플레이어의 오늘 드래곤 처치 현황 확인
     */
    public CompletableFuture<String> getTodayDragonInfo(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    SELECT damage_dealt, reward_amount 
                    FROM ggm_dragon_rewards 
                    WHERE uuid = ? AND reward_date = CURDATE()
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            double damage = rs.getDouble("damage_dealt");
                            long reward = rs.getLong("reward_amount");
                            return String.format("§a오늘 드래곤 보상: §6%,dG §7(기여도: %.1f)", reward, damage);
                        }
                    }
                }
                return "§7오늘 아직 드래곤 보상을 받지 않았습니다.";
            } catch (Exception e) {
                plugin.getLogger().severe("드래곤 정보 조회 실패: " + e.getMessage());
                return "§c정보를 불러오는데 실패했습니다.";
            }
        });
    }
}