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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EnderListener implements Listener {

    private final GGMSurvival plugin;
    private final EnderResetManager enderResetManager;

    // 플레이어별 마지막 경고 시간 (스팸 방지)
    private final Map<UUID, Long> lastWarningTime = new HashMap<>();
    private static final long WARNING_COOLDOWN = 5000; // 5초

    public EnderListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.enderResetManager = plugin.getEnderResetManager();
    }

    /**
     * 플레이어 이동 감지 (엔드시티 차단)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // 엔드시티 지역 확인
        if (enderResetManager.isInEndCityArea(to)) {
            // 경고 쿨다운 확인
            if (!canSendWarning(player)) {
                return;
            }

            // 엔드시티 지역에서 추방
            enderResetManager.kickFromEndCity(player);

            // 경고 시간 기록
            lastWarningTime.put(player.getUniqueId(), System.currentTimeMillis());

            // 이벤트 취소
            event.setCancelled(true);
        }
    }

    /**
     * 텔레포트 감지 (엔드시티 차단)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // 엔드시티 지역으로 텔레포트 차단
        if (enderResetManager.isInEndCityArea(to)) {
            // 관리자는 예외
            if (player.hasPermission("ggm.enderreset.bypass")) {
                player.sendMessage("[관리자] 엔드시티 지역에 접근했습니다. (우회 권한)");
                return;
            }

            event.setCancelled(true);
            player.sendMessage("[엔드시티 차단] 해당 지역으로는 텔레포트할 수 없습니다!");
            player.sendMessage("엔드시티 접근이 제한되어 있습니다.");
        }
    }

    /**
     * 엔드 게이트웨이 포털 생성 차단
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPortalCreate(PortalCreateEvent event) {
        World world = event.getWorld();

        // 엔드 월드에서만 체크
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }

        // 엔드시티 차단이 활성화된 경우
        if (enderResetManager.isEndCityBlockingEnabled()) {
            // 엔드 게이트웨이 포털 생성 차단
            if (event.getReason() == PortalCreateEvent.CreateReason.END_PLATFORM) {
                event.setCancelled(true);

                // 근처 플레이어들에게 알림
                Location portalLocation = event.getBlocks().get(0).getLocation();
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distance(portalLocation) <= 50) {
                        player.sendMessage("[엔드시티 차단] 엔드 게이트웨이 생성이 차단되었습니다!");
                        player.sendMessage("엔드시티로의 접근이 제한되어 있습니다.");
                    }
                }

                plugin.getLogger().info("엔드 게이트웨이 포털 생성이 차단되었습니다: " + portalLocation);
            }
        }
    }

    /**
     * 포털 사용 차단 (추가 보안)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // 엔드 게이트웨이를 통한 이동 차단
        if (to.getWorld().getEnvironment() == World.Environment.THE_END) {
            if (enderResetManager.isInEndCityArea(to)) {
                // 관리자는 예외
                if (player.hasPermission("ggm.enderreset.bypass")) {
                    player.sendMessage("[관리자] 엔드 게이트웨이를 통해 엔드시티로 이동했습니다. (우회 권한)");
                    return;
                }

                event.setCancelled(true);
                player.sendMessage("[엔드시티 차단] 엔드 게이트웨이 사용이 차단되었습니다!");
                player.sendMessage("엔드시티 접근이 제한되어 있습니다.");
                player.sendMessage("드래곤을 처치하고 메인 엔드 지역을 탐험해보세요!");
            }
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
     * 플레이어 접속 종료 시 데이터 정리
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastWarningTime.remove(playerId);
    }
}