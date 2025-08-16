// 완전한 EnderResetCommand.java - 이모티콘 제거 버전
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnderResetManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 완전한 엔더 리셋 명령어 시스템
 * - 리셋 정보 확인
 * - 강제 리셋 실행
 * - 리셋 시간 설정
 * - 엔드시티 차단 설정
 * - 설정 리로드
 */
public class EnderResetCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final EnderResetManager enderResetManager;

    public EnderResetCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.enderResetManager = plugin.getEnderResetManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // EnderResetManager 확인
        if (enderResetManager == null) {
            sender.sendMessage("§c엔더 리셋 시스템이 비활성화되어 있습니다.");
            sender.sendMessage("§7config.yml에서 ender_reset_system.enabled를 true로 설정하세요.");
            return true;
        }

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
                    sender.sendMessage("§c권한이 없습니다! 필요 권한: ggm.enderreset.admin");
                    return true;
                }
                forceReset(sender);
                break;

            case "schedule":
            case "time":
                if (!sender.hasPermission("ggm.enderreset.admin")) {
                    sender.sendMessage("§c권한이 없습니다! 필요 권한: ggm.enderreset.admin");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /enderreset schedule <시간(0-23)> <분(0-59)>");
                    sender.sendMessage("§7예시: /enderreset schedule 12 0 (매일 12:00에 리셋)");
                    return true;
                }
                scheduleReset(sender, args[1], args[2]);
                break;

            case "block":
            case "endcity":
                if (!sender.hasPermission("ggm.enderreset.admin")) {
                    sender.sendMessage("§c권한이 없습니다! 필요 권한: ggm.enderreset.admin");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c사용법: /enderreset block <true|false>");
                    sender.sendMessage("§7예시: /enderreset block true (엔드시티 차단 활성화)");
                    return true;
                }
                toggleEndCityBlock(sender, args[1]);
                break;

            case "reload":
            case "refresh":
                if (!sender.hasPermission("ggm.enderreset.admin")) {
                    sender.sendMessage("§c권한이 없습니다! 필요 권한: ggm.enderreset.admin");
                    return true;
                }
                reloadEnderReset(sender);
                break;

            case "test":
                if (!sender.hasPermission("ggm.enderreset.admin")) {
                    sender.sendMessage("§c권한이 없습니다! 필요 권한: ggm.enderreset.admin");
                    return true;
                }
                testEnderReset(sender);
                break;

            default:
                sender.sendMessage("§c알 수 없는 명령어입니다: " + subCommand);
                sendHelp(sender);
                break;
        }

        return true;
    }

    /**
     * 도움말 표시
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== 엔더 리셋 시스템 명령어 ===");
        sender.sendMessage("§e/enderreset info §7- 리셋 정보 및 상태 확인");
        sender.sendMessage("§e/enderreset status §7- 다음 리셋까지 남은 시간");

        if (sender.hasPermission("ggm.enderreset.admin")) {
            sender.sendMessage("");
            sender.sendMessage("§c=== 관리자 명령어 ===");
            sender.sendMessage("§e/enderreset force §7- 즉시 강제 리셋 (30초 대기)");
            sender.sendMessage("§e/enderreset schedule <시간> <분> §7- 리셋 시간 변경");
            sender.sendMessage("§e/enderreset block <true|false> §7- 엔드시티 차단 설정");
            sender.sendMessage("§e/enderreset reload §7- 설정 다시 로드");
            sender.sendMessage("§e/enderreset test §7- 시스템 테스트");
        }

        sender.sendMessage("§6===============================");
    }

    /**
     * 엔더 리셋 정보 표시
     */
    private void showEnderResetInfo(CommandSender sender) {
        try {
            sender.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage("§e§l     엔더 리셋 시스템 정보");
            sender.sendMessage("");

            sender.sendMessage("§7[시간] 리셋 시간: §f매일 " +
                    String.format("%02d:%02d", enderResetManager.getResetHour(), enderResetManager.getResetMinute()));

            sender.sendMessage("§7[대기] 다음 리셋: §a" + enderResetManager.getTimeUntilReset() + " 후");

            sender.sendMessage("§7[차단] 엔드시티 차단: " +
                    (enderResetManager.isEndCityBlockingEnabled() ? "§a활성화" : "§c비활성화"));

            if (enderResetManager.isEndCityBlockingEnabled()) {
                sender.sendMessage("§7[거리] 차단 최소 거리: §f" + enderResetManager.getEndCityMinDistance() + " 블록");
            }

            sender.sendMessage("§7[서버] 이동 대상 서버: §b" + enderResetManager.getTargetServerName());

            if (enderResetManager.isResetPending()) {
                sender.sendMessage("");
                sender.sendMessage("§c[경고] 강제 리셋이 대기 중입니다!");
            }

            sender.sendMessage("");
            sender.sendMessage("§7[기능] 시스템 설명:");
            sender.sendMessage("§8  • 매일 정해진 시간에 엔드 차원 자동 초기화");
            sender.sendMessage("§8  • 리셋 10분, 5분, 3분, 1분 전 예고 알림");
            sender.sendMessage("§8  • 엔드에 있는 플레이어 자동 서버 이동");

            if (enderResetManager.isEndCityBlockingEnabled()) {
                sender.sendMessage("§8  • 엔드시티 지역 접근 시 자동 메인 엔드로 이동");
            }

            sender.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            sender.sendMessage("§c정보 조회 중 오류가 발생했습니다.");
            plugin.getLogger().warning("엔더 리셋 정보 표시 오류: " + e.getMessage());
        }
    }

    /**
     * 강제 리셋 실행
     */
    private void forceReset(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 게임 내에서만 사용할 수 있습니다.");
            return;
        }

        Player admin = (Player) sender;

        try {
            admin.sendMessage("§c§l[엔더 리셋] §f강제 리셋을 시작합니다...");
            admin.sendMessage("§e[경고] 이 작업은 되돌릴 수 없습니다!");
            admin.sendMessage("§730초 후 자동 실행됩니다.");
            admin.sendMessage("§7취소하려면 명령어를 다시 입력하세요: §f/enderreset force");
            admin.sendMessage("");

            // 30초 후 실행 확인 로직은 EnderResetManager에서 처리
            enderResetManager.manualReset(admin);

        } catch (Exception e) {
            admin.sendMessage("§c강제 리셋 실행 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().warning("강제 리셋 실행 오류: " + e.getMessage());
        }
    }

    /**
     * 리셋 시간 설정
     */
    private void scheduleReset(CommandSender sender, String hourStr, String minuteStr) {
        try {
            int hour = Integer.parseInt(hourStr);
            int minute = Integer.parseInt(minuteStr);

            enderResetManager.setResetTime(hour, minute);

            sender.sendMessage("§a[성공] 리셋 시간이 성공적으로 변경되었습니다!");
            sender.sendMessage("§7새 리셋 시간: §f매일 " + String.format("%02d:%02d", hour, minute));
            sender.sendMessage("§7다음 리셋: §a" + enderResetManager.getTimeUntilReset() + " 후");

        } catch (NumberFormatException e) {
            sender.sendMessage("§c잘못된 숫자 형식입니다.");
            sender.sendMessage("§7사용법: /enderreset schedule <시간(0-23)> <분(0-59)>");
            sender.sendMessage("§7예시: /enderreset schedule 12 0");

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c" + e.getMessage());
            sender.sendMessage("§7시간은 0-23, 분은 0-59 범위여야 합니다.");

        } catch (Exception e) {
            sender.sendMessage("§c리셋 시간 설정 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().warning("리셋 시간 설정 오류: " + e.getMessage());
        }
    }

    /**
     * 엔드시티 차단 설정 토글
     */
    private void toggleEndCityBlock(CommandSender sender, String enableStr) {
        try {
            boolean enable;

            switch (enableStr.toLowerCase()) {
                case "true":
                case "on":
                case "활성화":
                case "1":
                    enable = true;
                    break;
                case "false":
                case "off":
                case "비활성화":
                case "0":
                    enable = false;
                    break;
                default:
                    sender.sendMessage("§c잘못된 값입니다. true 또는 false를 입력하세요.");
                    return;
            }

            // 설정 저장
            plugin.getConfig().set("ender_reset.block_end_cities", enable);
            plugin.saveConfig();

            sender.sendMessage("§a[성공] 엔드시티 차단 설정이 변경되었습니다!");
            sender.sendMessage("§7엔드시티 차단: " + (enable ? "§a활성화" : "§c비활성화"));

            if (enable) {
                int distance = plugin.getConfig().getInt("ender_reset.end_city_min_distance", 1000);
                sender.sendMessage("§7차단 거리: §f" + distance + " 블록");
            }

            sender.sendMessage("§e설정을 적용하려면 /enderreset reload를 실행하세요.");

        } catch (Exception e) {
            sender.sendMessage("§c엔드시티 차단 설정 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().warning("엔드시티 차단 설정 오류: " + e.getMessage());
        }
    }

    /**
     * 엔더 리셋 설정 리로드
     */
    private void reloadEnderReset(CommandSender sender) {
        try {
            sender.sendMessage("§e엔더 리셋 시스템을 다시 로드하고 있습니다...");

            // 플러그인 설정 리로드
            plugin.reloadConfig();

            // EnderResetManager 재초기화는 복잡하므로 재시작 권장
            sender.sendMessage("§a[성공] 설정 파일이 리로드되었습니다!");
            sender.sendMessage("§e[주의] 모든 변경사항을 적용하려면 서버 재시작을 권장합니다.");
            sender.sendMessage("");

            // 현재 설정 표시
            sender.sendMessage("§7현재 설정:");
            sender.sendMessage("§7• 시스템 활성화: " +
                    (plugin.getConfig().getBoolean("ender_reset_system.enabled", false) ? "§a예" : "§c아니오"));
            sender.sendMessage("§7• 리셋 시간: §f" +
                    plugin.getConfig().getInt("ender_reset.hour", 12) + ":" +
                    String.format("%02d", plugin.getConfig().getInt("ender_reset.minute", 0)));
            sender.sendMessage("§7• 엔드시티 차단: " +
                    (plugin.getConfig().getBoolean("ender_reset.block_end_cities", true) ? "§a활성화" : "§c비활성화"));

        } catch (Exception e) {
            sender.sendMessage("§c설정 리로드 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().warning("엔더 리셋 설정 리로드 오류: " + e.getMessage());
        }
    }

    /**
     * 엔더 리셋 시스템 테스트
     */
    private void testEnderReset(CommandSender sender) {
        try {
            sender.sendMessage("§e엔더 리셋 시스템 테스트를 시작합니다...");
            sender.sendMessage("");

            // 1. 설정 확인
            boolean systemEnabled = plugin.getConfig().getBoolean("ender_reset_system.enabled", false);
            sender.sendMessage("§7[테스트 1] 시스템 활성화: " +
                    (systemEnabled ? "§a통과" : "§c실패 - 시스템이 비활성화됨"));

            // 2. EnderResetManager 확인
            boolean managerExists = enderResetManager != null;
            sender.sendMessage("§7[테스트 2] 매니저 로드: " +
                    (managerExists ? "§a통과" : "§c실패 - 매니저 로드 안됨"));

            // 3. 엔드 월드 확인
            boolean endWorldExists = false;
            for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                if (world.getEnvironment() == org.bukkit.World.Environment.THE_END) {
                    endWorldExists = true;
                    break;
                }
            }
            sender.sendMessage("§7[테스트 3] 엔드 월드: " +
                    (endWorldExists ? "§a통과" : "§c실패 - 엔드 월드 없음"));

            // 4. BungeeCord 채널 확인
            boolean bungeeChannelRegistered = plugin.getServer().getMessenger()
                    .isOutgoingChannelRegistered(plugin, "BungeeCord");
            sender.sendMessage("§7[테스트 4] BungeeCord 채널: " +
                    (bungeeChannelRegistered ? "§a통과" : "§c실패 - 채널 등록 안됨"));

            // 5. 권한 확인
            boolean hasPermission = sender.hasPermission("ggm.enderreset.admin");
            sender.sendMessage("§7[테스트 5] 관리자 권한: " +
                    (hasPermission ? "§a통과" : "§c실패 - 권한 없음"));

            sender.sendMessage("");

            // 전체 결과
            if (systemEnabled && managerExists && endWorldExists && bungeeChannelRegistered) {
                sender.sendMessage("§a[결과] 엔더 리셋 시스템이 정상적으로 작동 중입니다!");

                if (managerExists) {
                    sender.sendMessage("§7다음 리셋: §a" + enderResetManager.getTimeUntilReset() + " 후");
                }
            } else {
                sender.sendMessage("§c[결과] 엔더 리셋 시스템에 문제가 있습니다.");
                sender.sendMessage("§7위의 실패한 항목들을 확인해주세요.");
            }

        } catch (Exception e) {
            sender.sendMessage("§c시스템 테스트 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().warning("엔더 리셋 시스템 테스트 오류: " + e.getMessage());
        }
    }

    /**
     * 탭 완성 제공
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 첫 번째 인수 - 하위 명령어
            List<String> subCommands = Arrays.asList("info", "status");

            if (sender.hasPermission("ggm.enderreset.admin")) {
                subCommands = Arrays.asList("info", "status", "force", "schedule", "block", "reload", "test");
            }

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }

        } else if (args.length == 2) {
            // 두 번째 인수
            String subCommand = args[0].toLowerCase();

            if ("block".equals(subCommand) && sender.hasPermission("ggm.enderreset.admin")) {
                completions.addAll(Arrays.asList("true", "false"));
            } else if ("schedule".equals(subCommand) && sender.hasPermission("ggm.enderreset.admin")) {
                // 시간 제안 (0-23)
                for (int i = 0; i <= 23; i++) {
                    completions.add(String.valueOf(i));
                }
            }

        } else if (args.length == 3) {
            // 세 번째 인수
            String subCommand = args[0].toLowerCase();

            if ("schedule".equals(subCommand) && sender.hasPermission("ggm.enderreset.admin")) {
                // 분 제안 (0, 15, 30, 45)
                completions.addAll(Arrays.asList("0", "15", "30", "45"));
            }
        }

        return completions;
    }
}