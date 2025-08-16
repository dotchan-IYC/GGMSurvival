// 완전한 JobCommand.java - 직업 시스템 명령어 (이모티콘 제거)
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * 완전한 직업 시스템 명령어 처리기
 * - 직업 정보 확인
 * - 직업 변경
 * - 직업 리스트
 * - 직업 통계
 * - 관리자 명령어
 */
public class JobCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // JobManager 확인
        if (jobManager == null) {
            sender.sendMessage("§c직업 시스템이 비활성화되어 있습니다.");
            sender.sendMessage("§7config.yml에서 job_system.enabled를 true로 설정하세요.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        try {
            if (args.length == 0) {
                showJobInfo(player);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "info":
                case "정보":
                    showJobInfo(player);
                    break;

                case "change":
                case "변경":
                    if (args.length < 2) {
                        sender.sendMessage("§c사용법: /job change <직업명>");
                        sender.sendMessage("§7사용 가능한 직업: 탱커, 전사, 궁수");
                        return true;
                    }
                    changeJob(player, args[1]);
                    break;

                case "reset":
                case "초기화":
                    if (!player.hasPermission("ggm.job.reset")) {
                        player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
                        return true;
                    }
                    resetJob(player);
                    break;

                case "level":
                case "레벨":
                    showJobLevel(player);
                    break;

                case "list":
                case "목록":
                    showJobList(player);
                    break;

                case "stats":
                case "통계":
                    showJobStats(player);
                    break;

                case "exp":
                case "경험치":
                    if (!player.hasPermission("ggm.job.admin")) {
                        player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage("§c사용법: /job exp <경험치량>");
                        return true;
                    }
                    addExperience(player, args[1]);
                    break;

                case "help":
                case "도움말":
                    showHelp(player);
                    break;

                default:
                    player.sendMessage("§c알 수 없는 명령어입니다: " + subCommand);
                    showHelp(player);
                    break;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "직업 명령어 처리 중 오류: " + player.getName() + " - " + String.join(" ", args), e);
            player.sendMessage("§c명령어 처리 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 직업 정보 표시
     */
    private void showJobInfo(Player player) {
        jobManager.getPlayerJobDataAsync(player.getUniqueId()).thenAccept(jobData -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6===========================================");
                player.sendMessage("§e§l        내 직업 정보");
                player.sendMessage("");

                if (jobData.getJobType() == JobManager.JobType.NONE) {
                    player.sendMessage("§7현재 직업: §c없음");
                    player.sendMessage("§7/job change <직업명> 으로 직업을 선택하세요!");
                    player.sendMessage("");
                    player.sendMessage("§a사용 가능한 직업:");
                    showAvailableJobs(player);
                } else {
                    player.sendMessage("§7현재 직업: §a" + jobData.getJobType().getDisplayName());
                    player.sendMessage("§7레벨: §f" + jobData.getLevel());
                    player.sendMessage("§7경험치: §f" + formatNumber(jobData.getExperience()));

                    // 다음 레벨까지 필요한 경험치
                    long expToNext = jobManager.getExpToNextLevel(jobData.getLevel() + 1) -
                            (jobData.getExperience() - jobManager.getExpToNextLevel(jobData.getLevel()));
                    player.sendMessage("§7다음 레벨까지: §e" + formatNumber(expToNext) + " EXP");

                    player.sendMessage("");
                    player.sendMessage("§a직업 설명:");
                    player.sendMessage("§7" + jobData.getJobType().getDescription());

                    // 직업별 능력 설명
                    showJobAbilities(player, jobData.getJobType());
                }

                player.sendMessage("§6===========================================");
            });
        });
    }

    /**
     * 직업 변경
     */
    private void changeJob(Player player, String jobName) {
        JobManager.JobType targetJob = parseJobType(jobName);

        if (targetJob == null) {
            player.sendMessage("§c존재하지 않는 직업입니다: " + jobName);
            player.sendMessage("§7사용 가능한 직업: 탱커, 전사, 궁수");
            return;
        }

        if (targetJob == JobManager.JobType.NONE) {
            player.sendMessage("§c직업을 선택해주세요.");
            return;
        }

        player.sendMessage("§e직업 변경을 시도하고 있습니다...");

        jobManager.changePlayerJob(player, targetJob).thenAccept(result -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    player.sendMessage("§a" + result.getMessage());
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    // 새 직업 정보 표시
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        showJobInfo(player);
                    }, 20L); // 1초 후
                } else {
                    player.sendMessage("§c" + result.getMessage());
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            });
        });
    }

    /**
     * 직업 초기화 (관리자용)
     */
    private void resetJob(Player player) {
        player.sendMessage("§e직업을 초기화하고 있습니다...");

        jobManager.changePlayerJob(player, JobManager.JobType.NONE).thenAccept(result -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    player.sendMessage("§a직업이 초기화되었습니다.");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
                } else {
                    player.sendMessage("§c직업 초기화 실패: " + result.getMessage());
                }
            });
        });
    }

    /**
     * 직업 레벨 정보 표시
     */
    private void showJobLevel(Player player) {
        jobManager.getPlayerJobDataAsync(player.getUniqueId()).thenAccept(jobData -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (jobData.getJobType() == JobManager.JobType.NONE) {
                    player.sendMessage("§c직업이 없습니다. /job change 로 직업을 선택하세요.");
                    return;
                }

                player.sendMessage("§6==== 직업 레벨 정보 ====");
                player.sendMessage("§a직업: §f" + jobData.getJobType().getDisplayName());
                player.sendMessage("§a현재 레벨: §f" + jobData.getLevel());
                player.sendMessage("§a현재 경험치: §f" + formatNumber(jobData.getExperience()));

                // 레벨별 필요 경험치 계산
                long currentLevelExp = (long) Math.pow(jobData.getLevel() - 1, 2) * 100;
                long nextLevelExp = (long) Math.pow(jobData.getLevel(), 2) * 100;
                long expInCurrentLevel = jobData.getExperience() - currentLevelExp;
                long expToNext = nextLevelExp - jobData.getExperience();

                player.sendMessage("§a이번 레벨 진행도: §f" + formatNumber(expInCurrentLevel) +
                        " / " + formatNumber(nextLevelExp - currentLevelExp));
                player.sendMessage("§a다음 레벨까지: §e" + formatNumber(expToNext) + " EXP");

                // 진행률 바 표시
                double progressPercent = (double) expInCurrentLevel / (nextLevelExp - currentLevelExp) * 100;
                String progressBar = createProgressBar(progressPercent);
                player.sendMessage("§a진행률: " + progressBar + " §f(" + String.format("%.1f", progressPercent) + "%)");
            });
        });
    }

    /**
     * 직업 목록 표시
     */
    private void showJobList(Player player) {
        player.sendMessage("§6===========================================");
        player.sendMessage("§e§l       사용 가능한 직업");
        player.sendMessage("");

        // 탱커
        player.sendMessage("§c§l[탱커]");
        player.sendMessage("§7• 설명: " + JobManager.JobType.TANK.getDescription());
        player.sendMessage("§7• 특징: 높은 체력, 강한 방어력, 느린 이동속도");
        player.sendMessage("§7• 전용 능력: 생명력 흡수, 저항 효과");
        player.sendMessage("");

        // 전사
        player.sendMessage("§4§l[전사]");
        player.sendMessage("§7• 설명: " + JobManager.JobType.WARRIOR.getDescription());
        player.sendMessage("§7• 특징: 높은 공격력, 치명타 확률");
        player.sendMessage("§7• 전용 능력: 힘 강화, 치명타 공격");
        player.sendMessage("");

        // 궁수
        player.sendMessage("§e§l[궁수]");
        player.sendMessage("§7• 설명: " + JobManager.JobType.ARCHER.getDescription());
        player.sendMessage("§7• 특징: 빠른 이동속도, 활 공격력 강화");
        player.sendMessage("§7• 전용 능력: 화살 회수, 점프 강화");
        player.sendMessage("");

        player.sendMessage("§a직업 변경: §f/job change <직업명>");
        player.sendMessage("§6===========================================");
    }

    /**
     * 직업 통계 표시
     */
    private void showJobStats(Player player) {
        jobManager.getPlayerJobDataAsync(player.getUniqueId()).thenAccept(jobData -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6==== 직업 통계 ====");

                if (jobData.getJobType() == JobManager.JobType.NONE) {
                    player.sendMessage("§c직업이 없어 통계를 표시할 수 없습니다.");
                    return;
                }

                player.sendMessage("§a현재 직업: §f" + jobData.getJobType().getDisplayName());
                player.sendMessage("§a달성 레벨: §f" + jobData.getLevel());
                player.sendMessage("§a총 경험치: §f" + formatNumber(jobData.getExperience()));

                // 시간당 경험치 (추정)
                long averageExpPerHour = jobData.getExperience() / Math.max(1, (System.currentTimeMillis() / 3600000));
                player.sendMessage("§a평균 경험치/시간: §f" + formatNumber(averageExpPerHour));

                // 직업 변경 기록 (향후 구현 가능)
                player.sendMessage("§7더 자세한 통계는 향후 업데이트 예정입니다.");
            });
        });
    }

    /**
     * 경험치 추가 (관리자용)
     */
    private void addExperience(Player player, String expStr) {
        try {
            long expAmount = Long.parseLong(expStr);

            if (expAmount <= 0) {
                player.sendMessage("§c경험치는 0보다 큰 값이어야 합니다.");
                return;
            }

            jobManager.addExperience(player.getUniqueId(), expAmount).thenAccept(success -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage("§a" + formatNumber(expAmount) + " 경험치를 획득했습니다!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    } else {
                        player.sendMessage("§c경험치 추가에 실패했습니다.");
                    }
                });
            });

        } catch (NumberFormatException e) {
            player.sendMessage("§c올바른 숫자를 입력해주세요: " + expStr);
        }
    }

    /**
     * 도움말 표시
     */
    private void showHelp(Player player) {
        player.sendMessage("§6=== 직업 시스템 명령어 ===");
        player.sendMessage("§e/job §7- 내 직업 정보");
        player.sendMessage("§e/job info §7- 내 직업 정보 (상세)");
        player.sendMessage("§e/job change <직업명> §7- 직업 변경");
        player.sendMessage("§e/job level §7- 직업 레벨 정보");
        player.sendMessage("§e/job list §7- 사용 가능한 직업 목록");
        player.sendMessage("§e/job stats §7- 직업 통계");

        if (player.hasPermission("ggm.job.admin")) {
            player.sendMessage("");
            player.sendMessage("§c=== 관리자 명령어 ===");
            player.sendMessage("§e/job reset §7- 직업 초기화");
            player.sendMessage("§e/job exp <경험치> §7- 경험치 추가");
        }

        player.sendMessage("§6========================");
    }

    /**
     * 사용 가능한 직업 간단 표시
     */
    private void showAvailableJobs(Player player) {
        player.sendMessage("§c• 탱커 §7- 방어 특화");
        player.sendMessage("§4• 전사 §7- 공격 특화");
        player.sendMessage("§e• 궁수 §7- 기동성 특화");
    }

    /**
     * 직업별 능력 설명
     */
    private void showJobAbilities(Player player, JobManager.JobType jobType) {
        player.sendMessage("");
        player.sendMessage("§a특수 능력:");

        switch (jobType) {
            case TANK:
                player.sendMessage("§7• 추가 체력 +2하트");
                player.sendMessage("§7• 저항 효과");
                player.sendMessage("§7• 생명력 흡수 (10% 확률)");
                player.sendMessage("§7• 이동속도 -2%");
                break;
            case WARRIOR:
                player.sendMessage("§7• 공격력 +20%");
                player.sendMessage("§7• 치명타 확률 15%");
                player.sendMessage("§7• 힘 강화 효과");
                break;
            case ARCHER:
                player.sendMessage("§7• 활 공격력 +25%");
                player.sendMessage("§7• 이동속도 +5%");
                player.sendMessage("§7• 화살 회수 확률 30%");
                player.sendMessage("§7• 점프 강화 효과");
                break;
        }
    }

    /**
     * 문자열을 JobType으로 파싱
     */
    private JobManager.JobType parseJobType(String jobName) {
        switch (jobName.toLowerCase()) {
            case "탱커":
            case "tank":
                return JobManager.JobType.TANK;
            case "전사":
            case "warrior":
                return JobManager.JobType.WARRIOR;
            case "궁수":
            case "archer":
                return JobManager.JobType.ARCHER;
            case "없음":
            case "none":
                return JobManager.JobType.NONE;
            default:
                return null;
        }
    }

    /**
     * 진행률 바 생성
     */
    private String createProgressBar(double percent) {
        int totalBars = 20;
        int filledBars = (int) (percent / 100.0 * totalBars);

        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < filledBars; i++) {
            bar.append("█");
        }
        bar.append("§7");
        for (int i = filledBars; i < totalBars; i++) {
            bar.append("█");
        }

        return bar.toString();
    }

    /**
     * 숫자 포맷팅
     */
    private String formatNumber(long number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        } else {
            return String.valueOf(number);
        }
    }

    /**
     * 탭 완성 제공
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("info", "change", "level", "list", "stats", "help");

            if (sender.hasPermission("ggm.job.admin")) {
                subCommands = Arrays.asList("info", "change", "reset", "level", "list", "stats", "exp", "help");
            }

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if ("change".equals(subCommand)) {
                completions.addAll(Arrays.asList("탱커", "전사", "궁수", "tank", "warrior", "archer"));
            } else if ("exp".equals(subCommand) && sender.hasPermission("ggm.job.admin")) {
                completions.addAll(Arrays.asList("100", "500", "1000", "5000"));
            }
        }

        return completions;
    }
}