// 완전한 TradeCommand.java - NPC 교환 시스템 명령어 (이모티콘 제거)
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;
import org.bukkit.Location;
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
 * 완전한 NPC 교환 시스템 명령어 처리기
 * - NPC 생성 및 제거
 * - 교환 정보 확인
 * - 교환 통계
 * - 상인 목록
 */
public class TradeCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final NPCTradeManager tradeManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public TradeCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.tradeManager = plugin.getNPCTradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // NPCTradeManager 확인
        if (tradeManager == null) {
            sender.sendMessage("§cNPC 교환 시스템이 비활성화되어 있습니다.");
            sender.sendMessage("§7config.yml에서 npc_trade_system.enabled를 true로 설정하세요.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        try {
            if (args.length == 0) {
                showTradeInfo(player);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "info":
                case "정보":
                    showPlayerTradeInfo(player);
                    break;

                case "list":
                case "목록":
                    showMerchantList(player);
                    break;

                case "stats":
                case "통계":
                    showTradeStats(player);
                    break;

                case "create":
                case "생성":
                    if (!player.hasPermission("ggm.trade.create")) {
                        player.sendMessage("§c권한이 없습니다! 필요 권한: ggm.trade.create");
                        return true;
                    }
                    if (args.length < 3) {
                        player.sendMessage("§c사용법: /trade create <상인ID> <상인이름>");
                        return true;
                    }
                    createMerchant(player, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                    break;

                case "remove":
                case "제거":
                    if (!player.hasPermission("ggm.trade.remove")) {
                        player.sendMessage("§c권한이 없습니다! 필요 권한: ggm.trade.remove");
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage("§c사용법: /trade remove <상인ID>");
                        return true;
                    }
                    removeMerchant(player, args[1]);
                    break;

                case "cooldown":
                case "쿨다운":
                    showCooldownInfo(player);
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
                    "교환 명령어 처리 중 오류: " + player.getName() + " - " + String.join(" ", args), e);
            player.sendMessage("§c명령어 처리 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 교환 시스템 정보 표시
     */
    private void showTradeInfo(Player player) {
        player.sendMessage("§6===========================================");
        player.sendMessage("§e§l      NPC 교환 시스템");
        player.sendMessage("");

        player.sendMessage("§a시스템 정보:");
        player.sendMessage("§7• 교환 쿨다운: §f" + tradeManager.getTradeCooldownSeconds() + "초");
        player.sendMessage("§7• 일일 최대 교환: §f" + tradeManager.getMaxTradesPerDay() + "회");
        player.sendMessage("§7• 활성 상인: §f" + tradeManager.getActiveNPCs().size() + "명");

        player.sendMessage("");
        player.sendMessage("§a사용 가능한 상인:");
        player.sendMessage("§7• §6음식 상인 §7- 음식 아이템 판매");
        player.sendMessage("§7• §6도구 상인 §7- 도구 및 무기 판매");
        player.sendMessage("§7• §6블록 상인 §7- 건축 블록 판매");
        player.sendMessage("§7• §6마법 상인 §7- 마법 아이템 판매");

        player.sendMessage("");
        player.sendMessage("§a교환 방법:");
        player.sendMessage("§71. 상인 NPC와 상호작용");
        player.sendMessage("§72. 원하는 아이템 선택");
        player.sendMessage("§73. 필요한 재료를 넣고 교환");

        player.sendMessage("");
        player.sendMessage("§7명령어: §f/trade list §7- 상인 목록 확인");
        player.sendMessage("§6===========================================");
    }

    /**
     * 플레이어 교환 정보 표시
     */
    private void showPlayerTradeInfo(Player player) {
        if (tradeManager != null) {
            tradeManager.showPlayerTradeInfo(player);
        }
    }

    /**
     * 상인 목록 표시
     */
    private void showMerchantList(Player player) {
        if (tradeManager != null) {
            tradeManager.showMerchantList(player);
        }
    }

    /**
     * 교환 통계 표시
     */
    private void showTradeStats(Player player) {
        tradeManager.getPlayerTradeStats(player.getUniqueId()).thenAccept(stats -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6==== 내 교환 통계 ====");

                if (stats.getTotalTrades() == 0) {
                    player.sendMessage("§c아직 교환한 기록이 없습니다.");
                    player.sendMessage("§7상인 NPC를 찾아 교환해보세요!");
                } else {
                    player.sendMessage("§a총 교환 횟수: §f" + stats.getTotalTrades() + "회");
                    player.sendMessage("§a거래한 상인: §f" + stats.getUniqueMerchants() + "명");

                    if (stats.getLastTrade() != null) {
                        player.sendMessage("§a마지막 교환: §f" +
                                dateFormat.format(stats.getLastTrade()));
                    }

                    // 교환 활동도 계산
                    double tradesPerMerchant = (double) stats.getTotalTrades() / Math.max(1, stats.getUniqueMerchants());
                    player.sendMessage("§a상인당 평균 교환: §f" +
                            String.format("%.1f", tradesPerMerchant) + "회");
                }

                player.sendMessage("§6==================");
            });
        });
    }

    /**
     * 상인 생성 (관리자용)
     */
    private void createMerchant(Player player, String merchantId, String displayName) {
        try {
            Location location = player.getLocation();

            // 상인 ID 유효성 검사
            if (merchantId.contains(" ") || merchantId.length() < 3) {
                player.sendMessage("§c상인 ID는 3글자 이상이며 공백을 포함할 수 없습니다.");
                return;
            }

            // 기존 상인 확인
            boolean exists = tradeManager.getActiveNPCs().values().stream()
                    .anyMatch(npc -> npc.getId().equals(merchantId));

            if (exists) {
                player.sendMessage("§c이미 존재하는 상인 ID입니다: " + merchantId);
                return;
            }

            // 상인 생성
            tradeManager.spawnMerchantNPC(merchantId, displayName, location);

            player.sendMessage("§a상인이 성공적으로 생성되었습니다!");
            player.sendMessage("§7상인 ID: §f" + merchantId);
            player.sendMessage("§7표시 이름: §f" + displayName);
            player.sendMessage("§7위치: §f" + formatLocation(location));

            plugin.getLogger().info("관리자 " + player.getName() + "이(가) 상인을 생성했습니다: " + merchantId);

        } catch (Exception e) {
            player.sendMessage("§c상인 생성 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "상인 생성 실패: " + merchantId, e);
        }
    }

    /**
     * 상인 제거 (관리자용)
     */
    private void removeMerchant(Player player, String merchantId) {
        try {
            // 상인 존재 확인
            boolean exists = tradeManager.getActiveNPCs().values().stream()
                    .anyMatch(npc -> npc.getId().equals(merchantId));

            if (!exists) {
                player.sendMessage("§c존재하지 않는 상인 ID입니다: " + merchantId);
                player.sendMessage("§7사용 가능한 상인: /trade list");
                return;
            }

            // 상인 제거
            tradeManager.removeMerchantNPC(merchantId);

            player.sendMessage("§a상인이 성공적으로 제거되었습니다!");
            player.sendMessage("§7제거된 상인 ID: §f" + merchantId);

            plugin.getLogger().info("관리자 " + player.getName() + "이(가) 상인을 제거했습니다: " + merchantId);

        } catch (Exception e) {
            player.sendMessage("§c상인 제거 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "상인 제거 실패: " + merchantId, e);
        }
    }

    /**
     * 쿨다운 정보 표시
     */
    private void showCooldownInfo(Player player) {
        player.sendMessage("§6==== 교환 쿨다운 정보 ====");
        player.sendMessage("§a기본 설정:");
        player.sendMessage("§7• 교환 후 쿨다운: §f" + tradeManager.getTradeCooldownSeconds() + "초");
        player.sendMessage("§7• 일일 최대 교환: §f" + tradeManager.getMaxTradesPerDay() + "회");
        player.sendMessage("");

        player.sendMessage("§a쿨다운 시스템:");
        player.sendMessage("§7• 각 상인별로 개별 쿨다운 적용");
        player.sendMessage("§7• 일일 교환 횟수는 매일 자정에 초기화");
        player.sendMessage("§7• 쿨다운 중에는 해당 상인과 교환 불가");
        player.sendMessage("");

        player.sendMessage("§a현재 상태:");
        player.sendMessage("§7내 교환 상태를 확인하려면 §f/trade info");
        player.sendMessage("§6======================");
    }

    /**
     * 도움말 표시
     */
    private void showHelp(Player player) {
        player.sendMessage("§6=== NPC 교환 시스템 명령어 ===");
        player.sendMessage("§e/trade §7- 교환 시스템 정보");
        player.sendMessage("§e/trade info §7- 내 교환 정보");
        player.sendMessage("§e/trade list §7- 상인 목록");
        player.sendMessage("§e/trade stats §7- 내 교환 통계");
        player.sendMessage("§e/trade cooldown §7- 쿨다운 정보");

        if (player.hasPermission("ggm.trade.admin")) {
            player.sendMessage("");
            player.sendMessage("§c=== 관리자 명령어 ===");
            player.sendMessage("§e/trade create <ID> <이름> §7- 상인 생성");
            player.sendMessage("§e/trade remove <ID> §7- 상인 제거");
        }

        player.sendMessage("§6=============================");
    }

    /**
     * 위치 포맷팅
     */
    private String formatLocation(Location location) {
        return String.format("(%d, %d, %d)",
                (int) location.getX(),
                (int) location.getY(),
                (int) location.getZ());
    }

    /**
     * 탭 완성 제공
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("info", "list", "stats", "cooldown", "help");

            if (sender.hasPermission("ggm.trade.admin")) {
                subCommands = Arrays.asList("info", "list", "stats", "create", "remove", "cooldown", "help");
            }

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }

        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if ("remove".equals(subCommand) && sender.hasPermission("ggm.trade.admin")) {
                // 현재 존재하는 상인 ID들을 제안
                if (tradeManager != null) {
                    for (NPCTradeManager.TradeNPC npc : tradeManager.getActiveNPCs().values()) {
                        completions.add(npc.getId());
                    }
                }
            } else if ("create".equals(subCommand) && sender.hasPermission("ggm.trade.admin")) {
                // 새로운 상인 ID 제안
                completions.addAll(Arrays.asList("food_merchant", "tool_merchant", "block_merchant", "magic_merchant"));
            }

        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if ("create".equals(subCommand) && sender.hasPermission("ggm.trade.admin")) {
                // 상인 이름 제안
                String merchantId = args[1].toLowerCase();
                switch (merchantId) {
                    case "food_merchant":
                        completions.add("음식상인");
                        break;
                    case "tool_merchant":
                        completions.add("도구상인");
                        break;
                    case "block_merchant":
                        completions.add("블록상인");
                        break;
                    case "magic_merchant":
                        completions.add("마법상인");
                        break;
                    default:
                        completions.add("커스텀상인");
                        break;
                }
            }
        }

        return completions;
    }
}