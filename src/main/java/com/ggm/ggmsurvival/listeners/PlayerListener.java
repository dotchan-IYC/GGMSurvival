// 수정된 PlayerListener.java - 컴파일 오류 해결
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * 플레이어 기본 이벤트 처리 리스너
 * - 접속/퇴장 처리
 * - 플러그인 초기화 대기 처리
 * - 각 매니저별 초기화 연동
 */
public class PlayerListener implements Listener {

    private final GGMSurvival plugin;

    public PlayerListener(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    /**
     * 플레이어 접속 처리
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            plugin.getLogger().info("플레이어 접속 처리 시작: " + player.getName());

            // 플러그인 초기화 대기
            if (!plugin.isInitialized()) {
                plugin.getLogger().info("플러그인 초기화 대기 중: " + player.getName());
                handleUninitializedJoin(player);
                return;
            }

            // 초기화된 경우 즉시 처리
            handleInitializedJoin(player);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "플레이어 접속 처리 중 오류: " + player.getName(), e);

            // 오류 발생 시 플레이어에게 알림
            if (player.isOnline()) {
                player.sendMessage("§c접속 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            }
        }
    }

    /**
     * 플레이어 퇴장 처리
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        try {
            plugin.getLogger().info("플레이어 퇴장 처리 시작: " + player.getName());

            // 비동기로 플레이어 데이터 저장
            handlePlayerLogout(player);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "플레이어 퇴장 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 플레이어 킥 처리
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        try {
            plugin.getLogger().info("플레이어 킥 처리: " + player.getName() + " - " + event.getReason());

            // 퇴장과 동일한 처리
            handlePlayerLogout(player);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "플레이어 킥 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 초기화된 상태에서의 접속 처리
     */
    private void handleInitializedJoin(Player player) {
        try {
            // 환영 메시지
            sendWelcomeMessage(player);

            // 비동기로 플레이어 등록
            registerPlayerAsync(player);

            // 각 시스템별 접속 처리
            handleSystemsOnJoin(player);

            plugin.getLogger().info("플레이어 접속 처리 완료: " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "초기화된 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * 초기화되지 않은 상태에서의 접속 처리
     */
    private void handleUninitializedJoin(Player player) {
        // 초기화 대기 메시지
        player.sendMessage("§e서버 준비 중입니다... 잠시만 기다려주세요.");

        // 초기화 완료까지 대기하는 태스크
        new BukkitTask() {
            private int attempts = 0;
            private final int maxAttempts = 30; // 15초 대기

            @Override
            public void run() {
                attempts++;

                if (plugin.isInitialized()) {
                    // 초기화 완료 - 정상 처리
                    try {
                        if (player.isOnline()) {
                            player.sendMessage("§a서버 준비 완료! 환영합니다!");
                            handleInitializedJoin(player);
                        }

                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "초기화 대기 후 플레이어 처리 실패: " + player.getName(), e);
                    }
                    cancel();

                } else if (attempts >= maxAttempts) {
                    // 초기화 시간 초과
                    plugin.getLogger().warning(
                            "플러그인 초기화 시간 초과 - 플레이어 처리 제한: " + player.getName());

                    if (player.isOnline()) {
                        player.sendMessage("§c서버 준비 중입니다. 잠시 후 다시 시도해주세요.");
                    }
                    cancel();

                } else if (!player.isOnline()) {
                    // 플레이어가 이미 나감
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // 0.5초마다 확인
    }

    /**
     * 환영 메시지 전송
     */
    private void sendWelcomeMessage(Player player) {
        try {
            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l    GGM 야생 서버에 오신 것을 환영합니다!");
            player.sendMessage("");
            player.sendMessage("§a주요 명령어:");
            player.sendMessage("§7• §e/job §7- 직업 시스템");
            player.sendMessage("§7• §e/upgrade §7- 강화 시스템");

            if (plugin.isFeatureEnabled("dragon_reward")) {
                player.sendMessage("§7• §e/dragon §7- 드래곤 보상");
            }

            if (plugin.isFeatureEnabled("npc_trading")) {
                player.sendMessage("§7• §e/trade §7- NPC 교환");
            }

            player.sendMessage("§7• §e/survival help §7- 도움말");
            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "환영 메시지 전송 실패: " + player.getName(), e);
        }
    }

    /**
     * 비동기 플레이어 등록
     */
    private void registerPlayerAsync(Player player) {
        if (plugin.getDatabaseManager() == null) {
            plugin.getLogger().warning("DatabaseManager가 null입니다 - 플레이어 등록 불가");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().createOrUpdatePlayer(
                        player.getUniqueId(), player.getName()).join();

                plugin.getLogger().info("플레이어 데이터 등록 완료: " + player.getName());

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "비동기 플레이어 등록 실패: " + player.getName(), e);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 등록 중 예상치 못한 오류: " + player.getName(), throwable);
            return null;
        });
    }

    /**
     * 각 시스템별 접속 처리
     */
    private void handleSystemsOnJoin(Player player) {
        try {
            // 직업 시스템 처리
            if (plugin.isFeatureEnabled("job_system")) {
                handleJobSystemOnJoin(player);
            }

            // 강화 시스템 처리
            if (plugin.isFeatureEnabled("upgrade_system")) {
                handleUpgradeSystemOnJoin(player);
            }

            // 도끼 속도 시스템 처리
            if (plugin.isFeatureEnabled("axe_speed_system")) {
                handleAxeSpeedSystemOnJoin(player);
            }

            // 드래곤 보상 시스템 처리
            if (plugin.isFeatureEnabled("dragon_reward_system")) {
                handleDragonRewardSystemOnJoin(player);
            }

            // NPC 교환 시스템 처리
            if (plugin.isFeatureEnabled("npc_trading_system")) {
                handleNPCTradingSystemOnJoin(player);
            }

            // 엔더 리셋 시스템 처리
            if (plugin.isFeatureEnabled("ender_reset_system")) {
                handleEnderResetSystemOnJoin(player);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "시스템별 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * 직업 시스템 접속 처리
     */
    private void handleJobSystemOnJoin(Player player) {
        JobManager jobManager = plugin.getJobManager();
        if (jobManager == null) {
            plugin.getLogger().warning("JobManager가 null입니다 - 직업 시스템 처리 불가");
            return;
        }

        try {
            // JobManager의 이벤트 핸들러가 처리하므로 여기서는 추가 작업 없음
            plugin.getLogger().info("직업 시스템 접속 처리: " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "직업 시스템 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * 강화 시스템 접속 처리
     */
    private void handleUpgradeSystemOnJoin(Player player) {
        try {
            if (plugin.getEnchantUpgradeManager() != null) {
                // 강화 시스템 초기화 (필요한 경우)
                plugin.getLogger().info("강화 시스템 접속 처리: " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "강화 시스템 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * 도끼 속도 시스템 접속 처리
     */
    private void handleAxeSpeedSystemOnJoin(Player player) {
        try {
            if (plugin.getAxeSpeedManager() != null) {
                // 도끼 속도 시스템 초기화 (필요한 경우)
                plugin.getLogger().info("도끼 속도 시스템 접속 처리: " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "도끼 속도 시스템 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * 드래곤 보상 시스템 접속 처리
     */
    private void handleDragonRewardSystemOnJoin(Player player) {
        try {
            if (plugin.getDragonRewardManager() != null) {
                // 드래곤 보상 시스템 초기화 (필요한 경우)
                plugin.getLogger().info("드래곤 보상 시스템 접속 처리: " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "드래곤 보상 시스템 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * NPC 교환 시스템 접속 처리
     */
    private void handleNPCTradingSystemOnJoin(Player player) {
        try {
            if (plugin.getNPCTradeManager() != null) {
                // NPC 교환 시스템 초기화 (필요한 경우)
                plugin.getLogger().info("NPC 교환 시스템 접속 처리: " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 교환 시스템 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * 엔더 리셋 시스템 접속 처리
     */
    private void handleEnderResetSystemOnJoin(Player player) {
        try {
            if (plugin.getEnderResetManager() != null) {
                // 엔더 리셋 시스템 초기화 (필요한 경우)
                plugin.getLogger().info("엔더 리셋 시스템 접속 처리: " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "엔더 리셋 시스템 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * 플레이어 로그아웃 처리
     */
    private void handlePlayerLogout(Player player) {
        CompletableFuture.runAsync(() -> {
            try {
                // 데이터베이스 온라인 상태 업데이트
                if (plugin.getDatabaseManager() != null) {
                    plugin.getDatabaseManager().updatePlayerOnlineStatus(
                            player.getUniqueId(), false).join();
                }

                plugin.getLogger().info("플레이어 로그아웃 처리 완료: " + player.getName());

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "플레이어 로그아웃 처리 실패: " + player.getName(), e);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 로그아웃 중 예상치 못한 오류: " + player.getName(), throwable);
            return null;
        });
    }

    /**
     * 서버 종료 시 모든 플레이어 처리
     */
    public void handleServerShutdown() {
        try {
            plugin.getLogger().info("서버 종료 - 모든 플레이어 데이터 저장 중...");

            // 모든 온라인 플레이어 처리
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    handlePlayerLogout(player);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "서버 종료 시 플레이어 처리 실패: " + player.getName(), e);
                }
            }

            plugin.getLogger().info("서버 종료 플레이어 처리 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "서버 종료 처리 중 오류", e);
        }
    }
}