package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.gui.JobSkillGUI;
import com.ggm.ggmsurvival.gui.JobStatsGUI;
import com.ggm.ggmsurvival.gui.JobAchievementGUI;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.managers.EconomyManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 직업 스킬 GUI 이벤트 처리 리스너
 */
public class JobSkillGUIListener implements Listener {

    private final GGMSurvival plugin;
    private final JobManager jobManager;
    private final EconomyManager economyManager;
    private final JobSkillGUI skillGUI;
    private final JobStatsGUI statsGUI;
    private final JobAchievementGUI achievementGUI;

    public JobSkillGUIListener(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
        this.economyManager = plugin.getEconomyManager();
        this.skillGUI = new JobSkillGUI(plugin);
        this.statsGUI = new JobStatsGUI(plugin);
        this.achievementGUI = new JobAchievementGUI(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // 스킬 관리 GUI 처리
        if (title.contains("스킬 관리")) {
            event.setCancelled(true);
            handleSkillGUIClick(player, event);
            return;
        }

        // 통계 GUI 처리
        if (title.contains("통계")) {
            event.setCancelled(true);
            handleStatsGUIClick(player, event);
            return;
        }

        // 업적 GUI 처리
        if (title.contains("업적")) {
            event.setCancelled(true);
            handleAchievementGUIClick(player, event);
            return;
        }
    }

    /**
     * 스킬 관리 GUI 클릭 처리
     */
    private void handleSkillGUIClick(Player player, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        Material material = clickedItem.getType();
        String displayName = clickedItem.getItemMeta().getDisplayName();

        switch (material) {
            case PLAYER_HEAD -> {
                // 개인 통계 보기
                player.closeInventory();
                statsGUI.openStatsGUI(player);
                player.sendMessage("§a개인 통계를 확인합니다...");
            }

            case GOLD_INGOT -> {
                // 서버 랭킹 보기
                player.closeInventory();
                showServerRanking(player);
            }

            case EXPERIENCE_BOTTLE -> {
                // 업적 시스템 보기
                player.closeInventory();
                achievementGUI.openAchievementGUI(player);
                player.sendMessage("§d업적 목록을 확인합니다...");
            }

            case REDSTONE -> {
                // 스킬 리셋
                player.closeInventory();
                handleSkillReset(player);
            }

            case ENDER_EYE -> {
                // 직업 변경
                player.closeInventory();
                handleJobChange(player);
            }

            case BOOK -> {
                // 도움말
                player.closeInventory();
                showJobHelp(player);
            }

            case BARRIER -> {
                // GUI 닫기
                player.closeInventory();
                player.sendMessage("§7스킬 관리를 종료합니다.");
            }

            default -> {
                // 스킬 아이템 클릭 (정보 표시)
                if (isSkillItem(material)) {
                    showSkillDetails(player, clickedItem);
                }
            }
        }
    }

    /**
     * 통계 GUI 클릭 처리
     */
    private void handleStatsGUIClick(Player player, InventoryClickEvent event) {
        // 통계 GUI의 클릭 이벤트 처리
        // JobStatsGUI에서 구현될 예정
    }

    /**
     * 업적 GUI 클릭 처리
     */
    private void handleAchievementGUIClick(Player player, InventoryClickEvent event) {
        // 업적 GUI의 클릭 이벤트 처리
        // JobAchievementGUI에서 구현될 예정
    }

    /**
     * 스킬 리셋 처리
     */
    private void handleSkillReset(Player player) {
        long resetCost = 50000L;

        economyManager.getBalance(player.getUniqueId()).thenAccept(balance -> {
            if (balance < resetCost) {
                player.sendMessage("§c스킬 리셋 비용이 부족합니다!");
                player.sendMessage("§7필요 금액: §c" + economyManager.formatMoney(resetCost) + "G");
                player.sendMessage("§7보유 금액: §f" + economyManager.formatMoney(balance) + "G");
                return;
            }

            // 확인 GUI 표시
            showSkillResetConfirmation(player);
        });
    }

    /**
     * 직업 변경 처리
     */
    private void handleJobChange(Player player) {
        long changeCost = 100000L;

        economyManager.getBalance(player.getUniqueId()).thenAccept(balance -> {
            if (balance < changeCost) {
                player.sendMessage("§c직업 변경 비용이 부족합니다!");
                player.sendMessage("§7필요 금액: §c" + economyManager.formatMoney(changeCost) + "G");
                player.sendMessage("§7보유 금액: §f" + economyManager.formatMoney(balance) + "G");
                return;
            }

            // 확인 GUI 표시
            showJobChangeConfirmation(player);
        });
    }

    /**
     * 서버 랭킹 표시
     */
    private void showServerRanking(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l서버 직업 랭킹 TOP 10");
        player.sendMessage("");

        // TODO: 실제 랭킹 데이터베이스에서 조회
        player.sendMessage("§71위 §f플레이어1 §7- §c검사 §7레벨 10");
        player.sendMessage("§72위 §f플레이어2 §7- §9탱커 §7레벨 9");
        player.sendMessage("§73위 §f플레이어3 §7- §e궁수 §7레벨 9");
        player.sendMessage("§74위 §f" + player.getName() + " §7- §a계산 중...");
        player.sendMessage("");
        player.sendMessage("§a자세한 랭킹은 추후 업데이트 예정입니다!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 스킬 상세 정보 표시
     */
    private void showSkillDetails(Player player, ItemStack skillItem) {
        String skillName = skillItem.getItemMeta().getDisplayName();

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l스킬 상세 정보");
        player.sendMessage("");
        player.sendMessage("§f스킬명: " + skillName);

        // 스킬 아이템의 설명을 그대로 표시
        if (skillItem.getItemMeta().hasLore()) {
            for (String lore : skillItem.getItemMeta().getLore()) {
                player.sendMessage(lore);
            }
        }

        player.sendMessage("");
        player.sendMessage("§7팁: GUI에서 더 자세한 정보를 확인하세요!");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 스킬 리셋 확인 GUI
     */
    private void showSkillResetConfirmation(Player player) {
        // 간단한 확인 메시지로 대체 (실제로는 별도 GUI 구현 가능)
        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§c§l스킬 리셋 확인");
        player.sendMessage("");
        player.sendMessage("§7스킬 리셋을 진행하시겠습니까?");
        player.sendMessage("§c주의: 모든 스킬 진행도가 초기화됩니다!");
        player.sendMessage("§7비용: §c50,000G");
        player.sendMessage("");
        player.sendMessage("§a/job reset confirm §7- 리셋 진행");
        player.sendMessage("§c/job reset cancel §7- 리셋 취소");
        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 직업 변경 확인 GUI
     */
    private void showJobChangeConfirmation(Player player) {
        player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§5§l직업 변경 확인");
        player.sendMessage("");
        player.sendMessage("§7직업 변경을 진행하시겠습니까?");
        player.sendMessage("§c주의: 모든 직업 진행도가 초기화됩니다!");
        player.sendMessage("§7비용: §c100,000G");
        player.sendMessage("");
        player.sendMessage("§a/job change confirm §7- 변경 진행");
        player.sendMessage("§c/job change cancel §7- 변경 취소");
        player.sendMessage("§5━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 직업 시스템 도움말
     */
    private void showJobHelp(Player player) {
        player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l직업 시스템 도움말");
        player.sendMessage("");
        player.sendMessage("§6§l기본 사용법:");
        player.sendMessage("§7• §a/job select §7- 직업 선택");
        player.sendMessage("§7• §a/job skills §7- 스킬 관리 GUI");
        player.sendMessage("§7• §a/job info §7- 내 정보 확인");
        player.sendMessage("§7• §a/job stats §7- 상세 통계");
        player.sendMessage("");
        player.sendMessage("§6§l성장 방법:");
        player.sendMessage("§7• 몬스터를 처치하여 경험치 획득");
        player.sendMessage("§7• 레벨업을 통해 새로운 능력 해금");
        player.sendMessage("§7• 레벨 5부터 특수 능력 활성화");
        player.sendMessage("§7• 레벨 10 달성 시 궁극기 해금");
        player.sendMessage("");
        player.sendMessage("§6§l직업별 특징:");
        player.sendMessage("§9탱커 §7- 방어력과 체력 특화");
        player.sendMessage("§c검사 §7- 근접 공격력 특화");
        player.sendMessage("§e궁수 §7- 원거리 공격과 기동성 특화");
        player.sendMessage("");
        player.sendMessage("§7더 궁금한 점이 있다면 관리자에게 문의하세요!");
        player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 스킬 아이템인지 확인
     */
    private boolean isSkillItem(Material material) {
        return material == Material.IRON_CHESTPLATE ||
                material == Material.SHIELD ||
                material == Material.TOTEM_OF_UNDYING ||
                material == Material.HORN_CORAL ||
                material == Material.NETHERITE_CHESTPLATE ||
                material == Material.IRON_SWORD ||
                material == Material.GOLDEN_SWORD ||
                material == Material.DIAMOND_SWORD ||
                material == Material.FEATHER ||
                material == Material.NETHERITE_SWORD ||
                material == Material.BOW ||
                material == Material.LEATHER_BOOTS ||
                material == Material.SPECTRAL_ARROW ||
                material == Material.TIPPED_ARROW ||
                material == Material.CROSSBOW;
    }
}