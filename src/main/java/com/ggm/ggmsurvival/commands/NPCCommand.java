package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.NPCTradeManager;
import com.ggm.ggmsurvival.managers.NPCTradeManager.NPCType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.logging.Level;

/**
 * 완전 안정화된 NPC 관리 명령어 처리기 (관리자 전용)
 */
public class NPCCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final NPCTradeManager npcManager;

    public NPCCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.npcManager = plugin.getNPCTradeManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (!sender.hasPermission("ggm.npc.admin")) {
                sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
                return true;
            }

            if (npcManager == null) {
                player.sendMessage("§cNPC 교환 시스템이 비활성화되어 있습니다.");
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
                    handleCreateCommand(player, args);
                    break;

                case "remove":
                case "제거":
                    handleRemoveCommand(player);
                    break;

                case "list":
                case "목록":
                    npcManager.showNPCList(player);
                    break;

                case "stats":
                case "통계":
                    showNPCStats(player);
                    break;

                default:
                    player.sendMessage("§c알 수 없는 명령어입니다. §7/npc help §c를 참고하세요.");
                    break;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "NPC 명령어 처리 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c명령어 처리 중 오류가 발생했습니다.");
            return true;
        }
    }

    private void handleCreateCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c사용법: /npc create <타입> <이름>");
            player.sendMessage("§7타입: item, resource, special");
            return;
        }

        try {
            NPCType type = parseNPCType(args[1]);
            if (type == null) {
                player.sendMessage("§c올바르지 않은 NPC 타입입니다: " + args[1]);
                player.sendMessage("§7사용 가능한 타입: item, resource, special");
                return;
            }

            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            if (name.length() > 20) {
                player.sendMessage("§cNPC 이름은 20자를 초과할 수 없습니다.");
                return;
            }

            boolean success = npcManager.createNPC(type, name, player.getLocation(), player);

            if (success) {
                plugin.getLogger().info(String.format("[NPC생성] %s이(가) %s 타입 NPC '%s'를 생성했습니다.",
                        player.getName(), type.name(), name));
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "NPC 생성 명령어 처리 중 오류: " + player.getName(), e);
            player.sendMessage("§cNPC 생성 중 오류가 발생했습니다.");
        }
    }

    private void handleRemoveCommand(Player player) {
        try {
            // 플레이어가 바라보고 있는 엔티티 확인
            var targetEntity = player.getTargetEntity(5);

            if (targetEntity == null) {
                player.sendMessage("§c제거할 NPC를 바라보고 명령어를 사용해주세요.");
                return;
            }

            if (!(targetEntity instanceof Villager)) {
                player.sendMessage("§c바라보고 있는 대상이 NPC가 아닙니다.");
                return;
            }

            boolean success = npcManager.removeNPC(targetEntity.getUniqueId(), player);

            if (success) {
                plugin.getLogger().info(String.format("[NPC제거] %s이(가) NPC를 제거했습니다.",
                        player.getName()));
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "NPC 제거 명령어 처리 중 오류: " + player.getName(), e);
            player.sendMessage("§cNPC 제거 중 오류가 발생했습니다.");
        }
    }

    private void showNPCHelp(Player player) {
        player.sendMessage("§6==========================================");
        player.sendMessage("§e§lNPC 관리 시스템 (관리자 전용)");
        player.sendMessage("");
        player.sendMessage("§a명령어:");
        player.sendMessage("§7• §e/npc create <타입> <이름> §7- NPC 생성");
        player.sendMessage("§7• §e/npc remove §7- 바라보는 NPC 제거");
        player.sendMessage("§7• §e/npc list §7- NPC 목록 보기");
        player.sendMessage("§7• §e/npc stats §7- NPC 통계");
        player.sendMessage("");
        player.sendMessage("§aNPC 타입:");
        player.sendMessage("§7• §eitem §7- 아이템 상인");
        player.sendMessage("§7• §eresource §7- 자원 상인");
        player.sendMessage("§7• §especial §7- 특수 상인");
        player.sendMessage("§6==========================================");
    }

    private void showNPCStats(Player player) {
        player.sendMessage("§6==========================================");
        player.sendMessage("§e§lNPC 시스템 통계");
        player.sendMessage("");
        player.sendMessage("§7" + npcManager.getNPCStats());
        player.sendMessage("§6==========================================");
    }

    private NPCType parseNPCType(String typeStr) {
        switch (typeStr.toLowerCase()) {
            case "item":
            case "아이템":
                return NPCType.ITEM_TRADER;
            case "resource":
            case "자원":
                return NPCType.RESOURCE_TRADER;
            case "special":
            case "특수":
                return NPCType.SPECIAL_TRADER;
            default:
                return null;
        }
    }
}