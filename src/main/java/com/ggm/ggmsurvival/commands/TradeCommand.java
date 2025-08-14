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
        this.npcTradeManager = plugin.getNPCTradeManager(); // 정확한 메서드명
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.isFeatureEnabled("npc_trading")) {
            player.sendMessage("NPC 교환은 야생 서버에서만 사용할 수 있습니다!");
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
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("NPC 교환 시스템");
        player.sendMessage("");
        player.sendMessage("이용 방법:");
        player.sendMessage("• 마을에서 상인 NPC를 찾으세요");
        player.sendMessage("• 상인을 우클릭하여 교환소 열기");
        player.sendMessage("• 아이템을 클릭하여 즉시 G로 교환");
        player.sendMessage("");
        player.sendMessage("상인 종류:");
        player.sendMessage("광물 상인 - 광산 아이템 전문");
        player.sendMessage("전투 상인 - 몬스터 드롭 전문");
        player.sendMessage("농업 상인 - 농작물 및 동물 재료");
        player.sendMessage("희귀 상인 - 최고급 희귀 아이템");
        player.sendMessage("건축 상인 - 건축 재료 전문");
        player.sendMessage("레드스톤 상인 - 기계 부품 전문");
        player.sendMessage("");
        player.sendMessage("더 자세한 정보: /trade prices");
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showTradePrices(Player player) {
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("주요 아이템 가격표");
        player.sendMessage("");
        player.sendMessage("광물 상인 가격:");
        player.sendMessage("석탄: 50G | 철 주괴: 100G | 금 주괴: 200G");
        player.sendMessage("다이아몬드: 1,000G | 에메랄드: 500G");
        player.sendMessage("");
        player.sendMessage("전투 상인 가격:");
        player.sendMessage("화약: 100G | 뼈: 50G | 엔더 진주: 1,500G");
        player.sendMessage("블레이즈 막대: 800G | 가스트의 눈물: 2,000G");
        player.sendMessage("");
        player.sendMessage("농업 상인 가격:");
        player.sendMessage("밀: 20G | 당근: 30G | 감자: 25G");
        player.sendMessage("소고기: 80G | 가죽: 60G");
        player.sendMessage("");
        player.sendMessage("희귀 상인 가격:");
        player.sendMessage("네더의 별: 50,000G | 드래곤 알: 100,000G");
        player.sendMessage("엘리트라: 75,000G | 슐커 상자: 25,000G");
        player.sendMessage("");
        player.sendMessage("※ 가격은 시장 상황에 따라 변동될 수 있습니다");
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showMerchantInfo(Player player) {
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("상인 위치 및 정보");
        player.sendMessage("");
        player.sendMessage("마을에서 다음 상인들을 찾아보세요:");
        player.sendMessage("");
        player.sendMessage("광물 상인: 광산 근처에서 발견 가능");
        player.sendMessage("→ 모든 광물과 원석을 최고가로 구매");
        player.sendMessage("");
        player.sendMessage("전투 상인: 전투 지역 근처에 위치");
        player.sendMessage("→ 몬스터 사냥으로 얻은 아이템 구매");
        player.sendMessage("");
        player.sendMessage("농업 상인: 농장 지역에서 만날 수 있음");
        player.sendMessage("→ 농업과 목축으로 얻은 모든 재료 구매");
        player.sendMessage("");
        player.sendMessage("희귀 상인: 특별한 장소에 숨어있음");
        player.sendMessage("→ 매우 희귀한 아이템만 취급");
        player.sendMessage("");
        player.sendMessage("건축 상인: 건설 현장 근처");
        player.sendMessage("→ 모든 건축 재료를 대량 구매");
        player.sendMessage("");
        player.sendMessage("레드스톤 상인: 기술자 구역");
        player.sendMessage("→ 레드스톤과 기계 부품 전문");
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void showTradeHelp(Player player) {
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("교환 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("/trade - 교환 시스템 정보");
        player.sendMessage("/trade prices - 아이템 가격표");
        player.sendMessage("/trade merchants - 상인 정보");
        player.sendMessage("");
        player.sendMessage("팁:");
        player.sendMessage("• 같은 종류 아이템을 대량으로 모아서 판매하세요");
        player.sendMessage("• 원석이 주괴보다 높은 가격으로 팔립니다");
        player.sendMessage("• 희귀 상인은 최고급 아이템만 거래합니다");
        player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}