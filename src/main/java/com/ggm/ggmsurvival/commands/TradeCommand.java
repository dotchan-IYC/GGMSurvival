package com.ggm.ggmsurvival.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;

public class TradeCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final NPCTradeManager npcTradeManager;

    public TradeCommand(GGMSurvival plugin) {
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

        if (!plugin.isFeatureEnabled("npc_trading")) {
            player.sendMessage("§cNPC 교환은 야생 서버에서만 사용할 수 있습니다!");
            return true;
        }

        if (args.length == 0) {
            showTradeInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "prices":
            case "가격":
                showTradePrices(player);
                break;
            case "merchants":
            case "상인":
                showMerchantInfo(player);
                break;
            case "help":
            case "도움말":
                showTradeHelp(player);
                break;
            default:
                showTradeInfo(player);
                break;
        }

        return true;
    }

    private void showTradeInfo(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l💰 NPC 교환 시스템");
        player.sendMessage("");
        player.sendMessage("§a§l이용 방법:");
        player.sendMessage("§7• 마을에서 상인 NPC를 찾으세요");
        player.sendMessage("§7• 상인을 우클릭하여 교환소 열기");
        player.sendMessage("§7• 아이템을 클릭하여 판매");
        player.sendMessage("");
        player.sendMessage("§e§l판매 방법:");
        player.sendMessage("§7• §a좌클릭§7: 1개 판매");
        player.sendMessage("§7• §a우클릭§7: 64개 판매");
        player.sendMessage("§7• §a쉬프트+클릭§7: 전체 판매");
        player.sendMessage("");
        player.sendMessage("§a§l상인 종류:");
        player.sendMessage("§7• §6광물 상인 §7- 광물, 원석");
        player.sendMessage("§7• §c전투 상인 §7- 몬스터 드롭");
        player.sendMessage("§7• §a농업 상인 §7- 농작물, 동물 재료");
        player.sendMessage("§7• §5희귀 상인 §7- 희귀 아이템");
        player.sendMessage("§7• §b건축 상인 §7- 건축 재료");
        player.sendMessage("§7• §4레드스톤 상인 §7- 레드스톤 부품");
        player.sendMessage("");
        player.sendMessage("§7더 자세한 정보: §e/trade prices");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showTradePrices(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l💎 주요 아이템 판매 가격표");
        player.sendMessage("");

        player.sendMessage("§6§l[광물 상인]");
        player.sendMessage("§7석탄: §f8G §7| 철괴: §f40G §7| 금괴: §f80G");
        player.sendMessage("§7다이아몬드: §f400G §7| 에메랄드: §f250G");
        player.sendMessage("§7네더라이트 주괴: §f5,000G");
        player.sendMessage("");

        player.sendMessage("§c§l[전투 상인]");
        player.sendMessage("§7화약: §f12G §7| 엔더 진주: §f80G");
        player.sendMessage("§7블레이즈 막대: §f40G §7| 가스트 눈물: §f150G");
        player.sendMessage("§7위더 해골: §f1,000G");
        player.sendMessage("");

        player.sendMessage("§a§l[농업 상인]");
        player.sendMessage("§7밀: §f5G §7| 당근/감자: §f4G");
        player.sendMessage("§7가죽: §f12G §7| 소고기: §f15G");
        player.sendMessage("§7황금 사과: §f200G");
        player.sendMessage("");

        player.sendMessage("§5§l[희귀 상인]");
        player.sendMessage("§7네더의 별: §f8,000G §7| 엘리트라: §f20,000G");
        player.sendMessage("§7드래곤 알: §f50,000G §7| 불사의 토템: §f5,000G");
        player.sendMessage("");

        player.sendMessage("§7※ 원석은 주괴보다 비싸게 판매됩니다!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showMerchantInfo(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🏪 상인 위치 및 정보");
        player.sendMessage("");
        player.sendMessage("§a§l마을에서 다음 상인들을 찾아보세요:");
        player.sendMessage("");
        player.sendMessage("§6광물 상인§7: 광산 근처에서 발견 가능");
        player.sendMessage("§7→ 모든 광물과 원석을 최고가로 구매");
        player.sendMessage("");
        player.sendMessage("§c전투 상인§7: 전투 지역 근처에 위치");
        player.sendMessage("§7→ 몬스터 사냥으로 얻은 아이템 구매");
        player.sendMessage("");
        player.sendMessage("§a농업 상인§7: 농장 지역에서 만날 수 있음");
        player.sendMessage("§7→ 농업과 목축으로 얻은 모든 재료 구매");
        player.sendMessage("");
        player.sendMessage("§5희귀 상인§7: 특별한 장소에 숨어있음");
        player.sendMessage("§7→ 매우 희귀한 아이템만 취급");
        player.sendMessage("");
        player.sendMessage("§b건축 상인§7: 건설 현장 근처");
        player.sendMessage("§7→ 모든 건축 재료를 대량 구매");
        player.sendMessage("");
        player.sendMessage("§4레드스톤 상인§7: 기술자 구역");
        player.sendMessage("§7→ 레드스톤과 기계 부품 전문");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showTradeHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l교환 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§7/trade §f- 교환 시스템 정보");
        player.sendMessage("§7/trade prices §f- 아이템 가격표");
        player.sendMessage("§7/trade merchants §f- 상인 정보");
        player.sendMessage("");
        player.sendMessage("§e§l팁:");
        player.sendMessage("§7• 같은 종류 아이템을 대량으로 모아서 판매하세요");
        player.sendMessage("§7• 원석이 주괴보다 높은 가격으로 팔립니다");
        player.sendMessage("§7• 희귀 상인은 최고급 아이템만 거래합니다");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
