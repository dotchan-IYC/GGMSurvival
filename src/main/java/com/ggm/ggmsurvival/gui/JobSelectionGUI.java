package com.ggm.ggmsurvival.gui;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.enums.JobType;
import com.ggm.ggmsurvival.managers.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * 직업 선택 GUI
 * 플레이어가 처음 직업을 선택할 때 사용하는 인터페이스
 */
public class JobSelectionGUI implements Listener {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobSelectionGUI(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();

        // 이벤트 리스너 등록
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 직업 선택 GUI 열기
     */
    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6§l직업 선택");

        // GUI 구성
        setupJobSelectionGUI(gui);

        player.openInventory(gui);
        player.sendMessage("§e원하는 직업을 선택해주세요!");
    }

    /**
     * GUI 구성
     */
    private void setupJobSelectionGUI(Inventory gui) {
        // 배경 채우기
        fillBackground(gui);

        // 직업 아이템들 배치
        gui.setItem(10, createJobItem(JobType.TANK));
        gui.setItem(13, createJobItem(JobType.WARRIOR));
        gui.setItem(16, createJobItem(JobType.ARCHER));

        // 안내 아이템
        gui.setItem(22, createInfoItem());
    }

    /**
     * 직업 아이템 생성
     */
    private ItemStack createJobItem(JobType jobType) {
        Material material = getJobMaterial(jobType);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(jobType.getColor() + "§l" + jobType.getDisplayName());

        List<String> lore = Arrays.asList(
                "",
                "§f" + jobType.getDescription(),
                "",
                "§e주요 능력:"
        );

        // 주요 능력 추가
        String[] abilities = jobType.getMainAbilities();
        for (String ability : abilities) {
            lore.add(ability);
        }

        lore.add("");
        lore.add("§a클릭하여 선택!");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 직업별 아이콘 재료 반환
     */
    private Material getJobMaterial(JobType jobType) {
        switch (jobType) {
            case TANK:
                return Material.SHIELD;
            case WARRIOR:
                return Material.DIAMOND_SWORD;
            case ARCHER:
                return Material.BOW;
            default:
                return Material.BARRIER;
        }
    }

    /**
     * 안내 아이템 생성
     */
    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6§l직업 시스템 안내");
        meta.setLore(Arrays.asList(
                "",
                "§7직업을 선택하면:",
                "§a• 특별한 능력을 얻습니다",
                "§a• 몬스터 처치로 경험치를 얻습니다",
                "§a• 레벨업을 통해 더 강해집니다",
                "",
                "§c주의: 직업은 나중에 변경 가능하지만",
                "§c비용이 많이 듭니다!",
                "",
                "§e신중하게 선택해주세요!"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 배경 채우기
     */
    private void fillBackground(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }

    /**
     * GUI 클릭 이벤트 처리
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (!title.equals("§6§l직업 선택")) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // 직업 선택 처리
        JobType selectedJob = getJobTypeFromItem(clickedItem.getType());

        if (selectedJob != JobType.NONE) {
            handleJobSelection(player, selectedJob);
        }
    }

    /**
     * 아이템으로부터 직업 타입 추출
     */
    private JobType getJobTypeFromItem(Material material) {
        switch (material) {
            case SHIELD:
                return JobType.TANK;
            case DIAMOND_SWORD:
                return JobType.WARRIOR;
            case BOW:
                return JobType.ARCHER;
            default:
                return JobType.NONE;
        }
    }

    /**
     * 직업 선택 처리
     */
    private void handleJobSelection(Player player, JobType jobType) {
        player.closeInventory();

        // 확인 메시지
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l직업 선택 확인");
        player.sendMessage("");
        player.sendMessage("§f선택한 직업: " + jobType.getColoredName());
        player.sendMessage("§f" + jobType.getDescription());
        player.sendMessage("");

        // 주요 능력 표시
        player.sendMessage("§e주요 능력:");
        for (String ability : jobType.getMainAbilities()) {
            player.sendMessage(ability);
        }

        player.sendMessage("");
        player.sendMessage("§c주의: 직업 변경 시 비용이 발생합니다!");
        player.sendMessage("");
        player.sendMessage("§a§l/job confirm §7- 선택 확정");
        player.sendMessage("§c§l/job cancel §7- 취소");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 임시 선택 저장 (실제로는 confirm 명령어로 확정)
        // 여기서는 바로 설정 (간단하게)
        confirmJobSelection(player, jobType);
    }

    /**
     * 직업 선택 확정
     */
    private void confirmJobSelection(Player player, JobType jobType) {
        jobManager.setJobType(player, jobType).thenAccept(success -> {
            if (success) {
                player.sendMessage("§a§l직업 선택 완료!");
                player.sendMessage("§f" + jobType.getColoredName() + " §a직업을 선택했습니다!");
                player.sendMessage("");
                player.sendMessage("§e이제 몬스터를 처치하여 경험치를 획득하세요!");
                player.sendMessage("§a/job info §7- 직업 정보 확인");
                player.sendMessage("§a/job skills §7- 스킬 관리 (새로운 기능!)");

                // 효과음
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            } else {
                player.sendMessage("§c직업 선택 중 오류가 발생했습니다.");
                player.sendMessage("§7다시 시도해주세요.");
            }
        });
    }
}