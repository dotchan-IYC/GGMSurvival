// 완전한 EnderResetManager.java - 이모티콘 제거 버전
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
            plugin.getLogger().info("• 대상 서버: " + targetServerName);
            plugin.getLogger().info("======================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "EnderResetManager 초기화 실패", e);
            throw new RuntimeException("EnderResetManager 초기화 실패", e);
        }
    }

    /**
     * 설정 로드
     */
    private void loadResetSettings() {
        try {
            // 리셋 시간 로드
            resetHour = plugin.getConfig().getInt("ender_reset.hour", 12);
            resetMinute = plugin.getConfig().getInt("ender_reset.minute", 0);

            // 시간 범위 검증
            if (resetHour < 0 || resetHour > 23) {
                plugin.getLogger().warning("잘못된 리셋 시간(시): " + resetHour + " - 기본값 12로 설정");
                resetHour = 12;
            }

            if (resetMinute < 0 || resetMinute > 59) {
                plugin.getLogger().warning("잘못된 리셋 시간(분): " + resetMinute + " - 기본값 0으로 설정");
                resetMinute = 0;
            }

            // 엔드시티 차단 설정
            blockEndCities = plugin.getConfig().getBoolean("ender_reset.block_end_cities", true);
            endCityMinDistance = plugin.getConfig().getInt("ender_reset.end_city_min_distance", 1000);

            // 대상 서버명
            targetServerName = plugin.getConfig().getString("ender_reset.target_server", "lobby");

            plugin.getLogger().info("엔더 리셋 설정 로드 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "엔더 리셋 설정 로드 실패", e);
            throw new RuntimeException("설정 로드 실패", e);
        }
    }

    /**
     * BungeeCord 채널 등록
     */
    private void registerBungeeChannel() {
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            plugin.getLogger().info("BungeeCord 채널 등록 완료");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "BungeeCord 채널 등록 실패", e);
        }
    }

    /**
     * 자동 리셋 스케줄러 시작
     */
    private void startResetScheduler() {
        try {
            // 기존 태스크 취소
            if (resetTask != null && !resetTask.isCancelled()) {
                resetTask.cancel();
            }

            if (warningTask != null && !warningTask.isCancelled()) {
                warningTask.cancel();
            }

            // 매 분마다 체크하는 태스크
            resetTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        checkResetTime();
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "리셋 시간 체크 중 오류", e);
                    }
                }
            }.runTaskTimer(plugin, 1200L, 1200L); // 1분마다 체크

            plugin.getLogger().info("자동 리셋 스케줄러 시작됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "리셋 스케줄러 시작 실패", e);
        }
    }

    /**
     * 리셋 시간 체크 및 예고 알림
     */
    private void checkResetTime() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime todayReset = now.toLocalDate().atTime(resetHour, resetMinute);
            LocalDateTime tomorrowReset = todayReset.plusDays(1);

            // 다음 리셋 시간 계산
            LocalDateTime nextReset = now.isAfter(todayReset) ? tomorrowReset : todayReset;
            long minutesUntilReset = ChronoUnit.MINUTES.between(now, nextReset);

            // 예고 알림 체크
            for (int warningMinute : warningTimes) {
                if (minutesUntilReset == warningMinute && !sentWarnings.contains(warningMinute)) {
                    sendWarningMessage(warningMinute);
                    sentWarnings.add(warningMinute);
                }
            }

            // 리셋 시간이 되었는지 체크
            if (minutesUntilReset <= 0 && now.getSecond() < 10) {
                executeEnderReset();
                sentWarnings.clear(); // 다음 리셋을 위해 경고 초기화
            }

            // 하루가 지나면 경고 초기화
            if (minutesUntilReset > 60) {
                sentWarnings.clear();
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "리셋 시간 체크 중 오류", e);
        }
    }

    /**
     * 예고 알림 메시지 전송
     */
    private void sendWarningMessage(int minutes) {
        String message = "§c§l[엔더 리셋] §f엔드 차원이 " + minutes + "분 후 초기화됩니다!";
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

        List<Player> endPlayers = new ArrayList<>();

        // 엔드에 있는 플레이어 찾기
        for (Player player : endWorld.getPlayers()) {
            endPlayers.add(player);
        }

        if (endPlayers.isEmpty()) {
            plugin.getLogger().info("엔드에 있는 플레이어가 없습니다.");
            return;
        }

        plugin.getLogger().info("엔드에서 " + endPlayers.size() + "명의 플레이어를 이동시킵니다.");

        // 각 플레이어를 대상 서버로 이동
        for (Player player : endPlayers) {
            try {
                sendPlayerToServer(player, targetServerName);

                player.sendMessage("§e§l[엔더 리셋] §f엔드 차원 초기화로 인해 " + targetServerName + " 서버로 이동됩니다.");
                player.sendMessage("§7잠시 후 다시 엔드로 돌아와 새로운 모험을 시작하세요!");

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 이동 실패: " + player.getName(), e);

                // BungeeCord 이동 실패시 스폰으로 이동
                Location spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
                player.teleport(spawnLocation);
                player.sendMessage("§c§l[엔더 리셋] §f엔드 차원 초기화로 인해 메인 월드로 이동되었습니다.");
            }
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

        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "BungeeCord 메시지 전송 실패: " + player.getName(), e);
            throw new RuntimeException("서버 이동 실패", e);
        }
    }

    /**
     * 엔드 월드 리셋
     */
    private void resetEndWorld() {
        try {
            World endWorld = getEndWorld();
            if (endWorld == null) {
                plugin.getLogger().warning("엔드 월드를 찾을 수 없어 리셋을 건너뜁니다.");
                return;
            }

            plugin.getLogger().info("엔드 월드 리셋 시작: " + endWorld.getName());

            // 1. 엔더 드래곤 제거
            removeEnderDragons(endWorld);

            // 2. 엔드 월드 파일 삭제 및 재생성
            regenerateEndWorld(endWorld);

            plugin.getLogger().info("엔드 월드 리셋 완료!");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "엔드 월드 리셋 중 오류", e);
            throw new RuntimeException("엔드 월드 리셋 실패", e);
        }
    }

    /**
     * 엔더 드래곤 제거
     */
    private void removeEnderDragons(World endWorld) {
        try {
            endWorld.getEntitiesByClass(EnderDragon.class).forEach(dragon -> {
                dragon.remove();
                plugin.getLogger().info("엔더 드래곤 제거됨");
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "엔더 드래곤 제거 중 오류", e);
        }
    }

    /**
     * 엔드 월드 재생성
     */
    private void regenerateEndWorld(World endWorld) {
        try {
            String worldName = endWorld.getName();

            // 월드 언로드
            if (!Bukkit.unloadWorld(endWorld, false)) {
                plugin.getLogger().warning("엔드 월드 언로드 실패, 강제 진행");
            }

            // 월드 폴더 삭제
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (deleteWorldFolder(worldFolder)) {
                plugin.getLogger().info("엔드 월드 폴더 삭제 완료: " + worldName);
            } else {
                plugin.getLogger().warning("엔드 월드 폴더 삭제 실패: " + worldName);
            }

            // 새 엔드 월드 생성
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    WorldCreator creator = new WorldCreator(worldName);
                    creator.environment(World.Environment.THE_END);
                    World newEndWorld = creator.createWorld();

                    if (newEndWorld != null) {
                        plugin.getLogger().info("새로운 엔드 월드 생성 완료: " + worldName);
                    } else {
                        plugin.getLogger().severe("새로운 엔드 월드 생성 실패!");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "새 엔드 월드 생성 중 오류", e);
                }
            }, 20L); // 1초 후 생성

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "엔드 월드 재생성 중 오류", e);
        }
    }

    /**
     * 월드 폴더 재귀적 삭제
     */
    private boolean deleteWorldFolder(File folder) {
        try {
            if (!folder.exists()) {
                return true;
            }

            if (folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteWorldFolder(file);
                    }
                }
            }

            return folder.delete();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "폴더 삭제 중 오류: " + folder.getAbsolutePath(), e);
            return false;
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
            player.sendMessage("§7새로운 엔드 차원에서 모험을 시작하세요!");
            player.sendMessage("§2═══════════════════════════════════════");

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        plugin.getLogger().info("======= 엔더 차원 초기화 완료 =======");
    }

    /**
     * 리셋 오류 알림
     */
    private void broadcastResetError() {
        String message = "§c§l[엔더 리셋] §f엔드 차원 초기화 중 오류가 발생했습니다.";

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("§c═══════════════════════════════════════");
            player.sendMessage(message);
            player.sendMessage("§7관리자에게 문의해주세요.");
            player.sendMessage("§c═══════════════════════════════════════");
        }

        plugin.getLogger().severe("======= 엔더 차원 초기화 실패 =======");
    }

    /**
     * 엔드 월드 찾기
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
     * 엔드시티 접근 차단 체크
     */
    public boolean checkEndCityAccess(Player player, Location location) {
        if (!blockEndCities) {
            return true; // 차단하지 않음
        }

        try {
            World endWorld = getEndWorld();
            if (endWorld == null || !location.getWorld().equals(endWorld)) {
                return true; // 엔드가 아니면 허용
            }

            // 메인 엔드 아일랜드 중심(0, y, 0)에서 거리 계산
            double distance = Math.sqrt(
                    Math.pow(location.getX(), 2) + Math.pow(location.getZ(), 2)
            );

            if (distance > endCityMinDistance) {
                // 엔드시티 지역에 접근 시도
                teleportToMainEnd(player);

                player.sendMessage("§c§l[엔드시티 차단] §f엔드시티 지역은 접근이 제한됩니다!");
                player.sendMessage("§7메인 엔드 아일랜드에서 플레이해주세요.");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

                return false; // 차단됨
            }

            return true; // 허용됨

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "엔드시티 접근 체크 중 오류", e);
            return true; // 오류 시 허용
        }
    }

    /**
     * 메인 엔드로 텔레포트
     */
    private void teleportToMainEnd(Player player) {
        try {
            World endWorld = getEndWorld();
            if (endWorld != null) {
                // 메인 엔드 아일랜드의 안전한 위치로 이동
                Location safeLocation = new Location(endWorld, 0, 65, 0);

                // 안전한 위치 찾기
                while (safeLocation.getY() < 128 &&
                        !safeLocation.getBlock().getType().isAir()) {
                    safeLocation.add(0, 1, 0);
                }

                player.teleport(safeLocation);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "메인 엔드 텔레포트 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 수동 리셋 (관리자용)
     */
    public void manualReset(Player admin) {
        try {
            UUID adminUUID = admin.getUniqueId();

            if (pendingForceReset.contains(adminUUID)) {
                // 두 번째 명령어 - 실제 실행
                pendingForceReset.remove(adminUUID);

                admin.sendMessage("§c§l[강제 리셋] §f엔더 차원을 즉시 초기화합니다!");
                executeEnderReset();

                plugin.getLogger().warning("관리자 " + admin.getName() + "이(가) 엔더 차원을 강제 리셋했습니다.");

            } else {
                // 첫 번째 명령어 - 확인 대기
                pendingForceReset.add(adminUUID);

                admin.sendMessage("§e§l[강제 리셋] §f정말로 엔더 차원을 즉시 리셋하시겠습니까?");
                admin.sendMessage("§c§l경고: §f이 작업은 되돌릴 수 없습니다!");
                admin.sendMessage("§730초 내에 §f/enderreset force §7를 다시 입력하세요.");

                // 30초 후 자동 취소
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (pendingForceReset.remove(adminUUID)) {
                        admin.sendMessage("§7강제 리셋이 시간 초과로 취소되었습니다.");
                    }
                }, 600L); // 30초 = 600틱
            }

        } catch (Exception e) {
            admin.sendMessage("§c강제 리셋 실행 중 오류가 발생했습니다.");
            plugin.getLogger().log(Level.SEVERE, "수동 리셋 실행 중 오류", e);
        }
    }

    /**
     * 리셋 시간 변경
     */
    public void setResetTime(int hour, int minute) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("시간은 0-23 범위여야 합니다: " + hour);
        }

        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("분은 0-59 범위여야 합니다: " + minute);
        }

        this.resetHour = hour;
        this.resetMinute = minute;

        // 설정 파일에 저장
        plugin.getConfig().set("ender_reset.hour", hour);
        plugin.getConfig().set("ender_reset.minute", minute);
        plugin.saveConfig();

        // 스케줄러 재시작
        startResetScheduler();

        plugin.getLogger().info("엔더 리셋 시간 변경됨: " +
                String.format("%02d:%02d", hour, minute));
    }

    /**
     * 다음 리셋까지 남은 시간 계산
     */
    public String getTimeUntilReset() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime todayReset = now.toLocalDate().atTime(resetHour, resetMinute);
            LocalDateTime tomorrowReset = todayReset.plusDays(1);

            LocalDateTime nextReset = now.isAfter(todayReset) ? tomorrowReset : todayReset;
            long hours = ChronoUnit.HOURS.between(now, nextReset);
            long minutes = ChronoUnit.MINUTES.between(now, nextReset) % 60;

            return String.format("%d시간 %d분", hours, minutes);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "리셋 시간 계산 중 오류", e);
            return "계산 불가";
        }
    }

    /**
     * 시스템 종료
     */
    public void shutdown() {
        try {
            // 태스크 취소
            if (resetTask != null && !resetTask.isCancelled()) {
                resetTask.cancel();
            }

            if (warningTask != null && !warningTask.isCancelled()) {
                warningTask.cancel();
            }

            // 대기 중인 강제 리셋 취소
            pendingForceReset.clear();

            // BungeeCord 채널 해제
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);

            plugin.getLogger().info("EnderResetManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "EnderResetManager 종료 중 오류", e);
        }
    }

    // Getter 메서드들
    public int getResetHour() {
        return resetHour;
    }

    public int getResetMinute() {
        return resetMinute;
    }

    public boolean isEndCityBlockingEnabled() {
        return blockEndCities;
    }

    public int getEndCityMinDistance() {
        return endCityMinDistance;
    }

    public String getTargetServerName() {
        return targetServerName;
    }

    public boolean isResetPending() {
        return !pendingForceReset.isEmpty();
    }
}