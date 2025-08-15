// 완전 안정화된 DatabaseManager.java
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * 완전 안정화된 데이터베이스 매니저
 * - 연결 풀링을 통한 성능 최적화
 * - 자동 재연결 기능
 * - Thread-Safe 구현
 * - 메모리 누수 방지
 * - 강력한 예외 처리
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

    // 연결 상태 추적
    private volatile boolean initialized = false;
    private volatile boolean shutdownInProgress = false;

    // 연결 테스트 쿼리
    private static final String TEST_QUERY = "SELECT 1";
    private static final String PING_QUERY = "/* ping */ SELECT 1";

    public DatabaseManager(GGMSurvival plugin) {
        this.plugin = plugin;

        try {
            // 설정 로드
            this.host = plugin.getConfig().getString("database.host", "localhost");
            this.port = plugin.getConfig().getInt("database.port", 3306);
            this.database = plugin.getConfig().getString("database.database", "ggm_server");
            this.username = plugin.getConfig().getString("database.username", "root");
            this.password = plugin.getConfig().getString("database.password", "");

            // 비동기 실행기 초기화
            this.asyncExecutor = Executors.newFixedThreadPool(4, r -> {
                Thread thread = new Thread(r, "GGMSurvival-DB-Thread");
                thread.setDaemon(true);
                return thread;
            });

            // 데이터베이스 연결 초기화
            initializeDatabase();

            // 연결 테스트
            if (!testConnection()) {
                throw new RuntimeException("데이터베이스 연결 테스트 실패");
            }

            // 기본 테이블 생성
            createBaseTables();

            this.initialized = true;
            plugin.getLogger().info("DatabaseManager 안정화 초기화 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "DatabaseManager 초기화 실패", e);
            cleanup();
            throw new RuntimeException("DatabaseManager 초기화 실패", e);
        }
    }

    /**
     * 데이터베이스 연결 초기화 (HikariCP 사용)
     */
    private void initializeDatabase() {
        try {
            HikariConfig config = new HikariConfig();

            // 기본 연결 설정
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", host, port, database));
            config.setUsername(username);
            config.setPassword(password);

            // 커넥션 풀 설정
            config.setMaximumPoolSize(20);           // 최대 연결 수
            config.setMinimumIdle(5);               // 최소 유휴 연결 수
            config.setConnectionTimeout(30000);     // 연결 타임아웃 (30초)
            config.setIdleTimeout(600000);          // 유휴 타임아웃 (10분)
            config.setMaxLifetime(1800000);         // 최대 생명주기 (30분)
            config.setLeakDetectionThreshold(60000); // 누수 탐지 (1분)

            // MySQL 특화 설정
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            config.addDataSourceProperty("useSSL", "false");
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
            config.addDataSourceProperty("serverTimezone", "Asia/Seoul");
            config.addDataSourceProperty("characterEncoding", "UTF-8");

            // 연결 검증 설정
            config.setConnectionTestQuery(TEST_QUERY);
            config.setValidationTimeout(5000);

            // 풀 이름 설정
            config.setPoolName("GGMSurvival-DB-Pool");

            this.dataSource = new HikariDataSource(config);

            plugin.getLogger().info("HikariCP 데이터베이스 연결 풀 초기화 완료");
            plugin.getLogger().info(String.format("데이터베이스: %s@%s:%d/%s", username, host, port, database));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "데이터베이스 연결 풀 초기화 실패", e);
            throw new RuntimeException("데이터베이스 연결 풀 초기화 실패", e);
        }
    }

    /**
     * 연결 획득 (자동 재시도 포함)
     */
    public Connection getConnection() throws SQLException {
        if (shutdownInProgress) {
            throw new SQLException("데이터베이스 매니저가 종료 중입니다.");
        }

        if (!initialized) {
            throw new SQLException("데이터베이스 매니저가 초기화되지 않았습니다.");
        }

        try {
            Connection connection = dataSource.getConnection();

            // 연결 유효성 검증
            if (!connection.isValid(5)) {
                connection.close();
                throw new SQLException("유효하지 않은 데이터베이스 연결");
            }

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
     * 연결 테스트
     */
    public boolean testConnection() {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(TEST_QUERY);
             ResultSet rs = stmt.executeQuery()) {

            return rs.next() && rs.getInt(1) == 1;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 연결 테스트 실패", e);
            return false;
        }
    }

    /**
     * 재연결 시도
     */
    private boolean attemptReconnection() {
        try {
            plugin.getLogger().info("데이터베이스 재연결 시도 중...");

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }

            // 잠시 대기
            Thread.sleep(2000);

            // 새 연결 풀 생성
            initializeDatabase();

            // 연결 테스트
            if (testConnection()) {
                plugin.getLogger().info("데이터베이스 재연결 성공!");
                return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "데이터베이스 재연결 실패", e);
        }

        return false;
    }

    /**
     * 기본 테이블 생성
     */
    private void createBaseTables() {
        executeUpdate(createPlayersTableSQL());
        executeUpdate(createPlayerJobsTableSQL());
        executeUpdate(createPlayerEconomyTableSQL());

        plugin.getLogger().info("기본 테이블 생성/확인 완료");
    }

    /**
     * 플레이어 테이블 SQL
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
                INDEX idx_last_join (last_join)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
    }

    /**
     * 플레이어 직업 테이블 SQL
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
                FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
                INDEX idx_job_type (job_type),
                INDEX idx_job_level (job_level),
                INDEX idx_updated_at (updated_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
    }

    /**
     * 플레이어 경제 테이블 SQL
     */
    private String createPlayerEconomyTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS player_economy (
                uuid VARCHAR(36) PRIMARY KEY,
                balance BIGINT NOT NULL DEFAULT 1000,
                last_transaction TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
                INDEX idx_balance (balance),
                INDEX idx_last_transaction (last_transaction)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
    }

    /**
     * 플레이어 생성 또는 업데이트
     */
    public CompletableFuture<Boolean> createOrUpdatePlayer(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                // 플레이어 정보 업데이트/삽입
                String playerSQL = """
                    INSERT INTO players (uuid, name, first_join, last_join) 
                    VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE 
                    name = VALUES(name), 
                    last_join = CURRENT_TIMESTAMP
                    """;

                try (PreparedStatement stmt = connection.prepareStatement(playerSQL)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, name);
                    stmt.executeUpdate();
                }

                // 경제 데이터 초기화 (없는 경우에만)
                String economySQL = """
                    INSERT IGNORE INTO player_economy (uuid, balance) 
                    VALUES (?, ?)
                    """;

                try (PreparedStatement stmt = connection.prepareStatement(economySQL)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setLong(2, plugin.getConfig().getLong("economy.starting_money", 1000L));
                    stmt.executeUpdate();
                }

                return true;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 생성/업데이트 실패: " + name + " (" + uuid + ")", e);
                return false;
            }
        }, asyncExecutor);
    }

    /**
     * 플레이어 존재 여부 확인
     */
    public CompletableFuture<Boolean> playerExists(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT 1 FROM players WHERE uuid = ? LIMIT 1")) {

                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 존재 확인 실패: " + uuid, e);
                return false;
            }
        }, asyncExecutor);
    }

    /**
     * 플레이어 이름으로 UUID 조회
     */
    public CompletableFuture<UUID> getPlayerUUID(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT uuid FROM players WHERE name = ? ORDER BY last_join DESC LIMIT 1")) {

                stmt.setString(1, name);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("uuid"));
                    }
                    return null;
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 UUID 조회 실패: " + name, e);
                return null;
            }
        }, asyncExecutor);
    }

    /**
     * UUID로 플레이어 이름 조회
     */
    public CompletableFuture<String> getPlayerName(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT name FROM players WHERE uuid = ? LIMIT 1")) {

                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("name");
                    }
                    return null;
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 이름 조회 실패: " + uuid, e);
                return null;
            }
        }, asyncExecutor);
    }

    /**
     * 동기 쿼리 실행 (UPDATE, INSERT, DELETE)
     */
    public boolean executeUpdate(String sql, Object... params) {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            // 파라미터 설정
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "쿼리 실행 실패: " + sql, e);
            return false;
        }
    }

    /**
     * 비동기 쿼리 실행
     */
    public CompletableFuture<Boolean> executeUpdateAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> executeUpdate(sql, params), asyncExecutor);
    }

    /**
     * 배치 쿼리 실행
     */
    public CompletableFuture<Boolean> executeBatch(String sql, Object[]... paramsList) {
        return CompletableFuture.supplyAsync(() -> {
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

                // 모든 배치가 성공했는지 확인
                for (int result : results) {
                    if (result == PreparedStatement.EXECUTE_FAILED) {
                        return false;
                    }
                }

                return true;

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "배치 쿼리 실행 실패: " + sql, e);
                return false;
            }
        }, asyncExecutor);
    }

    /**
     * 트랜잭션 실행
     */
    public CompletableFuture<Boolean> executeTransaction(TransactionCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                connection.setAutoCommit(false);

                try {
                    boolean result = callback.execute(connection);

                    if (result) {
                        connection.commit();
                        return true;
                    } else {
                        connection.rollback();
                        return false;
                    }

                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "트랜잭션 실행 실패", e);
                return false;
            }
        }, asyncExecutor);
    }

    /**
     * 트랜잭션 콜백 인터페이스
     */
    @FunctionalInterface
    public interface TransactionCallback {
        boolean execute(Connection connection) throws SQLException;
    }

    /**
     * 데이터베이스 상태 정보 반환
     */
    public DatabaseStats getStats() {
        if (dataSource == null) {
            return new DatabaseStats(false, 0, 0, 0, 0);
        }

        try {
            return new DatabaseStats(
                    !dataSource.isClosed(),
                    dataSource.getHikariPoolMXBean().getTotalConnections(),
                    dataSource.getHikariPoolMXBean().getActiveConnections(),
                    dataSource.getHikariPoolMXBean().getIdleConnections(),
                    dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 상태 조회 실패", e);
            return new DatabaseStats(false, 0, 0, 0, 0);
        }
    }

    /**
     * 데이터베이스 상태 정보 클래스
     */
    public static class DatabaseStats {
        public final boolean isAvailable;
        public final int totalConnections;
        public final int activeConnections;
        public final int idleConnections;
        public final int waitingThreads;

        public DatabaseStats(boolean isAvailable, int totalConnections, int activeConnections,
                             int idleConnections, int waitingThreads) {
            this.isAvailable = isAvailable;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.waitingThreads = waitingThreads;
        }

        @Override
        public String toString() {
            return String.format("DB Stats: Available=%s, Total=%d, Active=%d, Idle=%d, Waiting=%d",
                    isAvailable, totalConnections, activeConnections, idleConnections, waitingThreads);
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
     * 연결 종료
     */
    public void closeConnection() {
        if (shutdownInProgress) return;
        shutdownInProgress = true;

        try {
            plugin.getLogger().info("DatabaseManager 종료 중...");

            // 진행 중인 모든 작업 완료 대기
            if (asyncExecutor != null) {
                asyncExecutor.shutdown();
                if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("일부 데이터베이스 작업이 완료되지 않았습니다. 강제 종료합니다.");
                    asyncExecutor.shutdownNow();
                }
            }

            // 데이터소스 종료
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                plugin.getLogger().info("데이터베이스 연결 풀이 안전하게 종료되었습니다.");
            }

            initialized = false;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DatabaseManager 종료 중 오류", e);
        }
    }

    // Getter 메서드들
    public boolean isInitialized() {
        return initialized && !shutdownInProgress;
    }

    public boolean isShuttingDown() {
        return shutdownInProgress;
    }

    public String getDatabaseInfo() {
        return String.format("%s@%s:%d/%s", username, host, port, database);
    }
}