// 완전한 UpgradeCommand.java - 장비 강화 명령어 (이모티콘 제거)
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * 완전한 장비 강화 명령어 처리기
 * - 장비 강화 실행
 * - 강화 정보 확인
 * - 강화 통계
 * - 강화 시뮬레이션
 */
public class UpgradeCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final EnchantUpgradeManager upgradeManager;

    public UpgradeCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getEnchantUpgradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // UpgradeManager 확인
        if (upgradeManager == null) {
            sender.sendMessage("§c강화 시스템이 비활성화되어 있습니다.");
            sender.sendMessage("§7config.yml에서 upgrade_system.enabled를 true로 설정하세요.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        try {
            if (args.length == 0) {
                // 기본 강화 실행
                attemptUpgrade(player);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "info":
                case "정보":
                    showUpgradeInfo(player);
                    break;

                case "stats":
                case "통계":
                    showUpgradeStats(player);
                    break;

                case "rates":
                case "확률":
                    showUpgradeRates(player);
                    break;

                case "costs":
                case "비용":
                    showUpgradeCosts(player);
                    break;

                case "simulate":
                case "시뮬레이션":
                    if (args.length < 2) {
                        player.sendMessage("§c사용법: /upgrade simulate <목표레벨>");
                        return true;
                    }
                    simulateUpgrade(player, args[1]);
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
                    "강화 명령어 처리 중 오류: " + player.getName() + " - " + String.join(" ", args), e);
            player.sendMessage("§c명령어 처리 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 강화 시도
     */
    private void attemptUpgrade(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        // 손에 든 아이템 확인
        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c강화할 아이템을 손에 들고 사용하세요.");
            return;
        }

        // 강화 가능한 아이템인지 확인
        if (!upgradeManager.isUpgradeable(handItem)) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다.");
            player.sendMessage("§7강화 가능한 아이템 목록: /upgrade info");
            return;
        }

        // 현재 강화 레벨 확인
        int currentLevel = upgradeManager.getUpgradeLevel(handItem);
        int targetLevel = currentLevel + 1;

        // 강화 정보 표시
        showUpgradePreview(player, handItem, currentLevel, targetLevel);

        // 강화 실행 확인
        player.sendMessage("§e강화를 진행하려면 10초 내에 다시 /upgrade 명령어를 입력하세요.");
        player.sendMessage("§c주의: 강화 실패 시 아이템이 파괴되거나 강화 수치가 감소할 수 있습니다!");

        // 강화 실행
        executeUpgrade(player, handItem);
    }

    /**
     * 강화 실행
     */
    private void executeUpgrade(Player player, ItemStack item) {
        player.sendMessage("§e강화를 시도하고 있습니다...");

        // 강화 시도 사운드
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

        upgradeManager.upgradeItem(player, item).thenAccept(result -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                handleUpgradeResult(player, result);
            });
        });
    }

    /**
     * 강화 결과 처리
     */
    private void handleUpgradeResult(Player player, EnchantUpgradeManager.UpgradeResult result) {
        switch (result.getResult()) {
            case SUCCESS:
                // 강화 성공
                player.sendMessage("§a§l[강화 성공!] " + result.getMessage());
                player.getInventory().setItemInMainHand(result.getResultItem());
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

                // 성공 이펙트
                spawnSuccessEffect(player);
                break;

            case FAILED_NO_CHANGE:
                // 강화 실패 (변화 없음)
                player.sendMessage("§6[강화 실패] " + result.getMessage());
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 0.8f);
                break;

            case DOWNGRADED:
                // 강화 실패 (다운그레이드)
                player.sendMessage("§c[강화 실패] " + result.getMessage());
                player.getInventory().setItemInMainHand(result.getResultItem());
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.6f);
                break;

            case DESTROYED:
                // 아이템 파괴
                player.sendMessage("§4§l[아이템 파괴!] " + result.getMessage());
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);

                // 파괴 이펙트
                spawnDestroyEffect(player);
                break;

            case INVALID_ITEM:
                player.sendMessage("§c" + result.getMessage());
                break;

            case MAX_LEVEL:
                player.sendMessage("§e" + result.getMessage());
                break;

            case INSUFFICIENT_MONEY:
                player.sendMessage("§c" + result.getMessage());
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                break;

            case PAYMENT_FAILED:
                player.sendMessage("§c" + result.getMessage());
                break;

            case ERROR:
                player.sendMessage("§c" + result.getMessage());
                plugin.getLogger().warning("강화 시스템 오류: " + player.getName());
                break;
        }
    }

    /**
     * 강화 미리보기
     */
    private void showUpgradePreview(Player player, ItemStack item, int currentLevel, int targetLevel) {
        player.sendMessage("§6==== 강화 미리보기 ====");
        player.sendMessage("§7아이템: §f" + getItemDisplayName(item));
        player.sendMessage("§7현재 강화: §f" + currentLevel + "강 §7→ §a" + targetLevel + "강");

        // 성공률
        int successRate = upgradeManager.getSuccessRate(targetLevel);
        player.sendMessage("§7성공률: §e" + successRate + "%");

        // 비용
        long cost = upgradeManager.getUpgradeCost(targetLevel);
        player.sendMessage("§7비용: §f" + plugin.getEconomyManager().formatMoneyWithSymbol(cost));

        // 현재 잔액
        long balance = plugin.getEconomyManager().getBalanceSync(player.getUniqueId());
        player.sendMessage("§7보유 금액: §f" + plugin.getEconomyManager().formatMoneyWithSymbol(balance));

        player.sendMessage("§6====================");
    }

    /**
     * 강화 정보 표시
     */
    private void showUpgradeInfo(Player player) {
        player.sendMessage("§6===========================================");
        player.sendMessage("§e§l        장비 강화 시스템");
        player.sendMessage("");
        player.sendMessage("§a강화 가능한 아이템:");
        player.sendMessage("§7• 모든 무기 (검, 도끼, 곡괭이 등)");
        player.sendMessage("§7• 모든 방어구 (헬멧, 갑옷, 바지, 신발)");
        player.sendMessage("§7• 활, 삼지창");
        player.sendMessage("");
        player.sendMessage("§a강화 효과:");
        player.sendMessage("§7• 공격력 증가 (무기)");
        player.sendMessage("§7• 방어력 증가 (방어구)");
        player.sendMessage("§7• 효율성 증가 (도구)");
        player.sendMessage("§7• 내구도 증가");
        player.sendMessage("");
        player.sendMessage("§c주의사항:");
        player.sendMessage("§7• 강화 실패 시 아이템이 파괴될 수 있습니다");
        player.sendMessage("§7• 높은 강화 단계일수록 성공률이 낮습니다");
        player.sendMessage("§7• 3강까지는 실패해도 파괴되지 않습니다");
        player.sendMessage("");
        player.sendMessage("§a사용법: §f/upgrade (아이템을 손에 들고)");
        player.sendMessage("§6===========================================");
    }

    /**
     * 강화 통계 표시
     */
    private void showUpgradeStats(Player player) {
        upgradeManager.getPlayerUpgradeStats(player.getUniqueId()).thenAccept(stats -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6==== 강화 통계 ====");
                player.sendMessage("§a총 강화 시도: §f" + stats.getTotalAttempts() + "회");
                player.sendMessage("§a총 강화 레벨: §f" + stats.getTotalLevels());
                player.sendMessage("§a최고 강화: §f" + stats.getMaxLevel() + "강");
                player.sendMessage("§a성공률: §f" + String.format("%.1f", stats.getSuccessRate()) + "%");

                if (stats.getTotalAttempts() > 0) {
                    double avgLevel = (double) stats.getTotalLevels() / stats.getTotalAttempts();
                    player.sendMessage("§a평균 강화: §f" + String.format("%.1f", avgLevel) + "강");
                }

                player.sendMessage("§6================");
            });
        });
    }

    /**
     * 강화 성공률 표시
     */
    private void showUpgradeRates(Player player) {
        player.sendMessage("§6==== 강화 성공률 ====");

        for (int level = 1; level <= 10; level++) {
            int rate = upgradeManager.getSuccessRate(level);
            String color = getSuccessRateColor(rate);
            player.sendMessage("§f" + level + "강: " + color + rate + "%");
        }

        player.sendMessage("");
        player.sendMessage("§7• §a3강까지: 안전 강화 (실패해도 파괴되지 않음)");
        player.sendMessage("§7• §e4강부터: 실패 시 다운그레이드 가능");
        player.sendMessage("§7• §c7강부터: 실패 시 아이템 파괴 가능");
        player.sendMessage("§6=================");
    }

    /**
     * 강화 비용 표시
     */
    private void showUpgradeCosts(Player player) {
        player.sendMessage("§6==== 강화 비용 ====");

        for (int level = 1; level <= 10; level++) {
            long cost = upgradeManager.getUpgradeCost(level);
            String formattedCost = plugin.getEconomyManager().formatMoneyWithSymbol(cost);
            player.sendMessage("§f" + level + "강: §e" + formattedCost);
        }

        player.sendMessage("");
        player.sendMessage("§7총 비용 (1강→10강): §e" +
                plugin.getEconomyManager().formatMoneyWithSymbol(calculateTotalCost()));
        player.sendMessage("§6================");
    }

    /**
     * 강화 시뮬레이션
     */
    private void simulateUpgrade(Player player, String targetLevelStr) {
        try {
            int targetLevel = Integer.parseInt(targetLevelStr);

            if (targetLevel < 1 || targetLevel > 10) {
                player.sendMessage("§c목표 레벨은 1~10 사이여야 합니다.");
                return;
            }

            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem == null || handItem.getType() == Material.AIR) {
                player.sendMessage("§c시뮬레이션할 아이템을 손에 들고 사용하세요.");
                return;
            }

            int currentLevel = upgradeManager.getUpgradeLevel(handItem);

            if (currentLevel >= targetLevel) {
                player.sendMessage("§c현재 강화 레벨이 목표 레벨보다 높거나 같습니다.");
                return;
            }

            // 시뮬레이션 실행
            runUpgradeSimulation(player, currentLevel, targetLevel);

        } catch (NumberFormatException e) {
            player.sendMessage("§c올바른 숫자를 입력해주세요: " + targetLevelStr);
        }
    }

    /**
     * 강화 시뮬레이션 실행
     */
    private void runUpgradeSimulation(Player player, int currentLevel, int targetLevel) {
        player.sendMessage("§e강화 시뮬레이션을 실행하고 있습니다...");
        player.sendMessage("§7" + currentLevel + "강 → " + targetLevel + "강");
        player.sendMessage("");

        long totalCost = 0;
        int attempts = 0;
        int successes = 0;

        for (int level = currentLevel + 1; level <= targetLevel; level++) {
            int successRate = upgradeManager.getSuccessRate(level);
            long cost = upgradeManager.getUpgradeCost(level);

            // 평균 시도 횟수 계산 (기하분포)
            double avgAttempts = 100.0 / successRate;
            attempts += (int) Math.ceil(avgAttempts);
            successes++;
            totalCost += cost * (int) Math.ceil(avgAttempts);

            player.sendMessage("§f" + level + "강: §e" + successRate + "% §7(평균 " +
                    String.format("%.1f", avgAttempts) + "회 시도)");
        }

        player.sendMessage("");
        player.sendMessage("§a=== 시뮬레이션 결과 ===");
        player.sendMessage("§7예상 총 시도: §f" + attempts + "회");
        player.sendMessage("§7예상 성공: §f" + successes + "회");
        player.sendMessage("§7예상 총 비용: §e" + plugin.getEconomyManager().formatMoneyWithSymbol(totalCost));

        long currentBalance = plugin.getEconomyManager().getBalanceSync(player.getUniqueId());
        if (totalCost > currentBalance) {
            long needed = totalCost - currentBalance;
            player.sendMessage("§c부족한 금액: §f" + plugin.getEconomyManager().formatMoneyWithSymbol(needed));
        } else {
            player.sendMessage("§a충분한 자금이 있습니다!");
        }

        player.sendMessage("§7※ 이는 평균적인 예상치이며 실제 결과는 다를 수 있습니다.");
    }

    /**
     * 도움말 표시
     */
    private void showHelp(Player player) {
        player.sendMessage("§6=== 장비 강화 명령어 ===");
        player.sendMessage("§e/upgrade §7- 손에 든 아이템 강화");
        player.sendMessage("§e/upgrade info §7- 강화 시스템 정보");
        player.sendMessage("§e/upgrade stats §7- 내 강화 통계");
        player.sendMessage("§e/upgrade rates §7- 강화 성공률 표");
        player.sendMessage("§e/upgrade costs §7- 강화 비용 표");
        player.sendMessage("§e/upgrade simulate <레벨> §7- 강화 시뮬레이션");
        player.sendMessage("§6========================");
    }

    /**
     * 성공 이펙트
     */
    private void spawnSuccessEffect(Player player) {
        player.spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * 파괴 이펙트
     */
    private void spawnDestroyEffect(Player player) {
        player.spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * 성공률에 따른 색상
     */
    private String getSuccessRateColor(int rate) {
        if (rate >= 70) return "§a";
        else if (rate >= 50) return "§e";
        else if (rate >= 30) return "§6";
        else return "§c";
    }

    /**
     * 총 강화 비용 계산
     */
    private long calculateTotalCost() {
        long total = 0;
        for (int level = 1; level <= 10; level++) {
            total += upgradeManager.getUpgradeCost(level);
        }
        return total;
    }

    /**
     * 아이템 표시 이름
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ");
    }

    /**
     * 탭 완성 제공
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("info", "stats", "rates", "costs", "simulate", "help");

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if ("simulate".equals(subCommand)) {
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
            }
        }

        return completions;
    }
}