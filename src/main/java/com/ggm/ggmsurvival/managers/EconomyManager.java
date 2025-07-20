package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyManager {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;
    private boolean ggmCoreConnected = false;

    public EconomyManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        // GGMCore 연동 확인
        checkGGMCoreConnection();
    }

    /**
     * GGMCore 연동 확인
     */
    private void checkGGMCoreConnection() {
        try {
            if (Bukkit.getPluginManager().getPlugin("GGMCore") != null) {
                ggmCoreConnected = true;
                plugin.getLogger().info("GGMCore와 연동되었습니다.");
            } else {
                plugin.getLogger().warning("GGMCore가 감지되지 않았습니다. 독립 경제 시스템을 사용합니다.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("GGMCore 연동 확인 중 오류: " + e.getMessage());
        }
    }

    /**
     * GGMCore 스코어보드 업데이트 (G 변경 시 호출)
     */
    private void updateGGMCoreScoreboard(UUID uuid, long change) {
        if (!ggmCoreConnected) return;

        try {
            org.bukkit.plugin.Plugin ggmCore = Bukkit.getPluginManager().getPlugin("GGMCore");
            if (ggmCore != null && ggmCore.isEnabled()) {
                // GGMCore의 ScoreboardManager 가져오기
                Class<?> ggmCoreClass = ggmCore.getClass();
                java.lang.reflect.Method getScoreboardManagerMethod = ggmCoreClass.getMethod("getScoreboardManager");
                Object scoreboardManager = getScoreboardManagerMethod.invoke(ggmCore);

                if (scoreboardManager != null) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        // notifyBalanceChange 메서드 호출 (ActionBar + 스코어보드 업데이트)
                        getBalance(uuid).thenAccept(newBalance -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    Class<?> scoreboardManagerClass = scoreboardManager.getClass();
                                    java.lang.reflect.Method notifyMethod = scoreboardManagerClass.getMethod("notifyBalanceChange", Player.class, long.class, long.class);
                                    notifyMethod.invoke(scoreboardManager, player, newBalance, change);
                                } catch (Exception e) {
                                    // fallback: 기본 스코어보드 업데이트만
                                    try {
                                        Class<?> scoreboardManagerClass = scoreboardManager.getClass();
                                        java.lang.reflect.Method updateMethod = scoreboardManagerClass.getMethod("updatePlayerBalance", UUID.class);
                                        updateMethod.invoke(scoreboardManager, uuid);
                                    } catch (Exception ex) {
                                        plugin.getLogger().warning("스코어보드 업데이트 실패: " + ex.getMessage());
                                    }
                                }
                            });
                        });
                    } else {
                        // 플레이어가 오프라인이면 기본 업데이트만
                        Class<?> scoreboardManagerClass = scoreboardManager.getClass();
                        java.lang.reflect.Method updateMethod = scoreboardManagerClass.getMethod("updatePlayerBalance", UUID.class);
                        updateMethod.invoke(scoreboardManager, uuid);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("GGMCore 스코어보드 업데이트 실패: " + e.getMessage());
        }
    }

    /**
     * 플레이어 잔액 조회
     */
    public CompletableFuture<Long> getBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = "SELECT balance FROM ggm_economy WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getLong("balance");
                        }
                    }
                }
                return 0L;
            } catch (SQLException e) {
                plugin.getLogger().severe("잔액 조회 실패: " + e.getMessage());
                return 0L;
            }
        });
    }

    /**
     * 플레이어에게 돈 추가 - 스코어보드 업데이트 포함
     */
    public CompletableFuture<Boolean> addMoney(UUID uuid, String playerName, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 먼저 플레이어 존재 확인/생성
                databaseManager.createOrUpdatePlayer(uuid, playerName).join();

                String sql = "UPDATE ggm_economy SET balance = balance + ? WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, amount);
                    stmt.setString(2, uuid.toString());
                    int result = stmt.executeUpdate();

                    if (result > 0) {
                        plugin.getLogger().info(String.format("[경제] %s에게 %,dG 지급", playerName, amount));

                        // GGMCore 스코어보드 업데이트
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            updateGGMCoreScoreboard(uuid, amount);
                        });

                        return true;
                    }
                }
                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("돈 추가 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 플레이어에게서 돈 차감 - 스코어보드 업데이트 포함
     */
    public CompletableFuture<Boolean> removeMoney(UUID uuid, String playerName, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 잔액 확인
                long currentBalance = getBalance(uuid).join();
                if (currentBalance < amount) {
                    return false; // 잔액 부족
                }

                String sql = "UPDATE ggm_economy SET balance = balance - ? WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, amount);
                    stmt.setString(2, uuid.toString());
                    int result = stmt.executeUpdate();

                    if (result > 0) {
                        plugin.getLogger().info(String.format("[경제] %s에게서 %,dG 차감", playerName, amount));

                        // GGMCore 스코어보드 업데이트
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            updateGGMCoreScoreboard(uuid, -amount);
                        });

                        return true;
                    }
                }
                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("돈 차감 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 잔액 설정 - 스코어보드 업데이트 포함
     */
    public CompletableFuture<Boolean> setBalance(UUID uuid, String playerName, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 현재 잔액 조회
                long oldBalance = getBalance(uuid).join();

                // 먼저 플레이어 존재 확인/생성
                databaseManager.createOrUpdatePlayer(uuid, playerName).join();

                String sql = "UPDATE ggm_economy SET balance = ? WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, amount);
                    stmt.setString(2, uuid.toString());
                    int result = stmt.executeUpdate();

                    if (result > 0) {
                        plugin.getLogger().info(String.format("[경제] %s의 잔액을 %,dG로 설정", playerName, amount));

                        // GGMCore 스코어보드 업데이트
                        long change = amount - oldBalance;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            updateGGMCoreScoreboard(uuid, change);
                        });

                        return true;
                    }
                }
                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("잔액 설정 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * GGMCore 연동 상태 확인
     */
    public boolean isGGMCoreConnected() {
        return ggmCoreConnected;
    }

    /**
     * 금액 포맷팅
     */
    public String formatMoney(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * 플레이어 이름으로 UUID 조회
     */
    public CompletableFuture<UUID> getPlayerUUID(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            return offlinePlayer.hasPlayedBefore() ? offlinePlayer.getUniqueId() : null;
        });
    }
}