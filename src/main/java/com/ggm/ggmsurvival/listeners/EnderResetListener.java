// EnderResetListener.java - 엔더 리셋 관련 이벤트 처리
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnderResetManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;

import java.util.logging.Level;

/**
 * 엔더 리셋 시스템 관련 이벤트 리스너
 * - 엔드시티 접근 차단
 * - 플레이어 이동 감지
 * - 포털 사용 제한
 */
public class EnderResetListener implements Listener {

    private final GGMSurvival plugin;
    private final EnderResetManager enderResetManager;

    public EnderResetListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.enderResetManager = plugin.getEnderResetManager();
    }

    /**
     * 플레이어 이동 이벤트 - 엔드시티 접근 차단
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        // EnderResetManager가 없으면 무시
        if (enderResetManager == null) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        // 이동 위치가 null이면 무시
        if (to == null) {
            return;
        }

        try {
            // 엔드 월드가 아니면 무시
            if (to.getWorld().getEnvironment() != World.Environment.THE_END) {
                return;
            }

            // 엔드시티 접근 차단 검사
            if (!enderResetManager.checkEndCityAccess(player, to)) {
                // 접근이 차단된 경우 이동 취소
                event.setCancelled(true);

                // 플레이어를 이전 위치로 되돌림
                Location from = event.getFrom();
                player.teleport(from);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "엔드시티 접근 차단 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 플레이어 텔레포트 이벤트 - 엔드시티 텔레포트 차단
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // EnderResetManager가 없으면 무시
        if (enderResetManager == null) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        // 텔레포트 위치가 null이면 무시
        if (to == null) {
            return;
        }

        try {
            // 엔드 월드가 아니면 무시
            if (to.getWorld().getEnvironment() != World.Environment.THE_END) {
                return;
            }

            // 관리자는 제한하지 않음
            if (player.hasPermission("ggm.enderreset.bypass")) {
                return;
            }

            // 엔드시티 접근 차단 검사
            if (!enderResetManager.checkEndCityAccess(player, to)) {
                // 텔레포트 취소
                event.setCancelled(true);

                player.sendMessage("§c[엔드시티 차단] 해당 위치로 텔레포트할 수 없습니다!");
                player.sendMessage("§7엔드시티 지역은 접근이 제한됩니다.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "엔드시티 텔레포트 차단 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 플레이어 포털 이벤트 - 엔드 포털 사용 감지
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // EnderResetManager가 없으면 무시
        if (enderResetManager == null) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();

        // 포털 목적지가 null이면 무시
        if (to == null) {
            return;
        }

        try {
            // 엔드로 가는 포털인지 확인
            if (to.getWorld().getEnvironment() == World.Environment.THE_END) {

                // 엔드 진입 메시지
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() &&
                            player.getWorld().getEnvironment() == World.Environment.THE_END) {

                        player.sendMessage("§6[엔더 월드] §f엔드 차원에 오신 것을 환영합니다!");
                        player.sendMessage("§7• 엔더 리셋 시간: 매일 " +
                                String.format("%02d:%02d",
                                        enderResetManager.getResetHour(),
                                        enderResetManager.getResetMinute()));
                        player.sendMessage("§7• 다음 리셋: " +
                                enderResetManager.getTimeUntilReset() + " 후");

                        if (enderResetManager.isEndCityBlockingEnabled()) {
                            player.sendMessage("§c• 엔드시티 지역은 접근이 제한됩니다.");
                        }
                    }
                }, 20L); // 1초 후 메시지 전송
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "엔드 포털 이벤트 처리 중 오류: " + player.getName(), e);
        }
    }
}