// 완전 안정화된 DragonRewardManager.java
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 완전 안정화된 드래곤 보상 시스템 매니저
 * - 드래곤 처치 시 기여도별 보상 지급
 * - Thread-Safe 구현
 * - 데이터베이스 기록 관리
 * - 강력한 예외 처리
 */
public class DragonRewardManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;

    // 드래곤 기여도 추적 (UUID -> 데미지)
    private final ConcurrentHashMap<UUID, Double> dragonDamage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();

    // 설정값들
    private final long baseReward;
    private final long minReward;
    private final double minDamageThreshold;
    private final long damageTimeout;

    // 통계
    private volatile int totalDragonsKilled = 0;
    private volatile long totalRewardsGiven = 0;

    public DragonRewardManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();

        try {
            // 설정값 로드
            this.baseReward = plugin.getConfig().getLong("dragon_reward.base_reward", 100000L);
            this.minReward = plugin.getConfig().getLong("dragon_reward.min_reward", 10000L);
            this.minDamageThreshold = plugin.getConfig().getDouble("dragon_reward.min_damage_threshold", 50.0);
            this.damageTimeout = plugin.getConfig().getLong("dragon_reward.damage_timeout", 300000L); // 5분

            // 데이터베이스 테이블 생성
            createDragonTables();

            // 통계 로드
            loadStatistics();

            plugin.getLogger().info("DragonRewardManager 안정화 초기화 완료");
            plugin.getLogger().info("기본 보상: " + economyManager.formatMoney(baseReward) + "G");
            plugin.getLogger().info("최소 기여도: " + minDamageThreshold + " 데미지");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "DragonRewardManager 초기화 실패", e);
            throw new RuntimeException("DragonRewardManager 초기화 실패", e);
        }
    }

    /**
     * 드래곤 관련 테이블 생성
     */
    private void createDragonTables() {
        try (Connection connection = databaseManager.getConnection()) {

            // 드래곤 처치 기록 테이블
            String dragonKillsSQL = """
                CREATE TABLE IF NOT EXISTS dragon_kills (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    kill_date DATE NOT NULL,
                    kill_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    total_participants INT DEFAULT 0,
                    total_rewards BIGINT DEFAULT 0,
                    INDEX idx_kill_date (kill_date),
                    INDEX idx_kill_time (kill_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            // 플레이어별 드래곤 기여도 테이블
            String dragonParticipantsSQL = """
                CREATE TABLE IF NOT EXISTS dragon_participants (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    kill_id INT NOT NULL,
                    uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    damage_dealt DOUBLE NOT NULL DEFAULT 0,
                    reward_amount BIGINT NOT NULL DEFAULT 0,
                    kill_date DATE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (kill_id) REFERENCES dragon_kills(id) ON DELETE CASCADE,
                    INDEX idx_uuid (uuid),
                    INDEX idx_kill_date (kill_date),
                    INDEX idx_damage_dealt (damage_dealt)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            try (PreparedStatement stmt1 = connection.prepareStatement(dragonKillsSQL);
                 PreparedStatement stmt2 = connection.prepareStatement(dragonParticipantsSQL)) {

                stmt1.executeUpdate();
                stmt2.executeUpdate();
            }

            plugin.getLogger().info("드래곤 보상 데이터베이스 테이블 생성 완료");

        } catch (SQLException e) {
            throw new RuntimeException("드래곤 테이블 생성 실패", e);
        }
    }

    /**
     * 통계 로드
     */
    private void loadStatistics() {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT COUNT(*) as total_kills, COALESCE(SUM(total_rewards), 0) as total_rewards FROM dragon_kills");
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                totalDragonsKilled = rs.getInt("total_kills");
                totalRewardsGiven = rs.getLong("total_rewards");
            }

            plugin.getLogger().info("드래곤 통계 로드: 처치 " + totalDragonsKilled + "회, 총 보상 " +
                    economyManager.formatMoney(totalRewardsGiven) + "G");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "드래곤 통계 로드 실패", e);
        }
    }

    /**
     * 드래곤 피해 이벤트
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (plugin.isShuttingDown()) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        try {
            double damage = event.getFinalDamage();
            if (damage <= 0) return;

            UUID uuid = player.getUniqueId();
            long currentTime = System.currentTimeMillis();

            // 기존 데미지에 추가
            dragonDamage.merge(uuid, damage, Double::sum);
            lastDamageTime.put(uuid, currentTime);

            // 디버그 로그 (설정에 따라)
            if (plugin.getConfig().getBoolean("debug.log_dragon_damage", false)) {
                plugin.getLogger().info(String.format("[드래곤피해] %s: %.1f 데미지 (총 %.1f)",
                        player.getName(), damage, dragonDamage.get(uuid)));
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "드래곤 피해 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 드래곤 처치 이벤트
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonDeath(EntityDeathEvent event) {
        if (plugin.isShuttingDown()) return;
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;

        try {
            plugin.getLogger().info("엔더 드래곤이 처치되었습니다! 보상을 계산 중...");

            // 비동기로 보상 처리
            CompletableFuture.runAsync(() -> processDragonKill(dragon));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "드래곤 처치 이벤트 처리 중 오류", e);
        }
    }

    /**
     * 드래곤 처치 보상 처리
     */
    private void processDragonKill(EnderDragon dragon) {
        try {
            // 오래된 데미지 기록 정리
            cleanupOldDamage();

            if (dragonDamage.isEmpty()) {
                plugin.getLogger().warning("드래곤 처치되었지만 기여도 기록이 없습니다.");
                return;
            }

            // 총 데미지 계산
            double totalDamage = dragonDamage.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            if (totalDamage <= 0) {
                plugin.getLogger().warning("총 기여도가 0입니다.");
                return;
            }

            // 최소 기여도 이상인 플레이어만 필터링
            Map<UUID, Double> validParticipants = new HashMap<>();
            dragonDamage.entrySet().stream()
                    .filter(entry -> entry.getValue() >= minDamageThreshold)
                    .forEach(entry -> validParticipants.put(entry.getKey(), entry.getValue()));

            if (validParticipants.isEmpty()) {
                plugin.getLogger().warning("최소 기여도를 만족하는 플레이어가 없습니다.");
                dragonDamage.clear();
                return;
            }

            // 데이터베이스에 처치 기록 저장
            int killId = saveDragonKill(validParticipants.size());

            if (killId > 0) {
                // 보상 분배
                distributeRewards(killId, validParticipants, totalDamage);
            }

            // 기여도 초기화
            dragonDamage.clear();
            lastDamageTime.clear();

            totalDragonsKilled++;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "드래곤 처치 보상 처리 중 오류", e);
        }
    }

    /**
     * 오래된 데미지 기록 정리
     */
    private void cleanupOldDamage() {
        try {
            long currentTime = System.currentTimeMillis();

            lastDamageTime.entrySet().removeIf(entry -> {
                boolean isOld = currentTime - entry.getValue() > damageTimeout;
                if (isOld) {
                    dragonDamage.remove(entry.getKey());
                }
                return isOld;
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "오래된 드래곤 데미지 정리 중 오류", e);
        }
    }

    /**
     * 드래곤 처치 기록 저장
     */
    private int saveDragonKill(int participantCount) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "INSERT INTO dragon_kills (kill_date, total_participants) VALUES (CURDATE(), ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, participantCount);
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "드래곤 처치 기록 저장 실패", e);
        }

        return 0;
    }

    /**
     * 보상 분배
     */
    private void distributeRewards(int killId, Map<UUID, Double> participants, double totalDamage) {
        try {
            Map<UUID, Long> rewards = calculateRewards(participants, totalDamage);

            // 메인 스레드에서 보상 지급 및 메시지 전송
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    for (Map.Entry<UUID, Long> entry : rewards.entrySet()) {
                        UUID uuid = entry.getKey();
                        long reward = entry.getValue();

                        Player player = Bukkit.getPlayer(uuid);
                        String playerName = player != null ? player.getName() : "알 수 없음";

                        // 보상 지급
                        economyManager.addMoney(uuid, reward).thenAccept(success -> {
                            if (success) {
                                // 플레이어가 온라인인 경우 메시지 전송
                                if (player != null && player.isOnline()) {
                                    sendRewardMessage(player, reward, participants.get(uuid), totalDamage);
                                }

                                // 데이터베이스에 기여도 기록
                                saveParticipantRecord(killId, uuid, playerName,
                                        participants.get(uuid), reward);
                            }
                        });
                    }

                    // 서버 전체 알림
                    broadcastDragonKillMessage(participants.size(), rewards.values().stream()
                            .mapToLong(Long::longValue).sum());

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "보상 분배 중 오류", e);
                }
            });

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "보상 계산 중 오류", e);
        }
    }

    /**
     * 보상 계산
     */
    private Map<UUID, Long> calculateRewards(Map<UUID, Double> participants, double totalDamage) {
        Map<UUID, Long> rewards = new HashMap<>();

        try {
            for (Map.Entry<UUID, Double> entry : participants.entrySet()) {
                UUID uuid = entry.getKey();
                double damage = entry.getValue();

                // 기여도 비율 계산
                double contributionRatio = damage / totalDamage;

                // 보상 계산 (기본 보상 * 기여도 비율)
                long reward = Math.round(baseReward * contributionRatio);

                // 최소 보상 보장
                reward = Math.max(reward, minReward);

                rewards.put(uuid, reward);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "보상 계산 중 오류", e);
        }

        return rewards;
    }

    /**
     * 참가자 기록 저장
     */
    private void saveParticipantRecord(int killId, UUID uuid, String playerName,
                                       double damage, long reward) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "INSERT INTO dragon_participants (kill_id, uuid, player_name, damage_dealt, reward_amount, kill_date) " +
                                 "VALUES (?, ?, ?, ?, ?, CURDATE())")) {

                stmt.setInt(1, killId);
                stmt.setString(2, uuid.toString());
                stmt.setString(3, playerName);
                stmt.setDouble(4, damage);
                stmt.setLong(5, reward);

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "참가자 기록 저장 실패: " + playerName, e);
            }
        });
    }

    /**
     * 보상 메시지 전송
     */
    private void sendRewardMessage(Player player, long reward, double damage, double totalDamage) {
        try {
            double contributionPercent = (damage / totalDamage) * 100;

            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l🐉 엔더 드래곤 처치 보상!");
            player.sendMessage("");
            player.sendMessage("§7기여도: §a" + String.format("%.1f", damage) + " 데미지 §7(" +
                    String.format("%.1f%%", contributionPercent) + ")");
            player.sendMessage("§7보상: §6" + economyManager.formatMoney(reward) + "G");
            player.sendMessage("");
            player.sendMessage("§a수고하셨습니다! 보상이 지급되었습니다.");
            player.sendMessage("§6==========================================");

            // 효과음
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "보상 메시지 전송 실패: " + player.getName(), e);
        }
    }

    /**
     * 서버 전체 알림
     */
    private void broadcastDragonKillMessage(int participantCount, long totalRewards) {
        try {
            Bukkit.broadcastMessage("§6==========================================");
            Bukkit.broadcastMessage("§e§l🐉 엔더 드래곤이 처치되었습니다!");
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§7참가자: §a" + participantCount + "명");
            Bukkit.broadcastMessage("§7총 보상: §6" + economyManager.formatMoney(totalRewards) + "G");
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§a모든 참가자에게 기여도에 따른 보상이 지급되었습니다!");
            Bukkit.broadcastMessage("§6==========================================");

            totalRewardsGiven += totalRewards;

            plugin.getLogger().info(String.format("[드래곤처치] 참가자 %d명, 총 보상 %s",
                    participantCount, economyManager.formatMoney(totalRewards)));

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "드래곤 처치 알림 중 오류", e);
        }
    }

    /**
     * 오늘의 드래곤 처치 기록 조회
     */
    public CompletableFuture<List<DragonKillRecord>> getTodayDragonKills() {
        return CompletableFuture.supplyAsync(() -> {
            List<DragonKillRecord> records = new ArrayList<>();

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT dk.*, COUNT(dp.id) as participant_count " +
                                 "FROM dragon_kills dk " +
                                 "LEFT JOIN dragon_participants dp ON dk.id = dp.kill_id " +
                                 "WHERE dk.kill_date = CURDATE() " +
                                 "GROUP BY dk.id ORDER BY dk.kill_time DESC")) {

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        records.add(new DragonKillRecord(
                                rs.getInt("id"),
                                rs.getDate("kill_date").toLocalDate(),
                                rs.getTimestamp("kill_time"),
                                rs.getInt("participant_count"),
                                rs.getLong("total_rewards")
                        ));
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "오늘의 드래곤 기록 조회 실패", e);
            }

            return records;
        });
    }

    /**
     * 플레이어의 드래곤 참가 기록 조회
     */
    public CompletableFuture<List<PlayerDragonRecord>> getPlayerDragonHistory(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerDragonRecord> records = new ArrayList<>();

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT * FROM dragon_participants WHERE uuid = ? " +
                                 "ORDER BY created_at DESC LIMIT ?")) {

                stmt.setString(1, uuid.toString());
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        records.add(new PlayerDragonRecord(
                                rs.getDate("kill_date").toLocalDate(),
                                rs.getDouble("damage_dealt"),
                                rs.getLong("reward_amount"),
                                rs.getTimestamp("created_at")
                        ));
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 드래곤 기록 조회 실패: " + uuid, e);
            }

            return records;
        });
    }

    /**
     * 현재 기여도 상황 표시
     */
    public void showCurrentContribution(Player player) {
        try {
            UUID uuid = player.getUniqueId();
            Double damage = dragonDamage.get(uuid);

            if (damage == null || damage <= 0) {
                player.sendMessage("§c현재 드래곤에 대한 기여도가 없습니다.");
                return;
            }

            double totalDamage = dragonDamage.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();

            double contributionPercent = totalDamage > 0 ? (damage / totalDamage) * 100 : 0;
            long estimatedReward = Math.round(baseReward * (damage / totalDamage));
            estimatedReward = Math.max(estimatedReward, minReward);

            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l🐉 현재 드래곤 기여도");
            player.sendMessage("");
            player.sendMessage("§7내 기여도: §a" + String.format("%.1f", damage) + " 데미지");
            player.sendMessage("§7기여 비율: §e" + String.format("%.1f%%", contributionPercent));
            player.sendMessage("§7예상 보상: §6" + economyManager.formatMoney(estimatedReward) + "G");
            player.sendMessage("");
            player.sendMessage("§7최소 기여도: §c" + minDamageThreshold + " 데미지");
            player.sendMessage(damage >= minDamageThreshold ?
                    "§a보상 수령 가능!" : "§c최소 기여도 미달");
            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "기여도 표시 중 오류: " + player.getName(), e);
            player.sendMessage("§c기여도 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 드래곤 통계 정보
     */
    public String getDragonStats() {
        return String.format("총 처치: %d회 | 총 보상: %sG | 현재 참가자: %d명",
                totalDragonsKilled,
                economyManager.formatMoney(totalRewardsGiven),
                dragonDamage.size());
    }

    /**
     * 캐시 정리
     */
    public void cleanupCache() {
        try {
            cleanupOldDamage();
            plugin.getLogger().info("DragonRewardManager 캐시 정리 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "드래곤 보상 캐시 정리 중 오류", e);
        }
    }

    /**
     * 매니저 종료
     */
    public void onDisable() {
        try {
            // 현재 진행 중인 기여도 기록 저장 (필요한 경우)
            if (!dragonDamage.isEmpty()) {
                plugin.getLogger().info("진행 중인 드래곤 기여도 " + dragonDamage.size() + "개 기록이 있습니다.");
            }

            // 캐시 정리
            dragonDamage.clear();
            lastDamageTime.clear();

            plugin.getLogger().info("DragonRewardManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DragonRewardManager 종료 중 오류", e);
        }
    }

    // 데이터 클래스들
    public static class DragonKillRecord {
        public final int killId;
        public final LocalDate killDate;
        public final java.sql.Timestamp killTime;
        public final int participantCount;
        public final long totalRewards;

        public DragonKillRecord(int killId, LocalDate killDate, java.sql.Timestamp killTime,
                                int participantCount, long totalRewards) {
            this.killId = killId;
            this.killDate = killDate;
            this.killTime = killTime;
            this.participantCount = participantCount;
            this.totalRewards = totalRewards;
        }
    }

    public static class PlayerDragonRecord {
        public final LocalDate killDate;
        public final double damageDealt;
        public final long rewardAmount;
        public final java.sql.Timestamp timestamp;

        public PlayerDragonRecord(LocalDate killDate, double damageDealt,
                                  long rewardAmount, java.sql.Timestamp timestamp) {
            this.killDate = killDate;
            this.damageDealt = damageDealt;
            this.rewardAmount = rewardAmount;
            this.timestamp = timestamp;
        }
    }

    // Getter 메서드들
    public long getBaseReward() {
        return baseReward;
    }

    public long getMinReward() {
        return minReward;
    }

    public double getMinDamageThreshold() {
        return minDamageThreshold;
    }

    public int getTotalDragonsKilled() {
        return totalDragonsKilled;
    }

    public long getTotalRewardsGiven() {
        return totalRewardsGiven;
    }

    public int getCurrentParticipants() {
        return dragonDamage.size();
    }
}