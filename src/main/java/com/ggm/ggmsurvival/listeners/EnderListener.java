// 완전한 EnderListener.java
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnderResetManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 완전한 엔더 관련 이벤트 리스너
 * - 엔드시티 접근 차단
 * - 엔드 게이트웨이 포털 차단
 * - 플레이어 이동 감지
 * - 텔레포트 차단
 * - 경고 스팸 방지
 */
public class EnderListener implements Listener {

    private final GGMSurvival plugin;
    private final EnderResetManager enderResetManager;

    // 플레이어별 마지막 경고 시간 (스팸 방지)
    private final Map<UUID, Long> lastWarningTime = new HashMap<>();
    private final Map<UUID, Long> lastViolationTime = new HashMap<>();

    // 상수
    private static final long WARNING_COOLDOWN = 5000; // 5초
    private static final long VIOLATION_COOLDOWN = 2000; // 2초
    private static final double MOVEMENT_THRESHOLD = 5.0; // 5블록 이상 이동 시에만 체크

    public EnderListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.enderResetManager = plugin.getEnderResetManager();

        if (enderResetManager == null) {
            plugin.getLogger().warning("EnderListener 초기화: EnderResetManager가 null입니다.");
        } else {
            plugin.getLogger().info("EnderListener 초기화 완료 - 엔드시티 차단 시스템 활성화");
        }
    }

    /**
     * 플레이어 이동 감지 (엔드시티 차단)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // EnderResetManager 확인
        if (enderResetManager == null || !enderResetManager.isEndCityBlockingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // 엔드 월드가 아니면 무시
        if (to.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }

        // 작은 움직임 무시 (성능 최적화)
        if (from.distance(to) < MOVEMENT_THRESHOLD) {
            return;
        }

        // 관리자 우회 권한 확인
        if (player.hasPermission("ggm.enderreset.bypass")) {
            return;
        }

        // 엔드시티 지역 확인
        if (enderResetManager.isInEndCityArea(to)) {
            // 위반 쿨다운 확인 (너무 자주 처리되지 않도록)
            if (!canProcessViolation(player)) {
                event.setCancelled(true);
                return;
            }

            // 경고 메시지 (쿨다운 확인)
            if (canSendWarning(player)) {
                player.sendMessage("§c[엔드시티 차단] 엔드시티 지역에 접근할 수 없습니다!");
                player.sendMessage("§7메인 엔드 지역으로 이동됩니다.");
                lastWarningTime.put(player.getUniqueId(), System.currentTimeMillis());
            }

            // 엔드시티 지역에서 추방
            enderResetManager.kickFromEndCity(player);

            // 위반 시간 기록
            lastViolationTime.put(player.getUniqueId(), System.currentTimeMillis());

            // 이벤트 취소
            event.setCancelled(true);
        }
    }

    /**
     * 텔레포트 감지 (엔드시티 차단)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // EnderResetManager 확인
        if (enderResetManager == null || !enderResetManager.isEndCityBlockingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // 엔드 월드가 아니면 무시
        if (to.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }

        // 관리자 우회 권한 확인
        if (player.hasPermission("ggm.enderreset.bypass")) {
            if (enderResetManager.isInEndCityArea(to)) {
                player.sendMessage("§e[관리자] 엔드시티 지역에 텔레포트했습니다. (우회 권한)");
            }
            return;
        }

        // 엔드시티 지역으로 텔레포트 차단
        if (enderResetManager.isInEndCityArea(to)) {
            event.setCancelled(true);

            player.sendMessage("§c[엔드시티 차단] 해당 지역으로는 텔레포트할 수 없습니다!");
            player.sendMessage("§7엔드시티 접근이 제한되어 있습니다.");
            player.sendMessage("§e메인 엔드 지역을 탐험해보세요!");

            // 효과음
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * 엔드 게이트웨이 포털 생성 차단
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        // EnderResetManager 확인
        if (enderResetManager == null || !enderResetManager.isEndCityBlockingEnabled()) {
            return;
        }

        World world = event.getWorld();

        // 엔드 월드에서만 체크
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }

        // 엔드 게이트웨이 포털 생성 차단
        if (event.getReason() == PortalCreateEvent.CreateReason.END_PLATFORM) {
            event.setCancelled(true);

            // 근처 플레이어들에게 알림
            if (!event.getBlocks().isEmpty()) {
                Location portalLocation = event.getBlocks().get(0).getLocation();

                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distance(portalLocation) <= 50) {
                        // 관리자가 아닌 경우에만 알림
                        if (!player.hasPermission("ggm.enderreset.bypass")) {
                            player.sendMessage("§c[엔드시티 차단] 엔드 게이트웨이 생성이 차단되었습니다!");
                            player.sendMessage("§7엔드시티로의 접근이 제한되어 있습니다.");
                        }
                    }
                }

                plugin.getLogger().info("엔드 게이트웨이 포털 생성 차단됨: " + portalLocation);
            }
        }
    }

    /**
     * 포털 사용 차단 (추가 보안)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // EnderResetManager 확인
        if (enderResetManager == null || !enderResetManager.isEndCityBlockingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // 엔드 게이트웨이를 통한 이동 차단
        if (to.getWorld().getEnvironment() == World.Environment.THE_END) {
            if (enderResetManager.isInEndCityArea(to)) {
                // 관리자는 예외
                if (player.hasPermission("ggm.enderreset.bypass")) {
                    player.sendMessage("§e[관리자] 엔드 게이트웨이를 통해 엔드시티로 이동했습니다. (우회 권한)");
                    return;
                }

                event.setCancelled(true);

                player.sendMessage("§c[엔드시티 차단] 엔드 게이트웨이 사용이 차단되었습니다!");
                player.sendMessage("§7엔드시티 접근이 제한되어 있습니다.");
                player.sendMessage("§e드래곤을 처치하고 메인 엔드 지역을 탐험해보세요!");

                // 효과음
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    /**
     * 엔더 진주 사용 감지 (추가 보안)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderPearlTeleport(PlayerTeleportEvent event) {
        // 엔더 진주를 통한 텔레포트만 체크
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }

        // EnderResetManager 확인
        if (enderResetManager == null || !enderResetManager.isEndCityBlockingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // 엔드 월드가 아니면 무시
        if (to.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }

        // 관리자 우회 권한 확인
        if (player.hasPermission("ggm.enderreset.bypass")) {
            return;
        }

        // 엔드시티 지역으로 엔더 진주 텔레포트 차단
        if (enderResetManager.isInEndCityArea(to)) {
            event.setCancelled(true);

            player.sendMessage("§c[엔드시티 차단] 엔더 진주로 엔드시티 지역에 갈 수 없습니다!");
            player.sendMessage("§7엔드시티 접근이 제한되어 있습니다.");

            // 엔더 진주 반환
            ItemStack enderPearl = new ItemStack(Material.ENDER_PEARL, 1);
            player.getInventory().addItem(enderPearl);
            player.sendMessage("§a엔더 진주가 반환되었습니다.");

            // 효과음
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * 경고 메시지 전송 가능 여부 확인 (스팸 방지)
     */
    private boolean canSendWarning(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastWarning = lastWarningTime.getOrDefault(playerId, 0L);

        return (currentTime - lastWarning) >= WARNING_COOLDOWN;
    }

    /**
     * 위반 처리 가능 여부 확인 (성능 최적화)
     */
    private boolean canProcessViolation(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastViolation = lastViolationTime.getOrDefault(playerId, 0L);

        return (currentTime - lastViolation) >= VIOLATION_COOLDOWN;
    }

    /**
     * 플레이어 접속 종료 시 데이터 정리
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 메모리 누수 방지를 위한 데이터 정리
        lastWarningTime.remove(playerId);
        lastViolationTime.remove(playerId);
    }

    /**
     * 디버그 정보 (관리자용)
     */
    public void printDebugInfo(Player player) {
        if (!player.hasPermission("ggm.enderreset.admin")) {
            return;
        }

        Location loc = player.getLocation();
        boolean inEndCityArea = (enderResetManager != null) ? enderResetManager.isInEndCityArea(loc) : false;

        player.sendMessage("§6=== 엔드 디버그 정보 ===");
        player.sendMessage("§7현재 위치: §f" +
                String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()));
        player.sendMessage("§7월드: §f" + loc.getWorld().getName());
        player.sendMessage("§7환경: §f" + loc.getWorld().getEnvironment());
        player.sendMessage("§7엔드시티 지역: " + (inEndCityArea ? "§c예" : "§a아니요"));

        if (enderResetManager != null) {
            player.sendMessage("§7차단 활성화: " +
                    (enderResetManager.isEndCityBlockingEnabled() ? "§a예" : "§c아니요"));
            player.sendMessage("§7차단 거리: §f" + enderResetManager.getEndCityMinDistance() + " 블록");
        }

        player.sendMessage("§7우회 권한: " +
                (player.hasPermission("ggm.enderreset.bypass") ? "§a있음" : "§c없음"));
        player.sendMessage("§6==================");
    }

    /**
     * 캐시 정리 (메모리 관리)
     */
    public void cleanupCache() {
        try {
            long currentTime = System.currentTimeMillis();

            // 10분 이상 된 기록 삭제
            lastWarningTime.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue()) > 600000);

            lastViolationTime.entrySet().removeIf(entry ->
                    (currentTime - entry.getValue()) > 600000);

        } catch (Exception e) {
            plugin.getLogger().warning("EnderListener 캐시 정리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 통계 정보 반환
     */
    public String getStats() {
        return String.format("경고 기록: %d개, 위반 기록: %d개",
                lastWarningTime.size(), lastViolationTime.size());
    }
}