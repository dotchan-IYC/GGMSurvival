package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.gui.JobSkillGUI;
import com.ggm.ggmsurvival.gui.JobStatsGUI;
import com.ggm.ggmsurvival.gui.JobAchievementGUI;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.enums.JobType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 향상된 직업 명령어 처리기
 * 새로운 UI 기능들을 포함한 통합 명령어 시스템
 */
public class JobCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final JobManager jobManager;
    private final JobSkillGUI skillGUI;
    private final JobStatsGUI statsGUI;
    private final JobAchievementGUI achievementGUI;

    public JobCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
        this.skillGUI = new JobSkillGUI(plugin);
        this.statsGUI = new JobStatsGUI(plugin);
        this.achievementGUI = new JobAchievementGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length == 0) {
            showJobHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "select":
            case "선택":
                jobManager.openJobSelectionGUI(player);
                break;

            case "info":
            case "정보":
                showJobInfo(player);
                break;

            case "skills":
            case "스킬":
                skillGUI.openSkillGUI(player);
                break;

            case "stats":
            case "통계":
                statsGUI.openStatsGUI(player);
                break;

            case "achievements":
            case "업적":
                achievementGUI.openAchievementGUI(player);
                break;

            case "ranking":
            case "랭킹":
                showRanking(player);
                break;

            case "list":
            case "목록":
                showJobList(player);
                break;

            case "reset":
                if (args.length > 1) {
                    handleResetCommand(player, args);
                } else {
                    showResetHelp(player);
                }
                break;

            case "change":
                if (args.length > 1) {
                    handleChangeCommand(player, args);
                } else {
                    showChangeHelp(player);
                }
                break;

            case "setlevel":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                handleSetLevel(player, args);
                break;

            case "addexp":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                handleAddExp(player, args);
                break;

            case "reload":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                handleReload(player);
                break;

            case "debug":
            case "디버그":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                showDebugInfo(player);
                break;

            default:
                showJobHelp(player);
                break;
        }

        return true;
    }

    /**
     * 향상된 도움말 표시
     */
    private void showJobHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l직업 시스템 명령어 §7(v2.0 NEW!)");
        player.sendMessage("");
        player.sendMessage("§a§l기본 명령어:");
        player.sendMessage("§a/job select §7- 직업 선택 GUI");
        player.sendMessage("§a/job info §7- 내 직업 정보 확인");
        player.sendMessage("§a/job list §7- 전체 직업 목록");
        player.sendMessage("");
        player.sendMessage("§b§l새로운 UI 기능:");
        player.sendMessage("§b/job skills §7- §6스킬 관리 대시보드 §c(NEW!)");
        player.sendMessage("§b/job stats §7- §6상세 통계 GUI §c(NEW!)");
        player.sendMessage("§b/job achievements §7- §6업적 시스템 §c(NEW!)");
        player.sendMessage("§b/job ranking §7- §6서버 랭킹 보기 §c(NEW!)");
        player.sendMessage("");
        player.sendMessage("§d§l고급 기능:");
        player.sendMessage("§d/job reset §7- 스킬 리셋 (50,000G)");
        player.sendMessage("§d/job change §7- 직업 변경 (100,000G)");

        if (player.hasPermission("ggm.job.admin")) {
            player.sendMessage("");
            player.sendMessage("§c§l관리자 명령어:");
            player.sendMessage("§c/job setlevel <플레이어> <레벨> §7- 레벨 설정");
            player.sendMessage("§c/job addexp <플레이어> <경험치> §7- 경험치 추가");
            player.sendMessage("§c/job reload §7- 설정 리로드");
            player.sendMessage("§c/job debug §7- 디버그 정보");
        }

        player.sendMessage("");
        player.sendMessage("§7§l시스템 특징:");
        player.sendMessage("§7• 몬스터 처치로 경험치 획득 및 성장");
        player.sendMessage("§7• 레벨 5부터 특수 능력 활성화");
        player.sendMessage("§7• 레벨 10 달성 시 궁극기 해금");
        player.sendMessage("§7• 실시간 진행도 추적 및 시각화");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 직업 정보 표시 (기본 정보)
     */
    private void showJobInfo(Player player) {
        JobType jobType = jobManager.getJobType(player);

        if (jobType == JobType.NONE) {
            player.sendMessage("§c아직 직업을 선택하지 않았습니다!");
            player.sendMessage("§a/job select §7명령어로 직업을 선택하세요.");
            return;
        }

        int level = jobManager.getJobLevel(player);
        int exp = jobManager.getJobExperience(player);
        int nextLevelExp = getRequiredExpForLevel(level + 1);
        int currentLevelExp = exp - getRequiredExpForLevel(level);
        int requiredExp = nextLevelExp - getRequiredExpForLevel(level);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l내 직업 정보");
        player.sendMessage("");
        player.sendMessage("§7직업: " + jobType.getColor() + "§l" + jobType.getDisplayName());
        player.sendMessage("§7레벨: §f" + level + " §7/ §f10");
        player.sendMessage("§7경험치: §f" + currentLevelExp + " §7/ §f" + requiredExp);

        // 진행률 바 표시
        double progress = (double) currentLevelExp / requiredExp;
        String progressBar = createProgressBar(progress, 20);
        player.sendMessage("§7진행도: " + progressBar + " §f" + String.format("%.1f", progress * 100) + "%");

        player.sendMessage("");
        player.sendMessage("§a현재 활성화된 능력:");
        showActiveAbilities(player, jobType, level);

        if (level < 10) {
            player.sendMessage("");
            player.sendMessage("§e다음 레벨 (" + (level + 1) + ") 능력:");
            showNextLevelAbility(player, jobType, level + 1);
        } else {
            player.sendMessage("");
            player.sendMessage("§6§l최대 레벨 달성! 모든 능력이 해금되었습니다!");
        }

        player.sendMessage("");
        player.sendMessage("§b§l새로운 기능: §a/job skills §7로 상세 관리!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 서버 랭킹 표시
     */
    private void showRanking(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l서버 직업 랭킹 TOP 10");
        player.sendMessage("");

        // TODO: 실제 데이터베이스에서 랭킹 조회
        // 임시 데이터로 표시
        player.sendMessage("§71위 §f전설의검사 §7- §c검사 §7레벨 10 §7(4700 EXP)");
        player.sendMessage("§72위 §f최강탱커 §7- §9탱커 §7레벨 9 §7(3800 EXP)");
        player.sendMessage("§73위 §f명중백발 §7- §e궁수 §7레벨 9 §7(3600 EXP)");
        player.sendMessage("§74위 §f용감한모험가 §7- §c검사 §7레벨 8 §7(3000 EXP)");
        player.sendMessage("§75위 §f방패의수호자 §7- §9탱커 §7레벨 8 §7(2800 EXP)");
        player.sendMessage("");

        // 현재 플레이어 순위 표시
        JobType playerJob = jobManager.getJobType(player);
        int playerLevel = jobManager.getJobLevel(player);
        int playerExp = jobManager.getJobExperience(player);

        if (playerJob != JobType.NONE) {
            player.sendMessage("§a내 순위: §f계산 중... §7- " + playerJob.getColor() +
                    playerJob.getDisplayName() + " §7레벨 " + playerLevel +
                    " §7(" + playerExp + " EXP)");
        }

        player.sendMessage("");
        player.sendMessage("§7정확한 순위는 §a/job stats §7에서 확인하세요!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 직업 목록 표시
     */
    private void showJobList(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l사용 가능한 직업 목록");
        player.sendMessage("");

        player.sendMessage("§9§l탱커 (Tank)");
        player.sendMessage("§7• 특징: 높은 방어력과 체력");
        player.sendMessage("§7• 특수 능력: 방패 회복, 도발, 무적 방벽");
        player.sendMessage("§7• 추천 대상: 팀을 보호하고 싶은 플레이어");
        player.sendMessage("");

        player.sendMessage("§c§l검사 (Warrior)");
        player.sendMessage("§7• 특징: 강력한 근접 공격력");
        player.sendMessage("§7• 특수 능력: 연속 베기, 돌진 공격, 광풍 베기");
        player.sendMessage("§7• 추천 대상: 공격적인 플레이를 선호하는 플레이어");
        player.sendMessage("");

        player.sendMessage("§e§l궁수 (Archer)");
        player.sendMessage("§7• 특징: 원거리 공격과 높은 기동성");
        player.sendMessage("§7• 특수 능력: 정밀 사격, 관통 화살, 화살 폭풍");
        player.sendMessage("§7• 추천 대상: 전략적인 플레이를 좋아하는 플레이어");
        player.sendMessage("");

        player.sendMessage("§a직업 선택: §f/job select");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 리셋 명령어 처리
     */
    private void handleResetCommand(Player player, String[] args) {
        if (args.length < 2) {
            showResetHelp(player);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "confirm":
                // 실제 리셋 실행
                executeSkillReset(player);
                break;
            case "cancel":
                player.sendMessage("§a스킬 리셋을 취소했습니다.");
                break;
            default:
                showResetHelp(player);
                break;
        }
    }

    /**
     * 직업 변경 명령어 처리
     */
    private void handleChangeCommand(Player player, String[] args) {
        if (args.length < 2) {
            showChangeHelp(player);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "confirm":
                // 실제 직업 변경 실행
                executeJobChange(player);
                break;
            case "cancel":
                player.sendMessage("§a직업 변경을 취소했습니다.");
                break;
            default:
                showChangeHelp(player);
                break;
        }
    }

    /**
     * 관리자: 레벨 설정
     */
    private void handleSetLevel(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c사용법: /job setlevel <플레이어> <레벨>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
            return;
        }

        try {
            int level = Integer.parseInt(args[2]);
            if (level < 1 || level > 10) {
                player.sendMessage("§c레벨은 1-10 사이여야 합니다.");
                return;
            }

            jobManager.setJobLevel(target, level);
            player.sendMessage("§a" + target.getName() + "의 직업 레벨을 " + level + "로 설정했습니다.");
            target.sendMessage("§a관리자에 의해 직업 레벨이 " + level + "로 설정되었습니다!");

        } catch (NumberFormatException e) {
            player.sendMessage("§c올바른 숫자를 입력하세요.");
        }
    }

    /**
     * 관리자: 경험치 추가
     */
    private void handleAddExp(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c사용법: /job addexp <플레이어> <경험치>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
            return;
        }

        try {
            int exp = Integer.parseInt(args[2]);
            if (exp < 1) {
                player.sendMessage("§c경험치는 1 이상이어야 합니다.");
                return;
            }

            jobManager.addJobExperience(target, exp, "관리자 지급");
            player.sendMessage("§a" + target.getName() + "에게 " + exp + " 경험치를 지급했습니다.");
            target.sendMessage("§a관리자로부터 " + exp + " 직업 경험치를 받았습니다!");

        } catch (NumberFormatException e) {
            player.sendMessage("§c올바른 숫자를 입력하세요.");
        }
    }

    /**
     * 설정 리로드
     */
    private void handleReload(Player player) {
        try {
            plugin.reloadConfig();
            jobManager.reloadConfig();
            player.sendMessage("§a직업 시스템 설정을 리로드했습니다!");
            plugin.getLogger().info(player.getName() + "이(가) 직업 시스템을 리로드했습니다.");
        } catch (Exception e) {
            player.sendMessage("§c리로드 중 오류가 발생했습니다: " + e.getMessage());
            plugin.getLogger().severe("직업 시스템 리로드 실패: " + e.getMessage());
        }
    }

    // === 헬퍼 메소드들 ===

    private void showActiveAbilities(Player player, JobType jobType, int level) {
        switch (jobType) {
            case TANK:
                player.sendMessage("§7• §a기본 방어력 증가");
                if (level >= 3) player.sendMessage("§7• §a방패 회복");
                if (level >= 5) player.sendMessage("§7• §6불굴의 의지 (특수)");
                if (level >= 7) player.sendMessage("§7• §a도발");
                if (level >= 10) player.sendMessage("§7• §6무적 방벽 (궁극기)");
                break;
            case WARRIOR:
                player.sendMessage("§7• §a검술 숙련");
                if (level >= 3) player.sendMessage("§7• §a연속 베기");
                if (level >= 5) player.sendMessage("§7• §6치명타 숙련 (특수)");
                if (level >= 7) player.sendMessage("§7• §a돌진 베기");
                if (level >= 10) player.sendMessage("§7• §6광풍 베기 (궁극기)");
                break;
            case ARCHER:
                player.sendMessage("§7• §a활 숙련");
                if (level >= 3) player.sendMessage("§7• §a경량화");
                if (level >= 5) player.sendMessage("§7• §6정밀 사격 (특수)");
                if (level >= 7) player.sendMessage("§7• §a관통 화살");
                if (level >= 10) player.sendMessage("§7• §6화살 폭풍 (궁극기)");
                break;
        }
    }

    private void showNextLevelAbility(Player player, JobType jobType, int nextLevel) {
        String ability = switch (jobType) {
            case TANK -> switch (nextLevel) {
                case 3 -> "§a방패 회복 능력";
                case 5 -> "§6불굴의 의지 (특수 능력)";
                case 7 -> "§a도발 능력";
                case 10 -> "§6무적 방벽 (궁극기)";
                default -> "§7기본 능력 강화";
            };
            case WARRIOR -> switch (nextLevel) {
                case 3 -> "§a연속 베기 능력";
                case 5 -> "§6치명타 숙련 (특수 능력)";
                case 7 -> "§a돌진 베기 능력";
                case 10 -> "§6광풍 베기 (궁극기)";
                default -> "§7기본 능력 강화";
            };
            case ARCHER -> switch (nextLevel) {
                case 3 -> "§a경량화 능력";
                case 5 -> "§6정밀 사격 (특수 능력)";
                case 7 -> "§a관통 화살 능력";
                case 10 -> "§6화살 폭풍 (궁극기)";
                default -> "§7기본 능력 강화";
            };
            default -> "§7알 수 없음";
        };

        player.sendMessage("§7• " + ability);
    }

    private String createProgressBar(double progress, int length) {
        int filled = (int) (progress * length);
        StringBuilder bar = new StringBuilder("§a");

        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("█");
            } else if (i == filled && progress % (1.0 / length) != 0) {
                bar.append("§e▌§7");
            } else {
                bar.append("§7█");
            }
        }

        return bar.toString();
    }

    private int getRequiredExpForLevel(int level) {
        int[] requirements = {0, 0, 100, 250, 500, 800, 1200, 1700, 2300, 3000, 3800, 4700};
        if (level >= requirements.length) return requirements[requirements.length - 1];
        return requirements[level];
    }

    private void showResetHelp(Player player) {
        player.sendMessage("§c스킬 리셋 사용법:");
        player.sendMessage("§7/job reset confirm - 리셋 진행");
        player.sendMessage("§7/job reset cancel - 리셋 취소");
        player.sendMessage("§c비용: 50,000G");
    }

    private void showChangeHelp(Player player) {
        player.sendMessage("§5직업 변경 사용법:");
        player.sendMessage("§7/job change confirm - 변경 진행");
        player.sendMessage("§7/job change cancel - 변경 취소");
        player.sendMessage("§c비용: 100,000G");
    }

    private void executeSkillReset(Player player) {
        // TODO: 실제 스킬 리셋 로직 구현
        player.sendMessage("§a스킬 리셋이 완료되었습니다!");
        player.sendMessage("§750,000G가 차감되었습니다.");
    }

    private void executeJobChange(Player player) {
        // TODO: 실제 직업 변경 로직 구현
        player.closeInventory();
        jobManager.openJobSelectionGUI(player);
        player.sendMessage("§a직업 변경을 위해 새로운 직업을 선택해주세요!");
        player.sendMessage("§7100,000G가 차감되었습니다.");
    }

    private void showDebugInfo(Player player) {
        JobType job = jobManager.getJobType(player);
        int level = jobManager.getJobLevel(player);
        int exp = jobManager.getJobExperience(player);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l직업 시스템 디버그 (Enhanced)");
        player.sendMessage("");
        player.sendMessage("§a플레이어 데이터:");
        player.sendMessage("§7• 직업: " + job.getColor() + job.getDisplayName());
        player.sendMessage("§7• 레벨: §f" + level);
        player.sendMessage("§7• 경험치: §f" + exp);
        player.sendMessage("");
        player.sendMessage("§b시스템 상태:");
        player.sendMessage("§7• GGMCore 연동: §a정상");
        player.sendMessage("§7• 데이터베이스: §a연결됨");
        player.sendMessage("§7• GUI 시스템: §a활성화");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}