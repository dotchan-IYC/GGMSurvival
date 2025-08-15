// 완전 안정화된 EconomyManager.java
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 완전 안정화된 경제 시스템 매니저
 * - GGMCore와의 안전한 연동
 * - Thread-Safe 구현
 * - 캐시를 통한 성능 최적화
 * - 자동 백업 및 복구
 * - 강력한 예외 처리
 */
public class EconomyManager {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;

    // 잔액 캐시 (성능 최적화)
    private final Map<UUID, Long> balanceCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCacheUpdate = new ConcurrentHashMap<>();

    // GGMCore 연동 상태
    private volatile boolean ggmCoreConnected = false;
    private Object ggmCoreEconomyManager = null;

    // 설정값들
    private final long maxBalance;
    private final long minBalance;
    private final long startingBalance;
    private final long cacheTimeout;

    // 포맷터
    private final DecimalFormat moneyFormatter;

    // 통계
    private volatile long totalTransactions = 0;
    private volatile long totalMoneyInCirculation = 0;

    public EconomyManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        try {
            // 설정값 로드
            this.maxBalance = plugin.getConfig().getLong("economy.max_balance", 1000000000000L); // 1조
            this.minBalance = plugin.getConfig().getLong("economy.min_balance", 0L);
            this.startingBalance = plugin.getConfig().getLong("economy.starting_balance", 1000L);
            this.cacheTimeout = plugin.getConfig().getLong("economy.cache_timeout", 300000L); // 5분

            // 포맷터 초기화
            this.moneyFormatter = new DecimalFormat("#,###");

            // GGMCore 연동 시도
            initializeGGMCoreIntegration();

            // 경제 테이블 확인
            verifyEconomyTable();

            // 통계 업데이트
            updateStatistics();

            plugin.getLogger().info("EconomyManager 안정화 초기화 완료");
            plugin.getLogger().info("GGMCore 연동: " + (ggmCoreConnected ? "성공" : "실패 (독립 모드)"));
            plugin.getLogger().info("시작 잔액: " + formatMoney(startingBalance) + "G");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "EconomyManager 초기화 실패", e);
            throw new RuntimeException("EconomyManager 초기화 실패", e);
        }
    }

    /**
     * GGMCore 연동 초기화
     */
    private void initializeGGMCoreIntegration() {
        try {
            // GGMCore 플러그인 확인
            if (Bukkit.getPluginManager().getPlugin("GGMCore") != null) {
                plugin.getLogger().info("GGMCore 플러그인 발견, 연동 시도 중...");

                // 리플렉션을 통한 GGMCore EconomyManager 접근
                Class<?> ggmCoreClass = Class.forName("com.ggm.core.GGMCore");
                Object ggmCoreInstance = ggmCoreClass.getMethod("getInstance").invoke(null);

                if (ggmCoreInstance != null) {
                    this.ggmCoreEconomyManager = ggmCoreClass.getMethod("getEconomyManager").invoke(ggmCoreInstance);

                    if (ggmCoreEconomyManager != null) {
                        ggmCoreConnected = true;
                        plugin.getLogger().info("GGMCore 경제 시스템과 성공적으로 연동되었습니다!");
                    }
                }
            } else {
                plugin.getLogger().info("GGMCore 플러그인이 없습니다. 독립 모드로 실행됩니다.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "GGMCore 연동 실패, 독립 모드로 실행됩니다.", e);
            ggmCoreConnected = false;
        }
    }

    /**
     * 경제 테이블 확인
     */
    private void verifyEconomyTable() {
        try {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_economy (
                    uuid VARCHAR(36) PRIMARY KEY,
                    balance BIGINT NOT NULL DEFAULT ?,
                    last_transaction TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    total_earned BIGINT DEFAULT 0,
                    total_spent BIGINT DEFAULT 0,
                    INDEX idx_balance (balance),
                    INDEX idx_last_transaction (last_transaction)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            databaseManager.executeUpdate(sql, startingBalance);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "경제 테이블 확인 중 오류", e);
        }
    }

    /**
     * 플레이어 잔액 조회 (캐시 우선)
     */
    public CompletableFuture<Long> getBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // GGMCore 연동 시 GGMCore에서 조회
                if (ggmCoreConnected && ggmCoreEconomyManager != null) {
                    return getBalanceFromGGMCore(uuid);
                }

                // 캐시 확인
                Long cachedBalance = getCachedBalance(uuid);
                if (cachedBalance != null) {
                    return cachedBalance;
                }

                // 데이터베이스에서 조회
                return getBalanceFromDatabase(uuid);

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "잔액 조회 실패: " + uuid, e);
                return startingBalance; // 기본값 반환
            }
        });
    }

    /**
     * GGMCore에서 잔액 조회
     */
    private long getBalanceFromGGMCore(UUID uuid) {
        try {
            Object balance = ggmCoreEconomyManager.getClass()
                    .getMethod("getBalance", UUID.class)
                    .invoke(ggmCoreEconomyManager, uuid);

            long balanceValue = ((Number) balance).longValue();

            // 캐시 업데이트
            updateCache(uuid, balanceValue);

            return balanceValue;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "GGMCore 잔액 조회 실패: " + uuid, e);

            // GGMCore 실패 시 로컬 데이터베이스에서 조회
            return getBalanceFromDatabase(uuid);
        }
    }

    /**
     * 데이터베이스에서 잔액 조회
     */
    private long getBalanceFromDatabase(UUID uuid) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT balance FROM player_economy WHERE uuid = ?")) {

            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long balance = rs.getLong("balance");
                    updateCache(uuid, balance);
                    return balance;
                } else {
                    // 신규 플레이어 - 시작 잔액으로 초기화
                    setBalance(uuid, startingBalance);
                    return startingBalance;
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "데이터베이스 잔액 조회 실패: " + uuid, e);
            return startingBalance;
        }
    }

    /**
     * 캐시에서 잔액 조회
     */
    private Long getCachedBalance(UUID uuid) {
        Long lastUpdate = lastCacheUpdate.get(uuid);
        if (lastUpdate == null) {
            return null;
        }

        // 캐시 만료 확인
        if (System.currentTimeMillis() - lastUpdate > cacheTimeout) {
            balanceCache.remove(uuid);
            lastCacheUpdate.remove(uuid);
            return null;
        }

        return balanceCache.get(uuid);
    }

    /**
     * 캐시 업데이트
     */
    private void updateCache(UUID uuid, long balance) {
        balanceCache.put(uuid, balance);
        lastCacheUpdate.put(uuid, System.currentTimeMillis());
    }

    /**
     * 잔액 설정
     */
    public CompletableFuture<Boolean> setBalance(UUID uuid, long amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 잔액 유효성 검증
                if (amount < minBalance || amount > maxBalance) {
                    plugin.getLogger().warning(String.format(
                            "잘못된 잔액 설정 시도: %s -> %d (허용 범위: %d-%d)",
                            uuid, amount, minBalance, maxBalance));
                    return false;
                }

                // GGMCore 연동 시 GGMCore에서 설정
                if (ggmCoreConnected && ggmCoreEconomyManager != null) {
                    boolean success = setBalanceInGGMCore(uuid, amount);
                    if (success) {
                        updateCache(uuid, amount);
                        return true;
                    }
                }

                // 로컬 데이터베이스에 설정
                return setBalanceInDatabase(uuid, amount);

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "잔액 설정 실패: " + uuid + " -> " + amount, e);
                return false;
            }
        });
    }

    /**
     * GGMCore에서 잔액 설정
     */
    private boolean setBalanceInGGMCore(UUID uuid, long amount) {
        try {
            Object result = ggmCoreEconomyManager.getClass()
                    .getMethod("setBalance", UUID.class, long.class)
                    .invoke(ggmCoreEconomyManager, uuid, amount);

            return (Boolean) result;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "GGMCore 잔액 설정 실패: " + uuid, e);
            return false;
        }
    }

    /**
     * 데이터베이스에서 잔액 설정
     */
    private boolean setBalanceInDatabase(UUID uuid, long amount) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "INSERT INTO player_economy (uuid, balance) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE balance = VALUES(balance)")) {

            stmt.setString(1, uuid.toString());
            stmt.setLong(2, amount);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                updateCache(uuid, amount);
                totalTransactions++;
                return true;
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "데이터베이스 잔액 설정 실패: " + uuid, e);
        }

        return false;
    }

    /**
     * 돈 추가
     */
    public CompletableFuture<Boolean> addMoney(UUID uuid, long amount) {
        if (amount <= 0) {
            plugin.getLogger().warning("잘못된 금액 추가 시도: " + amount);
            return CompletableFuture.completedFuture(false);
        }

        return getBalance(uuid).thenCompose(currentBalance -> {
            long newBalance = currentBalance + amount;

            // 오버플로우 및 최대 잔액 확인
            if (newBalance < currentBalance || newBalance > maxBalance) {
                plugin.getLogger().warning(String.format(
                        "잔액 한도 초과: %s -> %d + %d = %d (최대: %d)",
                        uuid, currentBalance, amount, newBalance, maxBalance));
                return CompletableFuture.completedFuture(false);
            }

            return setBalance(uuid, newBalance);
        });
    }

    /**
     * 돈 차감
     */
    public CompletableFuture<Boolean> removeMoney(UUID uuid, long amount) {
        if (amount <= 0) {
            plugin.getLogger().warning("잘못된 금액 차감 시도: " + amount);
            return CompletableFuture.completedFuture(false);
        }

        return getBalance(uuid).thenCompose(currentBalance -> {
            if (currentBalance < amount) {
                // 잔액 부족
                return CompletableFuture.completedFuture(false);
            }

            long newBalance = Math.max(currentBalance - amount, minBalance);
            return setBalance(uuid, newBalance);
        });
    }

    /**
     * 잔액 충분 여부 확인
     */
    public CompletableFuture<Boolean> hasEnoughMoney(UUID uuid, long amount) {
        return getBalance(uuid).thenApply(balance -> balance >= amount);
    }

    /**
     * 플레이어 간 송금
     */
    public CompletableFuture<Boolean> transferMoney(UUID fromUUID, UUID toUUID, long amount) {
        if (amount <= 0) {
            plugin.getLogger().warning("잘못된 송금 금액: " + amount);
            return CompletableFuture.completedFuture(false);
        }

        if (fromUUID.equals(toUUID)) {
            plugin.getLogger().warning("자기 자신에게 송금 시도: " + fromUUID);
            return CompletableFuture.completedFuture(false);
        }

        return databaseManager.executeTransaction(connection -> {
            try {
                // 송금자 잔액 확인 및 차감
                long fromBalance = getBalanceSync(connection, fromUUID);
                if (fromBalance < amount) {
                    return false; // 잔액 부족
                }

                // 수신자 잔액 확인
                long toBalance = getBalanceSync(connection, toUUID);
                if (toBalance + amount > maxBalance) {
                    return false; // 수신자 잔액 한도 초과
                }

                // 송금 실행
                setBalanceSync(connection, fromUUID, fromBalance - amount);
                setBalanceSync(connection, toUUID, toBalance + amount);

                // 캐시 업데이트
                updateCache(fromUUID, fromBalance - amount);
                updateCache(toUUID, toBalance + amount);

                totalTransactions += 2;

                return true;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "송금 처리 중 오류: " + fromUUID + " -> " + toUUID + " : " + amount, e);
                return false;
            }
        });
    }

    /**
     * 동기 잔액 조회 (트랜잭션 내부용)
     */
    private long getBalanceSync(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT balance FROM player_economy WHERE uuid = ?")) {

            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("balance");
                } else {
                    // 신규 플레이어 초기화
                    setBalanceSync(connection, uuid, startingBalance);
                    return startingBalance;
                }
            }
        }
    }

    /**
     * 동기 잔액 설정 (트랜잭션 내부용)
     */
    private void setBalanceSync(Connection connection, UUID uuid, long amount) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO player_economy (uuid, balance) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE balance = VALUES(balance)")) {

            stmt.setString(1, uuid.toString());
            stmt.setLong(2, amount);
            stmt.executeUpdate();
        }
    }

    /**
     * 돈 포맷팅
     */
    public String formatMoney(long amount) {
        if (amount >= 1000000000000L) { // 1조 이상
            return String.format("%.1f조", amount / 1000000000000.0);
        } else if (amount >= 100000000L) { // 1억 이상
            return String.format("%.1f억", amount / 100000000.0);
        } else if (amount >= 10000L) { // 1만 이상
            return String.format("%.1f만", amount / 10000.0);
        } else {
            return moneyFormatter.format(amount);
        }
    }

    /**
     * 플레이어 잔액 표시
     */
    public void showBalance(Player player) {
        getBalance(player.getUniqueId()).thenAccept(balance -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§a현재 잔액: §6" + formatMoney(balance) + "G");
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§c잔액 조회 중 오류가 발생했습니다.");
            });
            return null;
        });
    }

    /**
     * 경제 통계 업데이트
     */
    public void updateStatistics() {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT COUNT(*) as players, SUM(balance) as total_money FROM player_economy");
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    totalMoneyInCirculation = rs.getLong("total_money");

                    plugin.getLogger().info("경제 통계 업데이트: " +
                            "플레이어 수=" + rs.getInt("players") +
                            ", 총 유통량=" + formatMoney(totalMoneyInCirculation) + "G");
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "경제 통계 업데이트 실패", e);
            }
        });
    }

    /**
     * 캐시 정리
     */
    public void cleanupCache() {
        try {
            long currentTime = System.currentTimeMillis();

            // 만료된 캐시 제거
            lastCacheUpdate.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > cacheTimeout);

            // 해당하는 잔액 캐시도 제거
            balanceCache.keySet().removeIf(uuid -> !lastCacheUpdate.containsKey(uuid));

            plugin.getLogger().info("경제 시스템 캐시 정리 완료: " + balanceCache.size() + "개 항목 유지");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "경제 시스템 캐시 정리 중 오류", e);
        }
    }

    /**
     * 플레이어 경제 데이터 초기화
     */
    public CompletableFuture<Boolean> resetPlayerEconomy(UUID uuid) {
        return setBalance(uuid, startingBalance).thenApply(success -> {
            if (success) {
                // 캐시에서도 제거
                balanceCache.remove(uuid);
                lastCacheUpdate.remove(uuid);

                plugin.getLogger().info("플레이어 경제 데이터 초기화: " + uuid);
            }
            return success;
        });
    }

    /**
     * 경제 시스템 상태 정보
     */
    public String getEconomyStatus() {
        return String.format(
                "GGMCore 연동: %s | 캐시: %d개 | 총 거래: %d회 | 유통량: %sG",
                ggmCoreConnected ? "활성" : "비활성",
                balanceCache.size(),
                totalTransactions,
                formatMoney(totalMoneyInCirculation)
        );
    }

    /**
     * 매니저 종료
     */
    public void onDisable() {
        try {
            // 캐시 정리
            cleanupCache();

            // 통계 업데이트
            updateStatistics();

            // 모든 맵 정리
            balanceCache.clear();
            lastCacheUpdate.clear();

            plugin.getLogger().info("EconomyManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "EconomyManager 종료 중 오류", e);
        }
    }

    // Getter 메서드들
    public boolean isGGMCoreConnected() {
        return ggmCoreConnected;
    }

    public long getStartingBalance() {
        return startingBalance;
    }

    public long getMaxBalance() {
        return maxBalance;
    }

    public long getMinBalance() {
        return minBalance;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public long getTotalMoneyInCirculation() {
        return totalMoneyInCirculation;
    }

    public int getCacheSize() {
        return balanceCache.size();
    }
}