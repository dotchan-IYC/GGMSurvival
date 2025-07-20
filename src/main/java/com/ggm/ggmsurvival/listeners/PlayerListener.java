package com.ggm.ggmsurvival.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;

public class PlayerListener implements Listener {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public PlayerListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getLogger().info(String.format("플레이어 %s이(가) 야생 서버에 접속했습니다.", player.getName()));

        // 환영 메시지
        sendWelcomeMessage(player);

        // 직업 선택 확인 (지연 후)
        long delay = plugin.getConfig().getLong("job_system.job_selection_delay", 60) * 20L; // 틱으로 변환

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkJobSelection(player);
        }, delay);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info(String.format("플레이어 %s이(가) 야생 서버에서 나갔습니다.", player.getName()));
    }

    /**
     * 환영 메시지 전송
     */
    private void sendWelcomeMessage(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 서버별로 다른 환영 메시지
            if (plugin.isFeatureEnabled("upgrade_system")) {
                // 야생 서버 메시지
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l🌲 GGM 야생 서버에 오신 것을 환영합니다! 🌲");
                player.sendMessage("");
                player.sendMessage("§a이곳은 특별한 기능들이 있는 야생 서버입니다:");
                player.sendMessage("§7• §c직업 시스템 §7- 탱커, 검사, 궁수 중 선택");
                player.sendMessage("§7• §6G 강화 시스템 §7- 인첸트 테이블로 아이템 강화");
                player.sendMessage("§7• §5드래곤 보상 §7- 엔더드래곤 처치 시 100,000G");
                player.sendMessage("§7• §b NPC 교환 §7- 아이템을 G로 판매");
                player.sendMessage("");
                player.sendMessage("§e명령어: §f/job, /upgrade, /dragon, /trade");
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            } else {
                // 다른 서버 메시지
                String serverName = getServerName();
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l🎮 GGM " + serverName + "에 오신 것을 환영합니다! 🎮");
                player.sendMessage("");
                player.sendMessage("§a직업 시스템이 활성화되어 있습니다:");
                player.sendMessage("§7• §c탱커 §7- 방어와 체력 특화");
                player.sendMessage("§7• §6검사 §7- 검술과 공격 특화");
                player.sendMessage("§7• §a궁수 §7- 활과 이동속도 특화");
                player.sendMessage("");
                player.sendMessage("§e명령어: §f/job select (직업 선택)");
                player.sendMessage("§7야생 서버에서 추가 기능을 이용하세요!");
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }
        }, 40L); // 2초 후
    }

    /**
     * 현재 서버 이름 가져오기
     */
    private String getServerName() {
        int port = plugin.getServer().getPort();
        return switch (port) {
            case 25565 -> "로비 서버";
            case 25566 -> "건축 서버";
            case 25567 -> "야생 서버";
            case 25568 -> "마을 서버";
            default -> "서버";
        };
    }

    /**
     * 직업 선택 확인
     */
    private void checkJobSelection(Player player) {
        if (!player.isOnline()) return;

        // 직업 선택 기능이 활성화되어 있는지 확인
        if (!plugin.isFeatureEnabled("job_selection")) return;

        boolean forceSelection = plugin.getConfig().getBoolean("job_system.force_job_selection", true);
        if (!forceSelection) return;

        jobManager.hasSelectedJob(player.getUniqueId()).thenAccept(hasJob -> {
            if (!hasJob) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        sendJobSelectionReminder(player);
                    }
                });
            } else {
                // 직업이 있는 경우 현재 직업 알림 (야생 서버에서만)
                if (plugin.isFeatureEnabled("upgrade_system")) { // 야생 서버 체크
                    jobManager.getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage("§a현재 직업: " + jobType.getDisplayName());
                                player.sendMessage("§7직업 특성을 활용하여 야생 생활을 즐겨보세요!");
                            }
                        });
                    });
                } else {
                    // 다른 서버에서는 간단한 알림만
                    jobManager.getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage("§a현재 직업: " + jobType.getDisplayName());
                                player.sendMessage("§7직업 효과가 적용 중입니다!");
                            }
                        });
                    });
                }
            }
        });
    }

    /**
     * 직업 선택 알림 메시지
     */
    private void sendJobSelectionReminder(Player player) {
        String serverName = getServerName();

        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§c§l⚠️ 직업 선택이 필요합니다! ⚠️");
        player.sendMessage("");

        if (plugin.isFeatureEnabled("upgrade_system")) {
            // 야생 서버에서의 메시지
            player.sendMessage("§e야생 서버의 특별한 기능을 사용하려면");
            player.sendMessage("§e먼저 직업을 선택해야 합니다!");
        } else {
            // 다른 서버에서의 메시지
            player.sendMessage("§e" + serverName + "에서 직업 효과를 받으려면");
            player.sendMessage("§e먼저 직업을 선택해야 합니다!");
        }

        player.sendMessage("");
        player.sendMessage("§a사용 가능한 직업:");
        player.sendMessage("§7• §c탱커 §7- 방어와 체력 특화");
        player.sendMessage("§7• §6검사 §7- 검술과 공격 특화");
        player.sendMessage("§7• §a궁수 §7- 활과 이동속도 특화");
        player.sendMessage("");
        player.sendMessage("§f§l명령어: §e/job select");
        player.sendMessage("§7자세한 정보: §e/job info");
        player.sendMessage("");
        player.sendMessage("§c※ 직업은 한 번 선택하면 변경할 수 없습니다!");

        if (!plugin.isFeatureEnabled("upgrade_system")) {
            player.sendMessage("§a※ 직업 효과는 모든 서버에서 적용됩니다!");
        }

        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 5분 후 재알림 스케줄
        scheduleJobReminder(player, 1);
    }

    /**
     * 직업 선택 재알림 스케줄
     */
    private void scheduleJobReminder(Player player, int reminderCount) {
        if (reminderCount > 3) return; // 최대 3번까지만 알림

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            jobManager.hasSelectedJob(player.getUniqueId()).thenAccept(hasJob -> {
                if (!hasJob) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage("§e§l[알림 " + reminderCount + "/3] §c아직 직업을 선택하지 않으셨습니다!");
                            player.sendMessage("§7직업 선택: §e/job select §7| 정보: §e/job info");

                            if (reminderCount == 3) {
                                player.sendMessage("§c더 이상 알림을 보내지 않습니다. 준비되면 언제든 선택하세요!");
                            } else {
                                // 다음 알림 스케줄 (5분 후)
                                scheduleJobReminder(player, reminderCount + 1);
                            }
                        }
                    });
                }
            });
        }, 6000L); // 5분 (6000틱)
    }
}