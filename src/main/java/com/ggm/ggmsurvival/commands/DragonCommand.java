// 완전한 DragonCommand.java - 드래곤 보상 시스템 명령어 (이모티콘 제거)
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.DragonRewardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * 완전한 드래곤 보상 시스템 명령어 처리기
 * - 드래곤 처치 정보
 * - 보상 통계
 * - 전역 통계
 * - 보상 시뮬레이션
 */
public class DragonCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final DragonRewardManager dragonManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public DragonCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.dragonManager = plugin.getDragonRewardManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // DragonRewardManager 확인
        if (dragonManager == null) {
            sender.sendMessage("§c드래곤 보상 시스템이 비활성화되어 있습니다.");
            sender.sendMessage("§7config.yml에서 dragon_reward_system.enabled를 true로 설정하세요.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        try {
            if (args.length == 0) {
                showDragonInfo(player);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "info":
                case "정보":
                    showDragonInfo(player);
                    break;

                case "stats":
                case "통계":
                    showPlayerStats(player);
                    break;

                case "global":
                case "전역":
                    showGlobalStats(player);
                    break;

                case "rewards":
                case "보상":
                    showRewardInfo(player);
                    break;

                case "simulate":
                case "시뮬레이션":
                    simulateDragonReward(player);
                    break;

                case "leaderboard":
                case "순위":
                    showLeaderboard(player);
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
                    "드래곤 명령어 처리 중 오류: " + player.getName() + " - " + String.join(" ", args), e);
            player.sendMessage("§c명령어 처리 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 드래곤 시스템 정보 표시
     */
    private void showDragonInfo(Player player) {
        player.sendMessage("§6===========================================");
        player.sendMessage("§e§l      엔더 드래곤 보상 시스템");
        player.sendMessage("");

        player.sendMessage("§a기본 정보:");
        player.sendMessage("§7• 기본 보상: §f" +
                plugin.getEconomyManager().formatMoneyWithSymbol(dragonManager.getBaseMoneyReward()) +
                " + " + dragonManager.getBaseExpReward() + " EXP");
        player.sendMessage("§7• 처치자 보너스: §e+50% 추가 보상");
        player.sendMessage("§7• 직업별 보너스: §a탱커 +20%, 전사 +10%");

        player.sendMessage("");
        player.sendMessage("§a특별 드롭:");
        player.sendMessage("§7• §5드래곤 알: §f5% 확률");
        player.sendMessage("§7• §b엘리트라: §f10% 확률");
        player.sendMessage("§7• §8드래곤 머리: §f15% 확률");

        player.sendMessage("");
        player.sendMessage("§a직업별 특별 아이템:");
        player.sendMessage("§7• §c탱커: §f수호자의 방패 + 철괴");
        player.sendMessage("§7• §4전사: §f용살자의 검 + 다이아몬드");
        player.sendMessage("§7• §e궁수: §f정령의 활 + 화살");

        player.sendMessage("");
        player.sendMessage("§7명령어: §f/dragon stats §7- 내 드래곤 처치 통계");
        player.sendMessage("§6===========================================");
    }

    /**
     * 플레이어 드래곤 통계 표시
     */
    private void showPlayerStats(Player player) {
        dragonManager.getPlayerDragonStats(player.getUniqueId()).thenAccept(stats -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6==== 내 드래곤 처치 통계 ====");

                if (stats.getKillCount() == 0) {
                    player.sendMessage("§c아직 엔더 드래곤을 처치한 기록이 없습니다.");
                    player.sendMessage("§7엔드로 가서 드래곤을 처치해보세요!");
                } else {
                    player.sendMessage("§a처치 횟수: §f" + stats.getKillCount() + "회");
                    player.sendMessage("§a총 획득 금액: §f" +
                            plugin.getEconomyManager().formatMoneyWithSymbol(stats.getTotalMoney()));
                    player.sendMessage("§a총 획득 경험치: §f" + formatNumber(stats.getTotalExp()) + " EXP");

                    if (stats.getKillCount() > 0) {
                        long avgMoney = stats.getTotalMoney() / stats.getKillCount();
                        int avgExp = stats.getTotalExp() / stats.getKillCount();
                        player.sendMessage("§a평균 보상: §f" +
                                plugin.getEconomyManager().formatMoneyWithSymbol(avgMoney) +
                                " + " + avgExp + " EXP");
                    }

                    if (stats.getLastKill() != null) {
                        player.sendMessage("§a마지막 처치: §f" +
                                dateFormat.format(stats.getLastKill()));
                    }
                }

                player.sendMessage("§6========================");
            });
        });
    }

    /**
     * 전역 드래곤 통계 표시
     */
    private void showGlobalStats(Player player) {
        dragonManager.getGlobalDragonStats().thenAccept(stats -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6==== 서버 드래곤 통계 ====");
                player.sendMessage("§a총 처치 횟수: §f" + stats.getTotalKills() + "회");
                player.sendMessage("§a처치한 플레이어: §f" + stats.getUniqueKillers() + "명");
                player.sendMessage("§a지급된 총 보상: §f" +
                        plugin.getEconomyManager().formatMoneyWithSymbol(stats.getTotalMoneyDistributed()));

                if (stats.getTotalKills() > 0 && stats.getUniqueKillers() > 0) {
                    double avgKillsPerPlayer = (double) stats.getTotalKills() / stats.getUniqueKillers();
                    player.sendMessage("§a플레이어당 평균 처치: §f" +
                            String.format("%.1f", avgKillsPerPlayer) + "회");
                }

                player.sendMessage("§6====================");
            });
        });
    }

    /**
     * 보상 정보 표시
     */
    private void showRewardInfo(Player player) {
        player.sendMessage("§6==== 드래곤 보상 상세 정보 ====");

        // 기본 보상
        long baseMoney = dragonManager.getBaseMoneyReward();
        int baseExp = dragonManager.getBaseExpReward();

        player.sendMessage("§a기본 보상:");
        player.sendMessage("§7• 금액: §f" + plugin.getEconomyManager().formatMoneyWithSymbol(baseMoney));
        player.sendMessage("§7• 경험치: §f" + baseExp + " EXP");
        player.sendMessage("§7• 직업 경험치: §f" + (baseExp * 2) + " EXP");

        // 처치자 보너스
        player.sendMessage("");
        player.sendMessage("§c처치자 보너스 (드래곤에게 마지막 타격을 가한 플레이어):");
        player.sendMessage("§7• 금액: §f" + plugin.getEconomyManager().formatMoneyWithSymbol((long)(baseMoney * 1.5)));
        player.sendMessage("§7• 경험치: §f" + (int)(baseExp * 1.5) + " EXP");

        // 직업별 보너스
        player.sendMessage("");
        player.sendMessage("§b직업별 추가 보너스:");
        player.sendMessage("§7• §c탱커: §f+20% 금액 보너스");
        player.sendMessage("§7• §4전사: §f+10% 금액 보너스");
        player.sendMessage("§7• §e궁수: §f기본 보상");

        // 레벨 보너스
        if (plugin.getJobManager() != null) {
            player.sendMessage("");
            player.sendMessage("§d레벨 보너스:");
            player.sendMessage("§7• 직업 레벨당 +10% 추가 보상");
            player.sendMessage("§7• 예시: 5레벨 = +40% 보너스");
        }

        player.sendMessage("§6========================");
    }

    /**
     * 드래곤 보상 시뮬레이션
     */
    private void simulateDragonReward(Player player) {
        player.sendMessage("§e드래곤 보상 시뮬레이션을 실행하고 있습니다...");

        // 현재 플레이어 직업 정보
        String jobInfo = "없음";
        int jobLevel = 1;

        if (plugin.getJobManager() != null) {
            var jobData = plugin.getJobManager().getPlayerJobData(player.getUniqueId());
            if (jobData != null && jobData.getJobType() != com.ggm.ggmsurvival.managers.JobManager.JobType.NONE) {
                jobInfo = jobData.getJobType().getDisplayName();
                jobLevel = jobData.getLevel();
            }
        }

        player.sendMessage("");
        player.sendMessage("§6=== 보상 시뮬레이션 결과 ===");
        player.sendMessage("§7현재 직업: §f" + jobInfo + " (레벨 " + jobLevel + ")");
        player.sendMessage("");

        // 일반 참여자 보상
        long participantMoney = calculateRewardMoney(false, jobInfo, jobLevel);
        int participantExp = calculateRewardExp(false, jobLevel);

        player.sendMessage("§a일반 참여자 보상:");
        player.sendMessage("§7• 금액: §f" + plugin.getEconomyManager().formatMoneyWithSymbol(participantMoney));
        player.sendMessage("§7• 경험치: §f" + participantExp + " EXP");

        // 처치자 보상
        long killerMoney = calculateRewardMoney(true, jobInfo, jobLevel);
        int killerExp = calculateRewardExp(true, jobLevel);

        player.sendMessage("");
        player.sendMessage("§c처치자 보상:");
        player.sendMessage("§7• 금액: §f" + plugin.getEconomyManager().formatMoneyWithSymbol(killerMoney));
        player.sendMessage("§7• 경험치: §f" + killerExp + " EXP");

        player.sendMessage("");
        player.sendMessage("§7※ 특별 아이템 드롭은 확률에 따라 추가로 지급됩니다.");
        player.sendMessage("§6=======================");
    }

    /**
     * 순위표 표시 (미래 기능)
     */
    private void showLeaderboard(Player player) {
        player.sendMessage("§6==== 드래곤 처치 순위 ====");
        player.sendMessage("§e이 기능은 향후 업데이트에서 제공될 예정입니다.");
        player.sendMessage("§7현재는 개인 통계만 확인 가능합니다.");
        player.sendMessage("§7명령어: /dragon stats");
        player.sendMessage("§6====================");
    }

    /**
     * 도움말 표시
     */
    private void showHelp(Player player) {
        player.sendMessage("§6=== 드래곤 보상 시스템 명령어 ===");
        player.sendMessage("§e/dragon §7- 드래곤 시스템 정보");
        player.sendMessage("§e/dragon info §7- 상세 정보");
        player.sendMessage("§e/dragon stats §7- 내 처치 통계");
        player.sendMessage("§e/dragon global §7- 서버 전체 통계");
        player.sendMessage("§e/dragon rewards §7- 보상 상세 정보");
        player.sendMessage("§e/dragon simulate §7- 보상 시뮬레이션");
        player.sendMessage("§e/dragon leaderboard §7- 순위표 (준비 중)");
        player.sendMessage("§6=============================");
    }

    /**
     * 보상 금액 계산
     */
    private long calculateRewardMoney(boolean isKiller, String jobName, int jobLevel) {
        long baseMoney = dragonManager.getBaseMoneyReward();

        // 처치자 보너스
        if (isKiller) {
            baseMoney = (long) (baseMoney * 1.5);
        }

        // 직업별 보너스
        switch (jobName) {
            case "탱커":
                baseMoney = (long) (baseMoney * 1.2);
                break;
            case "전사":
                baseMoney = (long) (baseMoney * 1.1);
                break;
        }

        // 레벨 보너스
        if (jobLevel > 1) {
            double levelBonus = 1.0 + (jobLevel - 1) * 0.1;
            baseMoney = (long) (baseMoney * levelBonus);
        }

        return baseMoney;
    }

    /**
     * 보상 경험치 계산
     */
    private int calculateRewardExp(boolean isKiller, int jobLevel) {
        int baseExp = dragonManager.getBaseExpReward();

        // 처치자 보너스
        if (isKiller) {
            baseExp = (int) (baseExp * 1.5);
        }

        // 레벨 보너스는 금액에만 적용
        return baseExp;
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
            List<String> subCommands = Arrays.asList("info", "stats", "global", "rewards", "simulate", "leaderboard", "help");

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        }

        return completions;
    }
}