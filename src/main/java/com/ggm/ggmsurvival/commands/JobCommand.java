// 새로운 직업 명령어 - 레벨 관리 기능 추가
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.managers.JobManager.JobType;
import com.ggm.ggmsurvival.managers.ScoreboardIntegration;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JobCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showJobHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "select":
            case "선택":
                if (args.length == 1) {
                    openJobSelectionGUI(player);
                } else {
                    selectJobByCommand(player, args[1]);
                }
                break;

            case "info":
            case "정보":
                jobManager.showJobInfo(player);
                break;

            case "list":
            case "목록":
                showJobList(player);
                break;

            case "reset":
            case "초기화":
                if (args.length > 1 && player.hasPermission("ggm.job.admin")) {
                    // 관리자가 다른 플레이어 초기화
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null) {
                        jobManager.resetJob(target);
                        player.sendMessage("§a" + target.getName() + "의 직업을 초기화했습니다.");
                        target.sendMessage("§c관리자에 의해 직업이 초기화되었습니다.");
                    } else {
                        player.sendMessage("§c플레이어를 찾을 수 없습니다.");
                    }
                } else if (player.hasPermission("ggm.job.admin")) {
                    // 자신의 직업 초기화
                    jobManager.resetJob(player);
                    player.sendMessage("§a직업이 초기화되었습니다.");
                } else {
                    player.sendMessage("§c권한이 없습니다.");
                }
                break;

            case "setlevel":
            case "레벨설정":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }

                if (args.length < 3) {
                    player.sendMessage("§c사용법: /job setlevel <플레이어> <레벨>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§c플레이어를 찾을 수 없습니다.");
                    return true;
                }

                try {
                    int level = Integer.parseInt(args[2]);
                    if (level < 1 || level > 10) {
                        player.sendMessage("§c레벨은 1~10 사이여야 합니다.");
                        return true;
                    }

                    jobManager.setJobLevel(target, level);
                    player.sendMessage("§a" + target.getName() + "의 직업 레벨을 " + level + "로 설정했습니다.");

                } catch (NumberFormatException e) {
                    player.sendMessage("§c올바른 숫자를 입력하세요.");
                }
                break;

            case "addexp":
            case "경험치추가":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }

                if (args.length < 3) {
                    player.sendMessage("§c사용법: /job addexp <플레이어> <경험치>");
                    return true;
                }

                Player expTarget = Bukkit.getPlayer(args[1]);
                if (expTarget == null) {
                    player.sendMessage("§c플레이어를 찾을 수 없습니다.");
                    return true;
                }

                try {
                    int exp = Integer.parseInt(args[2]);
                    jobManager.addJobExperience(expTarget, exp);
                    player.sendMessage("§a" + expTarget.getName() + "에게 " + exp + " 경험치를 추가했습니다.");

                } catch (NumberFormatException e) {
                    player.sendMessage("§c올바른 숫자를 입력하세요.");
                }
                break;

            case "debug":
            case "디버그":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                showScoreboardDebug(player);
                break;

            default:
                showJobHelp(player);
                break;
        }

        return true;
    }

    /**
     * 도움말 표시
     */
    private void showJobHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚔️ 직업 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§a/job select §7- 직업 선택 GUI");
        player.sendMessage("§a/job info §7- 내 직업 정보 확인");
        player.sendMessage("§a/job list §7- 직업 목록 보기");

        if (player.hasPermission("ggm.job.admin")) {
            player.sendMessage("");
            player.sendMessage("§c관리자 명령어:");
            player.sendMessage("§c/job reset [플레이어] §7- 직업 초기화");
            player.sendMessage("§c/job setlevel <플레이어> <레벨> §7- 직업 레벨 설정");
            player.sendMessage("§c/job addexp <플레이어> <경험치> §7- 경험치 추가");
            player.sendMessage("§c/job debug §7- 스코어보드 연동 디버그");
        }

        player.sendMessage("");
        player.sendMessage("§7§l새로운 기능:");
        player.sendMessage("§7• 몬스터 처치로 직업 경험치 획득");
        player.sendMessage("§7• 레벨 5 달성시 특수 능력 해제");
        player.sendMessage("§7• 최대 레벨 10까지 성장 가능");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 스코어보드 디버그 정보 표시
     */
    private void showScoreboardDebug(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🔧 스코어보드 연동 디버그");
        player.sendMessage("");

        // JobManager 상태
        JobManager.JobType job = jobManager.getJobType(player);
        int level = jobManager.getJobLevel(player);
        int exp = jobManager.getJobExperience(player);

        player.sendMessage("§a현재 직업 데이터:");
        player.sendMessage("§7• 직업: " + job.getColor() + job.getDisplayName());
        player.sendMessage("§7• 레벨: §f" + level);
        player.sendMessage("§7• 경험치: §f" + exp);
        player.sendMessage("");

        // 스코어보드용 표시명
        String scoreboardDisplay = jobManager.getJobDisplayForScoreboard(player);
        player.sendMessage("§b스코어보드 표시:");
        player.sendMessage("§7• 표시명: " + scoreboardDisplay);
        player.sendMessage("");

        // 스코어보드 연동 상태
        ScoreboardIntegration integration = jobManager.getScoreboardIntegration();
        if (integration != null) {
            boolean coreAvailable = integration.isGGMCoreAvailable();
            player.sendMessage("§c연동 상태:");
            player.sendMessage("§7• GGMCore 연동: " + (coreAvailable ? "§a성공" : "§c실패"));
            player.sendMessage("§7• 스코어보드 시스템: " + (integration != null ? "§a활성화" : "§c비활성화"));

            if (coreAvailable) {
                player.sendMessage("§a연동 재시도를 실행합니다...");
                integration.retryScoreboardIntegration();
            } else {
                player.sendMessage("§c문제: GGMCore가 감지되지 않았습니다.");
                player.sendMessage("§7해결: GGMCore 플러그인이 설치되고 활성화되어 있는지 확인하세요.");
            }
        } else {
            player.sendMessage("§c연동 상태: §c스코어보드 연동 시스템 없음");
            player.sendMessage("§7문제: 스코어보드 연동이 초기화되지 않았습니다.");
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 직업 선택 GUI 열기
     */
    private void openJobSelectionGUI(Player player) {
        JobType currentJob = jobManager.getJobType(player);

        if (currentJob != JobType.NONE) {
            player.sendMessage("§c이미 직업을 선택하셨습니다!");
            player.sendMessage("§7현재 직업: " + currentJob.getColor() + currentJob.getDisplayName());
            player.sendMessage("§7레벨: §f" + jobManager.getJobLevel(player));
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, "§6§l직업 선택 - 새로운 레벨 시스템");

        // 탱커
        ItemStack tankItem = new ItemStack(Material.SHIELD);
        ItemMeta tankMeta = tankItem.getItemMeta();
        tankMeta.setDisplayName("§9§l탱커 (TANK)");
        List<String> tankLore = new ArrayList<>();
        tankLore.add("§7방어와 생존에 특화된 직업");
        tankLore.add("");
        tankLore.add("§a기본 능력:");
        tankLore.add("§7• 방패로 공격을 막으면 체력 회복");
        tankLore.add("§7• 레벨업시 방어력 증가");
        tankLore.add("");
        tankLore.add("§6레벨 5 특수 능력:");
        tankLore.add("§7• 흉갑 착용 시 체력 +2칸 (24HP)");
        tankLore.add("");
        tankLore.add("§c★ 만렙 10 특수 능력:");
        tankLore.add("§7• 흉갑 착용 시 체력 +4칸 (28HP)");
        tankLore.add("");
        tankLore.add("§e클릭하여 선택");
        tankMeta.setLore(tankLore);
        tankItem.setItemMeta(tankMeta);
        gui.setItem(11, tankItem);

        // 검사
        ItemStack warriorItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta warriorMeta = warriorItem.getItemMeta();
        warriorMeta.setDisplayName("§c§l검사 (WARRIOR)");
        List<String> warriorLore = new ArrayList<>();
        warriorLore.add("§7근접 전투에 특화된 직업");
        warriorLore.add("");
        warriorLore.add("§a기본 능력:");
        warriorLore.add("§7• 검 공격력 증가 (레벨당 5%)");
        warriorLore.add("§7• 치명타 확률 증가");
        warriorLore.add("");
        warriorLore.add("§6레벨 5 특수 능력:");
        warriorLore.add("§7• 검 사용 시 공격속도 증가");
        warriorLore.add("");
        warriorLore.add("§c★ 만렙 10 특수 능력:");
        warriorLore.add("§7• 10% 확률로 크리티컬 (2.5배 데미지)");
        warriorLore.add("");
        warriorLore.add("§e클릭하여 선택");
        warriorMeta.setLore(warriorLore);
        warriorItem.setItemMeta(warriorMeta);
        gui.setItem(13, warriorItem);

        // 궁수
        ItemStack archerItem = new ItemStack(Material.BOW);
        ItemMeta archerMeta = archerItem.getItemMeta();
        archerMeta.setDisplayName("§a§l궁수 (ARCHER)");
        List<String> archerLore = new ArrayList<>();
        archerLore.add("§7원거리 전투에 특화된 직업");
        archerLore.add("");
        archerLore.add("§a기본 능력:");
        archerLore.add("§7• 활 공격력 증가 (레벨당 4%)");
        archerLore.add("§7• 화살 절약 확률");
        archerLore.add("");
        archerLore.add("§6레벨 5 특수 능력:");
        archerLore.add("§7• 가죽장화 착용 중 이동속도 +20% (패시브)");
        archerLore.add("");
        archerLore.add("§c★ 만렙 10 특수 능력:");
        archerLore.add("§7• 50% 확률로 화살 3발 발사");
        archerLore.add("");
        archerLore.add("§e클릭하여 선택");
        archerMeta.setLore(archerLore);
        archerItem.setItemMeta(archerMeta);
        gui.setItem(15, archerItem);

        // 정보 아이템
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§e§l새로운 직업 레벨 시스템");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7몬스터를 처치하여 경험치를 획득하세요!");
        infoLore.add("");
        infoLore.add("§a특징:");
        infoLore.add("§7• 최대 레벨 10까지 성장 (만렙)");
        infoLore.add("§7• 레벨업시 능력 강화");
        infoLore.add("§7• 레벨 5에서 특수 능력 해제");
        infoLore.add("§7• §6경험치바 UI로 실시간 확인");
        infoLore.add("");
        infoLore.add("§c★ 만렙 10 특수 효과:");
        infoLore.add("§7• 탱커: 체력 +4칸 (28HP)");
        infoLore.add("§7• 검사: 10% 크리티컬 (2.5배)");
        infoLore.add("§7• 궁수: 50% 화살 3발");
        infoLore.add("");
        infoLore.add("§6주의:");
        infoLore.add("§7• 직업 선택 후 변경 불가");
        infoLore.add("§7• 신중하게 선택하세요!");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(4, infoItem);

        player.openInventory(gui);
    }

    /**
     * 명령어로 직업 선택
     */
    private void selectJobByCommand(Player player, String jobName) {
        JobType currentJob = jobManager.getJobType(player);

        if (currentJob != JobType.NONE) {
            player.sendMessage("§c이미 직업을 선택하셨습니다!");
            return;
        }

        JobType selectedJob = null;
        switch (jobName.toLowerCase()) {
            case "tank":
            case "탱커":
                selectedJob = JobType.TANK;
                break;
            case "warrior":
            case "검사":
                selectedJob = JobType.WARRIOR;
                break;
            case "archer":
            case "궁수":
                selectedJob = JobType.ARCHER;
                break;
            default:
                player.sendMessage("§c올바른 직업명을 입력하세요: tank, warrior, archer");
                return;
        }

        if (jobManager.setJobType(player, selectedJob)) {
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e⚔️ 직업 선택 완료!");
            player.sendMessage("§7선택한 직업: " + selectedJob.getColor() + selectedJob.getDisplayName());
            player.sendMessage("§7레벨: §f1 §7(시작 레벨)");
            player.sendMessage("");
            player.sendMessage("§a이제 몬스터를 처치하여 경험치를 획득하세요!");
            player.sendMessage("§7레벨 5 달성시 특수 능력이 해제됩니다.");
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } else {
            player.sendMessage("§c직업 선택에 실패했습니다.");
        }
    }

    /**
     * 직업 목록 표시
     */
    private void showJobList(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚔️ 직업 목록 및 능력 (업데이트)");
        player.sendMessage("");

        player.sendMessage("§9§l탱커 (TANK)");
        player.sendMessage("§7• 방패 방어시 체력 회복");
        player.sendMessage("§7• 레벨 5: 흉갑 착용시 체력 +2칸");
        player.sendMessage("§c• 만렙 10: 흉갑 착용시 체력 +4칸 (28HP)");
        player.sendMessage("");

        player.sendMessage("§c§l검사 (WARRIOR)");
        player.sendMessage("§7• 검 공격력 증가 (레벨당 5%)");
        player.sendMessage("§7• 레벨 5: 검 사용시 공격속도 증가");
        player.sendMessage("§c• 만렙 10: 10% 확률 크리티컬 (2.5배 데미지)");
        player.sendMessage("");

        player.sendMessage("§a§l궁수 (ARCHER)");
        player.sendMessage("§7• 활 공격력 증가 (레벨당 4%)");
        player.sendMessage("§7• 레벨 5: 가죽장화 착용 중 이동속도 +20% (패시브)");
        player.sendMessage("§c• 만렙 10: 50% 확률로 화살 3발 발사");
        player.sendMessage("");

        player.sendMessage("§6새로운 기능: §f경험치바 UI로 실시간 확인!");
        player.sendMessage("§e직업 선택: §f/job select");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("select", "info", "list");
            if (sender.hasPermission("ggm.job.admin")) {
                subcommands = Arrays.asList("select", "info", "list", "reset", "setlevel", "addexp", "debug");
            }

            for (String subcommand : subcommands) {
                if (subcommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "select":
                    List<String> jobs = Arrays.asList("tank", "warrior", "archer");
                    for (String job : jobs) {
                        if (job.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(job);
                        }
                    }
                    break;
                case "reset":
                case "setlevel":
                case "addexp":
                    if (sender.hasPermission("ggm.job.admin")) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                completions.add(player.getName());
                            }
                        }
                    }
                    break;
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "setlevel":
                    if (sender.hasPermission("ggm.job.admin")) {
                        for (int i = 1; i <= 10; i++) {
                            completions.add(String.valueOf(i));
                        }
                    }
                    break;
                case "addexp":
                    if (sender.hasPermission("ggm.job.admin")) {
                        completions.addAll(Arrays.asList("10", "50", "100", "500", "1000"));
                    }
                    break;
            }
        }

        return completions;
    }
}