// DatabaseManager.java - HikariCP 연결 풀 버전 (이모티콘 제거)
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * HikariCP 기반 고성능 데이터베이스 매니저
 * - 연결 풀 관리
 * - 비동기 쿼리 지원
 * - 자동 재연결
 * - 연결 누수 감지
 * - 성능 최적화
 */
public class DatabaseManager {

    private final GGMSurvival plugin;
    private HikariDataSource dataSource;
    private boolean initialized = false;

    // 테이블 생성 쿼리들
    private static final String CREATE_PLAYER_DATA_TABLE = """
        CREATE TABLE IF NOT EXISTS player_data (
            uuid VARCHAR(36) PRIMARY KEY,
            username VARCHAR(16) NOT NULL,
            balance BIGINT DEFAULT 1000,
            job VARCHAR(20) DEFAULT 'none',
            job_level INT DEFAULT 1,
            job_exp BIGINT DEFAULT 0,
            last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_username (username),
            INDEX idx_job (job),
            INDEX idx_last_login (last_login)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private static final String CREATE_ITEM_UPGRADES_TABLE = """
        CREATE TABLE IF NOT EXISTS item_upgrades (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            player_uuid VARCHAR(36) NOT NULL,
            item_type VARCHAR(50) NOT NULL,
            item_data TEXT,
            upgrade_level INT DEFAULT 0,
            upgrade_attempts INT DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_player_uuid (player_uuid),
            INDEX idx_item_type (item_type),
            INDEX idx_upgrade_level (upgrade_level),
            FOREIGN KEY (player_uuid) REFERENCES player_data(uuid) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private static final String CREATE_DRAGON_KILLS_TABLE = """
        CREATE TABLE IF NOT EXISTS dragon_kills (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            player_uuid VARCHAR(36) NOT NULL,
            kill_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            reward_money BIGINT DEFAULT 0,
            reward_exp INT DEFAULT 0,
            special_reward VARCHAR(100),
            player_job VARCHAR(20),
            INDEX idx_player_uuid (player_uuid),
            INDEX idx_kill_time (kill_time),
            INDEX idx_player_job (player_job),
            FOREIGN KEY (player_uuid) REFERENCES player_data(uuid) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private static final String CREATE_NPC_TRADES_TABLE = """
        CREATE TABLE IF NOT EXISTS npc_trades (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            player_uuid VARCHAR(36) NOT NULL,
            npc_id VARCHAR(50) NOT NULL,
            trade_item VARCHAR(100) NOT NULL,
            trade_amount INT NOT NULL,
            trade_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_player_uuid (player_uuid),
            INDEX idx_npc_id (npc_id),
            INDEX idx_trade_time (trade_time),
            FOREIGN KEY (player_uuid) REFERENCES player_data(uuid) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    public DatabaseManager(GGMSurvival plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    /**
     * 데이터베이스 초기화
     */
    private void initializeDatabase() {
        try {
            plugin.getLogger().info("HikariCP 데이터베이스 연결 풀 초기화 중...");

            // HikariCP 설정
            HikariConfig config = createHikariConfig();

            // 데이터소스 생성
            dataSource = new HikariDataSource(config);

            // 연결 테스트
            if (!testConnection()) {
                throw new RuntimeException("데이터베이스 연결 테스트 실패");
            }

            // 테이블 생성
            createTables();

            initialized = true;
            plugin.getLogger().info("데이터베이스 연결 풀 초기화 완료!");
            plugin.getLogger().info("• 최대 연결 수: " + config.getMaximumPoolSize());
            plugin.getLogger().info("• 최소 유휴 연결: " + config.getMinimumIdle());
            plugin.getLogger().info("• 연결 타임아웃: " + config.getConnectionTimeout() + "ms");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "데이터베이스 초기화 실패", e);
            throw new RuntimeException("데이터베이스 초기화 실패", e);
        }
    }

    /**
     * HikariCP 설정 생성
     */
    private HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();

        // 기본 연결 정보
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.database", "ggmsurvival");
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "password");
        boolean ssl = plugin.getConfig().getBoolean("database.ssl", false);

        // JDBC URL 구성
        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=%s&autoReconnect=true&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC",
                host, port, database, ssl
        );

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // HikariCP 연결 풀 설정
        config.setMaximumPoolSize(plugin.getConfig().getInt("database.hikari.maximum_pool_size", 10));
        config.setMinimumIdle(plugin.getConfig().getInt("database.hikari.minimum_idle", 2));
        config.setConnectionTimeout(plugin.getConfig().getLong("database.hikari.connection_timeout", 30000));
        config.setIdleTimeout(plugin.getConfig().getLong("database.hikari.idle_timeout", 600000));
        config.setMaxLifetime(plugin.getConfig().getLong("database.hikari.max_lifetime", 1800000));
        config.setLeakDetectionThreshold(plugin.getConfig().getLong("database.hikari.leak_detection_threshold", 60000));

        // 연결 풀 이름
        config.setPoolName("GGMSurvival-Pool");

        // MySQL 최적화 설정
        config.addDataSourceProperty("cachePrepStmts", plugin.getConfig().getBoolean("database.cache_prep_stmts", true));
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", plugin.getConfig().getBoolean("database.use_server_prep_stmts", true));
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        return config;
    }

    /**
     * 연결 테스트
     */
    public boolean testConnection() {
        if (dataSource == null) {
            return false;
        }

        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5); // 5초 타임아웃
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 연결 테스트 실패", e);
            return false;
        }
    }

    /**
     * 테이블 생성
     */
    private void createTables() {
        try (Connection connection = getConnection()) {
            // 각 테이블 생성
            executeUpdate(connection, CREATE_PLAYER_DATA_TABLE);
            executeUpdate(connection, CREATE_ITEM_UPGRADES_TABLE);
            executeUpdate(connection, CREATE_DRAGON_KILLS_TABLE);
            executeUpdate(connection, CREATE_NPC_TRADES_TABLE);

            plugin.getLogger().info("모든 데이터베이스 테이블 생성/확인 완료");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "테이블 생성 실패", e);
            throw new RuntimeException("테이블 생성 실패", e);
        }
    }

    /**
     * 데이터베이스 연결 가져오기
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("데이터소스가 초기화되지 않았거나 닫혔습니다");
        }
        return dataSource.getConnection();
    }

    /**
     * 비동기 쿼리 실행 (SELECT)
     */
    public CompletableFuture<ResultSet> executeQueryAsync(String query, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {

                setParameters(statement, parameters);
                return statement.executeQuery();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "비동기 쿼리 실행 실패: " + query, e);
                throw new RuntimeException("쿼리 실행 실패", e);
            }
        });
    }

    /**
     * 비동기 업데이트 실행 (INSERT, UPDATE, DELETE)
     */
    public CompletableFuture<Integer> executeUpdateAsync(String query, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {

                setParameters(statement, parameters);
                return statement.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "비동기 업데이트 실행 실패: " + query, e);
                throw new RuntimeException("업데이트 실행 실패", e);
            }
        });
    }

    /**
     * 동기 쿼리 실행 (SELECT) - 주의: 호출자가 반드시 ResultSet과 Connection을 close해야 함
     */
    public ResultSet executeQuery(String query, Object... parameters) throws SQLException {
        Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(query);

        setParameters(statement, parameters);
        ResultSet rs = statement.executeQuery();

        // 주의: 호출자가 ResultSet, Statement, Connection을 모두 close해야 함
        return rs;
    }

    /**
     * 안전한 쿼리 실행 (SELECT) - 자동으로 리소스 정리
     */
    public <T> T executeQuerySafe(String query, ResultSetHandler<T> handler, Object... parameters) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            setParameters(statement, parameters);
            try (ResultSet rs = statement.executeQuery()) {
                return handler.handle(rs);
            }
        }
    }

    /**
     * 동기 업데이트 실행 (INSERT, UPDATE, DELETE)
     */
    public int executeUpdate(String query, Object... parameters) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            setParameters(statement, parameters);
            return statement.executeUpdate();
        }
    }

    /**
     * 연결 사용 업데이트 실행 (트랜잭션용)
     */
    public int executeUpdate(Connection connection, String query, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            setParameters(statement, parameters);
            return statement.executeUpdate();
        }
    }

    /**
     * 트랜잭션 실행
     */
    public CompletableFuture<Boolean> executeTransaction(TransactionCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                connection.setAutoCommit(false);

                try {
                    callback.execute(connection);
                    connection.commit();
                    return true;

                } catch (Exception e) {
                    connection.rollback();
                    plugin.getLogger().log(Level.SEVERE, "트랜잭션 실행 실패", e);
                    throw new RuntimeException("트랜잭션 실패", e);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "트랜잭션 연결 오류", e);
                throw new RuntimeException("트랜잭션 연결 오류", e);
            }
        });
    }

    /**
     * 배치 실행
     */
    public CompletableFuture<int[]> executeBatch(String query, Object[]... parameterSets) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {

                for (Object[] parameters : parameterSets) {
                    setParameters(statement, parameters);
                    statement.addBatch();
                }

                return statement.executeBatch();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "배치 실행 실패: " + query, e);
                throw new RuntimeException("배치 실행 실패", e);
            }
        });
    }

    /**
     * 매개변수 설정 헬퍼
     */
    private void setParameters(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            Object param = parameters[i];

            if (param == null) {
                statement.setNull(i + 1, Types.VARCHAR);
            } else if (param instanceof String) {
                statement.setString(i + 1, (String) param);
            } else if (param instanceof Integer) {
                statement.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                statement.setLong(i + 1, (Long) param);
            } else if (param instanceof Double) {
                statement.setDouble(i + 1, (Double) param);
            } else if (param instanceof Boolean) {
                statement.setBoolean(i + 1, (Boolean) param);
            } else if (param instanceof Timestamp) {
                statement.setTimestamp(i + 1, (Timestamp) param);
            } else {
                statement.setString(i + 1, param.toString());
            }
        }
    }

    /**
     * 플레이어 데이터 존재 확인
     */
    public CompletableFuture<Boolean> playerExists(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT 1 FROM player_data WHERE uuid = ? LIMIT 1")) {

                statement.setString(1, uuid);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "플레이어 존재 확인 실패: " + uuid, e);
                return false;
            }
        });
    }

    /**
     * 플레이어 생성
     */
    public CompletableFuture<Boolean> createPlayer(String uuid, String username) {
        return executeUpdateAsync(
                "INSERT INTO player_data (uuid, username, balance, job, job_level, job_exp) VALUES (?, ?, ?, ?, ?, ?)",
                uuid, username, 1000L, "none", 1, 0L
        ).thenApply(result -> result > 0);
    }

    /**
     * 플레이어 데이터 업데이트
     */
    public CompletableFuture<Boolean> updatePlayer(String uuid, String username) {
        return executeUpdateAsync(
                "UPDATE player_data SET username = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?",
                username, uuid
        ).thenApply(result -> result > 0);
    }

    /**
     * 연결 풀 상태 정보
     */
    public String getPoolStatus() {
        if (dataSource == null) {
            return "데이터소스 없음";
        }

        return String.format(
                "활성: %d, 유휴: %d, 대기: %d, 총: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }

    /**
     * 연결 풀 통계 로그
     */
    public void logPoolStats() {
        if (dataSource != null && plugin.getConfig().getBoolean("logging.detailed_logging.database_operations", false)) {
            plugin.getLogger().info("HikariCP 연결 풀 상태: " + getPoolStatus());
        }
    }

    /**
     * 데이터베이스 연결 해제
     */
    public void closeConnection() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                plugin.getLogger().info("데이터베이스 연결 풀이 안전하게 종료되었습니다.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "데이터베이스 연결 해제 중 오류", e);
        } finally {
            initialized = false;
        }
    }

    /**
     * 초기화 상태 확인
     */
    public boolean isInitialized() {
        return initialized && dataSource != null && !dataSource.isClosed();
    }

    /**
     * 트랜잭션 콜백 인터페이스
     */
    @FunctionalInterface
    public interface TransactionCallback {
        void execute(Connection connection) throws SQLException;
    }

    /**
     * ResultSet 처리 인터페이스
     */
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }
}