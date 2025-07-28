package com.ggm.ggmsurvival.commands;

import org.bukkit.World;
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
            case "status":
            case "상태":
                showDragonStatus(player);
                break;
            case "gui":
            case "화면":
                showDragonGui(player);  // 새로 추가!
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
        double maxHealth = 10000.0;
        double maxContribution = 100.0;
        long maxReward = 100000L;

        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🐉 엔더드래곤 보상 시스템");
        player.sendMessage("");
        player.sendMessage("§a§l시스템 정보:");
        player.sendMessage("§7• 드래곤 체력: §c" + String.format("%.0f", maxHealth) + " HP");
        player.sendMessage("§7• 최대 기여도: §6" + String.format("%.0f", maxContribution) + "%");
        player.sendMessage("§7• 최대 보상: §6" + String.format("%,d", maxReward) + "G");
        player.sendMessage("");
        player.sendMessage("§e§l보상 계산:");
        player.sendMessage("§7• 기여도에 정확히 비례하여 보상 지급");
        player.sendMessage("§7• 기여도 100% = 100,000G");
        player.sendMessage("§7• 기여도 50% = 50,000G");
        player.sendMessage("§7• 기여도 25% = 25,000G");
        player.sendMessage("§7• 기여도 1% = 1,000G (최소)");
        player.sendMessage("");
        player.sendMessage("§c§l주의사항:");
        player.sendMessage("§7• 최소 기여도 1% 이상 필요");
        player.sendMessage("§7• 하루에 1번만 보상 받을 수 있음");
        player.sendMessage("§7• 침대 사용 금지 (폭발 방지)");
        player.sendMessage("");
        player.sendMessage("§a§l팁:");
        player.sendMessage("§7• 엔드 진입시 자동으로 GUI가 표시됩니다");
        player.sendMessage("§7• ActionBar로 실시간 기여도 확인 가능");
        player.sendMessage("§7• 팀플레이로 안전하게 처치하세요!");

        // 오늘 보상 상태 확인
        dragonRewardManager.getTodayRewardInfo(player.getUniqueId()).thenAccept(info -> {
            player.sendMessage("");
            player.sendMessage("§e§l오늘의 현황:");
            if (info.hasReceived) {
                player.sendMessage("§a✅ 오늘 드래곤을 처치했습니다!");
                player.sendMessage("§7기여도: §6" + String.format("%.2f", info.contribution) + "%");
                player.sendMessage("§7받은 보상: §a" + String.format("%,d", info.rewardAmount) + "G");
            } else {
                // 현재 기여도 확인
                double currentContribution = dragonRewardManager.getCurrentPlayerContribution(player.getUniqueId());
                if (currentContribution > 0) {
                    player.sendMessage("§e⚡ 현재 참여 중!");
                    player.sendMessage("§7현재 기여도: §6" + String.format("%.2f", currentContribution) + "%");
                } else {
                    player.sendMessage("§7아직 드래곤을 공격하지 않았습니다.");
                }
            }
            player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }

    private void showTodayInfo(Player player) {
        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l오늘의 드래곤 처치 현황");
        player.sendMessage("");

        dragonRewardManager.getTodayRewardInfo(player.getUniqueId()).thenAccept(info -> {
            if (info.hasReceived) {
                player.sendMessage("§a✅ 성공적으로 드래곤을 처치했습니다!");
                player.sendMessage("");
                player.sendMessage("§7기여도: §6" + String.format("%.2f", info.contribution) + "%");
                player.sendMessage("§7받은 보상: §a" + String.format("%,d", info.rewardAmount) + "G");
                player.sendMessage("§7처치 시간: §f" + (info.receivedAt != null ?
                        info.receivedAt.toString().substring(11, 19) : "알 수 없음"));
                player.sendMessage("");
                player.sendMessage("§e내일 다시 도전해보세요!");
            } else {
                double currentContribution = dragonRewardManager.getCurrentPlayerContribution(player.getUniqueId());
                if (currentContribution > 0) {
                    player.sendMessage("§e⚡ 현재 드래곤과 전투 중입니다!");
                    player.sendMessage("");
                    player.sendMessage("§7현재 기여도: §6" + String.format("%.2f", currentContribution) + "%");
                    player.sendMessage("§7예상 보상: §a" + String.format("%,d", calculateReward(currentContribution)) + "G");
                    player.sendMessage("");
                    player.sendMessage(currentContribution >= 1.0 ? "§a✅ 최소 기여도 달성!" : "§c❌ 최소 기여도 1% 필요");
                } else {
                    player.sendMessage("§7아직 드래곤을 공격하지 않았습니다.");
                    player.sendMessage("");
                    player.sendMessage("§e엔드로 이동하여 드래곤을 처치해보세요!");
                }
            }
            player.sendMessage("");
            player.sendMessage("§7자세한 정보: §e/dragon info");
            player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
    }

    private void showDragonStatus(Player player) {
        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🐉 드래곤 실시간 상태");
        player.sendMessage("");

        org.bukkit.entity.EnderDragon dragon = dragonRewardManager.getCurrentDragon();
        if (dragon != null && !dragon.isDead()) {
            double currentHealth = dragon.getHealth();
            double maxHealth = 10000.0;
            double healthPercentage = (currentHealth / maxHealth) * 100;

            player.sendMessage("§c§l드래곤 상태: §a생존");
            player.sendMessage("§7체력: §c" + String.format("%.1f", currentHealth) + " / " + String.format("%.0f", maxHealth));
            player.sendMessage("§7체력 비율: §e" + String.format("%.1f", healthPercentage) + "%");
            player.sendMessage("");
            player.sendMessage("§a참여자 수: §f" + dragonRewardManager.getParticipantCount() + "명");
            player.sendMessage("§7총 입힌 피해: §f" + String.format("%.1f", dragonRewardManager.getTotalDamageDealt()));
            player.sendMessage("");
            player.sendMessage("§e엔드로 이동하면 상세한 GUI를 확인할 수 있습니다!");
        } else {
            player.sendMessage("§7§l드래곤 상태: §c처치됨");
            player.sendMessage("");
            player.sendMessage("§7새로운 드래곤은 내일 12시에 부활합니다.");
            player.sendMessage("§e오늘의 처치 기록을 확인해보세요!");
        }

        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showDragonHelp(Player player) {
        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🐉 드래곤 보상 명령어");
        player.sendMessage("");
        player.sendMessage("§a§l기본 명령어:");
        player.sendMessage("§7/dragon §f- 드래곤 보상 시스템 정보");
        player.sendMessage("§7/dragon info §f- 상세 시스템 정보");
        player.sendMessage("§7/dragon today §f- 오늘의 처치 현황");
        player.sendMessage("§7/dragon status §f- 드래곤 실시간 상태");
        player.sendMessage("§7/dragon gui §f- GUI 다시 열기 (엔드에서만)");  // 새로 추가!
        player.sendMessage("§7/dragon help §f- 이 도움말");
        player.sendMessage("");
        player.sendMessage("§e§l중요 정보:");
        player.sendMessage("§7• 엔드 진입시 자동으로 GUI 표시 (1회)");  // 수정!
        player.sendMessage("§7• GUI를 닫은 후에는 /dragon gui로 재열람");  // 새로 추가!
        player.sendMessage("§7• 최소 기여도 §c1%§7 이상 필요");
        player.sendMessage("§7• ActionBar로 실시간 기여도 확인");
        player.sendMessage("§7• 기여도에 정확히 비례한 보상 지급");
        player.sendMessage("");
        player.sendMessage("§a§l보상 예시:");
        player.sendMessage("§7100% 기여도 = 100,000G");
        player.sendMessage("§7 50% 기여도 =  50,000G");
        player.sendMessage("§7 25% 기여도 =  25,000G");
        player.sendMessage("§7  1% 기여도 =   1,000G");
        player.sendMessage("§d━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private long calculateReward(double contribution) {
        long maxReward = 100000L;
        long reward = Math.round((contribution / 100.0) * maxReward);
        return Math.max(1000L, Math.min(maxReward, reward));
    }

    /**
     * 드래곤 GUI 수동으로 열기
     */
    private void showDragonGui(Player player) {
        // 엔드에 있을 때만 GUI 열기 허용
        if (player.getWorld().getEnvironment() != World.Environment.THE_END) {
            player.sendMessage("§c드래곤 GUI는 엔드에서만 사용할 수 있습니다!");
            player.sendMessage("§7엔드로 이동 후 다시 시도해주세요.");
            return;
        }

        // DragonRewardManager의 GUI 열기
        dragonRewardManager.showDragonStatusGui(player);
        player.sendMessage("§a드래곤 GUI를 열었습니다!");
    }


}