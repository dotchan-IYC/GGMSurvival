// 완전한 EnderResetManager.java - 모든 기능 구현
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
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;

/**
 * 완전한 엔더 차원 자동 리셋 시스템
 * - 매일 정해진 시간에 자동 리셋
 * - 엔드시티 접근 차단 기능
 * - BungeeCord 서버 간 플레이어 이동
 * - 예고 알림 시스템
 * - 수동 리셋 기능
 * - 안전한 월드 파일 관리
 */
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

    // 강제 리셋 대기 중인 관리자
    private final Set<UUID> pendingForceReset = new HashSet<>();

    // 상수들
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final long EVACUATION_DELAY = 100L; // 5초
    private static final long RESET_DELAY = 200L; // 10초

    public EnderResetManager(GGMSurvival plugin) {
        this.plugin = plugin;

        try {
            // 설정 로드
            loadResetSettings();

            // BungeeCord 채널 등록
            registerBungeeChannel();

            // 자동 리셋 스케줄러 시작
            startResetScheduler();

            plugin.getLogger().info("===== 엔더 자동 초기화 시스템 활성화 =====");
            plugin.getLogger().info("• 리셋 시간: 매일 " + String.format("%02d:%02d", resetHour, resetMinute));
            plugin.getLogger().info("• 엔드시티 차단: " + (blockEndCities ? "활성화" : "비활성화"));
            plugin.getLogger().info("• 이동 대상 서버: " + targetServerName);
            plugin.getLogger().info("• 차단 최소 거리: " + endCityMinDistance + " 블록");
            plugin.getLogger().info("==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "EnderResetManager 초기화 실패", e);
            throw new RuntimeException("EnderResetManager 초기화 실패", e);
        }
    }

    /**
     * 설정 로드 및 검증
     */
    private void loadResetSettings() {
        resetHour = plugin.getConfig().getInt("ender_reset.hour", 12);
        resetMinute = plugin.getConfig().getInt("ender_reset.minute", 0);
        blockEndCities = plugin.getConfig().getBoolean("ender_reset.block_end_cities", true);
        endCityMinDistance = plugin.getConfig().getInt("ender_reset.end_city_min_distance", 1000);
        targetServerName = plugin.getConfig().getString("ender_reset.target_server", "survival");

        // 유효성 검사
        if (resetHour < 0 || resetHour > 23) {
            plugin.getLogger().warning("잘못된 리셋 시간: " + resetHour + ". 기본값 12로 설정됩니다.");
            resetHour = 12;
        }

        if (resetMinute < 0 || resetMinute > 59) {
            plugin.getLogger().warning("잘못된 리셋 분: " + resetMinute + ". 기본값 0으로 설정됩니다.");
            resetMinute = 0;
        }

        if (endCityMinDistance < 500) {
            plugin.getLogger().warning("엔드시티 최소 거리가 너무 작습니다: " + endCityMinDistance + ". 1000으로 설정됩니다.");
            endCityMinDistance = 1000;
        }

        if (targetServerName == null || targetServerName.trim().isEmpty()) {
            plugin.getLogger().warning("대상 서버명이 비어있습니다. 'survival'로 설정됩니다.");
            targetServerName = "survival";
        }
    }

    /**
     * BungeeCord 채널 등록
     */
    private void registerBungeeChannel() {
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            plugin.getLogger().info("BungeeCord 플러그인 메시징 채널 등록 완료");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "BungeeCord 채널 등록 실패", e);
        }
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
                try {
                    checkResetTime();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "리셋 시간 체크 중 오류", e);
                }
            }
        }.runTaskTimer(plugin, 0L, 1200L); // 1분마다 체크 (1200틱)

        plugin.getLogger().info("엔더 리셋 스케줄러 시작됨 (매 1분마다 체크)");
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
            plugin.getLogger().info("스케줄된 엔더 리셋 시간 도달 - 리셋 실행");
            executeEnderReset();
            sentWarnings.clear(); // 다음 날을 위해 경고 초기화
        }

        // 디버그용 (매시간 0분에 상태 로그)
        if (currentTime.getMinute() == 0 && plugin.getConfig().getBoolean("debug.log_reset_status", false)) {
            plugin.getLogger().info(String.format("엔더 리셋 상태: 다음 리셋까지 %d시간 %d분",
                    minutesUntilReset / 60, minutesUntilReset % 60));
        }
    }

    /**
     * 예고 알림 메시지 전송
     */
    private void sendWarningMessage(int minutes) {
        String message = String.format("§c§l[엔더 리셋] §e%d분 후 엔드 차원이 초기화됩니다!", minutes);
        String subMessage = "§7엔드에 있는 모든 플레이어는 " + targetServerName + " 서버로 자동 이동됩니다.";

        // 모든 플레이어에게 알림
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(message);
            player.sendMessage(subMessage);
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 경고음
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.0f);

            // ActionBar로도 표시
            player.sendActionBar("§c§l엔더 리셋 " + minutes + "분 전!");
        }

        // 콘솔 로그
        plugin.getLogger().warning("엔더 리셋 " + minutes + "분 전 알림 전송됨");
    }

    /**
     * 엔더 차원 리셋 실행
     */
    public void executeEnderReset() {
        plugin.getLogger().info("======= 엔더 차원 초기화 시작 =======");

        try {
            // 전체 서버 알림
            broadcastResetStart();

            // 1단계: 엔드에 있는 플레이어들 강제 이동
            evacuateEndPlayers();

            // 2단계: 엔드 월드 리셋 (5초 후)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    resetEndWorld();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "엔드 월드 리셋 실패", e);
                }
            }, EVACUATION_DELAY);

            // 3단계: 완료 알림 (10초 후)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                broadcastResetComplete();
            }, RESET_DELAY);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "엔더 리셋 실행 중 치명적 오류", e);
            broadcastResetError();
        }
    }

    /**
     * 리셋 시작 알림
     */
    private void broadcastResetStart() {
        String message = "§c§l[엔더 리셋] §f엔드 차원 초기화가 시작됩니다!";

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("§4═══════════════════════════════════════");
            player.sendMessage(message);
            player.sendMessage("§7잠시 후 새로운 엔드 차원에서 새로운 모험을 시작하세요!");
            player.sendMessage("§4═══════════════════════════════════════");

            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        }
    }

    /**
     * 엔드에 있는 플레이어들 강제 이동
     */
    private void evacuateEndPlayers() {
        World endWorld = getEndWorld();
        if (endWorld == null) {
            plugin.getLogger().warning("엔드 월드를 찾을 수 없습니다.");
            return;
        }

        List<Player> endPlayers = new ArrayList<>(endWorld.getPlayers());

        if (endPlayers.isEmpty()) {
            plugin.getLogger().info("엔드에 플레이어가 없어 바로 리셋 진행");
            return;
        }

        plugin.getLogger().info("엔드에서 " + endPlayers.size() + "명의 플레이어를 대피시킵니다");

        // 플레이어들을 다른 서버로 이동
        for (Player player : endPlayers) {
            try {
                sendPlayerToServer(player, targetServerName);

                player.sendMessage("§a[엔더 리셋] " + targetServerName + " 서버로 안전하게 이동되었습니다!");
                player.sendMessage("§7엔드 차원이 초기화되어 새로운 드래곤과 함께 돌아올 예정입니다.");
                player.sendMessage("§e새로운 모험이 시작됩니다!");

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 서버 이동 실패: " + player.getName(), e);

                // 서버 이동 실패 시 스폰으로 텔레포트
                teleportToSafeLocation(player);
            }
        }
    }

    /**
     * 플레이어를 안전한 위치로 텔레포트
     */
    private void teleportToSafeLocation(Player player) {
        try {
            World overworld = Bukkit.getWorlds().get(0); // 메인 월드
            Location spawnLocation = overworld.getSpawnLocation();

            player.teleport(spawnLocation);
            player.sendMessage("§c서버 이동에 실패하여 메인 월드 스폰으로 이동되었습니다.");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "플레이어 안전 위치 이동 실패: " + player.getName(), e);
        }
    }

    /**
     * 엔드 월드 리셋 (파일 삭제 및 재생성)
     */
    private void resetEndWorld() {
        plugin.getLogger().info("엔드 월드 파일 삭제 및 재생성 시작");

        World endWorld = getEndWorld();
        if (endWorld == null) {
            plugin.getLogger().warning("엔드 월드를 찾을 수 없습니다.");
            return;
        }

        String worldName = endWorld.getName();
        File worldFolder = endWorld.getWorldFolder();

        try {
            // 1. 월드 언로드 (플레이어 저장 포함)
            boolean unloaded = Bukkit.unloadWorld(endWorld, true);
            if (!unloaded) {
                plugin.getLogger().severe("엔드 월드 언로드 실패");
                return;
            }

            plugin.getLogger().info("엔드 월드 언로드 완료: " + worldName);

            // 2. 월드 폴더 삭제
            if (worldFolder.exists()) {
                boolean deleted = deleteWorldFolder(worldFolder);
                if (deleted) {
                    plugin.getLogger().info("엔드 월드 폴더 삭제 완료: " + worldFolder.getPath());
                } else {
                    plugin.getLogger().warning("엔드 월드 폴더 삭제 실패: " + worldFolder.getPath());
                }
            }

            // 3. 새 엔드 월드 생성 (1초 후)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    WorldCreator creator = new WorldCreator(worldName);
                    creator.environment(World.Environment.THE_END);
                    creator.generateStructures(true);

                    World newEndWorld = creator.createWorld();
                    if (newEndWorld != null) {
                        plugin.getLogger().info("새로운 엔드 월드 생성 완료: " + worldName);

                        // 엔더 드래곤 확인
                        ensureEnderDragon(newEndWorld);
                    } else {
                        plugin.getLogger().severe("새로운 엔드 월드 생성 실패");
                    }

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "새 엔드 월드 생성 중 오류", e);
                }
            }, 20L); // 1초 후

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "엔드 월드 리셋 중 오류", e);
        }
    }

    /**
     * 월드 폴더 재귀적 삭제
     */
    private boolean deleteWorldFolder(File folder) {
        try {
            if (!folder.exists()) return true;

            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorldFolder(file);
                    } else {
                        if (!file.delete()) {
                            plugin.getLogger().warning("파일 삭제 실패: " + file.getPath());
                        }
                    }
                }
            }

            return folder.delete();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "폴더 삭제 중 오류: " + folder.getPath(), e);
            return false;
        }
    }

    /**
     * 엔더 드래곤 존재 확인 및 생성
     */
    private void ensureEnderDragon(World endWorld) {
        try {
            // 기존 엔더 드래곤 확인
            boolean dragonExists = endWorld.getEntitiesByClass(EnderDragon.class).size() > 0;

            if (!dragonExists) {
                // 엔드 포털 위치에 드래곤 스폰
                Location dragonLocation = new Location(endWorld, 0, 128, 0);
                endWorld.spawnEntity(dragonLocation, org.bukkit.entity.EntityType.ENDER_DRAGON);

                plugin.getLogger().info("새로운 엔더 드래곤이 스폰되었습니다.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "엔더 드래곤 확인 중 오류", e);
        }
    }

    /**
     * 리셋 완료 알림
     */
    private void broadcastResetComplete() {
        String message = "§a§l[엔더 리셋] §f엔드 차원 초기화가 완료되었습니다!";

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("§2═══════════════════════════════════════");
            player.sendMessage(message);
            player.sendMessage("§7새로운 엔더 드래곤과 함께 새로운 모험을 시작하세요!");
            player.sendMessage("§e엔드 포털을 통해 엔드로 이동할 수 있습니다.");
            player.sendMessage("§2═══════════════════════════════════════");

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        plugin.getLogger().info("======= 엔드 차원 초기화 완료 =======");
    }

    /**
     * 리셋 오류 알림
     */
    private void broadcastResetError() {
        String message = "§c§l[엔더 리셋] §f리셋 중 오류가 발생했습니다!";

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("§4═══════════════════════════════════════");
            player.sendMessage(message);
            player.sendMessage("§7관리자에게 문의해주세요.");
            player.sendMessage("§4═══════════════════════════════════════");
        }
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

            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());

            plugin.getLogger().info("플레이어 " + player.getName() + "을(를) " + serverName + " 서버로 이동시켰습니다.");

        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 서버 이동 중 IO 오류: " + player.getName(), e);
            throw new RuntimeException("서버 이동 실패", e);
        }
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
     * 엔드시티 지역 확인
     */
    public boolean isInEndCityArea(Location location) {
        if (!blockEndCities) return false;

        World world = location.getWorld();
        if (world == null || world.getEnvironment() != World.Environment.THE_END) {
            return false;
        }

        // 원점(0, 0)으로부터의 거리 계산
        double distanceFromOrigin = Math.sqrt(
                Math.pow(location.getX(), 2) + Math.pow(location.getZ(), 2)
        );

        return distanceFromOrigin >= endCityMinDistance;
    }

    /**
     * 엔드시티에서 플레이어 추방
     */
    public void kickFromEndCity(Player player) {
        try {
            World endWorld = player.getWorld();

            // 메인 엔드 섬으로 텔레포트 (0, 64, 0 근처)
            Location safeLocation = new Location(endWorld, 0, 64, 0);

            // 안전한 위치 찾기
            safeLocation = findSafeLocationInEnd(endWorld, safeLocation);

            player.teleport(safeLocation);
            player.sendMessage("§c[엔드시티 차단] 엔드시티 지역에서 메인 엔드로 이동되었습니다!");
            player.sendMessage("§7엔드시티 접근이 제한되어 있습니다.");
            player.sendMessage("§e메인 엔드 지역을 탐험해보세요!");

            // 효과
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 20);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "엔드시티 추방 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 엔드에서 안전한 위치 찾기
     */
    private Location findSafeLocationInEnd(World endWorld, Location startLocation) {
        // 간단한 안전 위치 검증
        for (int y = 64; y >= 50; y--) {
            Location testLocation = new Location(endWorld, startLocation.getX(), y, startLocation.getZ());

            if (testLocation.getBlock().getType() == Material.END_STONE) {
                return testLocation.add(0, 1, 0); // 블록 위에 스폰
            }
        }

        // 안전한 위치를 찾지 못한 경우 기본 위치 반환
        return new Location(endWorld, 0, 64, 0);
    }

    /**
     * 수동 리셋 (관리자 명령어용)
     */
    public void manualReset(Player admin) {
        UUID adminId = admin.getUniqueId();

        if (pendingForceReset.contains(adminId)) {
            // 취소
            pendingForceReset.remove(adminId);
            admin.sendMessage("§a[엔더 리셋] 강제 리셋이 취소되었습니다.");
            return;
        }

        // 확인 대기 상태로 설정
        pendingForceReset.add(adminId);

        plugin.getLogger().info(admin.getName() + "이(가) 수동으로 엔드 리셋을 시작했습니다");

        // 관리자에게 확인 메시지
        admin.sendMessage("§c[엔더 리셋] 수동 리셋을 시작합니다...");
        admin.sendMessage("§e30초 후 자동 실행됩니다. 취소하려면 명령어를 다시 입력하세요.");

        // 모든 플레이어에게 알림
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(admin)) {
                player.sendMessage("§4[긴급 알림] 관리자에 의해 엔드 차원이 곧 초기화됩니다!");
                player.sendActionBar("§c§l관리자 수동 리셋 진행 중");
            }
        }

        // 30초 후 리셋 실행
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingForceReset.contains(adminId)) {
                pendingForceReset.remove(adminId);

                admin.sendMessage("§c[엔더 리셋] 강제 리셋을 실행합니다!");
                executeEnderReset();
            }
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

        // 경고 상태 초기화
        sentWarnings.clear();

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
     * 캐시 정리
     */
    public void cleanupCache() {
        // 대기 중인 강제 리셋 정리 (10분 이상 지난 것들)
        pendingForceReset.removeIf(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            return player == null || !player.isOnline();
        });
    }

    /**
     * 스케줄러 정리 및 종료
     */
    public void shutdown() {
        try {
            if (resetTask != null && !resetTask.isCancelled()) {
                resetTask.cancel();
            }

            if (warningTask != null && !warningTask.isCancelled()) {
                warningTask.cancel();
            }

            pendingForceReset.clear();
            sentWarnings.clear();

            plugin.getLogger().info("EnderResetManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "EnderResetManager 종료 중 오류", e);
        }
    }

    // === Getter 메서드들 ===

    public int getResetHour() {
        return resetHour;
    }

    public int getResetMinute() {
        return resetMinute;
    }

    public boolean isEndCityBlockingEnabled() {
        return blockEndCities;
    }

    public String getTargetServerName() {
        return targetServerName;
    }

    public int getEndCityMinDistance() {
        return endCityMinDistance;
    }

    public boolean isResetPending() {
        return !pendingForceReset.isEmpty();
    }
}