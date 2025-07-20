package com.ggm.ggmsurvival.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.DragonRewardManager;

public class DragonCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final DragonRewardManager dragonManager;

    public DragonCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.dragonManager = plugin.getDragonRewardManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

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
            case "today":
            case "오늘":
                showTodayDragonKills(player);
                break;
            case "history":
            case "기록":
                showPlayerDragonHistory(player);
                break;
            default:
                player.sendMessage("§c사용법: /dragon [info|today|history]");
                break;
        }

        return true;
    }

    private void showDragonInfo(Player player) {
        player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§d§l🐉 드래곤 보상 시스템");
        player.sendMessage("");
        player.sendMessage("§7엔더드래곤 처치 시 §6100,000G §7보상!");
        player.sendMessage("§7• 하루 1회 제한");
        player.sendMessage("§7• 최소 기여도 필요");
        player.sendMessage("§7• 기여도에 따라 보상 차등 지급");
        player.sendMessage("");
        player.sendMessage("§e명령어:");
        player.sendMessage("§7/dragon today - 오늘의 처치 기록");
        player.sendMessage("§7/dragon history - 내 처치 기록");
        player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showTodayDragonKills(Player player) {
        dragonManager.getTodayDragonKills().thenAccept(records -> {
            player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§d§l📊 오늘의 드래곤 처치 기록");

            if (records.isEmpty()) {
                player.sendMessage("§7오늘은 아직 드래곤이 처치되지 않았습니다.");
            } else {
                player.sendMessage("");
                for (int i = 0; i < records.size(); i++) {
                    DragonRewardManager.TodayDragonRecord record = records.get(i);
                    player.sendMessage(String.format("§e%d. §f%s §7- §6%s G §7(기여도: %d)",
                            i + 1, record.getPlayerName(),
                            plugin.getEconomyManager().formatMoney(record.getRewardAmount()),
                            record.getDamageDealt()));
                }
            }

            player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }

    private void showPlayerDragonHistory(Player player) {
        dragonManager.getPlayerDragonHistory(player.getUniqueId()).thenAccept(records -> {
            player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§d§l📈 " + player.getName() + "의 드래곤 처치 기록");

            if (records.isEmpty()) {
                player.sendMessage("§7아직 드래곤 처치 기록이 없습니다.");
                player.sendMessage("§7엔더드래곤을 처치하여 보상을 받아보세요!");
            } else {
                player.sendMessage("");
                for (int i = 0; i < records.size(); i++) {
                    DragonRewardManager.DragonRecord record = records.get(i);
                    player.sendMessage(String.format("§e%d. §7%s §f- §6%s G",
                            i + 1, record.getDate().toString(),
                            plugin.getEconomyManager().formatMoney(record.getRewardAmount())));
                }

                long totalReward = records.stream().mapToLong(DragonRewardManager.DragonRecord::getRewardAmount).sum();
                player.sendMessage("");
                player.sendMessage("§a총 처치 횟수: §f" + records.size() + "회");
                player.sendMessage("§a총 획득 보상: §6" + plugin.getEconomyManager().formatMoney(totalReward) + "G");
            }

            player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }
}