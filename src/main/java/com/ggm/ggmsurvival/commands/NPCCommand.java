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
        this.npcTradeManager = plugin.getNPCTradeManager(); // 정확한 메서드명
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("ggm.npc.admin")) {
            player.sendMessage("권한이 없습니다!");
            return true;
        }

        if (!plugin.isFeatureEnabled("npc_trading")) {
            player.sendMessage("NPC 교환은 야생 서버에서만 사용할 수 있습니다!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
            case "생성":
                if (args.length < 3) {
                    player.sendMessage("사용법: /npc create <이름> <타입>");
                    showNPCTypes(player);
                    return true;
                }
                createNPC(player, args[1], args[2]);
                break;

            case "remove":
            case "삭제":
                removeNPC(player);
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
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("=== NPC 관리 명령어 ===");
        player.sendMessage("/npc create <이름> <타입> - NPC 생성");
        player.sendMessage("/npc remove - 바라보는 NPC 삭제");
        player.sendMessage("/npc list - 사용 가능한 NPC 타입");
        player.sendMessage("/npc info - NPC 시스템 정보");
        player.sendMessage("=====================");
    }

    private void createNPC(Player player, String npcName, String typeStr) {
        try {
            NPCTradeManager.TradeType tradeType;

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
                    player.sendMessage("알 수 없는 NPC 타입입니다: " + typeStr);
                    player.sendMessage("사용 가능한 타입: mining, combat, farming, rare, building, redstone");
                    return;
            }

            // NPC 생성 (기존 메서드 호출)
            npcTradeManager.createTradeNPC(player, npcName, tradeType);

        } catch (Exception e) {
            player.sendMessage("NPC 생성 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().severe("NPC 생성 오류: " + e.getMessage());
        }
    }

    private void removeNPC(Player player) {
        player.sendMessage("[NPC] 바라보는 NPC를 삭제하려면 우클릭하세요.");
        player.sendMessage("(아직 구현되지 않은 기능입니다.)");
    }

    private void showNPCTypes(Player player) {
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("사용 가능한 NPC 타입");
        player.sendMessage("");
        player.sendMessage("mining - 광물 상인 (광물, 원석)");
        player.sendMessage("combat - 전투 상인 (몬스터 드롭)");
        player.sendMessage("farming - 농업 상인 (농작물, 동물 재료)");
        player.sendMessage("rare - 희귀 상인 (희귀 아이템)");
        player.sendMessage("building - 건축 상인 (건축 재료)");
        player.sendMessage("redstone - 레드스톤 상인 (레드스톤 부품)");
        player.sendMessage("");
        player.sendMessage("사용법: /npc create <이름> <타입>");
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showNPCInfo(Player player) {
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("NPC 교환 시스템 정보");
        player.sendMessage("");
        player.sendMessage("테마별 전문 상인들이 다양한 아이템을 구매합니다:");
        player.sendMessage("");
        player.sendMessage("광물 상인:");
        player.sendMessage("석탄, 철, 금, 다이아몬드, 에메랄드 등 모든 광물");
        player.sendMessage("");
        player.sendMessage("전투 상인:");
        player.sendMessage("화약, 뼈, 엔더 진주, 블레이즈 막대 등 몬스터 드롭");
        player.sendMessage("");
        player.sendMessage("농업 상인:");
        player.sendMessage("밀, 당근, 감자, 고기, 가죽 등 농업 관련 아이템");
        player.sendMessage("");
        player.sendMessage("희귀 상인:");
        player.sendMessage("네더의 별, 드래곤 알, 엘리트라 등 최고급 아이템");
        player.sendMessage("");
        player.sendMessage("건축 상인:");
        player.sendMessage("돌, 원목, 벽돌, 유리 등 건축 재료");
        player.sendMessage("");
        player.sendMessage("레드스톤 상인:");
        player.sendMessage("레드스톤, 피스톤, 호퍼 등 기계 부품");
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}