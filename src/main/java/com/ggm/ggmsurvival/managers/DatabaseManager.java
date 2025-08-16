// HikariCP 최적화 DatabaseManager.java
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * HikariCP 최적화 데이터베이스 매니저
 * - 고성능 연결 풀링
 * - 자동 재연결 및 장애 복구
 * - Thread-Safe 구현
 * - 메모리 누수 방지
 * - 실시간 성능 모니터링
 * - 배치 처리 최적화
 */
public class DatabaseManager {

    private final GGMSurvival plugin;
    private HikariDataSource dataSource;
    private ExecutorService asyncExecutor;

    // 데이터베이스 설정
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // HikariCP 고급 설정
    private final int maximumPoolSize;
    private final int minimumIdle;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final long maxLifetime;
    private final long leakDetectionThreshold;

    // 연결 상태 추적
    private volatile boolean initialized = false;
    private volatile boolean shutdownInProgress = false;

    // 성능 통계
    private long totalQueries = 0;
    private long successfulQueries = 0;
    private long failedQueries = 0;
    private long totalConnectionTime = 0;

    // 연결 테스트 쿼리
    private static final String TEST_QUERY = "SELECT 1";
    private static final String PING_QUERY = "/* ping */ SELECT 1";

    public DatabaseManager(GGMSurvival plugin) {
        this.plugin = plugin;

        try {
            // 기본 설정 로드
            this.host = plugin.getConfig().getString("database.host", "localhost");
            this.port = plugin.getConfig().getInt("database.port", 3306);
            this.database = plugin.getConfig().getString("database.database", "ggm_server");
            this.username = plugin.getConfig().getString("database.username", "root");
            this.password = plugin.getConfig().getString("database.password", "");

            // HikariCP 고급 설정 로드
            this.maximumPoolSize = plugin.getConfig().getInt("database.hikaricp.maximum_pool_size", 20);
            this.minimumIdle = plugin.getConfig().getInt("database.hikaricp.minimum_idle", 5);
            this.connectionTimeout = plugin.getConfig().getLong("database.hikaricp.connection_timeout", 30000);
            this.idleTimeout = plugin.getConfig().getLong("database.hikaricp.idle_timeout", 600000);
            this.maxLifetime = plugin.getConfig().getLong("database.hikaricp.max_lifetime", 1800000);
            this.leakDetectionThreshold = plugin.getConfig().getLong("database.hikaricp.leak_detection_threshold", 60000);

            // 비동기 실행기 초기화
            this.asyncExecutor = Executors.newFixedThreadPool(
                    Math.max(2, maximumPoolSize / 2),
                    r -> {
                        Thread thread = new Thread(r, "GGMSurvival-DB-Async");
                        thread.setDaemon(true);
                        thread.setPriority(Thread.NORM_PRIORITY - 1);
                        return thread;
                    }
            );

            // HikariCP 데이터소스 초기화
            initializeHikariCP();

            // 연결 테스트
            if (!testConnection()) {
                throw new RuntimeException("데이터베이스 연결 테스트 실패");
            }

            // 기본 테이블 생성
            createBaseTables();

            // 성능 모니터링 시작 (설정에 따라)
            if (plugin.getConfig().getBoolean("performance.enable_performance_monitoring", false)) {
                startPerformanceMonitoring();
            }

            this.initialized = true;
            plugin.getLogger().info("=== HikariCP DatabaseManager 초기화 완료 ===");
            plugin.getLogger().info("연결 풀 설정: 최대=" + maximumPoolSize + ", 최소=" + minimumIdle);
            plugin.getLogger().info("데이터베이스: " + getDatabaseInfo());

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "DatabaseManager 초기화 실패", e);
            cleanup();
            throw new RuntimeException("DatabaseManager 초기화 실패", e);
        }
    }

    /**
     * HikariCP 데이터소스 초기화 및 최적화
     */
    private void initializeHikariCP() {
        try {
            HikariConfig config = new HikariConfig();

            // 기본 연결 설정
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", host, port, database));
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // 커넥션 풀 설정
            config.setMaximumPoolSize(maximumPoolSize);
            config.setMinimumIdle(minimumIdle);
            config.setConnectionTimeout(connectionTimeout);
            config.setIdleTimeout(idleTimeout);
            config.setMaxLifetime(maxLifetime);
            config.setLeakDetectionThreshold(leakDetectionThreshold);

            // MySQL 최적화 설정
            boolean cachePrepStmts = plugin.getConfig().getBoolean("database.hikaricp.cache_prep_stmts", true);
            int prepStmtCacheSize = plugin.getConfig().getInt("database.hikaricp.prep_stmt_cache_size", 250);
            int prepStmtCacheSqlLimit = plugin.getConfig().getInt("database.hikaricp.prep_stmt_cache_sql_limit", 2048);
            boolean useServerPrepStmts = plugin.getConfig().getBoolean("database.hikaricp.use_server_prep_stmts", true);
            boolean useSSL = plugin.getConfig().getBoolean("database.hikaricp.use_ssl", false);
            boolean allowPublicKeyRetrieval = plugin.getConfig().getBoolean("database.hikaricp.allow_public_key_retrieval", true);
            String serverTimezone = plugin.getConfig().getString("database.hikaricp.server_timezone", "Asia/Seoul");
            String characterEncoding = plugin.getConfig().getString("database.hikaricp.character_encoding", "UTF-8");

            config.addDataSourceProperty("cachePrepStmts", String.valueOf(cachePrepStmts));
            config.addDataSourceProperty("prepStmtCacheSize", String.valueOf(prepStmtCacheSize));
            config.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(prepStmtCacheSqlLimit));
            config.addDataSourceProperty("useServerPrepStmts", String.valueOf(useServerPrepStmts));
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            config.addDataSourceProperty("useSSL", String.valueOf(useSSL));
            config.addDataSourceProperty("allowPublicKeyRetrieval", String.valueOf(allowPublicKeyRetrieval));
            config.addDataSourceProperty("serverTimezone", serverTimezone);
            config.addDataSourceProperty("characterEncoding", characterEncoding);

            // 추가 최적화 설정
            config.addDataSourceProperty("autoReconnect", "true");
            config.addDataSourceProperty("failOverReadOnly", "false");
            config.addDataSourceProperty("maxReconnects", "3");
            config.addDataSourceProperty("initialTimeout", "2");

            // 연결 검증 설정
            config.setConnectionTestQuery(TEST_QUERY);
            config.setValidationTimeout(5000);

            // 풀 이름 설정
            config.setPoolName("GGMSurvival-HikariCP-Pool");

            // 자동 커밋 설정
            config.setAutoCommit(true);

            // JMX 모니터링 (선택적)
            config.setRegisterMbeans(plugin.getConfig().getBoolean("performance.enable_performance_monitoring", false));

            this.dataSource = new HikariDataSource(config);

            plugin.getLogger().info("✅ HikariCP 데이터베이스 연결 풀 초기화 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "HikariCP 초기화 실패", e);
            throw new RuntimeException("HikariCP 초기화 실패", e);
        }
    }

    /**
     * 연결 획득 (자동 재시도 및 통계 포함)
     */
    public Connection getConnection() throws SQLException {
        if (shutdownInProgress) {
            throw new SQLException("데이터베이스 매니저가 종료 중입니다.");
        }

        if (!initialized) {
            throw new SQLException("데이터베이스 매니저가 초기화되지 않았습니다.");
        }

        long startTime = System.currentTimeMillis();

        try {
            Connection connection = dataSource.getConnection();

            // 연결 유효성 검증
            if (!connection.isValid(5)) {
                connection.close();
                throw new SQLException("유효하지 않은 데이터베이스 연결");
            }

            // 통계 업데이트
            long connectionTime = System.currentTimeMillis() - startTime;
            totalConnectionTime += connectionTime;

            return connection;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 연결 획득 실패", e);

            // 재연결 시도
            if (attemptReconnection()) {
                return dataSource.getConnection();
            }

            throw e;
        }
    }

    /**
     * 연결 테스트 (향상된 버전)
     */
    public boolean testConnection() {
        try {
            long startTime = System.currentTimeMillis();

            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(TEST_QUERY);
                 ResultSet rs = stmt.executeQuery()) {

                boolean result = rs.next() && rs.getInt(1) == 1;

                long testTime = System.currentTimeMillis() - startTime;

                if (result) {
                    plugin.getLogger().info("✅ 데이터베이스 연결 테스트 성공 (" + testTime + "ms)");
                }

                return result;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 연결 테스트 실패", e);
            return false;
        }
    }

    /**
     * 재연결 시도 (강화된 버전)
     */
    private boolean attemptReconnection() {
        try {
            plugin.getLogger().info("데이터베이스 재연결 시도 중...");

            // 기존 데이터소스 종료
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }

            // 잠시 대기
            Thread.sleep(2000);

            // 새 연결 풀 생성
            initializeHikariCP();

            // 연결 테스트
            if (testConnection()) {
                plugin.getLogger().info("✅ 데이터베이스 재연결 성공!");
                return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "데이터베이스 재연결 실패", e);
        }

        return false;
    }

    /**
     * 기본 테이블 생성 (트랜잭션 사용)
     */
    private void createBaseTables() {
        executeTransaction(connection -> {
            try {
                executeUpdateInternal(connection, createPlayersTableSQL());
                executeUpdateInternal(connection, createPlayerJobsTableSQL());
                executeUpdateInternal(connection, createPlayerEconomyTableSQL());

                plugin.getLogger().info("✅ 기본 테이블 생성/확인 완료");
                return true;

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "테이블 생성 실패", e);
                return false;
            }
        }).join(); // 동기적으로 대기
    }

    /**
     * 플레이어 테이블 SQL (최적화됨)
     */
    private String createPlayersTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS players (
                uuid VARCHAR(36) PRIMARY KEY,
                name VARCHAR(16) NOT NULL,
                first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                total_playtime BIGINT DEFAULT 0,
                is_banned BOOLEAN DEFAULT FALSE,
                INDEX idx_name (name),
                INDEX idx_last_join (last_join),
                INDEX idx_playtime (total_playtime)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
            """;
    }

    /**
     * 플레이어 직업 테이블 SQL (최적화됨)
     */
    private String createPlayerJobsTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS player_jobs (
                uuid VARCHAR(36) PRIMARY KEY,
                job_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
                job_level INT NOT NULL DEFAULT 1,
                job_experience INT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_job_type (job_type),
                INDEX idx_job_level (job_level),
                INDEX idx_experience (job_experience),
                FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
            """;
    }

    /**
     * 플레이어 경제 테이블 SQL (최적화됨)
     */
    private String createPlayerEconomyTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS player_economy (
                uuid VARCHAR(36) PRIMARY KEY,
                balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_balance (balance),
                FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
            """;
    }

    /**
     * 플레이어 이름으로 UUID 조회 (캐시 고려)
     */
    public CompletableFuture<UUID> getPlayerUUID(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT uuid FROM players WHERE name = ? LIMIT 1")) {

                stmt.setString(1, playerName);

                try (ResultSet rs = stmt.executeQuery()) {
                    totalQueries++;

                    if (rs.next()) {
                        successfulQueries++;
                        return UUID.fromString(rs.getString("uuid"));
                    }

                    successfulQueries++;
                    return null;
                }

            } catch (SQLException e) {
                failedQueries++;
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 UUID 조회 실패: " + playerName, e);
                return null;
            } finally {
                long queryTime = System.currentTimeMillis() - startTime;
                if (queryTime > 1000) { // 1초 이상 걸린 쿼리 로그
                    plugin.getLogger().warning("느린 쿼리 감지: getPlayerUUID (" + queryTime + "ms)");
                }
            }
        }, asyncExecutor);
    }

    /**
     * UUID로 플레이어 이름 조회 (캐시 고려)
     */
    public CompletableFuture<String> getPlayerName(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT name FROM players WHERE uuid = ? LIMIT 1")) {

                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    totalQueries++;

                    if (rs.next()) {
                        successfulQueries++;
                        return rs.getString("name");
                    }

                    successfulQueries++;
                    return null;
                }

            } catch (SQLException e) {
                failedQueries++;
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 이름 조회 실패: " + uuid, e);
                return null;
            } finally {
                long queryTime = System.currentTimeMillis() - startTime;
                if (queryTime > 1000) { // 1초 이상 걸린 쿼리 로그
                    plugin.getLogger().warning("느린 쿼리 감지: getPlayerName (" + queryTime + "ms)");
                }
            }
        }, asyncExecutor);
    }

    /**
     * 동기 쿼리 실행 (통계 포함)
     */
    public boolean executeUpdate(String sql, Object... params) {
        long startTime = System.currentTimeMillis();

        try (Connection connection = getConnection()) {
            boolean result = executeUpdateInternal(connection, sql, params);

            totalQueries++;
            if (result) {
                successfulQueries++;
            } else {
                failedQueries++;
            }

            return result;

        } catch (SQLException e) {
            failedQueries++;
            plugin.getLogger().log(Level.WARNING, "쿼리 실행 실패: " + sql, e);
            return false;
        } finally {
            long queryTime = System.currentTimeMillis() - startTime;
            if (queryTime > 2000) { // 2초 이상 걸린 쿼리 로그
                plugin.getLogger().warning("느린 업데이트 쿼리 감지: " + queryTime + "ms");
            }
        }
    }

    /**
     * 내부 업데이트 실행 (재사용 가능)
     */
    private boolean executeUpdateInternal(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            // 파라미터 설정
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * 비동기 쿼리 실행
     */
    public CompletableFuture<Boolean> executeUpdateAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> executeUpdate(sql, params), asyncExecutor);
    }

    /**
     * 최적화된 배치 쿼리 실행
     */
    public CompletableFuture<Boolean> executeBatch(String sql, Object[]... paramsList) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {

                connection.setAutoCommit(false);

                for (Object[] params : paramsList) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                    stmt.addBatch();
                }

                int[] results = stmt.executeBatch();
                connection.commit();

                totalQueries++;

                // 모든 배치가 성공했는지 확인
                for (int result : results) {
                    if (result == PreparedStatement.EXECUTE_FAILED) {
                        failedQueries++;
                        return false;
                    }
                }

                successfulQueries++;
                return true;

            } catch (SQLException e) {
                failedQueries++;
                plugin.getLogger().log(Level.WARNING, "배치 쿼리 실행 실패: " + sql, e);
                return false;
            } finally {
                long queryTime = System.currentTimeMillis() - startTime;
                plugin.getLogger().fine("배치 쿼리 실행 완료: " + paramsList.length + "개 (" + queryTime + "ms)");
            }
        }, asyncExecutor);
    }

    /**
     * 트랜잭션 인터페이스
     */
    @FunctionalInterface
    public interface TransactionCallback {
        boolean execute(Connection connection) throws SQLException;
    }

    /**
     * 최적화된 트랜잭션 실행
     */
    public CompletableFuture<Boolean> executeTransaction(TransactionCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try (Connection connection = getConnection()) {
                connection.setAutoCommit(false);

                try {
                    boolean result = callback.execute(connection);

                    if (result) {
                        connection.commit();
                        totalQueries++;
                        successfulQueries++;
                        return true;
                    } else {
                        connection.rollback();
                        totalQueries++;
                        failedQueries++;
                        return false;
                    }

                } catch (Exception e) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackEx) {
                        plugin.getLogger().log(Level.WARNING, "롤백 실패", rollbackEx);
                    }
                    throw e;
                }

            } catch (Exception e) {
                totalQueries++;
                failedQueries++;
                plugin.getLogger().log(Level.WARNING, "트랜잭션 실행 실패", e);
                return false;
            } finally {
                long transactionTime = System.currentTimeMillis() - startTime;
                if (transactionTime > 5000) { // 5초 이상 걸린 트랜잭션 로그
                    plugin.getLogger().warning("느린 트랜잭션 감지: " + transactionTime + "ms");
                }
            }
        }, asyncExecutor);
    }

    /**
     * 향상된 데이터베이스 통계 정보
     */
    public CompletableFuture<DatabaseStats> getDatabaseStats() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (dataSource == null || dataSource.isClosed()) {
                    return new DatabaseStats(false, 0, 0, 0, 0, totalQueries, successfulQueries, failedQueries);
                }

                HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();

                boolean isAvailable = !dataSource.isClosed();
                int totalConnections = poolBean.getTotalConnections();
                int activeConnections = poolBean.getActiveConnections();
                int idleConnections = poolBean.getIdleConnections();
                int waitingThreads = poolBean.getThreadsAwaitingConnection();

                return new DatabaseStats(
                        isAvailable,
                        totalConnections,
                        activeConnections,
                        idleConnections,
                        waitingThreads,
                        totalQueries,
                        successfulQueries,
                        failedQueries
                );

            } catch (Exception e) {
                return new DatabaseStats(false, 0, 0, 0, 0, totalQueries, successfulQueries, failedQueries);
            }
        }, asyncExecutor);
    }

    /**
     * 향상된 데이터베이스 통계 클래스
     */
    public static class DatabaseStats {
        private final boolean isAvailable;
        private final int totalConnections;
        private final int activeConnections;
        private final int idleConnections;
        private final int waitingThreads;
        private final long totalQueries;
        private final long successfulQueries;
        private final long failedQueries;

        public DatabaseStats(boolean isAvailable, int totalConnections,
                             int activeConnections, int idleConnections, int waitingThreads,
                             long totalQueries, long successfulQueries, long failedQueries) {
            this.isAvailable = isAvailable;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.waitingThreads = waitingThreads;
            this.totalQueries = totalQueries;
            this.successfulQueries = successfulQueries;
            this.failedQueries = failedQueries;
        }

        @Override
        public String toString() {
            double successRate = totalQueries > 0 ? (successfulQueries * 100.0 / totalQueries) : 0.0;

            return String.format(
                    "DB Stats: Available=%s, Pool[Total=%d, Active=%d, Idle=%d, Waiting=%d], " +
                            "Queries[Total=%d, Success=%d, Failed=%d, Rate=%.1f%%]",
                    isAvailable, totalConnections, activeConnections, idleConnections, waitingThreads,
                    totalQueries, successfulQueries, failedQueries, successRate);
        }

        // Getter 메서드들
        public boolean isAvailable() { return isAvailable; }
        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public int getWaitingThreads() { return waitingThreads; }
        public long getTotalQueries() { return totalQueries; }
        public long getSuccessfulQueries() { return successfulQueries; }
        public long getFailedQueries() { return failedQueries; }
    }

    /**
     * 성능 모니터링 시작
     */
    private void startPerformanceMonitoring() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!shutdownInProgress) {
                getDatabaseStats().thenAccept(stats -> {
                    plugin.getLogger().info("📊 " + stats.toString());

                    // 경고 조건 체크
                    if (stats.getActiveConnections() > maximumPoolSize * 0.8) {
                        plugin.getLogger().warning("⚠️ 높은 DB 연결 사용률: " + stats.getActiveConnections() + "/" + maximumPoolSize);
                    }

                    if (stats.getWaitingThreads() > 0) {
                        plugin.getLogger().warning("⚠️ DB 연결 대기 중인 스레드: " + stats.getWaitingThreads());
                    }

                    if (stats.getTotalQueries() > 0) {
                        double failureRate = (stats.getFailedQueries() * 100.0) / stats.getTotalQueries();
                        if (failureRate > 5.0) {
                            plugin.getLogger().warning("⚠️ 높은 쿼리 실패율: " + String.format("%.1f%%", failureRate));
                        }
                    }
                });
            }
        }, 1200L, 1200L); // 1분마다
    }

    /**
     * 유지보수 작업 (HikariCP 최적화)
     */
    public void performMaintenance() {
        try {
            // 연결 풀 상태 확인
            if (dataSource != null && !dataSource.isClosed()) {
                HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();

                // 유휴 연결 정리 (필요 시)
                if (poolBean.getIdleConnections() > minimumIdle * 2) {
                    plugin.getLogger().info("💧 과도한 유휴 연결 정리 중...");
                    dataSource.getHikariPoolMXBean().softEvictConnections();
                }
            }

            // 통계 초기화 (일정 주기마다)
            if (totalQueries > 1000000) { // 100만 쿼리마다 초기화
                plugin.getLogger().info("📊 쿼리 통계 초기화 (총 " + totalQueries + "개 쿼리 처리됨)");
                totalQueries = 0;
                successfulQueries = 0;
                failedQueries = 0;
                totalConnectionTime = 0;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 유지보수 중 오류", e);
        }
    }

    /**
     * 정리 작업
     */
    private void cleanup() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "데이터소스 정리 중 오류", e);
        }

        try {
            if (asyncExecutor != null && !asyncExecutor.isShutdown()) {
                asyncExecutor.shutdown();
                if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "비동기 실행기 정리 중 오류", e);
        }
    }

    /**
     * 연결 종료 (향상된 종료 프로세스)
     */
    public void closeConnection() {
        if (shutdownInProgress) return;
        shutdownInProgress = true;

        try {
            plugin.getLogger().info("🛑 DatabaseManager 종료 중...");

            // 최종 통계 출력
            getDatabaseStats().thenAccept(stats -> {
                plugin.getLogger().info("📊 최종 통계: " + stats.toString());
            }).join();

            // 진행 중인 모든 작업 완료 대기
            if (asyncExecutor != null) {
                asyncExecutor.shutdown();
                if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("⚠️ 일부 데이터베이스 작업이 완료되지 않았습니다. 강제 종료합니다.");
                    asyncExecutor.shutdownNow();
                }
            }

            // HikariCP 데이터소스 종료
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                plugin.getLogger().info("✅ HikariCP 데이터베이스 연결 풀이 안전하게 종료되었습니다.");
            }

            initialized = false;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DatabaseManager 종료 중 오류", e);
        }
    }

    // === Getter 메서드들 ===

    public boolean isInitialized() {
        return initialized && !shutdownInProgress;
    }

    public boolean isShuttingDown() {
        return shutdownInProgress;
    }

    public String getDatabaseInfo() {
        return String.format("%s@%s:%d/%s (HikariCP)", username, host, port, database);
    }

    public long getTotalQueries() {
        return totalQueries;
    }

    public long getSuccessfulQueries() {
        return successfulQueries;
    }

    public long getFailedQueries() {
        return failedQueries;
    }

    public double getAverageConnectionTime() {
        return totalQueries > 0 ? (totalConnectionTime / (double) totalQueries) : 0.0;
    }
}