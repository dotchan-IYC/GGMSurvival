// 완전 안정화된 TradeCommand.java
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;
import com.ggm.ggmsurvival.managers.NPCTradeManager.NPCType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * 완전 안정화된 교환 명령어 처리기
 */
public class TradeCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final NPCTradeManager npcManager;

    public TradeCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.npcManager = plugin.getNPCTradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                return true;
            }

            if (npcManager == null) {
                player.sendMessage("§cNPC 교환 시스템이 비활성화되어 있습니다.");
                return true;
            }

            if (args.length == 0) {
                showTradeHelp(player);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "prices":
                case "가격":
                    showAllTradePrices(player);
                    break;

                case "merchants":
                case "상인":
                    npcManager.showNPCList(player);
                    break;

                case "item":
                case "아이템":
                    npcManager.showAvailableTrades(player, NPCType.ITEM_TRADER);
                    break;

                case "resource":
                case "자원":
                    npcManager.showAvailableTrades(player, NPCType.RESOURCE_TRADER);
                    break;

                case "special":
                case "특수":
                    npcManager.showAvailableTrades(player, NPCType.SPECIAL_TRADER);
                    break;

                default:
                    // 직접 교환 시도
                    boolean success = npcManager.executeTrade(player, args[0]);
                    if (!success) {
                        player.sendMessage("§c교환에 실패했습니다. §7/trade prices §c로 교환 목록을 확인하세요.");
                    }
                    break;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "교환 명령어 처리 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c명령어 처리 중 오류가 발생했습니다.");
            return true;
        }
    }

    private void showTradeHelp(Player player) {
        player.sendMessage("§6==========================================");
        player.sendMessage("§e§lNPC 교환 시스템");
        player.sendMessage("");
        player.sendMessage("§a명령어:");
        player.sendMessage("§7• §e/trade prices §7- 모든 교환 가격 보기");
        player.sendMessage("§7• §e/trade merchants §7- 상인 목록 보기");
        player.sendMessage("§7• §e/trade item §7- 아이템 상인 교환");
        player.sendMessage("§7• §e/trade resource §7- 자원 상인 교환");
        player.sendMessage("§7• §e/trade special §7- 특수 상인 교환");
        player.sendMessage("");
        player.sendMessage("§7NPC와 상호작용하여 직접 교환할 수도 있습니다!");
        player.sendMessage("§6==========================================");
    }

    private void showAllTradePrices(Player player) {
        player.sendMessage("§6==========================================");
        player.sendMessage("§e§l모든 교환 가격표");
        player.sendMessage("");

        for (NPCType type : NPCType.values()) {
            player.sendMessage("§a" + type.getDisplayName() + ":");
            npcManager.showAvailableTrades(player, type);
        }

        player.sendMessage("§6==========================================");
    }
}
