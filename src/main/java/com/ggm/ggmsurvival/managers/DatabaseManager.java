package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final GGMSurvival plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    public DatabaseManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.host = plugin.getConfig().getString("database.host", "localhost");
        this.port = plugin.getConfig().getInt("database.port", 3306);
        this.database = plugin.getConfig().getString("database.database", "ggm_server");
        this.username = plugin.getConfig().getString("database.username", "root");
        this.password = plugin.getConfig().getString("database.password", "1224");

        // 데이터베이스 연결 테스트
        testConnection();

        // 기본 테이블 생성
        createTables();
    }

    /**
     * 새로운 데이터베이스 연결 생성
     */
    private Connection createConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("MySQL JDBC 드라이버를 찾을 수 없습니다!");
            throw new SQLException("MySQL JDBC 드라이버 없음", e);
        }

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&autoReconnect=true",
                host, port, database);

        Connection conn = DriverManager.getConnection(url, username, password);
        return conn;
    }

    /**
     * 연결 테스트
     */
    private void testConnection() {
        try (Connection conn = createConnection()) {
            plugin.getLogger().info("데이터베이스에 성공적으로 연결되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("데이터베이스 연결 테스트 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 기본 테이블 생성
     */
    private void createTables() {
        try (Connection conn = createConnection()) {
            // 플레이어 경제 테이블 (GGMCore와 공유)
            String economyTable = """
                CREATE TABLE IF NOT EXISTS ggm_economy (
                    uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    balance BIGINT NOT NULL DEFAULT 1000,
                    last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

            executeUpdate(conn, economyTable);
            plugin.getLogger().info("데이터베이스 테이블이 준비되었습니다.");

        } catch (SQLException e) {
            plugin.getLogger().severe("테이블 생성 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 데이터베이스 연결 반환 (매번 새 연결)
     */
    public Connection getConnection() throws SQLException {
        return createConnection();
    }

    /**
     * SQL 업데이트 실행
     */
    private void executeUpdate(Connection conn, String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    /**
     * 플레이어 데이터 생성 또는 업데이트
     */
    public CompletableFuture<Void> createOrUpdatePlayer(java.util.UUID uuid, String playerName) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = createConnection()) {
                String sql = """
                    INSERT INTO ggm_economy (uuid, player_name, balance) 
                    VALUES (?, ?, 1000) 
                    ON DUPLICATE KEY UPDATE 
                    player_name = VALUES(player_name),
                    last_login = CURRENT_TIMESTAMP
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("플레이어 데이터 생성/업데이트 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 연결 정리
     */
    public void closeConnection() {
        plugin.getLogger().info("데이터베이스 연결이 정리되었습니다.");
    }
}