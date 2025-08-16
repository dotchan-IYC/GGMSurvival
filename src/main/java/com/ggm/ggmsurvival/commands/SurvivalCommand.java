// 완전한 SurvivalCommand.java - 이모티콘 제거 버전
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * 완전한 야생 서버 메인 명령어 처리기
 * - 서버 정보 및 상태 확인
 * - 관리자 명령어 지원
 * - 강력한 예외 처리
 * - 시스템 통계 제공
 */
public class SurvivalCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public SurvivalCommand(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // 기본 정보 표시
            if (args.length == 0) {
                showServerInfo(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "info":
                case "정보":
                    showDetailedInfo(sender);
                    break;

                case "reload":
                case "리로드":
                    handleReloadCommand(sender);
                    break;

                case "stats":
                case "통계":
                    showServerStats(sender);
                    break;

                case "patch":
                case "패치":
                    showPatchInfo(sender);
                    break;

                case "help":
                case "도움말":
                    showHelpCommand(sender);
                    break;

                case "status":
                case "상태":
                    showSystemStatus(sender);
                    break;

                case "performance":
                case "성능":
                    if (!sender.hasPermission("ggm.survival.admin")) {
                        sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
                        return true;
                    }
                    showPerformanceInfo(sender);
                    break;

                case "test":
                case "테스트":
                    if (!sender.hasPermission("ggm.survival.admin")) {
                        sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
                        return true;
                    }
                    runSystemTest(sender);
                    break;

                default:
                    sender.sendMessage("§c알 수 없는 명령어입니다. §7/survival help §c를 참고하세요.");
                    break;
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "야생 명령어 처리 중 오류: " + sender.getName() + " - " + String.join(" ", args), e);
            sender.sendMessage("§c명령어 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            return true;
        }
    }

    /**
     * 기본 서버 정보 표시
     */
    private void showServerInfo(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l        GGM 야생 서버");
            sender.sendMessage("");
            sender.sendMessage("§a플러그인: §f" + plugin.getDescription().getName());
            sender.sendMessage("§a버전: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§a온라인: §f" + Bukkit.getOnlinePlayers().size() + "명");

            if (plugin.getEconomyManager() != null) {
                boolean ggmCoreConnected = plugin.getEconomyManager().isGGMCoreConnected();
                sender.sendMessage("§a경제 시스템: " + (ggmCoreConnected ? "§bGGMCore 연동" : "§e독립 모드"));
            }

            sender.sendMessage("");
            sender.sendMessage("§7명령어: §f/survival help");
            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "서버 정보 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c서버 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 상세 정보 표시
     */
    private void showDetailedInfo(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§lGGM 야생 서버 상세 정보");
            sender.sendMessage("");

            // 플러그인 정보
            sender.sendMessage("§a플러그인 정보:");
            sender.sendMessage("§7• 이름: §f" + plugin.getDescription().getName());
            sender.sendMessage("§7• 버전: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§7• 제작자: §f" + String.join(", ", plugin.getDescription().getAuthors()));
            sender.sendMessage("§7• 설명: §f" + plugin.getDescription().getDescription());

            // 서버 정보
            sender.sendMessage("");
            sender.sendMessage("§a서버 정보:");
            sender.sendMessage("§7• 버킷 버전: §f" + Bukkit.getVersion());
            sender.sendMessage("§7• API 버전: §f" + Bukkit.getBukkitVersion());
            sender.sendMessage("§7• 온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());
            sender.sendMessage("§7• 현재 시간: §f" + dateFormat.format(new Date()));

            // 시스템 상태
            sender.sendMessage("");
            sender.sendMessage("§a시스템 상태:");

            // 데이터베이스 상태
            if (plugin.getDatabaseManager() != null) {
                boolean dbConnected = plugin.getDatabaseManager().testConnection();
                sender.sendMessage("§7• 데이터베이스: " + (dbConnected ? "§a연결됨" : "§c연결 안됨"));
            }

            // 경제 시스템 상태
            if (plugin.getEconomyManager() != null) {
                boolean ggmCoreConnected = plugin.getEconomyManager().isGGMCoreConnected();
                sender.sendMessage("§7• GGMCore 연동: " + (ggmCoreConnected ? "§a연결됨" : "§c독립 모드"));
            }

            // 각 매니저 상태
            sender.sendMessage("§7• 직업 매니저: " + (plugin.getJobManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• 강화 매니저: " + (plugin.getEnchantUpgradeManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• 도끼속도 매니저: " + (plugin.getAxeSpeedManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• 드래곤보상 매니저: " + (plugin.getDragonRewardManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• NPC교환 매니저: " + (plugin.getNPCTradeManager() != null ? "§a활성화" : "§c비활성화"));
            sender.sendMessage("§7• 엔더리셋 매니저: " + (plugin.getEnderResetManager() != null ? "§a활성화" : "§c비활성화"));

            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "상세 정보 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c상세 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 리로드 명령어 처리 (관리자 전용)
     */
    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("ggm.survival.admin")) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
            return;
        }

        try {
            sender.sendMessage("§e설정 파일을 리로드하고 있습니다...");

            // 설정 파일 리로드
            plugin.reloadConfig();

            // 각 시스템별 상태 확인
            sender.sendMessage("");
            sender.sendMessage("§a리로드 완료! 시스템 상태:");

            // 직업 시스템
            boolean jobSystemEnabled = plugin.getConfig().getBoolean("job_system.enabled", true);
            sender.sendMessage("§7- 직업 시스템: " + (jobSystemEnabled ? "§a활성화" : "§c비활성화"));

            // 강화 시스템
            boolean upgradeSystemEnabled = plugin.getConfig().getBoolean("upgrade_system.enabled", true);
            sender.sendMessage("§7- 강화 시스템: " + (upgradeSystemEnabled ? "§a활성화" : "§c비활성화"));

            // 드래곤 보상
            boolean dragonRewardEnabled = plugin.getConfig().getBoolean("dragon_reward_system.enabled", true);
            sender.sendMessage("§7- 드래곤 보상: " + (dragonRewardEnabled ? "§a활성화" : "§c비활성화"));

            // NPC 교환
            boolean npcTradeEnabled = plugin.getConfig().getBoolean("npc_trade_system.enabled", true);
            sender.sendMessage("§7- NPC 교환: " + (npcTradeEnabled ? "§a활성화" : "§c비활성화"));

            // 엔더 리셋
            boolean enderResetEnabled = plugin.getConfig().getBoolean("ender_reset_system.enabled", false);
            sender.sendMessage("§7- 엔더 리셋: " + (enderResetEnabled ? "§a활성화" : "§c비활성화"));

            sender.sendMessage("");
            sender.sendMessage("§e일부 변경사항은 서버 재시작 후 적용됩니다.");

            plugin.getLogger().info(sender.getName() + "이(가) 플러그인 설정을 리로드했습니다.");

        } catch (Exception e) {
            sender.sendMessage("§c설정 리로드 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "설정 리로드 실패: " + sender.getName(), e);
        }
    }

    /**
     * 서버 통계 표시
     */
    private void showServerStats(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l야생 서버 통계");
            sender.sendMessage("");

            // 기본 서버 정보
            sender.sendMessage("§a기본 정보:");
            sender.sendMessage("§7• 플러그인 버전: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§7• 온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size() + "명");
            sender.sendMessage("§7• 최대 플레이어: §f" + Bukkit.getMaxPlayers() + "명");

            // 월드 정보
            sender.sendMessage("");
            sender.sendMessage("§a월드 정보:");
            int worldCount = Bukkit.getWorlds().size();
            sender.sendMessage("§7• 로드된 월드: §f" + worldCount + "개");

            for (org.bukkit.World world : Bukkit.getWorlds()) {
                String envName = getEnvironmentName(world.getEnvironment());
                int playerCount = world.getPlayers().size();
                sender.sendMessage("§7  - " + world.getName() + " (" + envName + "): §f" + playerCount + "명");
            }

            // 경제 시스템 상태
            if (plugin.getEconomyManager() != null) {
                sender.sendMessage("");
                sender.sendMessage("§a경제 시스템:");
                boolean ggmCoreConnected = plugin.getEconomyManager().isGGMCoreConnected();
                sender.sendMessage("§7• GGMCore 연동: " + (ggmCoreConnected ? "§a활성화" : "§c독립 모드"));
            }

            // 활성화된 시스템 개수
            sender.sendMessage("");
            sender.sendMessage("§a활성화된 시스템:");
            int activeSystemCount = 0;

            if (plugin.getJobManager() != null) activeSystemCount++;
            if (plugin.getEnchantUpgradeManager() != null) activeSystemCount++;
            if (plugin.getAxeSpeedManager() != null) activeSystemCount++;
            if (plugin.getDragonRewardManager() != null) activeSystemCount++;
            if (plugin.getNPCTradeManager() != null) activeSystemCount++;
            if (plugin.getEnderResetManager() != null) activeSystemCount++;

            sender.sendMessage("§7• 활성 시스템: §f" + activeSystemCount + "/6개");

            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "서버 통계 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c서버 통계 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 패치 정보 표시
     */
    private void showPatchInfo(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l최신 패치 정보");
            sender.sendMessage("");
            sender.sendMessage("§a버전: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§a패치명: §fHikariCP + EnderReset 통합");
            sender.sendMessage("");
            sender.sendMessage("§a주요 변경사항:");
            sender.sendMessage("§7• HikariCP 데이터베이스 연결 풀 도입");
            sender.sendMessage("§7• 엔더 자동 리셋 시스템 추가");
            sender.sendMessage("§7• 엔드시티 접근 차단 기능");
            sender.sendMessage("§7• BungeeCord 멀티 서버 지원");
            sender.sendMessage("§7• 성능 최적화 및 메모리 관리 개선");
            sender.sendMessage("§7• 스레드 안전성 강화");
            sender.sendMessage("§7• 포괄적인 오류 처리 시스템");
            sender.sendMessage("");
            sender.sendMessage("§a수정사항:");
            sender.sendMessage("§7• 메모리 누수 문제 해결");
            sender.sendMessage("§7• 데이터베이스 연결 안정성 향상");
            sender.sendMessage("§7• 플레이어 데이터 저장 로직 개선");
            sender.sendMessage("§7• 예외 처리 강화");
            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "패치 정보 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c패치 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 도움말 명령어 표시
     */
    private void showHelpCommand(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l야생 서버 명령어 도움말");
            sender.sendMessage("");
            sender.sendMessage("§a기본 명령어:");
            sender.sendMessage("§e/survival §7- 서버 기본 정보");
            sender.sendMessage("§e/survival info §7- 상세 정보");
            sender.sendMessage("§e/survival stats §7- 서버 통계");
            sender.sendMessage("§e/survival patch §7- 패치 정보");
            sender.sendMessage("§e/survival status §7- 시스템 상태");
            sender.sendMessage("");
            sender.sendMessage("§a시스템 명령어:");
            sender.sendMessage("§e/job §7- 직업 시스템");
            sender.sendMessage("§e/upgrade §7- 강화 시스템");
            sender.sendMessage("§e/trade §7- NPC 교환 시스템");
            sender.sendMessage("§e/dragon §7- 드래곤 보상 시스템");

            if (plugin.getEnderResetManager() != null) {
                sender.sendMessage("§e/enderreset §7- 엔더 리셋 시스템");
            }

            if (sender.hasPermission("ggm.survival.admin")) {
                sender.sendMessage("");
                sender.sendMessage("§c관리자 명령어:");
                sender.sendMessage("§e/survival reload §7- 설정 리로드");
                sender.sendMessage("§e/survival performance §7- 성능 정보");
                sender.sendMessage("§e/survival test §7- 시스템 테스트");
            }

            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "도움말 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c도움말 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 시스템 상태 표시
     */
    private void showSystemStatus(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l시스템 상태 확인");
            sender.sendMessage("");

            // 플러그인 초기화 상태
            sender.sendMessage("§a플러그인 상태:");
            sender.sendMessage("§7• 초기화 완료: " + (plugin.isInitialized() ? "§a예" : "§c아니오"));
            sender.sendMessage("§7• 종료 진행 중: " + (plugin.isShuttingDown() ? "§c예" : "§a아니오"));

            // 핵심 매니저 상태
            sender.sendMessage("");
            sender.sendMessage("§a핵심 매니저:");
            sender.sendMessage("§7• 데이터베이스: " + getManagerStatus(plugin.getDatabaseManager()));
            sender.sendMessage("§7• 경제 시스템: " + getManagerStatus(plugin.getEconomyManager()));

            // 게임 시스템 매니저
            sender.sendMessage("");
            sender.sendMessage("§a게임 시스템:");
            sender.sendMessage("§7• 직업 시스템: " + getManagerStatus(plugin.getJobManager()));
            sender.sendMessage("§7• 강화 시스템: " + getManagerStatus(plugin.getEnchantUpgradeManager()));
            sender.sendMessage("§7• 도끼 속도: " + getManagerStatus(plugin.getAxeSpeedManager()));
            sender.sendMessage("§7• 드래곤 보상: " + getManagerStatus(plugin.getDragonRewardManager()));
            sender.sendMessage("§7• NPC 교환: " + getManagerStatus(plugin.getNPCTradeManager()));
            sender.sendMessage("§7• 엔더 리셋: " + getManagerStatus(plugin.getEnderResetManager()));

            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "시스템 상태 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c시스템 상태 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 성능 정보 표시 (관리자 전용)
     */
    private void showPerformanceInfo(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l서버 성능 정보");
            sender.sendMessage("");

            // 메모리 정보
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / 1024 / 1024;
            long totalMemory = runtime.totalMemory() / 1024 / 1024;
            long freeMemory = runtime.freeMemory() / 1024 / 1024;
            long usedMemory = totalMemory - freeMemory;

            sender.sendMessage("§a메모리 사용량:");
            sender.sendMessage("§7• 사용 중: §f" + usedMemory + "MB");
            sender.sendMessage("§7• 할당됨: §f" + totalMemory + "MB");
            sender.sendMessage("§7• 최대: §f" + maxMemory + "MB");
            sender.sendMessage("§7• 사용률: §f" + (usedMemory * 100 / maxMemory) + "%");

            // TPS 정보 (근사치)
            sender.sendMessage("");
            sender.sendMessage("§a서버 성능:");
            sender.sendMessage("§7• 프로세서: §f" + runtime.availableProcessors() + "코어");
            sender.sendMessage("§7• 활성 스레드: §f" + Thread.activeCount() + "개");

            // 데이터베이스 연결 풀 정보 (HikariCP)
            if (plugin.getDatabaseManager() != null) {
                sender.sendMessage("");
                sender.sendMessage("§a데이터베이스:");
                sender.sendMessage("§7• 연결 상태: " +
                        (plugin.getDatabaseManager().testConnection() ? "§a정상" : "§c오류"));
                sender.sendMessage("§7• 연결 풀: §fHikariCP");
            }

            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "성능 정보 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c성능 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 시스템 테스트 실행 (관리자 전용)
     */
    private void runSystemTest(CommandSender sender) {
        try {
            sender.sendMessage("§e시스템 테스트를 시작합니다...");
            sender.sendMessage("");

            int passedTests = 0;
            int totalTests = 0;

            // 1. 플러그인 초기화 테스트
            totalTests++;
            boolean initTest = plugin.isInitialized();
            sender.sendMessage("§7[테스트 " + totalTests + "] 플러그인 초기화: " +
                    (initTest ? "§a통과" : "§c실패"));
            if (initTest) passedTests++;

            // 2. 데이터베이스 연결 테스트
            totalTests++;
            boolean dbTest = plugin.getDatabaseManager() != null &&
                    plugin.getDatabaseManager().testConnection();
            sender.sendMessage("§7[테스트 " + totalTests + "] 데이터베이스 연결: " +
                    (dbTest ? "§a통과" : "§c실패"));
            if (dbTest) passedTests++;

            // 3. 경제 시스템 테스트
            totalTests++;
            boolean ecoTest = plugin.getEconomyManager() != null;
            sender.sendMessage("§7[테스트 " + totalTests + "] 경제 시스템: " +
                    (ecoTest ? "§a통과" : "§c실패"));
            if (ecoTest) passedTests++;

            // 4. 설정 파일 테스트
            totalTests++;
            boolean configTest = plugin.getConfig() != null;
            sender.sendMessage("§7[테스트 " + totalTests + "] 설정 파일: " +
                    (configTest ? "§a통과" : "§c실패"));
            if (configTest) passedTests++;

            // 5. 매니저 로드 테스트
            totalTests++;
            int loadedManagers = 0;
            if (plugin.getJobManager() != null) loadedManagers++;
            if (plugin.getEnchantUpgradeManager() != null) loadedManagers++;
            if (plugin.getAxeSpeedManager() != null) loadedManagers++;
            if (plugin.getDragonRewardManager() != null) loadedManagers++;
            if (plugin.getNPCTradeManager() != null) loadedManagers++;
            if (plugin.getEnderResetManager() != null) loadedManagers++;

            boolean managerTest = loadedManagers > 0;
            sender.sendMessage("§7[테스트 " + totalTests + "] 매니저 로드 (" + loadedManagers + "/6): " +
                    (managerTest ? "§a통과" : "§c실패"));
            if (managerTest) passedTests++;

            // 테스트 결과
            sender.sendMessage("");
            double successRate = (double) passedTests / totalTests * 100;
            sender.sendMessage("§a테스트 결과: §f" + passedTests + "/" + totalTests +
                    " 통과 (" + String.format("%.1f", successRate) + "%)");

            if (successRate >= 80) {
                sender.sendMessage("§a시스템이 정상적으로 작동하고 있습니다!");
            } else {
                sender.sendMessage("§c일부 시스템에 문제가 있습니다. 로그를 확인하세요.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "시스템 테스트 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c시스템 테스트 중 오류가 발생했습니다.");
        }
    }

    /**
     * 매니저 상태 확인 헬퍼
     */
    private String getManagerStatus(Object manager) {
        if (manager == null) {
            return "§c비활성화";
        }
        return "§a활성화";
    }

    /**
     * 환경 이름 변환 헬퍼
     */
    private String getEnvironmentName(org.bukkit.World.Environment environment) {
        switch (environment) {
            case NORMAL:
                return "오버월드";
            case NETHER:
                return "네더";
            case THE_END:
                return "엔드";
            default:
                return "알 수 없음";
        }
    }

    /**
     * 탭 완성 제공
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("info", "stats", "patch", "help", "status");

            if (sender.hasPermission("ggm.survival.admin")) {
                subCommands = Arrays.asList("info", "reload", "stats", "patch", "help",
                        "status", "performance", "test");
            }

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        }

        return completions;
    }
}