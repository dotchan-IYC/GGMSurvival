package com.ggm.ggmsurvival.managers;

import org.bukkit.configuration.file.FileConfiguration;
import com.ggm.ggmsurvival.GGMSurvival;

import java.sql.*;

public class DatabaseManager {

    private final GGMSurvival plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String connectionUrl;

    public DatabaseManager(GGMSurvival plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.host = config.getString("database.host", "localhost");
        this.port = config.getInt("database.port", 3306);
        this.database = config.getString("database.database", "ggm_server");
        this.username = config.getString("database.username", "root");
        this.password = config.getString("database.password", "password");

        // 연결 URL 생성
        this.connectionUrl = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" +
                "&autoReconnect=true&useUnicode=true&characterEncoding=utf8";

        // 초기 연결 테스트
        testConnection();
    }

    /**
     * 새 데이터베이스 연결 생성
     */
    public Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(connectionUrl, username, password);
            plugin.getLogger().finest("새 데이터베이스 연결이 생성되었습니다.");
            return conn;
        } catch (SQLException e) {
            plugin.getLogger().severe("데이터베이스 연결 실패: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 연결 테스트
     */
    private void testConnection() {
        try (Connection conn = getConnection()) {
            plugin.getLogger().info("데이터베이스에 성공적으로 연결되었습니다.");
            plugin.getLogger().info("데이터베이스: " + database + " (호스트: " + host + ":" + port + ")");
        } catch (SQLException e) {
            plugin.getLogger().severe("데이터베이스 연결 테스트 실패: " + e.getMessage());
            plugin.getLogger().severe("야생 서버의 일부 기능이 제한될 수 있습니다.");
        }
    }

    /**
     * 연결 종료
     */
    public void closeConnection() {
        plugin.getLogger().info("데이터베이스 매니저가 종료되었습니다.");
    }

    /**
     * 데이터베이스 정보 출력
     */
    public void printDatabaseInfo() {
        plugin.getLogger().info("=== 데이터베이스 정보 ===");
        plugin.getLogger().info("호스트: " + host + ":" + port);
        plugin.getLogger().info("데이터베이스: " + database);
        plugin.getLogger().info("사용자: " + username);
        plugin.getLogger().info("==================");
    }
}