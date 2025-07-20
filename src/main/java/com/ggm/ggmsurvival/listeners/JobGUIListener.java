// 직업 선택 GUI 이벤트 처리
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.managers.JobManager.JobType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class JobGUIListener implements Listener {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobGUIListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // 직업 선택 GUI가 아니면 무시
        if (!title.contains("직업 선택")) return;

        event.setCancelled(true); // 아이템 이동 방지

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // 이미 직업이 있는지 확인
        if (jobManager.getJobType(player) != JobType.NONE) {
            player.closeInventory();
            player.sendMessage("§c이미 직업을 선택하셨습니다!");
            return;
        }

        JobType selectedJob = null;
        String jobDescription = "";

        // 클릭한 아이템에 따라 직업 결정
        switch (clickedItem.getType()) {
            case SHIELD:
                selectedJob = JobType.TANK;
                jobDescription = "§9탱커 §7- 방어와 생존의 전문가";
                break;

            case DIAMOND_SWORD:
                selectedJob = JobType.WARRIOR;
                jobDescription = "§c검사 §7- 근접 전투의 달인";
                break;

            case BOW:
                selectedJob = JobType.ARCHER;
                jobDescription = "§a궁수 §7- 원거리 공격의 명수";
                break;

            default:
                return; // 다른 아이템 클릭 시 무시
        }

        // 직업 선택 처리
        if (selectedJob != null && jobManager.setJobType(player, selectedJob)) {
            player.closeInventory();

            // 성공 메시지
            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§e⚔️ 직업 선택 완료!");
            player.sendMessage("§7선택한 직업: " + jobDescription);
            player.sendMessage("§7레벨: §f1 §7(시작 레벨)");
            player.sendMessage("");
            player.sendMessage("§a§l새로운 모험이 시작됩니다!");
            player.sendMessage("§7몬스터를 처치하여 경험치를 획득하세요.");
            player.sendMessage("§7레벨업할 때마다 능력이 강화됩니다.");
            player.sendMessage("§6레벨 5 달성시 특수 능력이 해제됩니다!");
            player.sendMessage("");

            // 직업별 특징 안내
            switch (selectedJob) {
                case TANK:
                    player.sendMessage("§9§l탱커 특징:");
                    player.sendMessage("§7• 방패로 공격을 막으면 체력 회복");
                    player.sendMessage("§7• 레벨 5: 흉갑 착용시 체력 +2칸");
                    break;
                case WARRIOR:
                    player.sendMessage("§c§l검사 특징:");
                    player.sendMessage("§7• 검 공격력이 레벨당 5% 증가");
                    player.sendMessage("§7• 레벨 5: 검 사용시 공격속도 증가");
                    break;
                case ARCHER:
                    player.sendMessage("§a§l궁수 특징:");
                    player.sendMessage("§7• 활 공격력이 레벨당 4% 증가");
                    player.sendMessage("§7• 레벨 5: 가죽장화 착용시 이동속도 +20%");
                    break;
            }

            player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 성공 효과
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.sendTitle("§6직업 선택 완료!", jobDescription, 10, 40, 10);

            // 로그
            plugin.getLogger().info(String.format("[직업선택] %s: %s 선택",
                    player.getName(), selectedJob.getDisplayName()));

        } else {
            player.closeInventory();
            player.sendMessage("§c직업 선택에 실패했습니다. 다시 시도해주세요.");
        }
    }
}