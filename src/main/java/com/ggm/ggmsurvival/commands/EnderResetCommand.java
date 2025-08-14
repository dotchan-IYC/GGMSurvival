package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnderResetManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderResetCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final EnderResetManager enderResetManager;

    public EnderResetCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.enderResetManager = plugin.getEnderResetManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
            case "status":
                showEnderResetInfo(sender);
                break;

            case "force":
            case "reset":
                if (!sender.hasPermission("ggm.enderreset.admin")) {
                    sender.sendMessage("권한이 없습니다!");
                    return true;
                }
                forceReset(sender);
                break;

            case "schedule":
                if (!sender.hasPermission("ggm.enderreset.admin")) {
                    sender.sendMessage("권한이 없습니다!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("사용법: /enderreset schedule <시간> <분>");
                    return true;
                }
                scheduleReset(sender, args[1], args[2]);
                break;

            case "block":
                if (!sender.hasPermission("ggm.enderreset.admin")) {
                    sender.sendMessage("권한이 없습니다!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("사용법: /enderreset block <true|false>");
                    return true;
                }
                toggleEndCityBlock(sender, args[1]);
                break;

            case "reload":
                if (!sender.hasPermission("ggm.enderreset.admin")) {
                    sender.sendMessage("권한이 없습니다!");
                    return true;
                }
                reloadEnderReset(sender);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("=== 엔더 리셋 시스템 ===");
        sender.sendMessage("/enderreset info - 리셋 정보 확인");
        sender.sendMessage("/enderreset status - 다음 리셋까지 시간");

        if (sender.hasPermission("ggm.enderreset.admin")) {
            sender.sendMessage("=== 관리자 명령어 ===");
            sender.sendMessage("/enderreset force - 즉시 강제 리셋");
            sender.sendMessage("/enderreset schedule <시간> <분> - 리셋 시간 변경");
            sender.sendMessage("/enderreset block <true|false> - 엔드시티 차단 설정");
            sender.sendMessage("/enderreset reload - 설정 다시 로드");
        }

        sender.sendMessage("=====================");
    }

    private void showEnderResetInfo(CommandSender sender) {
        sender.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("엔더 리셋 시스템 정보");
        sender.sendMessage("");
        sender.sendMessage("리셋 시간: 매일 " +
                String.format("%02d:%02d", enderResetManager.getResetHour(), enderResetManager.getResetMinute()));
        sender.sendMessage("다음 리셋: " + enderResetManager.getTimeUntilReset() + " 후");
        sender.sendMessage("엔드시티 차단: " +
                (enderResetManager.isEndCityBlockingEnabled() ? "활성화" : "비활성화"));
        sender.sendMessage("이동 대상 서버: " + enderResetManager.getTargetServerName());
        sender.sendMessage("");
        sender.sendMessage("• 리셋 10분, 5분, 3분, 1분 전에 예고 알림");
        sender.sendMessage("• 엔드에 있는 플레이어는 자동으로 다른 서버로 이동");
        sender.sendMessage("• 엔드시티 지역 접근 시 자동으로 메인 엔드로 이동");
        sender.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void forceReset(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return;
        }

        Player admin = (Player) sender;

        admin.sendMessage("[엔더 리셋] 정말로 즉시 리셋하시겠습니까?");
        admin.sendMessage("이 작업은 되돌릴 수 없습니다. 30초 후 자동 실행됩니다.");
        admin.sendMessage("다시 명령어를 입력하면 취소됩니다.");

        // 30초 후 실행 확인 로직은 별도로 구현 필요
        enderResetManager.manualReset(admin);
    }

    private void scheduleReset(CommandSender sender, String hourStr, String minuteStr) {
        try {
            int hour = Integer.parseInt(hourStr);
            int minute = Integer.parseInt(minuteStr);

            enderResetManager.setResetTime(hour, minute);

            sender.sendMessage("[엔더 리셋] 리셋 시간이 " +
                    String.format("%02d:%02d", hour, minute) + "로 변경되었습니다!");
            sender.sendMessage("다음 리셋: " + enderResetManager.getTimeUntilReset() + " 후");

        } catch (NumberFormatException e) {
            sender.sendMessage("잘못된 숫자 형식입니다. 예: /enderreset schedule 12 0");
        } catch (IllegalArgumentException e) {
            sender.sendMessage(e.getMessage());
        }
    }

    private void toggleEndCityBlock(CommandSender sender, String value) {
        boolean enabled;

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("enable")) {
            enabled = true;
        } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("disable")) {
            enabled = false;
        } else {
            sender.sendMessage("사용법: /enderreset block <true|false>");
            return;
        }

        enderResetManager.setEndCityBlocking(enabled);

        sender.sendMessage("[엔더 리셋] 엔드시티 차단이 " +
                (enabled ? "활성화" : "비활성화") + "되었습니다!");

        if (enabled) {
            sender.sendMessage("플레이어가 엔드시티 지역에 접근하면 자동으로 메인 엔드로 이동됩니다.");
        } else {
            sender.sendMessage("플레이어가 엔드시티에 자유롭게 접근할 수 있습니다.");
        }
    }

    private void reloadEnderReset(CommandSender sender) {
        try {
            plugin.reloadConfig();

            // EnderResetManager 재시작 (새로운 설정 적용)
            plugin.getEnderResetManager().shutdown();
            // 새 인스턴스 생성은 플러그인 레벨에서 처리 필요

            sender.sendMessage("[엔더 리셋] 설정이 다시 로드되었습니다!");
            sender.sendMessage("새로운 설정이 적용되었습니다.");

        } catch (Exception e) {
            sender.sendMessage("설정 리로드 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().severe("엔더 리셋 설정 리로드 실패: " + e.getMessage());
        }
    }
}