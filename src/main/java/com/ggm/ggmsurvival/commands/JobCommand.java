// 수정된 JobCommand.java - CompletableFuture 문제 해결
package com.ggm.ggmsurvival.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class JobCommand implements CommandExecutor {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();

        plugin.getLogger().info("JobCommand 초기화 완료 - JobManager: " + (jobManager != null ? "OK" : "NULL"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        // JobManager 확인
        if (jobManager == null) {
            player.sendMessage("§c직업 시스템이 초기화되지 않았습니다!");
            plugin.getLogger().severe("JobManager가 null입니다!");
            return true;
        }

        if (args.length == 0) {
            showCurrentJobSafe(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        plugin.getLogger().info("JobCommand 실행: " + subCommand + " by " + player.getName());

        switch (subCommand) {
            case "select":
            case "선택":
                openJobSelectionFixed(player);
                break;
            case "info":
            case "정보":
                showDetailedJobInfo(player);
                break;
            case "list":
            case "목록":
                showJobList(player);
                break;
            case "reset":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /job reset <플레이어>");
                    return true;
                }
                resetPlayerJob(player, args[1]);
                break;
            case "help":
            case "도움말":
                sendHelp(player);
                break;
            case "debug":
                showDebugInfo(player);
                break;
            case "testgui":
                openTestGUI(player);
                break;
            default:
                showCurrentJobSafe(player);
                break;
        }

        return true;
    }

    /**
     * 수정된 직업 선택 - CompletableFuture 문제 해결
     */
    private void openJobSelectionFixed(Player player) {
        try {
            player.sendMessage("§e직업 선택을 처리하는 중...");
            plugin.getLogger().info("직업 선택 처리 시작: " + player.getName());

            // CompletableFuture 대신 직접 GUI 열기
            player.sendMessage("§a직업 선택 GUI를 엽니다!");
            openJobSelectionGUI(player);

        } catch (Exception e) {
            player.sendMessage("§c직업 선택 처리 중 오류 발생: " + e.getMessage());
            plugin.getLogger().severe("직업 선택 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 직접 구현한 직업 선택 GUI
     */
    private void openJobSelectionGUI(Player player) {
        try {
            plugin.getLogger().info("GUI 생성 시작: " + player.getName());

            // 27칸 인벤토리 생성
            Inventory gui = Bukkit.createInventory(null, 27, "§6§l직업 선택");

            // 탱커 아이템
            ItemStack tankItem = createJobItem(
                    Material.IRON_CHESTPLATE,
                    "§c§l탱커",
                    Arrays.asList(
                            "§7방어와 체력에 특화된 근접 전투 직업",
                            "",
                            "§a§l효과:",
                            "§7• 흉갑 착용 시 체력 +2하트",
                            "§7• 방패 사용 시 체력 0.5하트 회복",
                            "§7• 받는 피해 10% 감소",
                            "",
                            "§e클릭하여 선택!"
                    )
            );

            // 검사 아이템
            ItemStack warriorItem = createJobItem(
                    Material.IRON_SWORD,
                    "§6§l검사",
                    Arrays.asList(
                            "§7검술에 특화된 공격적인 근접 전투 직업",
                            "",
                            "§a§l효과:",
                            "§7• 검 공격력 +20%",
                            "§7• 치명타 확률 10%",
                            "§7• 검 내구도 소모 확률 15% 감소",
                            "",
                            "§e클릭하여 선택!"
                    )
            );

            // 궁수 아이템
            ItemStack archerItem = createJobItem(
                    Material.BOW,
                    "§a§l궁수",
                    Arrays.asList(
                            "§7원거리 공격과 기동성에 특화된 직업",
                            "",
                            "§a§l효과:",
                            "§7• 활 공격력 +15%",
                            "§7• 가죽부츠 착용 시 이동속도 증가",
                            "§7• 화살 소모 확률 20% 감소",
                            "",
                            "§e클릭하여 선택!"
                    )
            );

            // 취소 버튼
            ItemStack cancelItem = createJobItem(
                    Material.BARRIER,
                    "§c§l취소",
                    Arrays.asList("§7직업 선택을 취소합니다")
            );

            // GUI에 아이템 배치
            gui.setItem(11, tankItem);
            gui.setItem(13, warriorItem);
            gui.setItem(15, archerItem);
            gui.setItem(22, cancelItem);

            // GUI 열기
            player.openInventory(gui);
            player.sendMessage("§a직업을 선택해주세요!");

            plugin.getLogger().info("GUI 열기 성공: " + player.getName());

        } catch (Exception e) {
            player.sendMessage("§cGUI 생성 중 오류 발생: " + e.getMessage());
            plugin.getLogger().severe("GUI 생성 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 안전한 아이템 생성
     */
    private ItemStack createJobItem(Material material, String name, java.util.List<String> lore) {
        try {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(lore);
                item.setItemMeta(meta);
            } else {
                plugin.getLogger().warning("ItemMeta가 null입니다: " + material.name());
            }

            return item;
        } catch (Exception e) {
            plugin.getLogger().severe("아이템 생성 실패: " + material.name() + " - " + e.getMessage());
            return new ItemStack(Material.STONE);
        }
    }

    /**
     * 안전한 현재 직업 표시
     */
    private void showCurrentJobSafe(Player player) {
        try {
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e§l⚔️ 직업 정보");
            player.sendMessage("");
            player.sendMessage("§c현재 직업이 없습니다!");
            player.sendMessage("");
            player.sendMessage("§7/job select 명령어로 직업을 선택하세요.");
            player.sendMessage("");
            player.sendMessage("§a§l💡 직업 선택의 이점:");
            player.sendMessage("§7• 각 직업별 특수 능력 획득");
            player.sendMessage("§7• 전투/채굴/탐험에서 보너스");
            player.sendMessage("§7• 야생 서버만의 특별한 경험!");
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            player.sendMessage("§c직업 정보 표시 중 오류: " + e.getMessage());
            plugin.getLogger().severe("showCurrentJobSafe 오류: " + e.getMessage());
        }
    }

    /**
     * 상세 직업 정보 표시
     */
    private void showDetailedJobInfo(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l야생 서버 직업 안내");
        player.sendMessage("");

        player.sendMessage("§c§l🛡 탱커 (TANK)");
        player.sendMessage("§7방어와 체력에 특화된 근접 전투 직업");
        player.sendMessage("§7효과: 흉갑 착용 시 체력 증가, 방패로 체력 회복");
        player.sendMessage("§7추천: 몬스터와 정면 대결을 즐기는 플레이어");
        player.sendMessage("");

        player.sendMessage("§6§l⚔ 검사 (WARRIOR)");
        player.sendMessage("§7검술에 특화된 공격적인 근접 전투 직업");
        player.sendMessage("§7효과: 검 공격력 증가, 치명타 확률 증가");
        player.sendMessage("§7추천: 높은 데미지로 빠르게 적을 처치하고 싶은 플레이어");
        player.sendMessage("");

        player.sendMessage("§a§l🏹 궁수 (ARCHER)");
        player.sendMessage("§7원거리 공격과 기동성에 특화된 직업");
        player.sendMessage("§7효과: 활 공격력 증가, 가죽부츠 착용 시 이동속도 증가");
        player.sendMessage("§7추천: 안전한 거리에서 전투하고 빠른 이동을 원하는 플레이어");
        player.sendMessage("");

        player.sendMessage("§e§l⚠️ 중요한 안내:");
        player.sendMessage("§7• 직업은 §c한 번 선택하면 변경할 수 없습니다!");
        player.sendMessage("§7• 각 직업은 고유한 플레이 스타일을 제공합니다");
        player.sendMessage("§7• 신중하게 선택하시기 바랍니다");

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 직업 목록 표시
     */
    private void showJobList(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l사용 가능한 직업");
        player.sendMessage("");
        player.sendMessage("§c탱커 §7- 방어형 근접 전투 직업");
        player.sendMessage("§6검사 §7- 공격형 근접 전투 직업");
        player.sendMessage("§a궁수 §7- 원거리 전투 직업");
        player.sendMessage("");
        player.sendMessage("§7자세한 정보: §e/job info");
        player.sendMessage("§7직업 선택: §e/job select");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 플레이어 직업 초기화 (OP 전용)
     */
    private void resetPlayerJob(Player sender, String targetName) {
        try {
            Player target = plugin.getServer().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
                return;
            }

            // 직접 데이터베이스 업데이트 시도
            sender.sendMessage("§e" + targetName + "의 직업을 초기화하는 중...");

            // 간단한 방법: JobManager 통해 NONE 설정
            jobManager.setPlayerJob(target.getUniqueId(), target.getName(), JobManager.JobType.NONE)
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage("§a" + targetName + "의 직업을 초기화했습니다.");
                            target.sendMessage("§e관리자에 의해 직업이 초기화되었습니다.");
                            target.sendMessage("§7다시 /job select 명령어로 직업을 선택할 수 있습니다.");
                            plugin.getLogger().info(sender.getName() + "이(가) " + targetName + "의 직업을 초기화했습니다.");
                        } else {
                            sender.sendMessage("§c직업 초기화에 실패했습니다.");
                        }
                    })
                    .exceptionally(throwable -> {
                        sender.sendMessage("§c직업 초기화 중 오류: " + throwable.getMessage());
                        plugin.getLogger().severe("직업 초기화 오류: " + throwable.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            sender.sendMessage("§c직업 초기화 중 예외 발생: " + e.getMessage());
            plugin.getLogger().severe("resetPlayerJob 예외: " + e.getMessage());
        }
    }

    /**
     * 디버그 정보 표시
     */
    private void showDebugInfo(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🔧 직업 시스템 디버그 정보");
        player.sendMessage("");
        player.sendMessage("§7Plugin: " + (plugin != null ? "§aOK" : "§cNULL"));
        player.sendMessage("§7JobManager: " + (jobManager != null ? "§aOK" : "§cNULL"));
        player.sendMessage("§7Player UUID: " + player.getUniqueId());
        player.sendMessage("§7Player Name: " + player.getName());
        player.sendMessage("");
        player.sendMessage("§7테스트 명령어:");
        player.sendMessage("§e/job testgui §7- 간단한 GUI 테스트");
        player.sendMessage("§e/job select §7- 직업 선택 (수정됨)");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 테스트용 간단한 GUI
     */
    private void openTestGUI(Player player) {
        try {
            Inventory testGui = Bukkit.createInventory(null, 9, "§aTest GUI");
            testGui.setItem(4, new ItemStack(Material.DIAMOND));
            player.openInventory(testGui);
            player.sendMessage("§a테스트 GUI 열기 성공!");
            plugin.getLogger().info("테스트 GUI 성공: " + player.getName());
        } catch (Exception e) {
            player.sendMessage("§c테스트 GUI 실패: " + e.getMessage());
            plugin.getLogger().severe("테스트 GUI 실패: " + e.getMessage());
        }
    }

    /**
     * 도움말 표시
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l직업 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§7/job §f- 현재 직업 정보 확인");
        player.sendMessage("§7/job select §f- 직업 선택 GUI 열기");
        player.sendMessage("§7/job info §f- 모든 직업 상세 정보");
        player.sendMessage("§7/job list §f- 직업 목록 확인");
        player.sendMessage("§7/job debug §f- 디버그 정보 확인");
        player.sendMessage("§7/job testgui §f- 테스트 GUI 열기");

        if (player.hasPermission("ggm.job.admin")) {
            player.sendMessage("");
            player.sendMessage("§c관리자 명령어:");
            player.sendMessage("§7/job reset <플레이어> §f- 직업 초기화");
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}