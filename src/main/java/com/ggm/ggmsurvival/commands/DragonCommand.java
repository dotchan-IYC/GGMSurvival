package com.ggm.ggmsurvival.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.DragonRewardManager;

public class DragonCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final DragonRewardManager dragonRewardManager;

    public DragonCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.dragonRewardManager = plugin.getDragonRewardManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.isFeatureEnabled("dragon_reward")) {
            player.sendMessage("§c드래곤 보상 시스템은 야생 서버에서만 사용할 수 있습니다!");
            return true;
        }

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
                showTodayInfo(player);
                break;
            case "help":
            case "도움말":
                showDragonHelp(player);
                break;
            default:
                showDragonInfo(player);
                break;
        }

        return true;
    }

    private void showDragonInfo(Player player) {
        long baseReward = plugin.getConfig().getLong("dragon_reward.base_reward", 100000);
        long minReward = plugin.getConfig().getLong("dragon_reward.min_reward", 10000);
        double minDamage = plugin.getConfig().getDouble("dragon_reward.min_damage_threshold", 50);

        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🐉 엔더드래곤 보상 시스템");
        player.sendMessage("");
        player.sendMessage("§a§l보상 정보:");
        player.sendMessage("§7• 기본 보상: §6" + String.format("%,d", baseReward) + "G");
        player.sendMessage("§7• 최소 보상: §6" + String.format("%,d", minReward) + "G");
        player.sendMessage("§7• 최소 기여도: §c" + String.format("%.0f", minDamage) + " 피해");
        player.sendMessage("");
        player.sendMessage("§e§l보상 계산:");
        player.sendMessage("§7• 드래곤에게 입힌 피해량에 따라 보상 결정");
        player.sendMessage("§7• 최소 기여도 미달 시 보상 없음");
        player.sendMessage("§7• 하루에 1번만 보상 가능");
        player.sendMessage("");
        player.sendMessage("§a§l팁:");
        player.sendMessage("§7• 드래곤과 가까이서 싸울수록 기여도 증가");
        player.sendMessage("§7• 팀플레이로 안전하게 처치하세요!");
        player.sendMessage("");

        // 오늘 보상 상태 확인
        dragonRewardManager.getTodayDragonInfo(player.getUniqueId()).thenAccept(info -> {
            player.sendMessage("§e§l오늘의 현황:");
            player.sendMessage("§7" + info);
            player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }

    private void showTodayInfo(Player player) {
        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l오늘의 드래곤 처치 현황");
        player.sendMessage("");

        dragonRewardManager.getTodayDragonInfo(player.getUniqueId()).thenAccept(info -> {
            player.sendMessage(info);
            player.sendMessage("");
            player.sendMessage("§7자세한 정보: §e/dragon info");
            player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }

    private void showDragonHelp(Player player) {
        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l드래곤 보상 명령어");
        player.sendMessage("");
        player.sendMessage("§7/dragon §f- 드래곤 보상 시스템 정보");
        player.sendMessage("§7/dragon info §f- 상세 보상 정보");
        player.sendMessage("§7/dragon today §f- 오늘의 처치 현황");
        player.sendMessage("");
        player.sendMessage("§e§l중요:");
        player.sendMessage("§7드래곤에게 최소 §c50 피해§7를 입혀야 보상을 받을 수 있습니다!");
        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}