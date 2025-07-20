package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class JobCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showJobHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "select":
            case "선택":
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /job select <직업명>");
                    player.sendMessage("§7사용 가능한 직업: 탱커, 검사, 궁수");
                    return true;
                }
                selectJob(player, args[1]);
                break;

            case "gui":
                jobManager.openJobSelectionGUI(player);
                break;

            case "info":
            case "정보":
                showJobInfo(player);
                break;

            case "list":
            case "목록":
                showJobList(player);
                break;

            default:
                showJobHelp(player);
                break;
        }

        return true;
    }

    /**
     * 직업 선택 - 스코어보드 업데이트 포함
     */
    private void selectJob(Player player, String jobName) {
        try {
            JobManager.JobType jobType;

            switch (jobName.toLowerCase()) {
                case "탱커":
                case "tank":
                    jobType = JobManager.JobType.TANK;
                    break;
                case "검사":
                case "warrior":
                    jobType = JobManager.JobType.WARRIOR;
                    break;
                case "궁수":
                case "archer":
                    jobType = JobManager.JobType.ARCHER;
                    break;
                default:
                    player.sendMessage("§c알 수 없는 직업입니다!");
                    player.sendMessage("§7사용 가능한 직업: 탱커, 검사, 궁수");
                    return;
            }

            // 현재 직업 확인
            UUID uuid = player.getUniqueId();
            jobManager.getPlayerJob(uuid).thenAccept(currentJob -> {
                if (currentJob == jobType) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c이미 " + jobType.getDisplayName() + "§c 직업입니다!");
                    });
                    return;
                }

                // 직업 변경 가능한지 확인
                boolean allowJobChange = plugin.getConfig().getBoolean("job_system.allow_job_change", false);
                if (currentJob != JobManager.JobType.NONE && !allowJobChange) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c이미 직업이 설정되어 있습니다!");
                        player.sendMessage("§7현재 직업: " + currentJob.getDisplayName());
                        player.sendMessage("§7직업 변경이 허용되지 않습니다.");
                    });
                    return;
                }

                // 직업 설정 (스코어보드 업데이트 포함)
                jobManager.setPlayerJobWithUpdate(uuid, player.getName(), jobType).thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            player.sendMessage("§a§l✅ 직업 선택 완료!");
                            player.sendMessage("§7선택된 직업: " + jobType.getDisplayName());
                            player.sendMessage("");

                            showJobDescription(player, jobType);

                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

                            plugin.getLogger().info(String.format("[직업선택] %s님이 %s 직업을 선택했습니다.",
                                    player.getName(), jobType.getName()));
                        } else {
                            player.sendMessage("§c직업 설정에 실패했습니다. 관리자에게 문의하세요.");
                        }
                    });
                }).exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c직업 설정 중 오류가 발생했습니다: " + throwable.getMessage());
                    });
                    return null;
                });
            });

        } catch (Exception e) {
            player.sendMessage("§c직업 선택 중 오류가 발생했습니다.");
            plugin.getLogger().severe("직업 선택 오류: " + e.getMessage());
        }
    }

    /**
     * 직업 설명 표시
     */
    private void showJobDescription(Player player, JobManager.JobType jobType) {
        switch (jobType) {
            case TANK:
                player.sendMessage("§c[탱커 효과]");
                player.sendMessage("§7• 흉갑 착용 시 체력 +4 (하트 2개)");
                player.sendMessage("§7• 방패 사용 시 체력 회복 (3초 쿨타임)");
                player.sendMessage("§7• 받는 피해 10% 감소");
                break;
            case WARRIOR:
                player.sendMessage("§6[검사 효과]");
                player.sendMessage("§7• 검 공격력 20% 증가");
                player.sendMessage("§7• 10% 확률로 치명타 (1.5배 피해)");
                player.sendMessage("§7• 검 내구도 보호 15% 확률");
                break;
            case ARCHER:
                player.sendMessage("§a[궁수 효과]");
                player.sendMessage("§7• 활 공격력 15% 증가");
                player.sendMessage("§7• 화살 절약 확률 20%");
                player.sendMessage("§7• 가죽부츠 착용 시 속도 효과");
                player.sendMessage("§7• 기본 이동속도 10% 증가");
                break;
        }
    }

    /**
     * 현재 직업 정보 표시
     */
    private void showJobInfo(Player player) {
        UUID uuid = player.getUniqueId();

        jobManager.getPlayerJob(uuid).thenAccept(jobType -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l📋 내 직업 정보");
                player.sendMessage("");
                player.sendMessage("§7현재 직업: " + jobType.getDisplayName());
                player.sendMessage("");

                if (jobType != JobManager.JobType.NONE) {
                    showJobDescription(player, jobType);
                } else {
                    player.sendMessage("§7직업이 설정되지 않았습니다.");
                    player.sendMessage("§a/job select <직업명> §7으로 직업을 선택하세요!");
                }

                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            });
        });
    }

    /**
     * 사용 가능한 직업 목록 표시
     */
    private void showJobList(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🏆 사용 가능한 직업");
        player.sendMessage("");

        player.sendMessage("§c§l탱커 (Tank)");
        player.sendMessage("§7• 방어에 특화된 근접 직업");
        player.sendMessage("§7• 높은 생존력과 체력");
        player.sendMessage("");

        player.sendMessage("§6§l검사 (Warrior)");
        player.sendMessage("§7• 공격에 특화된 근접 직업");
        player.sendMessage("§7• 높은 공격력과 치명타");
        player.sendMessage("");

        player.sendMessage("§a§l궁수 (Archer)");
        player.sendMessage("§7• 원거리 공격 특화 직업");
        player.sendMessage("§7• 빠른 이동속도와 화살 절약");
        player.sendMessage("");

        player.sendMessage("§e명령어:");
        player.sendMessage("§a/job select <직업명> §7- 직업 선택");
        player.sendMessage("§a/job gui §7- GUI로 직업 선택");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 도움말 표시
     */
    private void showJobHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l💼 직업 시스템 도움말");
        player.sendMessage("");
        player.sendMessage("§a/job select <직업명> §7- 직업 선택");
        player.sendMessage("§a/job gui §7- GUI로 직업 선택");
        player.sendMessage("§a/job info §7- 내 직업 정보 확인");
        player.sendMessage("§a/job list §7- 사용 가능한 직업 목록");
        player.sendMessage("");
        player.sendMessage("§7사용 가능한 직업: §f탱커, 검사, 궁수");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}