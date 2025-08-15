// 완전 안정화된 DragonCommand.java
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.DragonRewardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * 완전 안정화된 드래곤 명령어 처리기
 */
public class DragonCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final DragonRewardManager dragonManager;

    public DragonCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.dragonManager = plugin.getDragonRewardManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (dragonManager == null) {
                sender.sendMessage("§c드래곤 보상 시스템이 비활성화되어 있습니다.");
                return true;
            }

            if (args.length == 0) {
                showDragonInfo(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "info":
                case "정보":
                    if (sender instanceof Player) {
                        dragonManager.showCurrentContribution((Player) sender);
                    } else {
                        sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                    }
                    break;

                case "today":
                case "오늘":
                    showTodayDragons(sender);
                    break;

                case "history":
                case "기록":
                    if (sender instanceof Player) {
                        showPlayerHistory((Player) sender);
                    } else {
                        sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                    }
                    break;

                case "stats":
                case "통계":
                    showDragonStats(sender);
                    break;

                default:
                    sender.sendMessage("§c알 수 없는 명령어입니다. §7/dragon help §c를 참고하세요.");
                    break;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "드래곤 명령어 처리 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c명령어 처리 중 오류가 발생했습니다.");
            return true;
        }
    }

    private void showDragonInfo(CommandSender sender) {
        sender.sendMessage("§6==========================================");
        sender.sendMessage("§e§l🐉 드래곤 보상 시스템");
        sender.sendMessage("");
        sender.sendMessage("§a기본 보상: §6" + plugin.getEconomyManager().formatMoney(dragonManager.getBaseReward()) + "G");
        sender.sendMessage("§a최소 기여도: §c" + dragonManager.getMinDamageThreshold() + " 데미지");
        sender.sendMessage("§a최소 보상: §6" + plugin.getEconomyManager().formatMoney(dragonManager.getMinReward()) + "G");
        sender.sendMessage("");
        sender.sendMessage("§7명령어:");
        sender.sendMessage("§7• §e/dragon info §7- 현재 기여도 확인");
        sender.sendMessage("§7• §e/dragon today §7- 오늘의 처치 기록");
        sender.sendMessage("§7• §e/dragon history §7- 내 참가 기록");
        sender.sendMessage("§6==========================================");
    }

    private void showTodayDragons(CommandSender sender) {
        dragonManager.getTodayDragonKills().thenAccept(records -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§6==========================================");
                sender.sendMessage("§e§l🐉 오늘의 드래곤 처치 기록");
                sender.sendMessage("");

                if (records.isEmpty()) {
                    sender.sendMessage("§7오늘 처치된 드래곤이 없습니다.");
                } else {
                    for (int i = 0; i < records.size(); i++) {
                        var record = records.get(i);
                        sender.sendMessage(String.format("§a%d. §7%s - 참가자 %d명, 보상 %s",
                                i + 1,
                                record.killTime.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                                record.participantCount,
                                plugin.getEconomyManager().formatMoney(record.totalRewards) + "G"));
                    }
                }

                sender.sendMessage("§6==========================================");
            });
        });
    }

    private void showPlayerHistory(Player player) {
        dragonManager.getPlayerDragonHistory(player.getUniqueId(), 10).thenAccept(records -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6==========================================");
                player.sendMessage("§e§l🐉 내 드래곤 참가 기록 (최근 10회)");
                player.sendMessage("");

                if (records.isEmpty()) {
                    player.sendMessage("§7드래곤 참가 기록이 없습니다.");
                } else {
                    for (int i = 0; i < records.size(); i++) {
                        var record = records.get(i);
                        player.sendMessage(String.format("§a%d. §7%s - 기여도 %.1f, 보상 %s",
                                i + 1,
                                record.killDate.format(DateTimeFormatter.ofPattern("MM/dd")),
                                record.damageDealt,
                                plugin.getEconomyManager().formatMoney(record.rewardAmount) + "G"));
                    }
                }

                player.sendMessage("§6==========================================");
            });
        });
    }

    private void showDragonStats(CommandSender sender) {
        sender.sendMessage("§6==========================================");
        sender.sendMessage("§e§l🐉 드래곤 시스템 통계");
        sender.sendMessage("");
        sender.sendMessage("§7" + dragonManager.getDragonStats());
        sender.sendMessage("§6==========================================");
    }
}