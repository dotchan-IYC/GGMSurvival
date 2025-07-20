// 수정된 PlayerListener.java - hasSelectedJob 메소드 오류 해결
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.managers.JobManager.JobType;
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
            plugin.getLogger().warning("플레이어 퇴장 처리 중 오료: " + e.getMessage());
        }
    }

    /**
     * 직업 선택 확인 및 안내 - 수정된 버전
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

            // getJobType을 사용하여 직업 확인 (hasSelectedJob 대신)
            JobType currentJob = jobManager.getJobType(player);

            if (currentJob == JobType.NONE) {
                // 직업 미선택 플레이어에게 안내
                sendJobSelectionReminder(player);

                // 1분 후 다시 알림
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            JobType stillNoJob = jobManager.getJobType(player);
                            if (stillNoJob == JobType.NONE) {
                                player.sendMessage("§e직업을 선택하여 특수 능력을 활용하세요!");
                                player.sendMessage("§7명령어: §a/job select");
                            }
                        }
                    }
                }.runTaskLater(plugin, 1200L); // 1분 (1200 ticks)
            } else {
                // 직업이 있는 플레이어에게 환영 메시지
                player.sendMessage("§a환영합니다! " + currentJob.getColor() + currentJob.getDisplayName() + " §a직업으로 플레이하시는군요!");
                player.sendMessage("§7레벨: §f" + jobManager.getJobLevel(player) + " §7경험치: §f" + jobManager.getJobExperience(player));
            }

        } catch (Exception e) {
            plugin.getLogger().severe("직업 선택 확인 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 직업 선택 안내 메시지
     */
    private void sendJobSelectionReminder(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚔️ 직업을 선택하세요!");
        player.sendMessage("");
        player.sendMessage("§7직업을 선택하면:");
        player.sendMessage("§7• 각 직업별 고유 능력 획득");
        player.sendMessage("§7• 몬스터 처치로 경험치 획득");
        player.sendMessage("§7• 레벨 5 달성 시 특수 능력 해제");
        player.sendMessage("§7• 최대 10레벨까지 성장 가능");
        player.sendMessage("");
        player.sendMessage("§c⚠️ 직업 선택 후 변경은 불가능합니다!");
        player.sendMessage("");
        player.sendMessage("§a명령어: §f/job select");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}