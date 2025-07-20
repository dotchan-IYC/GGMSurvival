package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

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
     * 플레이어에게 돈 추가
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
     * 플레이어에게서 돈 차감
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
     * 잔액 설정
     */
    public CompletableFuture<Boolean> setBalance(UUID uuid, String playerName, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 먼저 플레이어 존재 확인/생성
                databaseManager.createOrUpdatePlayer(uuid, playerName).join();

                String sql = "UPDATE ggm_economy SET balance = ? WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, amount);
                    stmt.setString(2, uuid.toString());
                    int result = stmt.executeUpdate();

                    if (result > 0) {
                        plugin.getLogger().info(String.format("[경제] %s의 잔액을 %,dG로 설정", playerName, amount));
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
     * 잔액 포맷팅
     */
    public String formatMoney(long amount) {
        return String.format("%,dG", amount);
    }
}
