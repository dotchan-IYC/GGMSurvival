// 수정된 PlayerListener.java - 메서드 시그니처 문제 해결
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerListener implements Listener {

    private final GGMSurvival plugin;

    public PlayerListener(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    /**
     * 플레이어 접속 이벤트
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            // 데이터베이스에 플레이어 등록
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().createOrUpdatePlayer(player.getUniqueId(), player.getName());
            }

            // 직업 시스템이 활성화된 경우
            if (plugin.isFeatureEnabled("job_system") && plugin.getJobManager() != null) {

                // 강제 직업 선택이 활성화된 경우
                boolean forceJobSelection = plugin.getConfig().getBoolean("job_system.force_job_selection", true);
                int delay = plugin.getConfig().getInt("job_system.job_selection_delay", 60);

                if (forceJobSelection) {
                    // 지연 후 직업 선택 확인
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.isOnline()) {
                                checkJobSelection(player);
                            }
                        }
                    }.runTaskLater(plugin, delay * 20L); // 초를 틱으로 변환
                }
            }

            plugin.getLogger().info(player.getName() + "이(가) 서버에 접속했습니다.");

        } catch (Exception e) {
            plugin.getLogger().severe("플레이어 접속 처리 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 플레이어 퇴장 이벤트
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        try {
            plugin.getLogger().info(player.getName() + "이(가) 서버에서 퇴장했습니다.");

            // 추가적인 정리 작업이 필요하면 여기에 추가

        } catch (Exception e) {
            plugin.getLogger().warning("플레이어 퇴장 처리 중 오류: " + e.getMessage());
        }
    }

    /**
     * 직업 선택 확인 및 안내
     */
    private void checkJobSelection(Player player) {
        try {
            JobManager jobManager = plugin.getJobManager();
            if (jobManager == null) {
                plugin.getLogger().warning("JobManager가 null입니다 - 직업 선택 확인 불가");
                return;
            }

            // 플레이어가 여전히 온라인인지 확인
            if (!player.isOnline()) {
                return;
            }

            jobManager.hasSelectedJob(player.getUniqueId()).thenAccept(hasJob -> {
                // 메인 스레드에서 실행
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return; // 플레이어가 오프라인이 된 경우
                    }

                    if (!hasJob) {
                        // 직업 미선택 플레이어에게 안내
                        sendJobSelectionReminder(player);

                        // 1분 후 다시 알림
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) {
                                    jobManager.hasSelectedJob(player.getUniqueId()).thenAccept(stillNoJob -> {
                                        if (!stillNoJob) {
                                            Bukkit.getScheduler().runTask(plugin, () -> {
                                                if (player.isOnline()) {
                                                    player.sendMessage("§e직업을 선택하여 특수 능력을 활용하세요! §7/job select");
                                                }
                                            });
                                        }
                                    });
                                }
                            }
                        }.runTaskLater(plugin, 1200L); // 1분 후
                    }
                });
            }).exceptionally(throwable -> {
                // 오류 처리
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().warning("직업 선택 확인 중 오류: " + throwable.getMessage());
                });
                return null;
            });

        } catch (Exception e) {
            plugin.getLogger().severe("checkJobSelection 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 직업 선택 안내 메시지 전송
     */
    private void sendJobSelectionReminder(Player player) {
        try {
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e§l⚠️ 직업 선택 안내");
            player.sendMessage("");
            player.sendMessage("§c아직 직업을 선택하지 않으셨습니다!");
            player.sendMessage("§a직업을 선택하면 특수 능력을 얻을 수 있습니다.");
            player.sendMessage("");
            player.sendMessage("§e명령어: §f/job select");
            player.sendMessage("§7또는 §e/job info §7로 직업 정보를 확인하세요.");
            player.sendMessage("");
            player.sendMessage("§a§l💡 직업 선택의 이점:");
            player.sendMessage("§7• 각 직업별 특수 능력 획득");
            player.sendMessage("§7• 전투/채굴/탐험에서 보너스");
            player.sendMessage("§7• 야생 서버만의 특별한 경험!");
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            plugin.getLogger().warning("직업 선택 안내 메시지 전송 중 오류: " + e.getMessage());
        }
    }
}