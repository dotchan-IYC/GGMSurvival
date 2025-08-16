// 수정된 NPCCommand.java - 컴파일 오류 해결
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * NPC 관리 명령어 처리기
 * - NPC 생성, 제거, 목록 조회
 * - 관리자 전용 기능
 */
public class NPCCommand implements CommandExecutor {

    private final GGMSurvival plugin;

    public NPCCommand(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // NPC 교환 시스템이 활성화되어 있는지 확인
        if (!plugin.isFeatureEnabled("npc_trading")) {
            sender.sendMessage("§cNPC 교환 시스템이 비활성화되어 있습니다.");
            return true;
        }

        // NPCTradeManager 확인
        NPCTradeManager npcManager = plugin.getNPCTradeManager();
        if (npcManager == null) {
            sender.sendMessage("§cNPC 교환 시스템을 사용할 수 없습니다.");
            return true;
        }

        // 인게임 플레이어만 사용 가능
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        // 기본 권한 확인
        if (!player.hasPermission("ggm.npc.use")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            showHelpCommand(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create":
                case "생성":
                    return handleCreateCommand(player, args);

                case "remove":
                case "제거":
                    return handleRemoveCommand(player, args);

                case "list":
                case "목록":
                    return handleListCommand(player);

                case "info":
                case "정보":
                    return handleInfoCommand(player);

                case "help":
                case "도움말":
                    return showHelpCommand(player);

                default:
                    player.sendMessage("§c알 수 없는 명령어입니다. §e/npc help §c를 확인해주세요.");
                    return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 명령어 처리 중 오류: " + player.getName(), e);
            player.sendMessage("§c명령어 처리 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * NPC 생성 명령어 처리
     */
    private boolean handleCreateCommand(Player player, String[] args) {
        // 관리자 권한 확인
        if (!player.hasPermission("ggm.npc.admin")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
            return true;
        }

        if (args.length < 3) {
            player.sendMessage("§c사용법: /npc create <타입> <이름>");
            player.sendMessage("§7타입: item_trader, resource_trader, special_trader");
            return true;
        }

        try {
            String typeStr = args[1].toUpperCase();
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

            // NPC 타입 검증
            NPCTradeManager.NPCType npcType;
            try {
                npcType = NPCTradeManager.NPCType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c잘못된 NPC 타입입니다.");
                player.sendMessage("§7사용 가능한 타입: ITEM_TRADER, RESOURCE_TRADER, SPECIAL_TRADER");
                return true;
            }

            // 이름 길이 제한
            if (name.length() > 20) {
                player.sendMessage("§cNPC 이름은 20자 이하여야 합니다.");
                return true;
            }

            // 플레이어 위치에서 NPC 생성
            Location location = player.getLocation();

            NPCTradeManager npcManager = plugin.getNPCTradeManager();
            boolean success = npcManager.createNPC(npcType, name, location, player);

            if (success) {
                player.sendMessage("§aNPC '" + name + "'이(가) 생성되었습니다!");
                player.sendMessage("§7타입: " + npcType.getDisplayName());
            } else {
                player.sendMessage("§cNPC 생성에 실패했습니다.");
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 생성 중 오류: " + player.getName(), e);
            player.sendMessage("§cNPC 생성 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * NPC 제거 명령어 처리
     */
    private boolean handleRemoveCommand(Player player, String[] args) {
        // 관리자 권한 확인
        if (!player.hasPermission("ggm.npc.admin")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
            return true;
        }

        player.sendMessage("§e가까이 있는 NPC를 우클릭하여 제거하거나,");
        player.sendMessage("§e/npc list 명령어로 NPC 목록을 확인한 후 직접 접근해주세요.");

        return true;
    }

    /**
     * NPC 목록 명령어 처리
     */
    private boolean handleListCommand(Player player) {
        try {
            NPCTradeManager npcManager = plugin.getNPCTradeManager();
            npcManager.showNPCList(player);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 목록 조회 중 오류: " + player.getName(), e);
            player.sendMessage("§cNPC 목록 조회 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * NPC 정보 명령어 처리
     */
    private boolean handleInfoCommand(Player player) {
        try {
            NPCTradeManager npcManager = plugin.getNPCTradeManager();

            player.sendMessage("§6==========================================");
            player.sendMessage("§e§lNPC 교환 시스템 정보");
            player.sendMessage("");
            player.sendMessage("§a시스템 상태: §f활성화");
            player.sendMessage("§a통계: §f" + npcManager.getNPCStats());
            player.sendMessage("");
            player.sendMessage("§eNPC 타입:");

            for (NPCTradeManager.NPCType type : NPCTradeManager.NPCType.values()) {
                player.sendMessage("§7• §a" + type.getDisplayName() + "§7: " + type.getDescription());
            }

            player.sendMessage("");
            player.sendMessage("§7우클릭으로 NPC와 거래할 수 있습니다.");
            player.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 정보 조회 중 오류: " + player.getName(), e);
            player.sendMessage("§cNPC 정보 조회 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 도움말 명령어 처리
     */
    private boolean showHelpCommand(Player player) {
        try {
            boolean isAdmin = player.hasPermission("ggm.npc.admin");

            player.sendMessage("§6==========================================");
            player.sendMessage("§e§lNPC 시스템 도움말");
            player.sendMessage("");
            player.sendMessage("§a일반 명령어:");
            player.sendMessage("§7• §e/npc list §7- NPC 목록 조회");
            player.sendMessage("§7• §e/npc info §7- 시스템 정보");

            if (isAdmin) {
                player.sendMessage("");
                player.sendMessage("§c관리자 명령어:");
                player.sendMessage("§7• §e/npc create <타입> <이름> §7- NPC 생성");
                player.sendMessage("§7• §e/npc remove §7- NPC 제거 (우클릭)");
                player.sendMessage("");
                player.sendMessage("§7NPC 타입: item_trader, resource_trader, special_trader");
            }

            player.sendMessage("");
            player.sendMessage("§7NPC를 우클릭하여 거래를 시작할 수 있습니다.");
            player.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "NPC 도움말 표시 중 오류: " + player.getName(), e);
            player.sendMessage("§c도움말 조회 중 오류가 발생했습니다.");
            return true;
        }
    }
}