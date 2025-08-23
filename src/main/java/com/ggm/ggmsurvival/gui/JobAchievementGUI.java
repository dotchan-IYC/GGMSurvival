package com.ggm.ggmsurvival.gui;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.enums.JobType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 직업 업적 시스템 GUI
 * 플레이어의 업적 달성 현황과 보상을 관리
 */
public class JobAchievementGUI {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    // 업적 데이터 (실제로는 데이터베이스에서 관리)
    private final Map<String, Achievement> achievements;

    public JobAchievementGUI(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
        this.achievements = new HashMap<>();
        initializeAchievements();
    }

    /**
     * 업적 GUI 열기
     */
    public void openAchievementGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54,
                "§d§l업적 시스템 §7- " + player.getName());

        setupAchievementGUI(gui, player);
        player.openInventory(gui);
    }

    /**
     * 업적 GUI 구성
     */
    private void setupAchievementGUI(Inventory gui, Player player) {
        // 상단: 업적 진행 상황 (0-8)
        setupProgressSection(gui, player);

        // 중앙: 업적 목록 (9-44)
        setupAchievementList(gui, player);

        // 하단: 네비게이션 (45-53)
        setupAchievementNavigation(gui);

        // 테두리 장식
        fillEmptySlots(gui);
    }

    /**
     * 진행 상황 섹션
     */
    private void setupProgressSection(Inventory gui, Player player) {
        JobType jobType = jobManager.getJobType(player);
        int completedCount = getCompletedAchievements(player);
        int totalCount = achievements.size();
        int achievementScore = getAchievementScore(player);

        // 진행률 표시
        gui.setItem(1, createProgressItem("§a완료된 업적",
                completedCount + " / " + totalCount, Material.LIME_DYE));

        gui.setItem(3, createProgressItem("§6업적 점수",
                achievementScore + " 점", Material.GOLD_INGOT));

        gui.setItem(5, createProgressItem("§d현재 직업",
                jobType.getColor() + jobType.getDisplayName(), getJobMaterial(jobType)));

        gui.setItem(7, createProgressItem("§e완료율",
                String.format("%.1f", (double) completedCount / totalCount * 100) + "%",
                Material.EXPERIENCE_BOTTLE));

        // 진행률 바
        setupProgressBar(gui, completedCount, totalCount);
    }

    /**
     * 업적 목록 섹션
     */
    private void setupAchievementList(Inventory gui, Player player) {
        int slot = 10;

        // 일반 업적
        setupGeneralAchievements(gui, player, slot);

        // 직업별 업적
        JobType jobType = jobManager.getJobType(player);
        if (jobType != JobType.NONE) {
            setupJobSpecificAchievements(gui, player, jobType);
        }
    }

    /**
     * 일반 업적 설정
     */
    private void setupGeneralAchievements(Inventory gui, Player player, int startSlot) {
        int slot = startSlot;

        // 첫 걸음
        gui.setItem(slot++, createAchievementItem(player, "first_job",
                "§a첫 걸음", "직업을 선택하세요",
                Material.COMPASS, AchievementRarity.COMMON, 100));

        // 성장의 시작
        gui.setItem(slot++, createAchievementItem(player, "level_5",
                "§b성장의 시작", "레벨 5에 도달하세요",
                Material.IRON_INGOT, AchievementRarity.COMMON, 250));

        // 숙련자
        gui.setItem(slot++, createAchievementItem(player, "level_10",
                "§6숙련자", "레벨 10에 도달하세요",
                Material.GOLD_INGOT, AchievementRarity.RARE, 500));

        // 몬스터 헌터
        gui.setItem(slot++, createAchievementItem(player, "kill_100",
                "§c몬스터 헌터", "몬스터를 100마리 처치하세요",
                Material.IRON_SWORD, AchievementRarity.COMMON, 200));

        // 학살자
        gui.setItem(slot++, createAchievementItem(player, "kill_1000",
                "§4학살자", "몬스터를 1000마리 처치하세요",
                Material.DIAMOND_SWORD, AchievementRarity.EPIC, 1000));

        // 경험 수집가
        gui.setItem(slot++, createAchievementItem(player, "exp_5000",
                "§e경험 수집가", "5000 경험치를 획득하세요",
                Material.EXPERIENCE_BOTTLE, AchievementRarity.RARE, 300));

        // 불굴의 의지
        gui.setItem(slot++, createAchievementItem(player, "no_death_24h",
                "§9불굴의 의지", "24시간 동안 죽지 않기",
                Material.TOTEM_OF_UNDYING, AchievementRarity.EPIC, 750));
    }

    /**
     * 직업별 업적 설정
     */
    private void setupJobSpecificAchievements(Inventory gui, Player player, JobType jobType) {
        switch (jobType) {
            case TANK -> setupTankAchievements(gui, player);
            case WARRIOR -> setupWarriorAchievements(gui, player);
            case ARCHER -> setupArcherAchievements(gui, player);
        }
    }

    /**
     * 탱커 업적
     */
    private void setupTankAchievements(Inventory gui, Player player) {
        gui.setItem(28, createAchievementItem(player, "shield_master",
                "§9방패의 달인", "방패로 100번 막기",
                Material.SHIELD, AchievementRarity.RARE, 400));

        gui.setItem(29, createAchievementItem(player, "damage_tank",
                "§b데미지 탱커", "한 번에 50 이상 피해 받기",
                Material.IRON_CHESTPLATE, AchievementRarity.COMMON, 200));

        gui.setItem(30, createAchievementItem(player, "ultimate_tank",
                "§6궁극 탱커", "무적 방벽을 10번 사용",
                Material.NETHERITE_CHESTPLATE, AchievementRarity.LEGENDARY, 1500));
    }

    /**
     * 검사 업적
     */
    private void setupWarriorAchievements(Inventory gui, Player player) {
        gui.setItem(28, createAchievementItem(player, "critical_master",
                "§c크리티컬 마스터", "크리티컬 100번 성공",
                Material.DIAMOND_SWORD, AchievementRarity.RARE, 400));

        gui.setItem(29, createAchievementItem(player, "combo_king",
                "§e콤보 킹", "10연속 콤보 달성",
                Material.GOLDEN_SWORD, AchievementRarity.EPIC, 600));

        gui.setItem(30, createAchievementItem(player, "sword_legend",
                "§6검의 전설", "광풍 베기로 10마리 동시 처치",
                Material.NETHERITE_SWORD, AchievementRarity.LEGENDARY, 1500));
    }

    /**
     * 궁수 업적
     */
    private void setupArcherAchievements(Inventory gui, Player player) {
        gui.setItem(28, createAchievementItem(player, "sniper",
                "§e저격수", "50블록 이상에서 헤드샷 10번",
                Material.BOW, AchievementRarity.RARE, 400));

        gui.setItem(29, createAchievementItem(player, "arrow_rain",
                "§a화살비", "관통 화살로 5마리 동시 처치",
                Material.TIPPED_ARROW, AchievementRarity.EPIC, 600));

        gui.setItem(30, createAchievementItem(player, "master_archer",
                "§6명궁", "화살 폭풍으로 20마리 처치",
                Material.CROSSBOW, AchievementRarity.LEGENDARY, 1500));
    }

    /**
     * 네비게이션 설정
     */
    private void setupAchievementNavigation(Inventory gui) {
        // 통계로 돌아가기
        gui.setItem(45, createNavItem(Material.BOOK, "§b통계 보기",
                Arrays.asList("§7상세 통계로 돌아갑니다")));

        // 스킬로 돌아가기
        gui.setItem(47, createNavItem(Material.ENCHANTED_BOOK, "§a스킬 관리",
                Arrays.asList("§7스킬 관리로 돌아갑니다")));

        // 업적 상점
        gui.setItem(49, createNavItem(Material.EMERALD, "§2업적 상점",
                Arrays.asList("§7업적 점수로 아이템을 구매합니다", "§e준비 중...")));

        // 새로고침
        gui.setItem(51, createNavItem(Material.COMPASS, "§e새로고침",
                Arrays.asList("§7업적 진행도를 새로고침합니다")));

        // 닫기
        gui.setItem(53, createNavItem(Material.BARRIER, "§c닫기",
                Arrays.asList("§7GUI를 닫습니다")));
    }

    /**
     * 업적 아이템 생성
     */
    private ItemStack createAchievementItem(Player player, String achievementId,
                                            String name, String description,
                                            Material material, AchievementRarity rarity,
                                            int rewardPoints) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        boolean completed = isAchievementCompleted(player, achievementId);

        // 완료 상태에 따른 이름 설정
        if (completed) {
            meta.setDisplayName(rarity.getColor() + "§l✓ " + name);
        } else {
            meta.setDisplayName("§7" + name);
        }

        // 설명 추가
        meta.setLore(Arrays.asList(
                "§7" + description,
                "",
                "§7등급: " + rarity.getColor() + rarity.getName(),
                "§7보상: §6" + rewardPoints + " 업적 점수",
                "",
                completed ? "§a✓ 완료됨!" : "§c진행 중...",
                getAchievementProgress(player, achievementId)
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 진행률 아이템 생성
     */
    private ItemStack createProgressItem(String name, String value, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList("§f" + value));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 네비게이션 아이템 생성
     */
    private ItemStack createNavItem(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 진행률 바 설정
     */
    private void setupProgressBar(Inventory gui, int completed, int total) {
        double progress = (double) completed / total;
        int filledSlots = (int) (progress * 7);

        for (int i = 0; i < 7; i++) {
            Material material = i < filledSlots ?
                    Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;

            ItemStack progressItem = new ItemStack(material);
            ItemMeta meta = progressItem.getItemMeta();
            meta.setDisplayName(i < filledSlots ? "§a완료" : "§7미완료");
            progressItem.setItemMeta(meta);

            gui.setItem(i + 1, progressItem);
        }
    }

    /**
     * 빈 슬롯 채우기
     */
    private void fillEmptySlots(Inventory gui) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
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
     * 업적 완료 처리
     */
    public void completeAchievement(Player player, String achievementId) {
        if (isAchievementCompleted(player, achievementId)) {
            return; // 이미 완료됨
        }

        Achievement achievement = achievements.get(achievementId);
        if (achievement == null) return;

        // 데이터베이스에 완료 기록
        setAchievementCompleted(player, achievementId, true);

        // 업적 점수 지급
        addAchievementScore(player, achievement.getRewardPoints());

        // 알림 및 효과
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l업적 달성!");
        player.sendMessage("");
        player.sendMessage(achievement.getRarity().getColor() + "§l" + achievement.getName());
        player.sendMessage("§7" + achievement.getDescription());
        player.sendMessage("");
        player.sendMessage("§a보상: §6+" + achievement.getRewardPoints() + " 업적 점수");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 효과음 및 파티클
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendTitle("§6§l업적 달성!", achievement.getRarity().getColor() + achievement.getName(),
                10, 70, 20);
    }

    /**
     * 업적 진행도 확인
     */
    public void checkAchievementProgress(Player player, String achievementType, int currentValue) {
        // 업적 진행도 체크 로직
        switch (achievementType) {
            case "level":
                if (currentValue >= 5) checkAndComplete(player, "level_5");
                if (currentValue >= 10) checkAndComplete(player, "level_10");
                break;
            case "monster_kill":
                if (currentValue >= 100) checkAndComplete(player, "kill_100");
                if (currentValue >= 1000) checkAndComplete(player, "kill_1000");
                break;
            case "experience":
                if (currentValue >= 5000) checkAndComplete(player, "exp_5000");
                break;
        }
    }

    /**
     * 업적 체크 및 완료
     */
    private void checkAndComplete(Player player, String achievementId) {
        if (!isAchievementCompleted(player, achievementId)) {
            completeAchievement(player, achievementId);
        }
    }

    // === 업적 데이터 관리 메소드들 ===

    /**
     * 업적 초기화
     */
    private void initializeAchievements() {
        // 일반 업적
        achievements.put("first_job", new Achievement("first_job", "첫 걸음",
                "직업을 선택하세요", AchievementRarity.COMMON, 100));
        achievements.put("level_5", new Achievement("level_5", "성장의 시작",
                "레벨 5에 도달하세요", AchievementRarity.COMMON, 250));
        achievements.put("level_10", new Achievement("level_10", "숙련자",
                "레벨 10에 도달하세요", AchievementRarity.RARE, 500));
        achievements.put("kill_100", new Achievement("kill_100", "몬스터 헌터",
                "몬스터를 100마리 처치하세요", AchievementRarity.COMMON, 200));
        achievements.put("kill_1000", new Achievement("kill_1000", "학살자",
                "몬스터를 1000마리 처치하세요", AchievementRarity.EPIC, 1000));
        achievements.put("exp_5000", new Achievement("exp_5000", "경험 수집가",
                "5000 경험치를 획득하세요", AchievementRarity.RARE, 300));

        // 직업별 업적 추가...
    }

    /**
     * 업적 완료 여부 확인 (임시 구현)
     */
    private boolean isAchievementCompleted(Player player, String achievementId) {
        // TODO: 데이터베이스에서 조회
        return false;
    }

    /**
     * 업적 완료 설정 (임시 구현)
     */
    private void setAchievementCompleted(Player player, String achievementId, boolean completed) {
        // TODO: 데이터베이스에 저장
    }

    /**
     * 업적 진행도 반환 (임시 구현)
     */
    private String getAchievementProgress(Player player, String achievementId) {
        // TODO: 실제 진행도 계산
        return "§7진행도: §f50/100";
    }

    /**
     * 완료된 업적 수 반환 (임시 구현)
     */
    private int getCompletedAchievements(Player player) {
        // TODO: 데이터베이스에서 조회
        return 15;
    }

    /**
     * 업적 점수 반환 (임시 구현)
     */
    private int getAchievementScore(Player player) {
        // TODO: 데이터베이스에서 조회
        return 3450;
    }

    /**
     * 업적 점수 추가 (임시 구현)
     */
    private void addAchievementScore(Player player, int points) {
        // TODO: 데이터베이스에 저장
    }

    /**
     * 직업별 아이콘 반환
     */
    private Material getJobMaterial(JobType jobType) {
        return switch (jobType) {
            case TANK -> Material.SHIELD;
            case WARRIOR -> Material.DIAMOND_SWORD;
            case ARCHER -> Material.BOW;
            default -> Material.BARRIER;
        };
    }

    // === 내부 클래스들 ===

    /**
     * 업적 데이터 클래스
     */
    private static class Achievement {
        private final String id;
        private final String name;
        private final String description;
        private final AchievementRarity rarity;
        private final int rewardPoints;

        public Achievement(String id, String name, String description,
                           AchievementRarity rarity, int rewardPoints) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.rarity = rarity;
            this.rewardPoints = rewardPoints;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public AchievementRarity getRarity() { return rarity; }
        public int getRewardPoints() { return rewardPoints; }
    }

    /**
     * 업적 등급 열거형
     */
    public enum AchievementRarity {
        COMMON("§f일반", "§f"),
        RARE("§9레어", "§9"),
        EPIC("§5에픽", "§5"),
        LEGENDARY("§6전설", "§6");

        private final String name;
        private final String color;

        AchievementRarity(String name, String color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public String getColor() { return color; }
    }
}