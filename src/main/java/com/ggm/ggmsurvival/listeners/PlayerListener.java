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

        // JobManager가 있는 경우 직업 캐시 로드
        if (jobManager != null) {
            jobManager.onPlayerJoin(player);

            // 직업 선택 강제 여부 확인
            boolean forceJobSelection = plugin.getConfig().getBoolean("job_system.force_job_selection", true);
            if (forceJobSelection) {
                int delay = plugin.getConfig().getInt("job_system.job_selection_delay", 60);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        jobManager.getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
                            if (jobType == JobManager.JobType.NONE) {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                    player.sendMessage("§e§l⚠ 직업을 선택해주세요!");
                                    player.sendMessage("§7직업을 선택하지 않으면 특별한 능력을 사용할 수 없습니다.");
                                    player.sendMessage("§a명령어: §f/job select <직업명>");
                                    player.sendMessage("§a또는: §f/job gui");
                                    player.sendMessage("§7사용 가능한 직업: §f탱커, 검사, 궁수");
                                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                                    // 선택적으로 GUI 자동 열기
                                    boolean autoOpenGUI = plugin.getConfig().getBoolean("job_system.auto_open_gui", false);
                                    if (autoOpenGUI) {
                                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                            if (player.isOnline()) {
                                                jobManager.openJobSelectionGUI(player);
                                            }
                                        }, 40L); // 2초 후 GUI 열기
                                    }
                                });
                            }
                        });
                    }
                }, delay * 20L);
            }
        }

        // 환영 메시지 (설정 가능)
        boolean welcomeMessage = plugin.getConfig().getBoolean("messages.welcome_enabled", true);
        if (welcomeMessage) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§6§l🌟 GGM 야생 서버에 오신 것을 환영합니다!");
                    player.sendMessage("");
                    player.sendMessage("§e💼 직업 시스템: §f/job gui");
                    player.sendMessage("§e⚡ 강화 시스템: §f인챈트 테이블 사용");
                    player.sendMessage("§e🐉 드래곤 토벌: §f보상 시스템 활성화");
                    player.sendMessage("§e🏪 NPC 교환: §f/trade 명령어");
                    player.sendMessage("");
                    player.sendMessage("§7즐거운 게임 되세요!");
                    player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                }
            }, 80L); // 4초 후 환영 메시지
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // JobManager가 있는 경우 캐시 정리
        if (jobManager != null) {
            jobManager.onPlayerQuit(player);
        }

        // 퇴장 로그
        plugin.getLogger().info(String.format("[플레이어 퇴장] %s님이 야생 서버에서 퇴장했습니다.", player.getName()));
    }

    /**
     * 첫 접속 플레이어 감지 및 특별 처리
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 첫 접속 감지
        if (!player.hasPlayedBefore()) {
            plugin.getLogger().info(String.format("[신규 플레이어] %s님이 야생 서버에 첫 접속했습니다!", player.getName()));

            // 신규 플레이어 혜택 (설정으로 제어)
            boolean newPlayerBonus = plugin.getConfig().getBoolean("new_player.bonus_enabled", true);
            if (newPlayerBonus) {
                long bonusAmount = plugin.getConfig().getLong("new_player.bonus_amount", 5000L);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && plugin.getEconomyManager() != null) {
                        plugin.getEconomyManager().addMoney(player.getUniqueId(), player.getName(), bonusAmount)
                                .thenAccept(success -> {
                                    if (success) {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                            player.sendMessage("§6§l🎁 신규 플레이어 혜택!");
                                            player.sendMessage("§a+" + plugin.getEconomyManager().formatMoney(bonusAmount) + "G가 지급되었습니다!");
                                            player.sendMessage("§7야생 서버에서 즐거운 시간 보내세요!");
                                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                                        });
                                    }
                                });
                    }
                }, 100L); // 5초 후 신규 혜택 지급
            }
        }
    }
}