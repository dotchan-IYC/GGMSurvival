package com.ggm.ggmsurvival.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;

public class NPCCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final NPCTradeManager npcTradeManager;

    public NPCCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.npcTradeManager = plugin.getNPCTradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("ggm.npc.admin")) {
            player.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            showNPCHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
            case "생성":
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /npc create <타입>");
                    player.sendMessage("§7타입: mining, combat, farming, rare, building, redstone");
                    return true;
                }
                createNPC(player, args[1]);
                break;
            case "list":
            case "목록":
                showNPCTypes(player);
                break;
            case "info":
            case "정보":
                showNPCInfo(player);
                break;
            default:
                showNPCHelp(player);
                break;
        }

        return true;
    }

    private void createNPC(Player player, String typeStr) {
        NPCTradeManager.TradeType tradeType;

        try {
            switch (typeStr.toLowerCase()) {
                case "mining":
                case "광물":
                    tradeType = NPCTradeManager.TradeType.MINING;
                    break;
                case "combat":
                case "전투":
                    tradeType = NPCTradeManager.TradeType.COMBAT;
                    break;
                case "farming":
                case "농업":
                    tradeType = NPCTradeManager.TradeType.FARMING;
                    break;
                case "rare":
                case "희귀":
                    tradeType = NPCTradeManager.TradeType.RARE;
                    break;
                case "building":
                case "건축":
                    tradeType = NPCTradeManager.TradeType.BUILDING;
                    break;
                case "redstone":
                case "레드스톤":
                    tradeType = NPCTradeManager.TradeType.REDSTONE;
                    break;
                default:
                    player.sendMessage("§c알 수 없는 NPC 타입입니다.");
                    player.sendMessage("§7사용 가능한 타입: mining, combat, farming, rare, building, redstone");
                    return;
            }

            npcTradeManager.createTradeNPC(player, tradeType);

        } catch (Exception e) {
            player.sendMessage("§cNPC 생성 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().severe("NPC 생성 오류: " + e.getMessage());
        }
    }

    private void showNPCTypes(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l사용 가능한 NPC 타입");
        player.sendMessage("");
        player.sendMessage("§6mining §7- 광물 상인 (광물, 원석)");
        player.sendMessage("§ccombat §7- 전투 상인 (몬스터 드롭)");
        player.sendMessage("§afarming §7- 농업 상인 (농작물, 동물 재료)");
        player.sendMessage("§5rare §7- 희귀 상인 (희귀 아이템)");
        player.sendMessage("§bbuilding §7- 건축 상인 (건축 재료)");
        player.sendMessage("§4redstone §7- 레드스톤 상인 (레드스톤 부품)");
        player.sendMessage("");
        player.sendMessage("§7사용법: §e/npc create <타입>");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showNPCInfo(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§lNPC 교환 시스템 정보");
        player.sendMessage("");
        player.sendMessage("§a테마별 전문 상인들이 다양한 아이템을 구매합니다:");
        player.sendMessage("");
        player.sendMessage("§6§l광물 상인:");
        player.sendMessage("§7석탄, 철, 금, 다이아몬드, 에메랄드 등 모든 광물");
        player.sendMessage("");
        player.sendMessage("§c§l전투 상인:");
        player.sendMessage("§7화약, 뼈, 엔더 진주, 블레이즈 막대 등 몬스터 드롭");
        player.sendMessage("");
        player.sendMessage("§a§l농업 상인:");
        player.sendMessage("§7밀, 당근, 감자, 고기, 가죽 등 농업 관련 아이템");
        player.sendMessage("");
        player.sendMessage("§5§l희귀 상인:");
        player.sendMessage("§7네더의 별, 드래곤 알, 엘리트라 등 최고급 아이템");
        player.sendMessage("");
        player.sendMessage("§b§l건축 상인:");
        player.sendMessage("§7돌, 원목, 벽돌, 유리 등 건축 재료");
        player.sendMessage("");
        player.sendMessage("§4§l레드스톤 상인:");
        player.sendMessage("§7레드스톤, 피스톤, 호퍼 등 기계 부품");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showNPCHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§lNPC 관리 명령어");
        player.sendMessage("");
        player.sendMessage("§7/npc create <타입> §f- NPC 생성");
        player.sendMessage("§7/npc list §f- 사용 가능한 NPC 타입 목록");
        player.sendMessage("§7/npc info §f- NPC 시스템 정보");
        player.sendMessage("");
        player.sendMessage("§e§l참고:");
        player.sendMessage("§7생성된 NPC를 우클릭하여 교환소를 이용하세요!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}