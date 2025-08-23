package com.ggm.ggmsurvival.gui;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.enums.JobType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * 직업 스킬 관리 GUI
 * 플레이어의 직업 능력, 스킬, 진행도를 한눈에 볼 수 있는 UI
 */
public class JobSkillGUI {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobSkillGUI(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    /**
     * 메인 스킬 GUI 열기
     */
    public void openSkillGUI(Player player) {
        JobType jobType = jobManager.getJobType(player);
        int jobLevel = jobManager.getJobLevel(player);
        int jobExp = jobManager.getJobExperience(player);

        if (jobType == JobType.NONE) {
            player.sendMessage("§c먼저 직업을 선택해주세요! /job select");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54,
                jobType.getColor() + "§l" + jobType.getDisplayName() + " 스킬 관리");

        // GUI 레이아웃 설정
        setupGUILayout(gui, player, jobType, jobLevel, jobExp);

        player.openInventory(gui);
    }

    /**
     * GUI 레이아웃 구성
     */
    private void setupGUILayout(Inventory gui, Player player, JobType jobType, int jobLevel, int jobExp) {
        // 상단: 직업 정보 (0-8)
        setupJobInfoSection(gui, player, jobType, jobLevel, jobExp);

        // 중앙: 스킬 트리 (9-35)
        setupSkillTreeSection(gui, player, jobType, jobLevel);

        // 하단: 통계 및 기능 (36-53)
        setupStatsSection(gui, player, jobType);

        // 테두리 장식
        setupBorderDecoration(gui);
    }

    /**
     * 직업 정보 섹션 (상단)
     */
    private void setupJobInfoSection(Inventory gui, Player player, JobType jobType, int jobLevel, int jobExp) {
        // 직업 아이콘 (슬롯 4)
        ItemStack jobIcon = createJobIcon(jobType, jobLevel, jobExp);
        gui.setItem(4, jobIcon);

        // 경험치 진행바 (슬롯 1-7)
        setupExpProgressBar(gui, jobLevel, jobExp);

        // 현재 능력치 (슬롯 0, 8)
        gui.setItem(0, createCurrentStatsItem(player, jobType, jobLevel));
        gui.setItem(8, createNextLevelPreview(jobType, jobLevel));
    }

    /**
     * 스킬 트리 섹션 (중앙)
     */
    private void setupSkillTreeSection(Inventory gui, Player player, JobType jobType, int jobLevel) {
        switch (jobType) {
            case TANK:
                setupTankSkillTree(gui, jobLevel);
                break;
            case WARRIOR:
                setupWarriorSkillTree(gui, jobLevel);
                break;
            case ARCHER:
                setupArcherSkillTree(gui, jobLevel);
                break;
        }
    }

    /**
     * 탱커 스킬 트리 구성
     */
    private void setupTankSkillTree(Inventory gui, int jobLevel) {
        // 레벨 1-2: 기본 능력
        gui.setItem(10, createSkillItem(Material.IRON_CHESTPLATE, "§9기본 방어력",
                Arrays.asList("§7탱커의 기본 능력", "§a흉갑 착용 시 체력 증가", "§7레벨 1부터 사용 가능"),
                1, jobLevel, true));

        // 레벨 3-4: 방패 능력
        gui.setItem(12, createSkillItem(Material.SHIELD, "§9방패 회복",
                Arrays.asList("§7방패로 공격을 막을 때", "§a체력 0.5 회복", "§7레벨 3부터 사용 가능"),
                3, jobLevel, jobLevel >= 3));

        // 레벨 5: 특수 능력 해금
        gui.setItem(14, createSkillItem(Material.TOTEM_OF_UNDYING, "§6불굴의 의지",
                Arrays.asList("§7레벨 5 특수 능력", "§a체력이 20% 이하일 때", "§a받는 피해 50% 감소", "§7쿨다운: 60초"),
                5, jobLevel, jobLevel >= 5));

        // 레벨 7: 도발 능력
        gui.setItem(16, createSkillItem(Material.HORN_CORAL, "§9도발",
                Arrays.asList("§7주변 몬스터들을 자신에게", "§a강제로 어그로 집중", "§7범위: 10블록", "§7쿨다운: 30초"),
                7, jobLevel, jobLevel >= 7));

        // 레벨 10: 궁극기
        gui.setItem(22, createSkillItem(Material.NETHERITE_CHESTPLATE, "§6무적 방벽",
                Arrays.asList("§7레벨 10 궁극기", "§a10초간 모든 피해 무효", "§c1일 1회 사용 가능", "§7최강의 방어 능력"),
                10, jobLevel, jobLevel >= 10));
    }

    /**
     * 검사 스킬 트리 구성
     */
    private void setupWarriorSkillTree(Inventory gui, int jobLevel) {
        // 레벨 1-2: 기본 능력
        gui.setItem(10, createSkillItem(Material.IRON_SWORD, "§c검술 숙련",
                Arrays.asList("§7검사의 기본 능력", "§a검 사용 시 공격력 증가", "§7레벨 1부터 사용 가능"),
                1, jobLevel, true));

        // 레벨 3-4: 콤보 시스템
        gui.setItem(12, createSkillItem(Material.GOLDEN_SWORD, "§c연속 베기",
                Arrays.asList("§7연속 공격 시 데미지 증가", "§a최대 3회까지 스택", "§7레벨 3부터 사용 가능"),
                3, jobLevel, jobLevel >= 3));

        // 레벨 5: 특수 능력
        gui.setItem(14, createSkillItem(Material.DIAMOND_SWORD, "§6치명타 숙련",
                Arrays.asList("§7레벨 5 특수 능력", "§a크리티컬 확률 20% 증가", "§a크리티컬 데미지 50% 증가"),
                5, jobLevel, jobLevel >= 5));

        // 레벨 7: 돌진 공격
        gui.setItem(16, createSkillItem(Material.FEATHER, "§c돌진 베기",
                Arrays.asList("§7앞으로 빠르게 돌진하며", "§a경로상 모든 적에게 피해", "§7사거리: 8블록", "§7쿨다운: 20초"),
                7, jobLevel, jobLevel >= 7));

        // 레벨 10: 궁극기
        gui.setItem(22, createSkillItem(Material.NETHERITE_SWORD, "§6광풍 베기",
                Arrays.asList("§7레벨 10 궁극기", "§a주변 15블록 모든 적 공격", "§a3배 데미지로 광역 공격", "§c1일 1회 사용 가능"),
                10, jobLevel, jobLevel >= 10));
    }

    /**
     * 궁수 스킬 트리 구성
     */
    private void setupArcherSkillTree(Inventory gui, int jobLevel) {
        // 레벨 1-2: 기본 능력
        gui.setItem(10, createSkillItem(Material.BOW, "§e활 숙련",
                Arrays.asList("§7궁수의 기본 능력", "§a활 사용 시 공격력 증가", "§7레벨 1부터 사용 가능"),
                1, jobLevel, true));

        // 레벨 3-4: 이동속도
        gui.setItem(12, createSkillItem(Material.LEATHER_BOOTS, "§e경량화",
                Arrays.asList("§7가죽 부츠 착용 시", "§a이동속도 증가", "§7레벨 3부터 사용 가능"),
                3, jobLevel, jobLevel >= 3));

        // 레벨 5: 특수 능력
        gui.setItem(14, createSkillItem(Material.SPECTRAL_ARROW, "§6정밀 사격",
                Arrays.asList("§7레벨 5 특수 능력", "§a30블록 이상 거리에서", "§a헤드샷 확률 30% 증가"),
                5, jobLevel, jobLevel >= 5));

        // 레벨 7: 관통 화살
        gui.setItem(16, createSkillItem(Material.TIPPED_ARROW, "§e관통 화살",
                Arrays.asList("§7화살이 적을 관통하여", "§a최대 3명까지 피해", "§7관통 시 데미지 10% 감소"),
                7, jobLevel, jobLevel >= 7));

        // 레벨 10: 궁극기
        gui.setItem(22, createSkillItem(Material.CROSSBOW, "§6화살 폭풍",
                Arrays.asList("§7레벨 10 궁극기", "§a5초간 화살 10발 연속 발사", "§a각 화살마다 폭발 효과", "§c1일 1회 사용 가능"),
                10, jobLevel, jobLevel >= 10));
    }

    /**
     * 통계 섹션 (하단)
     */
    private void setupStatsSection(Inventory gui, Player player, JobType jobType) {
        // 개인 통계
        gui.setItem(45, createPersonalStatsItem(player, jobType));

        // 서버 랭킹
        gui.setItem(46, createRankingItem(player, jobType));

        // 업적 시스템
        gui.setItem(47, createAchievementItem(player, jobType));

        // 스킬 리셋
        gui.setItem(49, createSkillResetItem());

        // 직업 변경
        gui.setItem(51, createJobChangeItem());

        // 도움말
        gui.setItem(52, createHelpItem());

        // 나가기
        gui.setItem(53, createExitItem());
    }

    /**
     * 스킬 아이템 생성
     */
    private ItemStack createSkillItem(Material material, String name, List<String> lore,
                                      int requiredLevel, int currentLevel, boolean unlocked) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(name);

        // 잠금/해금 상태에 따른 설명 추가
        lore.add("");
        if (unlocked) {
            lore.add("§a✓ 해금됨");
            if (currentLevel >= requiredLevel) {
                lore.add("§f현재 활성화 중");
            }
        } else {
            lore.add("§c✗ 잠김 (레벨 " + requiredLevel + " 필요)");
            lore.add("§7현재 레벨: " + currentLevel);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 경험치 진행바 설정
     */
    private void setupExpProgressBar(Inventory gui, int level, int currentExp) {
        int requiredExp = getRequiredExpForLevel(level + 1);
        int expInCurrentLevel = currentExp - getRequiredExpForLevel(level);

        for (int i = 1; i <= 7; i++) {
            Material material;
            String name;

            double progress = (double) expInCurrentLevel / requiredExp;
            int progressSlots = (int) (progress * 7);

            if (i <= progressSlots) {
                material = Material.LIME_STAINED_GLASS_PANE;
                name = "§a경험치 진행도";
            } else {
                material = Material.GRAY_STAINED_GLASS_PANE;
                name = "§7경험치 진행도";
            }

            ItemStack progressItem = new ItemStack(material);
            ItemMeta meta = progressItem.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(
                    "§7현재 경험치: §f" + expInCurrentLevel + " / " + requiredExp,
                    "§7진행률: §f" + String.format("%.1f", progress * 100) + "%"
            ));
            progressItem.setItemMeta(meta);

            gui.setItem(i, progressItem);
        }
    }

    /**
     * 레벨별 필요 경험치 반환 (임시 구현)
     */
    private int getRequiredExpForLevel(int level) {
        int[] requirements = {0, 100, 250, 500, 800, 1200, 1700, 2300, 3000, 3800, 4700};
        if (level >= requirements.length) return requirements[requirements.length - 1];
        return requirements[level];
    }

    /**
     * 테두리 장식 설정
     */
    private void setupBorderDecoration(Inventory gui) {
        ItemStack borderItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();
        meta.setDisplayName(" ");
        borderItem.setItemMeta(meta);

        // 세로 테두리
        for (int i = 9; i < 45; i += 9) {
            if (gui.getItem(i) == null) gui.setItem(i, borderItem);
            if (gui.getItem(i + 8) == null) gui.setItem(i + 8, borderItem);
        }
    }

    // === 추가 아이템 생성 메소드들 ===

    private ItemStack createJobIcon(JobType jobType, int level, int exp) {
        Material material = switch (jobType) {
            case TANK -> Material.SHIELD;
            case WARRIOR -> Material.DIAMOND_SWORD;
            case ARCHER -> Material.BOW;
            default -> Material.BARRIER;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(jobType.getColor() + "§l" + jobType.getDisplayName());
        meta.setLore(Arrays.asList(
                "§7현재 레벨: §f" + level,
                "§7총 경험치: §f" + exp,
                "§7다음 레벨까지: §f" + (getRequiredExpForLevel(level + 1) - exp)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCurrentStatsItem(Player player, JobType jobType, int level) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a현재 능력치");

        // 직업별 현재 적용 중인 능력치 표시
        meta.setLore(Arrays.asList(
                "§7=== 현재 적용 중인 효과 ===",
                "§7레벨: §f" + level,
                "§7직업: " + jobType.getColor() + jobType.getDisplayName(),
                "",
                "§a활성화된 능력:",
                getActiveAbilitiesForJob(jobType, level)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private String getActiveAbilitiesForJob(JobType jobType, int level) {
        // 각 직업별로 현재 레벨에서 활성화된 능력들을 반환
        StringBuilder abilities = new StringBuilder();

        switch (jobType) {
            case TANK:
                abilities.append("§7• 기본 방어력 증가\n");
                if (level >= 3) abilities.append("§7• 방패 회복\n");
                if (level >= 5) abilities.append("§7• 불굴의 의지\n");
                if (level >= 7) abilities.append("§7• 도발\n");
                if (level >= 10) abilities.append("§7• 무적 방벽\n");
                break;
            case WARRIOR:
                abilities.append("§7• 검술 숙련\n");
                if (level >= 3) abilities.append("§7• 연속 베기\n");
                if (level >= 5) abilities.append("§7• 치명타 숙련\n");
                if (level >= 7) abilities.append("§7• 돌진 베기\n");
                if (level >= 10) abilities.append("§7• 광풍 베기\n");
                break;
            case ARCHER:
                abilities.append("§7• 활 숙련\n");
                if (level >= 3) abilities.append("§7• 경량화\n");
                if (level >= 5) abilities.append("§7• 정밀 사격\n");
                if (level >= 7) abilities.append("§7• 관통 화살\n");
                if (level >= 10) abilities.append("§7• 화살 폭풍\n");
                break;
        }

        return abilities.toString().trim();
    }

    private ItemStack createNextLevelPreview(JobType jobType, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e다음 레벨 미리보기");

        if (level >= 10) {
            meta.setLore(Arrays.asList("§6최대 레벨 달성!", "§7더 이상 성장할 수 없습니다."));
        } else {
            meta.setLore(Arrays.asList(
                    "§7레벨 " + (level + 1) + " 달성 시:",
                    getNextLevelAbility(jobType, level + 1),
                    "",
                    "§7필요 경험치: §f" + getRequiredExpForLevel(level + 1)
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    private String getNextLevelAbility(JobType jobType, int nextLevel) {
        // 다음 레벨에서 얻을 수 있는 능력 반환
        switch (jobType) {
            case TANK:
                if (nextLevel == 3) return "§a방패 회복 능력 해금";
                if (nextLevel == 5) return "§6불굴의 의지 특수 능력 해금";
                if (nextLevel == 7) return "§a도발 능력 해금";
                if (nextLevel == 10) return "§6무적 방벽 궁극기 해금";
                break;
            case WARRIOR:
                if (nextLevel == 3) return "§a연속 베기 능력 해금";
                if (nextLevel == 5) return "§6치명타 숙련 특수 능력 해금";
                if (nextLevel == 7) return "§a돌진 베기 능력 해금";
                if (nextLevel == 10) return "§6광풍 베기 궁극기 해금";
                break;
            case ARCHER:
                if (nextLevel == 3) return "§a경량화 능력 해금";
                if (nextLevel == 5) return "§6정밀 사격 특수 능력 해금";
                if (nextLevel == 7) return "§a관통 화살 능력 해금";
                if (nextLevel == 10) return "§6화살 폭풍 궁극기 해금";
                break;
        }
        return "§7기본 능력 강화";
    }

    private ItemStack createPersonalStatsItem(Player player, JobType jobType) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b개인 통계");
        meta.setLore(Arrays.asList(
                "§7총 플레이 시간: §f계산 중...",
                "§7몬스터 처치 수: §f계산 중...",
                "§7총 획득 경험치: §f계산 중...",
                "",
                "§e클릭하여 상세 통계 보기"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRankingItem(Player player, JobType jobType) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6서버 랭킹");
        meta.setLore(Arrays.asList(
                "§7" + jobType.getDisplayName() + " 순위: §f계산 중...",
                "§7전체 순위: §f계산 중...",
                "",
                "§e클릭하여 랭킹 보기"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAchievementItem(Player player, JobType jobType) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d업적 시스템");
        meta.setLore(Arrays.asList(
                "§7달성한 업적: §f0/20",
                "§7업적 점수: §f0점",
                "",
                "§e클릭하여 업적 목록 보기"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSkillResetItem() {
        ItemStack item = new ItemStack(Material.REDSTONE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c스킬 리셋");
        meta.setLore(Arrays.asList(
                "§7스킬을 초기화하고",
                "§7포인트를 재할당합니다",
                "",
                "§c비용: 50,000G",
                "§e클릭하여 리셋하기"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createJobChangeItem() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§5직업 변경");
        meta.setLore(Arrays.asList(
                "§7다른 직업으로 변경합니다",
                "§c주의: 모든 진행도가 초기화됩니다",
                "",
                "§c비용: 100,000G",
                "§e클릭하여 변경하기"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHelpItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a도움말");
        meta.setLore(Arrays.asList(
                "§7직업 시스템 사용법을",
                "§7자세히 알려드립니다",
                "",
                "§e클릭하여 도움말 보기"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createExitItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c나가기");
        meta.setLore(Arrays.asList("§7GUI를 닫습니다"));
        item.setItemMeta(meta);
        return item;
    }
}