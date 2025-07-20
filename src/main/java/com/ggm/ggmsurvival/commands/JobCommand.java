package com.ggm.ggmsurvival.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;

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
            // 현재 직업 정보 표시 또는 직업 선택 GUI
            showJobInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "select":
            case "선택":
                openJobSelection(player);
                break;

            case "info":
            case "정보":
                showDetailedJobInfo(player);
                break;

            case "list":
            case "목록":
                showJobList(player);
                break;

            case "reset":
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /job reset <플레이어> (OP 전용)");
                    return true;
                }
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                resetPlayerJob(player, args[1]);
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    /**
     * 현재 직업 정보 표시
     */
    private void showJobInfo(Player player) {
        jobManager.getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e§l나의 직업 정보");
            player.sendMessage("");

            if (jobType == JobManager.JobType.NONE) {
                player.sendMessage("§7현재 직업: §c없음");
                player.sendMessage("");
                player.sendMessage("§e아직 직업을 선택하지 않으셨습니다!");
                player.sendMessage("§7/job select 명령어로 직업을 선택하세요.");
                player.sendMessage("");
                player.sendMessage("§a§l💡 직업 선택의 이점:");
                player.sendMessage("§7• 각 직업별 특수 능력 획득");
                player.sendMessage("§7• 전투/채굴/탐험에서 보너스");
                player.sendMessage("§7• 야생 서버만의 특별한 경험!");
            } else {
                player.sendMessage("§7현재 직업: " + jobType.getDisplayName());
                player.sendMessage("");
                player.sendMessage("§e직업 효과:");

                switch (jobType) {
                    case TANK:
                        player.sendMessage("§7• §c흉갑 착용 시 체력 증가 (+2하트)");
                        player.sendMessage("§7• §c방패 사용 시 체력 회복 (0.5하트)");
                        player.sendMessage("§7• §c받는 피해 감소");
                        break;
                    case WARRIOR:
                        player.sendMessage("§7• §6검 사용 시 공격력 증가 (+20%)");
                        player.sendMessage("§7• §6치명타 확률 증가 (10%)");
                        player.sendMessage("§7• §6검 내구도 소모 감소");
                        break;
                    case ARCHER:
                        player.sendMessage("§7• §a활 사용 시 공격력 증가 (+15%)");
                        player.sendMessage("§7• §a가죽부츠 착용 시 이동속도 증가");
                        player.sendMessage("§7• §a화살 소모 확률 감소");
                        break;
                }

                player.sendMessage("");
                player.sendMessage("§a선택한 직업의 특성을 활용해보세요!");
            }

            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }

    /**
     * 직업 선택 GUI 열기
     */
    private void openJobSelection(Player player) {
        jobManager.hasSelectedJob(player.getUniqueId()).thenAccept(hasJob -> {
            if (hasJob) {
                player.sendMessage("§c이미 직업을 선택하셨습니다!");
                player.sendMessage("§7현재 직업 정보를 보려면 /job info 를 사용하세요.");
                player.sendMessage("§e§l참고: §7직업은 한 번 선택하면 변경할 수 없습니다!");
                return;
            }

            // 직업 선택 GUI 열기
            jobManager.openJobSelectionGUI(player);
        });
    }

    /**
     * 상세 직업 정보 표시
     */
    private void showDetailedJobInfo(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l야생 서버 직업 안내");
        player.sendMessage("");

        player.sendMessage("§c§l🛡 탱커 (TANK)");
        player.sendMessage("§7방어와 체력에 특화된 근접 전투 직업");
        player.sendMessage("§7효과: 흉갑 착용 시 체력 증가, 방패로 체력 회복");
        player.sendMessage("§7추천: 몬스터와 정면 대결을 즐기는 플레이어");
        player.sendMessage("");

        player.sendMessage("§6§l⚔ 검사 (WARRIOR)");
        player.sendMessage("§7검술에 특화된 공격적인 근접 전투 직업");
        player.sendMessage("§7효과: 검 공격력 증가, 치명타 확률 증가");
        player.sendMessage("§7추천: 높은 데미지로 빠르게 적을 처치하고 싶은 플레이어");
        player.sendMessage("");

        player.sendMessage("§a§l🏹 궁수 (ARCHER)");
        player.sendMessage("§7원거리 공격과 기동성에 특화된 직업");
        player.sendMessage("§7효과: 활 공격력 증가, 가죽부츠 착용 시 이동속도 증가");
        player.sendMessage("§7추천: 안전한 거리에서 전투하고 빠른 이동을 원하는 플레이어");
        player.sendMessage("");

        player.sendMessage("§e§l⚠️ 중요한 안내:");
        player.sendMessage("§7• 직업은 §c한 번 선택하면 변경할 수 없습니다!");
        player.sendMessage("§7• 각 직업은 고유한 플레이 스타일을 제공합니다");
        player.sendMessage("§7• 신중하게 선택하시기 바랍니다");

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 직업 목록 표시
     */
    private void showJobList(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l사용 가능한 직업");
        player.sendMessage("");
        player.sendMessage("§c탱커 §7- 방어형 근접 전투 직업");
        player.sendMessage("§6검사 §7- 공격형 근접 전투 직업");
        player.sendMessage("§a궁수 §7- 원거리 전투 직업");
        player.sendMessage("");
        player.sendMessage("§7자세한 정보: §e/job info");
        player.sendMessage("§7직업 선택: §e/job select");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 플레이어 직업 초기화 (OP 전용)
     */
    private void resetPlayerJob(Player sender, String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
            return;
        }

        jobManager.setPlayerJob(target.getUniqueId(), target.getName(), JobManager.JobType.NONE)
                .thenAccept(success -> {
                    if (success) {
                        sender.sendMessage("§a" + targetName + "의 직업을 초기화했습니다.");
                        target.sendMessage("§e관리자에 의해 직업이 초기화되었습니다.");
                        target.sendMessage("§7다시 /job select 명령어로 직업을 선택할 수 있습니다.");

                        plugin.getLogger().info(String.format("[직업초기화] %s이(가) %s의 직업을 초기화했습니다.",
                                sender.getName(), targetName));
                    } else {
                        sender.sendMessage("§c직업 초기화에 실패했습니다.");
                    }
                });
    }

    /**
     * 도움말 표시
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l직업 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§7/job §f- 현재 직업 정보 확인");
        player.sendMessage("§7/job select §f- 직업 선택 GUI 열기");
        player.sendMessage("§7/job info §f- 모든 직업 상세 정보");
        player.sendMessage("§7/job list §f- 직업 목록 확인");

        if (player.hasPermission("ggm.job.admin")) {
            player.sendMessage("");
            player.sendMessage("§c관리자 명령어:");
            player.sendMessage("§7/job reset <플레이어> §f- 직업 초기화");
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}