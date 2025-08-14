package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.*;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class EnderResetManager {

    private final GGMSurvival plugin;
    private BukkitTask resetTask;
    private BukkitTask warningTask;

    // 예고 알림 시간들 (분 단위)
    private final List<Integer> warningTimes = Arrays.asList(10, 5, 3, 1);
    private final Set<Integer> sentWarnings = new HashSet<>();

    // 리셋 시간 설정
    private int resetHour;
    private int resetMinute;

    // 엔드시티 차단 설정
    private boolean blockEndCities;
    private int endCityMinDistance;
    private String targetServerName;

    public EnderResetManager(GGMSurvival plugin) {
        this.plugin = plugin;

        // 설정 로드
        loadResetSettings();

        // 자동 리셋 스케줄러 시작
        startResetScheduler();

        // BungeeCord 채널 등록
        registerBungeeChannel();

        plugin.getLogger().info("엔더 자동 초기화 시스템 활성화");
        plugin.getLogger().info("• 리셋 시간: 매일 " + String.format("%02d:%02d", resetHour, resetMinute));
        plugin.getLogger().info("• 엔드시티 차단: " + (blockEndCities ? "활성화" : "비활성화"));
        plugin.getLogger().info("• 이동 대상 서버: " + targetServerName);
    }

    /**
     * 설정 로드
     */
    private void loadResetSettings() {
        resetHour = plugin.getConfig().getInt("ender_reset.hour", 12);
        resetMinute = plugin.getConfig().getInt("ender_reset.minute", 0);
        blockEndCities = plugin.getConfig().getBoolean("ender_reset.block_end_cities", true);
        endCityMinDistance = plugin.getConfig().getInt("ender_reset.end_city_min_distance", 1000);
        targetServerName = plugin.getConfig().getString("ender_reset.target_server", "survival");

        // 유효성 검사
        if (resetHour < 0 || resetHour > 23) resetHour = 12;
        if (resetMinute < 0 || resetMinute > 59) resetMinute = 0;
        if (endCityMinDistance < 500) endCityMinDistance = 1000;
    }

    /**
     * BungeeCord 채널 등록
     */
    private void registerBungeeChannel() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    /**
     * 자동 리셋 스케줄러 시작
     */
    private void startResetScheduler() {
        // 기존 태스크 취소
        if (resetTask != null) resetTask.cancel();
        if (warningTask != null) warningTask.cancel();

        // 매 분마다 체크하는 태스크
        resetTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkResetTime();
            }
        }.runTaskTimer(plugin, 0L, 1200L); // 1분마다 체크 (1200틱)

        plugin.getLogger().info("엔더 리셋 스케줄러 시작됨");
    }

    /**
     * 리셋 시간 체크 및 실행
     */
    private void checkResetTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();
        LocalTime resetTime = LocalTime.of(resetHour, resetMinute);

        // 리셋 시간까지 남은 분 계산
        long minutesUntilReset = ChronoUnit.MINUTES.between(currentTime, resetTime);

        // 다음 날로 넘어간 경우
        if (minutesUntilReset < 0) {
            minutesUntilReset += 24 * 60; // 24시간 추가
        }

        // 예고 알림 체크
        for (int warningTime : warningTimes) {
            if (minutesUntilReset == warningTime && !sentWarnings.contains(warningTime)) {
                sendWarningMessage(warningTime);
                sentWarnings.add(warningTime);
            }
        }

        // 리셋 시간 도달
        if (minutesUntilReset == 0) {
            executeEnderReset();
            sentWarnings.clear(); // 다음 날을 위해 경고 초기화
        }
    }

    /**
     * 예고 알림 메시지 전송
     */
    private void sendWarningMessage(int minutes) {
        String message = String.format("[엔더 리셋] %d분 후 엔드 차원이 초기화됩니다!", minutes);
        String subMessage = "엔드에 있는 모든 플레이어는 " + targetServerName + " 서버로 자동 이동됩니다.";

        // 모든 플레이어에게 알림
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(message);
            player.sendMessage(subMessage);
            player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 경고음
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.0f);

            // ActionBar로도 표시
            player.sendActionBar("엔더 리셋 " + minutes + "분 전!");
        }

        // 콘솔 로그
        plugin.getLogger().warning("엔더 리셋 " + minutes + "분 전 알림 전송");
    }

    /**
     * 엔더 차원 리셋 실행
     */
    public void executeEnderReset() {
        plugin.getLogger().info("엔더 차원 초기화 시작...");

        // 1단계: 엔드에 있는 플레이어들 강제 이동
        evacuateEndPlayers();

        // 2단계: 엔드 월드 리셋 (5초 후)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetEndWorld();
        }, 100L); // 5초 후
    }

    /**
     * 엔드에 있는 플레이어들 강제 이동
     */
    private void evacuateEndPlayers() {
        World endWorld = getEndWorld();
        if (endWorld == null) return;

        List<Player> endPlayers = new ArrayList<>();

        // 엔드에 있는 플레이어 찾기
        for (Player player : endWorld.getPlayers()) {
            endPlayers.add(player);
        }

        if (endPlayers.isEmpty()) {
            plugin.getLogger().info("엔드에 플레이어가 없어 바로 리셋 진행");
            return;
        }

        // 플레이어들을 다른 서버로 이동
        for (Player player : endPlayers) {
            sendPlayerToServer(player, targetServerName);
            player.sendMessage("[엔더 리셋] " + targetServerName + " 서버로 안전하게 이동되었습니다!");
            player.sendMessage("엔드 차원이 초기화되어 새로운 드래곤과 함께 돌아올 예정입니다.");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }

        plugin.getLogger().info(endPlayers.size() + "명의 플레이어를 " + targetServerName + " 서버로 이동시켰습니다");
    }

    /**
     * 플레이어를 다른 서버로 이동
     */
    private void sendPlayerToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());

        } catch (Exception e) {
            plugin.getLogger().warning("플레이어 서버 이동 실패 (" + player.getName() + "): " + e.getMessage());

            // BungeeCord 연동 실패 시 기본 월드로 이동
            World spawnWorld = Bukkit.getWorld("world");
            if (spawnWorld != null) {
                player.teleport(spawnWorld.getSpawnLocation());
            }
        }
    }

    /**
     * 엔드시티 접근 차단 확인
     */
    public boolean isInEndCityArea(Location location) {
        if (!blockEndCities) return false;

        World world = location.getWorld();
        if (world == null || world.getEnvironment() != World.Environment.THE_END) {
            return false;
        }

        // 엔드시티는 보통 (1000, 1000) 이상의 좌표에 생성됨
        int x = Math.abs(location.getBlockX());
        int z = Math.abs(location.getBlockZ());

        return x >= endCityMinDistance || z >= endCityMinDistance;
    }

    /**
     * 엔드시티 지역에서 플레이어 추방
     */
    public void kickFromEndCity(Player player) {
        World endWorld = getEndWorld();
        if (endWorld == null) return;

        // 엔드 메인 섬으로 텔레포트 (0, 64, 0 근처)
        Location safeLocation = new Location(endWorld, 0, 64, 0);

        // 안전한 위치 찾기
        for (int y = 50; y <= 100; y++) {
            Location checkLoc = new Location(endWorld, 0, y, 0);
            if (checkLoc.getBlock().getType() == Material.AIR &&
                    checkLoc.clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                safeLocation = checkLoc;
                break;
            }
        }

        player.teleport(safeLocation);
        player.sendMessage("[엔드시티 차단] 엔드시티 지역은 접근이 제한됩니다!");
        player.sendMessage("드래곤을 처치하고 메인 엔드 지역을 탐험해보세요.");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        // 경고 이펙트
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
    }

    /**
     * 엔드 월드 리셋
     */
    private void resetEndWorld() {
        try {
            World endWorld = getEndWorld();
            if (endWorld == null) {
                plugin.getLogger().warning("엔드 월드를 찾을 수 없습니다!");
                return;
            }

            plugin.getLogger().info("엔드 차원 리셋 중...");

            // 모든 플레이어에게 리셋 시작 알림
            broadcastResetMessage("[엔더 리셋] 엔드 차원 초기화 중...");

            // 엔드 월드 언로드
            boolean unloaded = Bukkit.getServer().unloadWorld(endWorld, false);

            if (unloaded) {
                // 월드 폴더 삭제
                deleteWorldFolder(endWorld.getWorldFolder());

                // 새로운 엔드 월드 생성 (5초 후)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    recreateEndWorld();
                }, 100L);
            } else {
                plugin.getLogger().severe("엔드 월드 언로드 실패!");
                broadcastResetMessage("[엔더 리셋] 리셋 실패! 관리자에게 문의하세요.");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("엔드 리셋 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 엔드 월드 재생성
     */
    private void recreateEndWorld() {
        try {
            plugin.getLogger().info("새로운 엔드 월드 생성 중...");

            // 새로운 엔드 월드 생성
            WorldCreator creator = new WorldCreator("world_the_end");
            creator.environment(World.Environment.THE_END);
            World newEndWorld = creator.createWorld();

            if (newEndWorld != null) {
                // 드래곤 리스폰 (드래곤이 자동으로 생성되지 않는 경우)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    respawnEnderDragon(newEndWorld);
                }, 60L); // 3초 후

                // 완료 알림
                broadcastResetComplete();

                plugin.getLogger().info("엔드 차원 리셋 완료!");
            } else {
                plugin.getLogger().severe("새로운 엔드 월드 생성 실패!");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("엔드 월드 재생성 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 엔더 드래곤 리스폰
     */
    private void respawnEnderDragon(World endWorld) {
        try {
            // 기존 드래곤 제거
            endWorld.getEntitiesByClass(EnderDragon.class).forEach(dragon -> dragon.remove());

            // 새 드래곤 소환
            Location dragonLocation = new Location(endWorld, 0, 80, 0);
            EnderDragon dragon = endWorld.spawn(dragonLocation, EnderDragon.class);

            plugin.getLogger().info("새로운 엔더 드래곤이 소환되었습니다!");

        } catch (Exception e) {
            plugin.getLogger().warning("드래곤 리스폰 중 오류: " + e.getMessage());
        }
    }

    /**
     * 월드 폴더 삭제
     */
    private void deleteWorldFolder(File worldFolder) {
        if (worldFolder.exists() && worldFolder.isDirectory()) {
            deleteFolder(worldFolder);
            plugin.getLogger().info("엔드 월드 폴더 삭제 완료");
        }
    }

    /**
     * 폴더 재귀 삭제
     */
    private void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }
        folder.delete();
    }

    /**
     * 엔드 월드 가져오기
     */
    private World getEndWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                return world;
            }
        }
        return null;
    }

    /**
     * 리셋 메시지 브로드캐스트
     */
    private void broadcastResetMessage(String message) {
        Bukkit.broadcastMessage(message);
    }

    /**
     * 리셋 완료 알림
     */
    private void broadcastResetComplete() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("엔드 차원 초기화 완료!");
            player.sendMessage("새로운 엔더 드래곤이 기다리고 있습니다!");
            player.sendMessage("드래곤을 처치하고 풍성한 보상을 받아보세요!");
            player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 완료 효과음
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.sendActionBar("엔드 차원 리셋 완료! 새로운 모험이 시작됩니다!");
        }
    }

    /**
     * 수동 리셋 (관리자 명령어용)
     */
    public void manualReset(Player admin) {
        plugin.getLogger().info(admin.getName() + "이(가) 수동으로 엔드 리셋을 실행했습니다");

        // 관리자에게 확인 메시지
        admin.sendMessage("[엔더 리셋] 수동 리셋을 시작합니다...");

        // 모든 플레이어에게 알림
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(admin)) {
                player.sendMessage("[긴급 알림] 관리자에 의해 엔드 차원이 곧 초기화됩니다!");
                player.sendActionBar("관리자 수동 리셋 진행 중");
            }
        }

        // 30초 후 리셋 실행
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            executeEnderReset();
        }, 600L); // 30초 후
    }

    /**
     * 다음 리셋까지 시간 조회
     */
    public String getTimeUntilReset() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();
        LocalTime resetTime = LocalTime.of(resetHour, resetMinute);

        long minutesUntilReset = ChronoUnit.MINUTES.between(currentTime, resetTime);

        if (minutesUntilReset < 0) {
            minutesUntilReset += 24 * 60;
        }

        long hours = minutesUntilReset / 60;
        long minutes = minutesUntilReset % 60;

        return String.format("%d시간 %d분", hours, minutes);
    }

    /**
     * 리셋 시간 변경
     */
    public void setResetTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("잘못된 시간 형식입니다. (시간: 0-23, 분: 0-59)");
        }

        this.resetHour = hour;
        this.resetMinute = minute;

        // 설정 파일에 저장
        plugin.getConfig().set("ender_reset.hour", hour);
        plugin.getConfig().set("ender_reset.minute", minute);
        plugin.saveConfig();

        // 스케줄러 재시작
        startResetScheduler();

        plugin.getLogger().info("엔더 리셋 시간이 " + String.format("%02d:%02d", hour, minute) + "로 변경되었습니다");
    }

    /**
     * 엔드시티 차단 설정 변경
     */
    public void setEndCityBlocking(boolean enabled) {
        this.blockEndCities = enabled;
        plugin.getConfig().set("ender_reset.block_end_cities", enabled);
        plugin.saveConfig();

        plugin.getLogger().info("엔드시티 차단이 " + (enabled ? "활성화" : "비활성화") + "되었습니다");
    }

    /**
     * 스케줄러 정리
     */
    public void shutdown() {
        if (resetTask != null) {
            resetTask.cancel();
        }
        if (warningTask != null) {
            warningTask.cancel();
        }
        plugin.getLogger().info("엔더 리셋 시스템 종료");
    }

    // Getter 메서드들
    public int getResetHour() { return resetHour; }
    public int getResetMinute() { return resetMinute; }
    public boolean isEndCityBlockingEnabled() { return blockEndCities; }
    public String getTargetServerName() { return targetServerName; }
}