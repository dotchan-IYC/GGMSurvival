// 완전 안정화된 JobCommand.java
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.managers.JobManager.JobType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 완전 안정화된 직업 명령어 처리기
 * - 모든 직업 관련 명령어 처리
 * - 강력한 예외 처리
 * - 권한 시스템 통합
 * - GUI 시스템 포함
 */
public class JobCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    // 명령어 권한 맵
    private final Map<String, String> commandPermissions;

    public JobCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();

        // 명령어별 권한 초기화
        this.commandPermissions = initializePermissions();

        if (jobManager == null) {
            plugin.getLogger().warning("JobManager가 null입니다. 직업 명령어가 제한될 수 있습니다.");
        }
    }

    /**
     * 명령어별 권한 초기화
     */
    private Map<String, String> initializePermissions() {
        Map<String, String> permissions = new HashMap<>();
        permissions.put("select", "ggm.job");
        permissions.put("info", "ggm.job");
        permissions.put("list", "ggm.job");
        permissions.put("reset", "ggm.job.admin");
        permissions.put("setlevel", "ggm.job.admin");
        permissions.put("addexp", "ggm.job.admin");
        return Collections.unmodifiableMap(permissions);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // 플러그인 상태 확인
            if (plugin.isShuttingDown()) {
                sender.sendMessage("§c서버가 종료 중입니다. 잠시 후 다시 시도해주세요.");
                return true;
            }

            // JobManager 상태 확인
            if (jobManager == null) {
                sender.sendMessage("§c직업 시스템이 비활성화되어 있습니다.");
                return true;
            }

            // 기본 사용법
            if (args.length == 0) {
                showMainHelp(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            // 권한 확인
            if (!hasPermissionForCommand(sender, subCommand)) {
                sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
                return true;
            }

            // 하위 명령어 처리
            switch (subCommand) {
                case "select":
                case "choose":
                    return handleSelectCommand(sender, args);

                case "info":
                case "status":
                    return handleInfoCommand(sender, args);

                case "list":
                case "jobs":
                    return handleListCommand(sender);

                case "reset":
                    return handleResetCommand(sender, args);

                case "setlevel":
                case "level":
                    return handleSetLevelCommand(sender, args);

                case "addexp":
                case "exp":
                    return handleAddExpCommand(sender, args);

                case "help":
                    return handleHelpCommand(sender, args);

                default:
                    sender.sendMessage("§c알 수 없는 명령어입니다. §7/job help §c를 참고하세요.");
                    return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "직업 명령어 처리 중 오류: " + sender.getName() + " - " + String.join(" ", args), e);
            sender.sendMessage("§c명령어 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            return true;
        }
    }

    /**
     * 직업 선택 명령어 처리
     */
    private boolean handleSelectCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        try {
            // 이미 직업이 있는지 확인
            JobType currentJob = jobManager.getJobType(player);
            if (currentJob != JobType.NONE) {
                player.sendMessage("§c이미 " + currentJob.getColor() + currentJob.getDisplayName() +
                        " §c직업을 선택하셨습니다!");
                player.sendMessage("§7직업 변경은 불가능합니다. §c/job reset §7(관리자만)");
                return true;
            }

            // 직업 타입이 지정된 경우
            if (args.length >= 2) {
                return handleDirectJobSelection(player, args[1]);
            }

            // GUI 선택 창 열기
            openJobSelectionGUI(player);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 선택 처리 중 오류: " + player.getName(), e);
            player.sendMessage("§c직업 선택 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 직접 직업 선택 처리
     */
    private boolean handleDirectJobSelection(Player player, String jobName) {
        try {
            JobType selectedJob = parseJobType(jobName);
            if (selectedJob == null || selectedJob == JobType.NONE) {
                player.sendMessage("§c존재하지 않는 직업입니다: " + jobName);
                player.sendMessage("§7사용 가능한 직업: §atank, warrior, archer");
                return true;
            }

            // 직업 선택 실행
            if (jobManager.setJobType(player, selectedJob)) {
                String message = plugin.getConfig().getString("messages.job_selected",
                                "{job} 직업을 선택하셨습니다! 몬스터를 처치하여 레벨을 올리세요.")
                        .replace("{job}", selectedJob.getColor() + selectedJob.getDisplayName() + "§a");

                player.sendMessage("§a" + message);
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // 직업별 안내 메시지
                showJobWelcomeMessage(player, selectedJob);

            } else {
                player.sendMessage("§c직업 선택에 실패했습니다. 이미 직업이 있거나 오류가 발생했습니다.");
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직접 직업 선택 중 오류: " + player.getName(), e);
            return true;
        }
    }

    /**
     * 직업 정보 명령어 처리
     */
    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        try {
            // 다른 플레이어 정보 조회 (관리자)
            if (args.length >= 2 && sender.hasPermission("ggm.job.admin")) {
                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
                    return true;
                }

                showJobInfoToSender(sender, targetPlayer);
            } else {
                // 자신의 정보 조회
                jobManager.showJobInfo(player);
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 정보 조회 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c정보 조회 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 직업 목록 명령어 처리
     */
    private boolean handleListCommand(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e사용 가능한 직업 목록");
            sender.sendMessage("");

            for (JobType job : JobType.values()) {
                if (job == JobType.NONE) continue;

                sender.sendMessage(job.getColor() + "§l" + job.getDisplayName());

                switch (job) {
                    case TANK:
                        sender.sendMessage("§7• 높은 생존력과 방어력");
                        sender.sendMessage("§7• 레벨 5: 흉갑 착용시 체력 +2칸");
                        sender.sendMessage("§7• 레벨 10: 체력 +4칸, 방패 방어시 회복");
                        break;

                    case WARRIOR:
                        sender.sendMessage("§7• 높은 공격력과 근접 전투");
                        sender.sendMessage("§7• 레벨 5: 검 사용시 공격속도 증가");
                        sender.sendMessage("§7• 레벨 10: 크리티컬 공격 (10% 확률)");
                        break;

                    case ARCHER:
                        sender.sendMessage("§7• 원거리 공격과 이동성");
                        sender.sendMessage("§7• 레벨 5: 가죽장화 착용시 이동속도 +20%");
                        sender.sendMessage("§7• 레벨 10: 트리플 샷 (화살 3발 동시 발사)");
                        break;
                }

                sender.sendMessage("");
            }

            sender.sendMessage("§a명령어: §f/job select <직업명>");
            sender.sendMessage("§c주의: 직업 선택 후 변경은 불가능합니다!");
            sender.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 목록 표시 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c목록 조회 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 직업 초기화 명령어 처리 (관리자 전용)
     */
    private boolean handleResetCommand(CommandSender sender, String[] args) {
        try {
            Player targetPlayer;

            if (args.length >= 2) {
                // 다른 플레이어 초기화
                targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
                    return true;
                }
            } else if (sender instanceof Player) {
                // 자신 초기화
                targetPlayer = (Player) sender;
            } else {
                sender.sendMessage("§c콘솔에서는 플레이어 이름을 지정해주세요.");
                return true;
            }

            JobType oldJob = jobManager.getJobType(targetPlayer);

            // 직업 초기화 실행
            jobManager.resetJob(targetPlayer);

            // 메시지
            String resetMessage = String.format("§a%s§a의 직업이 초기화되었습니다. (이전: %s%s§a)",
                    targetPlayer.getName(),
                    oldJob.getColor(),
                    oldJob.getDisplayName());

            sender.sendMessage(resetMessage);

            if (!sender.equals(targetPlayer)) {
                targetPlayer.sendMessage("§c관리자에 의해 직업이 초기화되었습니다.");
                targetPlayer.sendMessage("§7/job select 명령어로 새로운 직업을 선택할 수 있습니다.");
            }

            plugin.getLogger().info(String.format("[직업초기화] %s이(가) %s의 직업을 초기화했습니다.",
                    sender.getName(), targetPlayer.getName()));

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 초기화 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c직업 초기화 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 직업 레벨 설정 명령어 처리 (관리자 전용)
     */
    private boolean handleSetLevelCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /job setlevel <플레이어> <레벨>");
            return true;
        }

        try {
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
                return true;
            }

            int level;
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c올바른 숫자를 입력해주세요: " + args[2]);
                return true;
            }

            if (level < 1 || level > 10) {
                sender.sendMessage("§c레벨은 1부터 10까지만 설정할 수 있습니다.");
                return true;
            }

            // 직업이 없는 경우 확인
            if (jobManager.getJobType(targetPlayer) == JobType.NONE) {
                sender.sendMessage("§c해당 플레이어는 직업이 없습니다. 먼저 직업을 선택해주세요.");
                return true;
            }

            // 레벨 설정 실행
            jobManager.setJobLevel(targetPlayer, level);

            sender.sendMessage(String.format("§a%s의 직업 레벨을 %d로 설정했습니다.",
                    targetPlayer.getName(), level));

            plugin.getLogger().info(String.format("[직업레벨설정] %s이(가) %s의 레벨을 %d로 설정했습니다.",
                    sender.getName(), targetPlayer.getName(), level));

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 레벨 설정 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c레벨 설정 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 경험치 추가 명령어 처리 (관리자 전용)
     */
    private boolean handleAddExpCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /job addexp <플레이어> <경험치>");
            return true;
        }

        try {
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
                return true;
            }

            int experience;
            try {
                experience = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c올바른 숫자를 입력해주세요: " + args[2]);
                return true;
            }

            if (experience <= 0) {
                sender.sendMessage("§c경험치는 1 이상이어야 합니다.");
                return true;
            }

            // 직업이 없는 경우 확인
            if (jobManager.getJobType(targetPlayer) == JobType.NONE) {
                sender.sendMessage("§c해당 플레이어는 직업이 없습니다. 먼저 직업을 선택해주세요.");
                return true;
            }

            // 경험치 추가 실행
            jobManager.addJobExperience(targetPlayer, experience);

            sender.sendMessage(String.format("§a%s에게 %d 경험치를 추가했습니다.",
                    targetPlayer.getName(), experience));

            plugin.getLogger().info(String.format("[경험치추가] %s이(가) %s에게 %d 경험치를 추가했습니다.",
                    sender.getName(), targetPlayer.getName(), experience));

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "경험치 추가 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c경험치 추가 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 도움말 명령어 처리
     */
    private boolean handleHelpCommand(CommandSender sender, String[] args) {
        try {
            boolean isAdmin = sender.hasPermission("ggm.job.admin");

            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e직업 시스템 도움말");
            sender.sendMessage("");
            sender.sendMessage("§a플레이어 명령어:");
            sender.sendMessage("§7• §e/job select §7- 직업 선택 GUI 열기");
            sender.sendMessage("§7• §e/job select <직업명> §7- 직접 직업 선택");
            sender.sendMessage("§7• §e/job info §7- 내 직업 정보 확인");
            sender.sendMessage("§7• §e/job list §7- 사용 가능한 직업 목록");

            if (isAdmin) {
                sender.sendMessage("");
                sender.sendMessage("§c관리자 명령어:");
                sender.sendMessage("§7• §e/job reset [플레이어] §7- 직업 초기화");
                sender.sendMessage("§7• §e/job setlevel <플레이어> <레벨> §7- 레벨 설정");
                sender.sendMessage("§7• §e/job addexp <플레이어> <경험치> §7- 경험치 추가");
                sender.sendMessage("§7• §e/job info <플레이어> §7- 다른 플레이어 정보");
            }

            sender.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "도움말 표시 중 오류: " + sender.getName(), e);
            return true;
        }
    }

    /**
     * 직업 선택 GUI 열기
     */
    private void openJobSelectionGUI(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, 27, "§6§l직업 선택");

            // 탱커
            ItemStack tankItem = createJobItem(Material.IRON_CHESTPLATE, JobType.TANK,
                    "§9§l탱커", Arrays.asList(
                            "§7높은 생존력과 방어력을 가진 직업",
                            "",
                            "§a레벨 5 특수 능력:",
                            "§7• 흉갑 착용시 최대 체력 +2칸",
                            "",
                            "§6레벨 10 최강 능력:",
                            "§7• 최대 체력 +4칸 (총 28HP)",
                            "§7• 방패 방어시 체력 회복",
                            "",
                            "§e클릭하여 선택하기"
                    ));

            // 검사
            ItemStack warriorItem = createJobItem(Material.IRON_SWORD, JobType.WARRIOR,
                    "§c§l검사", Arrays.asList(
                            "§7높은 공격력과 근접 전투 전문 직업",
                            "",
                            "§a레벨 5 특수 능력:",
                            "§7• 검 사용시 공격속도 증가",
                            "",
                            "§6레벨 10 최강 능력:",
                            "§7• 크리티컬 공격 (10% 확률, 2.5배 피해)",
                            "",
                            "§e클릭하여 선택하기"
                    ));

            // 궁수
            ItemStack archerItem = createJobItem(Material.BOW, JobType.ARCHER,
                    "§a§l궁수", Arrays.asList(
                            "§7원거리 공격과 뛰어난 이동성의 직업",
                            "",
                            "§a레벨 5 특수 능력:",
                            "§7• 가죽장화 착용시 이동속도 +20%",
                            "",
                            "§6레벨 10 최강 능력:",
                            "§7• 트리플 샷 (화살 3발 동시 발사)",
                            "",
                            "§e클릭하여 선택하기"
                    ));

            // 아이템 배치
            gui.setItem(11, tankItem);
            gui.setItem(13, warriorItem);
            gui.setItem(15, archerItem);

            // 안내 아이템
            ItemStack infoItem = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = infoItem.getItemMeta();
            infoMeta.setDisplayName("§6§l직업 선택 안내");
            infoMeta.setLore(Arrays.asList(
                    "§7직업을 선택하면:",
                    "§7• 몬스터 처치로 경험치 획득",
                    "§7• 레벨 5에서 특수 능력 해제",
                    "§7• 레벨 10에서 최강 능력 획득",
                    "",
                    "§c주의: 직업 선택 후 변경 불가!"
            ));
            infoItem.setItemMeta(infoMeta);

            gui.setItem(22, infoItem);

            // GUI 열기
            player.openInventory(gui);
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 선택 GUI 열기 실패: " + player.getName(), e);
            player.sendMessage("§cGUI를 열 수 없습니다. 명령어를 사용해주세요: /job select <직업명>");
        }
    }

    /**
     * 직업 아이템 생성
     */
    private ItemStack createJobItem(Material material, JobType jobType, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 문자열을 JobType으로 변환
     */
    private JobType parseJobType(String jobName) {
        switch (jobName.toLowerCase()) {
            case "tank":
            case "탱커":
                return JobType.TANK;
            case "warrior":
            case "검사":
                return JobType.WARRIOR;
            case "archer":
            case "궁수":
                return JobType.ARCHER;
            default:
                return null;
        }
    }

    /**
     * 직업 환영 메시지 표시
     */
    private void showJobWelcomeMessage(Player player, JobType job) {
        try {
            player.sendMessage("");
            player.sendMessage("§6==========================================");
            player.sendMessage("§e§l" + job.getDisplayName() + " 직업에 오신 것을 환영합니다!");

            switch (job) {
                case TANK:
                    player.sendMessage("§9당신은 이제 강력한 방어력을 가진 탱커입니다!");
                    player.sendMessage("§7• 몬스터의 공격을 견디며 팀을 보호하세요");
                    player.sendMessage("§7• 레벨 5가 되면 흉갑의 진정한 힘을 느낄 수 있습니다");
                    break;

                case WARRIOR:
                    player.sendMessage("§c당신은 이제 용맹한 검사입니다!");
                    player.sendMessage("§7• 검으로 적을 베어나가며 전장을 지배하세요");
                    player.sendMessage("§7• 레벨 5가 되면 검의 진정한 힘을 느낄 수 있습니다");
                    break;

                case ARCHER:
                    player.sendMessage("§a당신은 이제 민첩한 궁수입니다!");
                    player.sendMessage("§7• 활로 원거리에서 적을 제압하세요");
                    player.sendMessage("§7• 레벨 5가 되면 바람의 속도를 느낄 수 있습니다");
                    break;
            }

            player.sendMessage("");
            player.sendMessage("§a몬스터를 처치하여 경험치를 획득하고 성장하세요!");
            player.sendMessage("§7현재 레벨: §f1 §7| 목표: §e레벨 10 만렙");
            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "환영 메시지 표시 실패: " + player.getName(), e);
        }
    }

    /**
     * 다른 플레이어의 직업 정보를 발신자에게 표시
     */
    private void showJobInfoToSender(CommandSender sender, Player targetPlayer) {
        try {
            JobType job = jobManager.getJobType(targetPlayer);
            int level = jobManager.getJobLevel(targetPlayer);
            int exp = jobManager.getJobExperience(targetPlayer);

            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l" + targetPlayer.getName() + "의 직업 정보");
            sender.sendMessage("§7직업: " + job.getColor() + job.getDisplayName());
            sender.sendMessage("§7레벨: §f" + level + " / 10");
            sender.sendMessage("§7경험치: §f" + exp);
            sender.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "다른 플레이어 정보 표시 실패: " + sender.getName(), e);
            sender.sendMessage("§c정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 메인 도움말 표시
     */
    private void showMainHelp(CommandSender sender) {
        sender.sendMessage("§6==========================================");
        sender.sendMessage("§e§l직업 시스템");
        sender.sendMessage("");
        sender.sendMessage("§a주요 명령어:");
        sender.sendMessage("§7• §e/job select §7- 직업 선택");
        sender.sendMessage("§7• §e/job info §7- 내 정보 확인");
        sender.sendMessage("§7• §e/job list §7- 직업 목록");
        sender.sendMessage("§7• §e/job help §7- 상세 도움말");
        sender.sendMessage("");
        sender.sendMessage("§7몬스터를 처치하여 경험치를 얻고 성장하세요!");
        sender.sendMessage("§6==========================================");
    }

    /**
     * 명령어별 권한 확인
     */
    private boolean hasPermissionForCommand(CommandSender sender, String subCommand) {
        String permission = commandPermissions.get(subCommand);
        if (permission == null) {
            return true; // 권한이 정의되지 않은 명령어는 허용
        }

        return sender.hasPermission(permission);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            List<String> completions = new ArrayList<>();

            if (args.length == 1) {
                // 첫 번째 인수: 하위 명령어
                List<String> subCommands = Arrays.asList("select", "info", "list", "help");

                // 관리자 명령어 추가
                if (sender.hasPermission("ggm.job.admin")) {
                    subCommands = new ArrayList<>(subCommands);
                    subCommands.addAll(Arrays.asList("reset", "setlevel", "addexp"));
                }

                return subCommands.stream()
                        .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());

            } else if (args.length == 2) {
                String subCommand = args[0].toLowerCase();

                switch (subCommand) {
                    case "select":
                    case "choose":
                        // 직업 목록
                        return Arrays.asList("tank", "warrior", "archer").stream()
                                .filter(job -> job.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());

                    case "reset":
                    case "setlevel":
                    case "addexp":
                    case "info":
                        // 온라인 플레이어 목록 (관리자 명령어)
                        if (sender.hasPermission("ggm.job.admin")) {
                            return Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .collect(Collectors.toList());
                        }
                        break;
                }

            } else if (args.length == 3) {
                String subCommand = args[0].toLowerCase();

                if ("setlevel".equals(subCommand) && sender.hasPermission("ggm.job.admin")) {
                    // 레벨 목록 (1-10)
                    return Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10").stream()
                            .filter(level -> level.startsWith(args[2]))
                            .collect(Collectors.toList());
                }
            }

            return completions;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "탭 완성 처리 중 오류: " + sender.getName(), e);
            return new ArrayList<>();
        }
    }
}