package com.ggm.ggmsurvival.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;

public class TradeCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final NPCTradeManager npcManager;

    public TradeCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.npcManager = plugin.getNPCTradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showTradeInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "prices":
            case "가격":
                showItemPrices(player);
                break;
            case "info":
            case "정보":
                showTradeInfo(player);
                break;
            default:
                showTradeInfo(player);
                break;
        }

        return true;
    }

    private void showTradeInfo(Player player) {
        player.sendMessage("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🛒 NPC 교환 시스템");
        player.sendMessage("");
        player.sendMessage("§7NPC 교환상을 찾아 아이템을 G로 판매하세요!");
        player.sendMessage("");
        player.sendMessage("§a교환 방법:");
        player.sendMessage("§71. 교환 NPC와 상호작용");
        player.sendMessage("§72. 판매할 아이템 클릭");
        player.sendMessage("§73. 좌클릭: 1개 / 우클릭: 스택 / Shift+클릭: 모두");
        player.sendMessage("");
        player.sendMessage("§e명령어:");
        player.sendMessage("§7/trade prices - 아이템 가격표");
        player.sendMessage("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showItemPrices(Player player) {
        player.sendMessage("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l💰 주요 아이템 가격표");
        player.sendMessage("");

        player.sendMessage("§6§l광물류:");
        showItemPrice(player, Material.COAL, "석탄");
        showItemPrice(player, Material.IRON_INGOT, "철 주괴");
        showItemPrice(player, Material.GOLD_INGOT, "금 주괴");
        showItemPrice(player, Material.DIAMOND, "다이아몬드");
        showItemPrice(player, Material.EMERALD, "에메랄드");

        player.sendMessage("");
        player.sendMessage("§c§l희귀 아이템:");
        showItemPrice(player, Material.NETHER_STAR, "네더의 별");
        showItemPrice(player, Material.DRAGON_EGG, "드래곤 알");
        showItemPrice(player, Material.ELYTRA, "겉날개");

        player.sendMessage("");
        player.sendMessage("§7더 많은 아이템은 NPC에서 확인하세요!");
        player.sendMessage("§b━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showItemPrice(Player player, Material material, String name) {
        Long price = npcManager.getItemPrice(material);
        if (price != null) {
            player.sendMessage(String.format("§7• %s: §6%s G",
                    name, plugin.getEconomyManager().formatMoney(price)));
        }
    }
}