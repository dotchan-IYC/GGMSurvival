package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
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
     * 플레이어 접속
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 데이터베이스에 플레이어 등록
        plugin.getDatabaseManager().createOrUpdatePlayer(player.getUniqueId(), player.getName());

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
    }

    /**
     * 플레이어 퇴장
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info(player.getName() + "이(가) 서버에서 퇴장했습니다.");
    }

    /**
     * 직업 선택 확인 및 안내
     */
    private void checkJobSelection(Player player) {
        JobManager jobManager = plugin.getJobManager();
        if (jobManager == null) return;

        jobManager.hasSelectedJob(player.getUniqueId()).thenAccept(hasJob -> {
            if (!hasJob) {
                // 직업 미선택 플레이어에게 안내
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l⚠️ 직업 선택 안내");
                player.sendMessage("");
                player.sendMessage("§c아직 직업을 선택하지 않으셨습니다!");
                player.sendMessage("§a직업을 선택하면 특수 능력을 얻을 수 있습니다.");
                player.sendMessage("");
                player.sendMessage("§e명령어: §f/job select");
                player.sendMessage("§7또는 §e/job info §7로 직업 정보를 확인하세요.");
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                // 1분 후 다시 알림
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            jobManager.hasSelectedJob(player.getUniqueId()).thenAccept(stillNoJob -> {
                                if (!stillNoJob) {
                                    player.sendMessage("§e직업을 선택하여 특수 능력을 활용하세요! §7/job select");
                                }
                            });
                        }
                    }
                }.runTaskLater(plugin, 1200L); // 1분 후
            }
        });
    }
}