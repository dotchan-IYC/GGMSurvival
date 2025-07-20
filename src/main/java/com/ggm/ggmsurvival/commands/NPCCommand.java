package com.ggm.ggmsurvival.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;

public class NPCCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final NPCTradeManager npcManager;

    public NPCCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.npcManager = plugin.getNPCTradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ggm.npc.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
            case "생성":
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /npc create <이름>");
                    return true;
                }
                createNPC(player, args[1]);
                break;
            case "help":
            case "도움말":
                sendHelp(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void createNPC(Player player, String npcName) {
        Location location = player.getLocation();

        boolean success = npcManager.createTradeNPC(location, npcName);

        if (success) {
            player.sendMessage("§a교환 NPC '" + npcName + "'이 생성되었습니다!");
            player.sendMessage("§7플레이어들은 이 NPC와 상호작용하여 아이템을 G로 판매할 수 있습니다.");
        } else {
            player.sendMessage("§cNPC 생성에 실패했습니다.");
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🤖 NPC 관리 명령어 (OP 전용)");
        player.sendMessage("");
        player.sendMessage("§7/npc create <이름> §f- 현재 위치에 교환 NPC 생성");
        player.sendMessage("§7/npc help §f- 이 도움말");
        player.sendMessage("");
        player.sendMessage("§a생성된 NPC는 플레이어들이 아이템을 G로");
        player.sendMessage("§a판매할 수 있게 해줍니다.");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}