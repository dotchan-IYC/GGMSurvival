// 완전 안정화된 JobGUIListener.java
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.managers.JobManager.JobType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/**
 * 완전 안정화된 직업 GUI 리스너
 * - 직업 선택 GUI 처리
 * - 강력한 예외 처리
 * - 무효한 클릭 방지
 */
public class JobGUIListener implements Listener {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobGUIListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        // 기본 검증
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.isShuttingDown()) return;
        if (jobManager == null) return;

        try {
            String title = event.getView().getTitle();

            // 직업 선택 GUI인지 확인
            if (!"§6§l직업 선택".equals(title)) return;

            // 모든 클릭 취소
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // 이미 직업이 있는지 확인
            JobType currentJob = jobManager.getJobType(player);
            if (currentJob != JobType.NONE) {
                player.closeInventory();
                player.sendMessage("§c이미 " + currentJob.getColor() + currentJob.getDisplayName() +
                        " §c직업을 선택하셨습니다!");
                return;
            }

            // 클릭된 아이템에 따른 직업 선택
            JobType selectedJob = getJobFromItem(clickedItem);
            if (selectedJob == null || selectedJob == JobType.NONE) return;

            // 직업 선택 실행
            if (jobManager.setJobType(player, selectedJob)) {
                player.closeInventory();

                // 성공 메시지
                String message = plugin.getConfig().getString("messages.job_selected",
                                "{job} 직업을 선택하셨습니다! 몬스터를 처치하여 레벨을 올리세요.")
                        .replace("{job}", selectedJob.getColor() + selectedJob.getDisplayName() + "§a");

                player.sendMessage("§a" + message);
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // 환영 메시지
                showJobWelcomeMessage(player, selectedJob);

                plugin.getLogger().info(String.format("[직업선택] %s이(가) %s 직업을 선택했습니다.",
                        player.getName(), selectedJob.getDisplayName()));

            } else {
                player.closeInventory();
                player.sendMessage("§c직업 선택에 실패했습니다. 이미 직업이 있거나 오류가 발생했습니다.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 GUI 처리 중 오류: " + player.getName(), e);

            player.closeInventory();
            player.sendMessage("§c직업 선택 중 오류가 발생했습니다. 명령어를 사용해주세요: /job select");
        }
    }

    /**
     * 아이템으로부터 직업 타입 추출
     */
    private JobType getJobFromItem(ItemStack item) {
        switch (item.getType()) {
            case IRON_CHESTPLATE:
                return JobType.TANK;
            case IRON_SWORD:
                return JobType.WARRIOR;
            case BOW:
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
}