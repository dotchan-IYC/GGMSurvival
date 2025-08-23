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

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * 직업 상세 통계 GUI
 * 플레이어의 직업 관련 통계를 시각적으로 표시
 */
public class JobStatsGUI {

    private final GGMSurvival plugin;
    private final JobManager jobManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public JobStatsGUI(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();
    }

    /**
     * 통계 GUI 열기
     */
    public void openStatsGUI(Player player) {
        JobType jobType = jobManager.getJobType(player);

        if (jobType == JobType.NONE) {
            player.sendMessage("§c먼저 직업을 선택해주세요!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54,
                "§b§l" + player.getName() + "의 직업 통계");

        setupStatsGUI(gui, player, jobType);
        player.openInventory(gui);
    }

    /**
     * 통계 GUI 구성
     */
    private void setupStatsGUI(Inventory gui, Player player, JobType jobType) {
        // 상단: 기본 정보 (0-8)
        setupBasicInfo(gui, player, jobType);

        // 중앙: 상세 통계 (9-44)
        setupDetailedStats(gui, player, jobType);

        // 하단: 네비게이션 (45-53)
        setupNavigation(gui);

        // 테두리 장식
        fillEmptySlots(gui);
    }

    /**
     * 기본 정보 섹션
     */
    private void setupBasicInfo(Inventory gui, Player player, JobType jobType) {
        int level = jobManager.getJobLevel(player);
        int exp = jobManager.getJobExperience(player);

        // 플레이어 정보 (중앙)
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta playerMeta = playerInfo.getItemMeta();
        playerMeta.setDisplayName("§e§l" + player.getName());
        playerMeta.setLore(Arrays.asList(
                "§7직업: " + jobType.getColor() + jobType.getDisplayName(),
                "§7레벨: §f" + level + " / 10",
                "§7총 경험치: §f" + exp,
                "§7가입일: §f" + dateFormat.format(new Date(player.getFirstPlayed())),
                "§7마지막 접속: §f" + dateFormat.format(new Date(player.getLastPlayed()))
        ));
        playerInfo.setItemMeta(playerMeta);
        gui.setItem(4, playerInfo);

        // 서버 순위
        gui.setItem(2, createRankingItem(player, jobType));

        // 플레이 시간
        gui.setItem(6, createPlayTimeItem(player));
    }

    /**
     * 상세 통계 섹션
     */
    private void setupDetailedStats(Inventory gui, Player player, JobType jobType) {
        // 전투 통계 (왼쪽)
        setupCombatStats(gui, player);

        // 경험치 통계 (중앙)
        setupExpStats(gui, player);

        // 직업별 특화 통계 (오른쪽)
        setupJobSpecificStats(gui, player, jobType);

        // 성취 통계 (하단)
        setupAchievementStats(gui, player);
    }

    /**
     * 전투 통계 설정
     */
    private void setupCombatStats(Inventory gui, Player player) {
        // 몬스터 처치 수
        gui.setItem(10, createStatItem(Material.IRON_SWORD, "§c전투 통계",
                Arrays.asList(
                        "§7총 몬스터 처치: §f" + getMonsterKills(player),
                        "§7보스 몬스터 처치: §f" + getBossKills(player),
                        "§7최고 연속 처치: §f" + getKillStreak(player),
                        "§7받은 총 피해: §f" + getTotalDamageReceived(player),
                        "§7가한 총 피해: §f" + getTotalDamageDealt(player)
                )));

        // PvP 통계
        gui.setItem(19, createStatItem(Material.DIAMOND_SWORD, "§4PvP 통계",
                Arrays.asList(
                        "§7플레이어 처치: §f" + getPvpKills(player),
                        "§7플레이어에게 사망: §f" + getPvpDeaths(player),
                        "§7PvP K/D 비율: §f" + getPvpKDRatio(player),
                        "§7PvP 승률: §f" + getPvpWinRate(player) + "%"
                )));
    }

    /**
     * 경험치 통계 설정
     */
    private void setupExpStats(Inventory gui, Player player) {
        int totalExp = jobManager.getJobExperience(player);
        int todayExp = getTodayExp(player);
        int weekExp = getWeekExp(player);

        gui.setItem(13, createStatItem(Material.EXPERIENCE_BOTTLE, "§a경험치 통계",
                Arrays.asList(
                        "§7총 획득 경험치: §f" + totalExp,
                        "§7오늘 획득 경험치: §f" + todayExp,
                        "§7이번 주 경험치: §f" + weekExp,
                        "§7평균 일일 경험치: §f" + getAverageDaily(player),
                        "§7최고 일일 기록: §f" + getBestDailyExp(player)
                )));

        // 경험치 출처
        gui.setItem(22, createStatItem(Material.EMERALD, "§2경험치 출처",
                Arrays.asList(
                        "§7몬스터 처치: §f" + getExpFromMobs(player) + " (65%)",
                        "§7보스 처치: §f" + getExpFromBoss(player) + " (20%)",
                        "§7퀘스트 완료: §f" + getExpFromQuests(player) + " (10%)",
                        "§7기타: §f" + getExpFromOther(player) + " (5%)"
                )));
    }

    /**
     * 직업별 특화 통계
     */
    private void setupJobSpecificStats(Inventory gui, Player player, JobType jobType) {
        switch (jobType) {
            case TANK -> setupTankStats(gui, player);
            case WARRIOR -> setupWarriorStats(gui, player);
            case ARCHER -> setupArcherStats(gui, player);
        }
    }

    /**
     * 탱커 전용 통계
     */
    private void setupTankStats(Inventory gui, Player player) {
        gui.setItem(16, createStatItem(Material.SHIELD, "§9탱커 특화 통계",
                Arrays.asList(
                        "§7방패로 막은 공격: §f" + getBlockedAttacks(player),
                        "§7방패 회복 횟수: §f" + getShieldHeals(player),
                        "§7도발 사용 횟수: §f" + getTauntUses(player),
                        "§7무적 방벽 사용: §f" + getUltimateUses(player),
                        "§7팀원 보호 횟수: §f" + getTeamProtections(player)
                )));

        gui.setItem(25, createStatItem(Material.HEART_OF_THE_SEA, "§b생존 통계",
                Arrays.asList(
                        "§7총 생존 시간: §f" + getSurvivalTime(player),
                        "§7위험 상황 탈출: §f" + getNearDeathEscapes(player),
                        "§7최장 무사망 기록: §f" + getLongestSurvival(player),
                        "§7체력 회복량: §f" + getTotalHealing(player)
                )));
    }

    /**
     * 검사 전용 통계
     */
    private void setupWarriorStats(Inventory gui, Player player) {
        gui.setItem(16, createStatItem(Material.NETHERITE_SWORD, "§c검사 특화 통계",
                Arrays.asList(
                        "§7크리티컬 공격: §f" + getCriticalHits(player),
                        "§7연속 베기 성공: §f" + getComboHits(player),
                        "§7돌진 베기 사용: §f" + getDashAttacks(player),
                        "§7광풍 베기 사용: §f" + getUltimateUses(player),
                        "§7완벽한 콤보: §f" + getPerfectCombos(player)
                )));

        gui.setItem(25, createStatItem(Material.BLAZE_POWDER, "§6공격 통계",
                Arrays.asList(
                        "§7최고 데미지: §f" + getMaxDamage(player),
                        "§7평균 데미지: §f" + getAverageDamage(player),
                        "§7총 공격 횟수: §f" + getTotalAttacks(player),
                        "§7명중률: §f" + getAccuracy(player) + "%"
                )));
    }

    /**
     * 궁수 전용 통계
     */
    private void setupArcherStats(Inventory gui, Player player) {
        gui.setItem(16, createStatItem(Material.BOW, "§e궁수 특화 통계",
                Arrays.asList(
                        "§7헤드샷 성공: §f" + getHeadshots(player),
                        "§7장거리 저격: §f" + getLongRangeShots(player),
                        "§7관통 화살 적중: §f" + getPiercingHits(player),
                        "§7화살 폭풍 사용: §f" + getUltimateUses(player),
                        "§7연속 명중 기록: §f" + getConsecutiveHits(player)
                )));

        gui.setItem(25, createStatItem(Material.TARGET, "§a사격 통계",
                Arrays.asList(
                        "§7총 화살 발사: §f" + getTotalArrowsShot(player),
                        "§7명중한 화살: §f" + getArrowsHit(player),
                        "§7명중률: §f" + getArcherAccuracy(player) + "%",
                        "§7최장 사거리: §f" + getMaxRange(player) + "블록"
                )));
    }

    /**
     * 성취 통계 설정
     */
    private void setupAchievementStats(Inventory gui, Player player) {
        gui.setItem(37, createStatItem(Material.GOLD_INGOT, "§6성취 통계",
                Arrays.asList(
                        "§7달성한 업적: §f" + getAchievementsCompleted(player) + "/50",
                        "§7업적 점수: §f" + getAchievementScore(player),
                        "§7레어 업적: §f" + getRareAchievements(player),
                        "§7전설 업적: §f" + getLegendaryAchievements(player)
                )));

        gui.setItem(43, createStatItem(Material.CLOCK, "§e활동 통계",
                Arrays.asList(
                        "§7총 플레이 시간: §f" + getTotalPlayTime(player),
                        "§7일평균 플레이: §f" + getAveragePlayTime(player),
                        "§7연속 접속일: §f" + getLoginStreak(player) + "일",
                        "§7최장 연속 접속: §f" + getMaxLoginStreak(player) + "일"
                )));
    }

    /**
     * 네비게이션 설정
     */
    private void setupNavigation(Inventory gui) {
        // 스킬 관리로 돌아가기
        gui.setItem(45, createNavItem(Material.ENCHANTED_BOOK, "§a스킬 관리",
                Arrays.asList("§7스킬 관리 화면으로 돌아갑니다")));

        // 업적 보기
        gui.setItem(47, createNavItem(Material.EXPERIENCE_BOTTLE, "§d업적 보기",
                Arrays.asList("§7업적 목록을 확인합니다")));

        // 랭킹 보기
        gui.setItem(49, createNavItem(Material.GOLD_INGOT, "§6랭킹 보기",
                Arrays.asList("§7서버 랭킹을 확인합니다")));

        // 새로고침
        gui.setItem(51, createNavItem(Material.COMPASS, "§b새로고침",
                Arrays.asList("§7통계를 새로고침합니다")));

        // 닫기
        gui.setItem(53, createNavItem(Material.BARRIER, "§c닫기",
                Arrays.asList("§7GUI를 닫습니다")));
    }

    // === 헬퍼 메소드들 ===

    private ItemStack createStatItem(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRankingItem(Player player, JobType jobType) {
        return createStatItem(Material.GOLD_INGOT, "§6서버 순위",
                Arrays.asList(
                        "§7전체 순위: §f계산 중...",
                        "§7" + jobType.getDisplayName() + " 순위: §f계산 중...",
                        "§7상위 %: §f계산 중...",
                        "",
                        "§e클릭하여 상세 랭킹 보기"
                ));
    }

    private ItemStack createPlayTimeItem(Player player) {
        long playTime = System.currentTimeMillis() - player.getFirstPlayed();
        String formattedTime = formatPlayTime(playTime);

        return createStatItem(Material.CLOCK, "§e플레이 시간",
                Arrays.asList(
                        "§7총 플레이 시간: §f" + formattedTime,
                        "§7이번 세션: §f" + getSessionTime(player),
                        "§7일평균: §f" + getAveragePlayTime(player),
                        "§7이번 주: §f" + getWeekPlayTime(player)
                ));
    }

    private void fillEmptySlots(Inventory gui) {
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

    // === 통계 데이터 가져오기 메소드들 (임시 구현) ===

    private int getMonsterKills(Player player) { return 1547; }
    private int getBossKills(Player player) { return 23; }
    private int getKillStreak(Player player) { return 47; }
    private int getTotalDamageReceived(Player player) { return 15847; }
    private int getTotalDamageDealt(Player player) { return 89432; }
    private int getPvpKills(Player player) { return 5; }
    private int getPvpDeaths(Player player) { return 2; }
    private String getPvpKDRatio(Player player) { return "2.5"; }
    private int getPvpWinRate(Player player) { return 71; }
    private int getTodayExp(Player player) { return 340; }
    private int getWeekExp(Player player) { return 2180; }
    private int getAverageDaily(Player player) { return 280; }
    private int getBestDailyExp(Player player) { return 650; }
    private int getExpFromMobs(Player player) { return 2850; }
    private int getExpFromBoss(Player player) { return 890; }
    private int getExpFromQuests(Player player) { return 450; }
    private int getExpFromOther(Player player) { return 210; }
    private int getBlockedAttacks(Player player) { return 342; }
    private int getShieldHeals(Player player) { return 89; }
    private int getTauntUses(Player player) { return 156; }
    private int getUltimateUses(Player player) { return 3; }
    private int getTeamProtections(Player player) { return 67; }
    private String getSurvivalTime(Player player) { return "4시간 23분"; }
    private int getNearDeathEscapes(Player player) { return 12; }
    private String getLongestSurvival(Player player) { return "47분 30초"; }
    private int getTotalHealing(Player player) { return 2340; }
    private int getCriticalHits(Player player) { return 89; }
    private int getComboHits(Player player) { return 234; }
    private int getDashAttacks(Player player) { return 67; }
    private int getPerfectCombos(Player player) { return 23; }
    private int getMaxDamage(Player player) { return 450; }
    private int getAverageDamage(Player player) { return 85; }
    private int getTotalAttacks(Player player) { return 1340; }
    private int getAccuracy(Player player) { return 73; }
    private int getHeadshots(Player player) { return 145; }
    private int getLongRangeShots(Player player) { return 78; }
    private int getPiercingHits(Player player) { return 234; }
    private int getConsecutiveHits(Player player) { return 23; }
    private int getTotalArrowsShot(Player player) { return 2890; }
    private int getArrowsHit(Player player) { return 2180; }
    private int getArcherAccuracy(Player player) { return 75; }
    private int getMaxRange(Player player) { return 87; }
    private int getAchievementsCompleted(Player player) { return 34; }
    private int getAchievementScore(Player player) { return 3450; }
    private int getRareAchievements(Player player) { return 7; }
    private int getLegendaryAchievements(Player player) { return 2; }
    private String getTotalPlayTime(Player player) { return "156시간 23분"; }
    private String getAveragePlayTime(Player player) { return "2시간 15분"; }
    private int getLoginStreak(Player player) { return 23; }
    private int getMaxLoginStreak(Player player) { return 67; }
    private String getSessionTime(Player player) { return "1시간 34분"; }
    private String getWeekPlayTime(Player player) { return "18시간 45분"; }

    private String formatPlayTime(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis / (1000 * 60)) % 60;
        return hours + "시간 " + minutes + "분";
    }
}