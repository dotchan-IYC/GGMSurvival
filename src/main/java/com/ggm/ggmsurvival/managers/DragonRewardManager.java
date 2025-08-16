// 완전한 DragonRewardManager.java - 드래곤 보상 시스템 (이모티콘 제거)
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 완전한 드래곤 보상 시스템 매니저
 * - 엔더 드래곤 처치 감지
 * - 직업별 차등 보상
 * - 특별 아이템 드롭
 * - 보상 기록 저장
 * - 통계 제공
 */
public class DragonRewardManager {

    private final GGMSurvival plugin;

    // 드래곤 처치 기록 캐시
    private final Map<UUID, DragonKillData> killDataCache = new ConcurrentHashMap<>();

    // 드래곤 전투 참여자 추적
    private final Map<UUID, Set<UUID>> dragonFighters = new ConcurrentHashMap<>();

    // 보상 설정
    private final long baseMoneyReward;
    private final int baseExpReward;
    private final Map<String, Double> jobBonusMultipliers = new HashMap<>();
    private final Map<String, Double> specialDropChances = new HashMap<>();

    private final Random random = new Random();

    public DragonRewardManager(GGMSurvival plugin) {
        this.plugin = plugin;

        // 설정 로드
        this.baseMoneyReward = plugin.getConfig().getLong("dragon_reward_system.base_reward.money", 50000L);
        this.baseExpReward = plugin.getConfig().getInt("dragon_reward_system.base_reward.exp", 1000);

        try {
            initializeDragonSystem();
            loadRewardConfig();

            plugin.getLogger().info("=== 드래곤 보상 시스템 초기화 완료 ===");
            plugin.getLogger().info("기본 보상: " + baseMoneyReward + "G + " + baseExpReward + " EXP");
            plugin.getLogger().info("직업별 보너스: " + jobBonusMultipliers.size() + "개");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "DragonRewardManager 초기화 실패", e);
            throw new RuntimeException("DragonRewardManager 초기화 실패", e);
        }
    }

    /**
     * 드래곤 시스템 초기화
     */
    private void initializeDragonSystem() {
        plugin.getLogger().info("드래곤 보상 시스템 데이터베이스 연결 확인 완료");
    }

    /**
     * 보상 설정 로드
     */
    private void loadRewardConfig() {
        // 직업별 보너스 설정
        jobBonusMultipliers.put("tank", plugin.getConfig().getDouble("dragon_reward_system.job_bonus.tank.money_multiplier", 1.2));
        jobBonusMultipliers.put("warrior", plugin.getConfig().getDouble("dragon_reward_system.job_bonus.warrior.money_multiplier", 1.1));
        jobBonusMultipliers.put("archer", plugin.getConfig().getDouble("dragon_reward_system.job_bonus.archer.money_multiplier", 1.0));

        // 특별 드롭 확률
        specialDropChances.put("dragon_egg", plugin.getConfig().getDouble("dragon_reward_system.special_rewards.dragon_egg_chance", 0.05));
        specialDropChances.put("elytra", plugin.getConfig().getDouble("dragon_reward_system.special_rewards.elytra_chance", 0.10));
        specialDropChances.put("dragon_head", plugin.getConfig().getDouble("dragon_reward_system.special_rewards.dragon_head_chance", 0.15));

        plugin.getLogger().info("드래곤 보상 설정 로드 완료");
    }

    /**
     * 드래곤 처치 이벤트 처리
     */
    public void handleDragonKill(EnderDragon dragon, Player killer) {
        try {
            // 드래곤 전투 참여자 목록 가져오기
            Set<Player> participants = getDragonParticipants(dragon);

            if (participants.isEmpty()) {
                // 최소한 처치자는 포함
                participants.add(killer);
            }

            plugin.getLogger().info("엔더 드래곤이 처치되었습니다! 참여자: " + participants.size() + "명");

            // 각 참여자에게 보상 지급
            for (Player participant : participants) {
                if (participant.isOnline()) {
                    giveDragonReward(participant, killer.equals(participant));
                }
            }

            // 전역 알림
            broadcastDragonKillAnnouncement(killer, participants.size());

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "드래곤 처치 보상 처리 중 오류", e);
        }
    }

    /**
     * 드래곤 전투 참여자 목록 가져오기
     */
    private Set<Player> getDragonParticipants(EnderDragon dragon) {
        Set<Player> participants = new HashSet<>();

        // 드래곤 주변 플레이어들을 참여자로 간주 (반경 50블록)
        for (Player player : dragon.getWorld().getPlayers()) {
            if (player.getLocation().distance(dragon.getLocation()) <= 50.0) {
                participants.add(player);
            }
        }

        return participants;
    }

    /**
     * 드래곤 보상 지급
     */
    public void giveDragonReward(Player player, boolean isKiller) {
        CompletableFuture.runAsync(() -> {
            try {
                // 직업 정보 조회
                JobManager.PlayerJobData jobData = null;
                if (plugin.getJobManager() != null) {
                    jobData = plugin.getJobManager().getPlayerJobData(player.getUniqueId());
                }

                // 보상 계산
                DragonReward reward = calculateDragonReward(player, jobData, isKiller);

                // 메인 스레드에서 보상 지급
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    applyDragonReward(player, reward, isKiller);
                });

                // 기록 저장
                saveDragonKillRecord(player, reward, isKiller);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "드래곤 보상 지급 중 오류: " + player.getName(), e);
            }
        });
    }

    /**
     * 드래곤 보상 계산
     */
    private DragonReward calculateDragonReward(Player player, JobManager.PlayerJobData jobData, boolean isKiller) {
        DragonReward reward = new DragonReward();

        // 기본 보상
        reward.money = baseMoneyReward;
        reward.experience = baseExpReward;

        // 처치자 보너스
        if (isKiller) {
            reward.money = (long) (reward.money * 1.5); // 50% 보너스
            reward.experience = (int) (reward.experience * 1.5);
        }

        // 직업별 보너스
        if (jobData != null && jobData.getJobType() != JobManager.JobType.NONE) {
            String jobName = jobData.getJobType().name().toLowerCase();
            double multiplier = jobBonusMultipliers.getOrDefault(jobName, 1.0);
            reward.money = (long) (reward.money * multiplier);

            // 직업별 특별 아이템
            reward.specialItems.addAll(getJobSpecialItems(jobName));
        }

        // 특별 드롭 체크
        reward.specialItems.addAll(checkSpecialDrops());

        // 레벨 기반 보너스 (직업 레벨)
        if (jobData != null && jobData.getLevel() > 1) {
            double levelBonus = 1.0 + (jobData.getLevel() - 1) * 0.1; // 레벨당 10% 보너스
            reward.money = (long) (reward.money * levelBonus);
        }

        return reward;
    }

    /**
     * 직업별 특별 아이템
     */
    private List<ItemStack> getJobSpecialItems(String jobName) {
        List<ItemStack> items = new ArrayList<>();

        switch (jobName) {
            case "tank":
                // 탱커: 방패, 철괴
                items.add(createSpecialItem(Material.SHIELD, "§6[드래곤] 수호자의 방패",
                        Arrays.asList("§7드래곤을 처치한 탱커에게 주어지는 방패", "§a방어력 +10%")));
                items.add(new ItemStack(Material.IRON_INGOT, 32));
                break;

            case "warrior":
                // 전사: 다이아몬드 검, 다이아몬드
                items.add(createSpecialItem(Material.DIAMOND_SWORD, "§c[드래곤] 용살자의 검",
                        Arrays.asList("§7드래곤을 처치한 전사에게 주어지는 검", "§c공격력 +15%")));
                items.add(new ItemStack(Material.DIAMOND, 16));
                break;

            case "archer":
                // 궁수: 활, 화살
                items.add(createSpecialItem(Material.BOW, "§e[드래곤] 정령의 활",
                        Arrays.asList("§7드래곤을 처치한 궁수에게 주어지는 활", "§e사거리 +20%")));
                items.add(new ItemStack(Material.ARROW, 64));
                break;
        }

        return items;
    }

    /**
     * 특별 드롭 체크
     */
    private List<ItemStack> checkSpecialDrops() {
        List<ItemStack> drops = new ArrayList<>();

        // 드래곤 알 (5% 확률)
        if (random.nextDouble() < specialDropChances.get("dragon_egg")) {
            drops.add(createSpecialItem(Material.DRAGON_EGG, "§5[전설] 엔더 드래곤의 알",
                    Arrays.asList("§7전설적인 엔더 드래곤의 알", "§5매우 희귀한 아이템")));
        }

        // 엘리트라 (10% 확률)
        if (random.nextDouble() < specialDropChances.get("elytra")) {
            drops.add(createSpecialItem(Material.ELYTRA, "§b[희귀] 드래곤의 날개",
                    Arrays.asList("§7드래곤의 힘이 깃든 날개", "§b하늘을 자유롭게 날 수 있다")));
        }

        // 드래곤 머리 (15% 확률)
        if (random.nextDouble() < specialDropChances.get("dragon_head")) {
            drops.add(createSpecialItem(Material.DRAGON_HEAD, "§8[기념품] 엔더 드래곤의 머리",
                    Arrays.asList("§7엔더 드래곤 처치의 증표", "§8장식용 아이템")));
        }

        return drops;
    }

    /**
     * 특별 아이템 생성
     */
    private ItemStack createSpecialItem(Material material, String name, List<String> lore) {
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
     * 드래곤 보상 적용
     */
    private void applyDragonReward(Player player, DragonReward reward, boolean isKiller) {
        try {
            // 돈 지급
            plugin.getEconomyManager().addMoney(player.getUniqueId(), reward.money);

            // 경험치 지급
            player.giveExp(reward.experience);

            // 직업 경험치 지급
            if (plugin.getJobManager() != null) {
                plugin.getJobManager().addExperience(player.getUniqueId(), reward.experience * 2L);
            }

            // 특별 아이템 지급
            for (ItemStack item : reward.specialItems) {
                // 인벤토리에 공간이 있으면 추가, 없으면 떨어뜨림
                HashMap<Integer, ItemStack> excess = player.getInventory().addItem(item);
                for (ItemStack excessItem : excess.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), excessItem);
                }
            }

            // 보상 메시지
            sendRewardMessage(player, reward, isKiller);

            // 효과음 및 이펙트
            playRewardEffects(player, isKiller);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "드래곤 보상 적용 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 보상 메시지 전송
     */
    private void sendRewardMessage(Player player, DragonReward reward, boolean isKiller) {
        player.sendMessage("§6===========================================");
        player.sendMessage("§e§l    엔더 드래곤 처치 보상");

        if (isKiller) {
            player.sendMessage("§c§l          [처치자 보너스!]");
        }

        player.sendMessage("");
        player.sendMessage("§a획득 보상:");
        player.sendMessage("§7• 골드: §f+" + plugin.getEconomyManager().formatMoneyWithSymbol(reward.money));
        player.sendMessage("§7• 경험치: §f+" + reward.experience + " EXP");

        if (!reward.specialItems.isEmpty()) {
            player.sendMessage("§7• 특별 아이템: §f" + reward.specialItems.size() + "개");
        }

        player.sendMessage("");
        player.sendMessage("§e축하합니다! 엔더 드래곤을 처치하셨습니다!");
        player.sendMessage("§6===========================================");
    }

    /**
     * 보상 효과음 및 이펙트
     */
    private void playRewardEffects(Player player, boolean isKiller) {
        if (isKiller) {
            // 처치자 특별 효과
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.spawnParticle(org.bukkit.Particle.DRAGON_BREATH,
                    player.getLocation().add(0, 1, 0), 30, 1.0, 1.0, 1.0, 0.1);
        } else {
            // 일반 참여자 효과
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                    player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        }
    }

    /**
     * 전역 알림
     */
    private void broadcastDragonKillAnnouncement(Player killer, int participantCount) {
        String message = "§5§l[드래곤 처치!] §f" + killer.getName() + "님이 엔더 드래곤을 처치했습니다!";
        String subMessage = "§7참여자 " + participantCount + "명이 보상을 받았습니다.";

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage("§5═══════════════════════════════════════");
            player.sendMessage(message);
            player.sendMessage(subMessage);
            player.sendMessage("§5═══════════════════════════════════════");

            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.0f);
        }

        plugin.getLogger().info("엔더 드래곤이 " + killer.getName() + "에 의해 처치되었습니다 (참여자: " + participantCount + "명)");
    }

    /**
     * 드래곤 처치 기록 저장
     */
    private void saveDragonKillRecord(Player player, DragonReward reward, boolean isKiller) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO dragon_kills (player_uuid, reward_money, reward_exp, special_reward, player_job) VALUES (?, ?, ?, ?, ?)")) {

                JobManager.PlayerJobData jobData = null;
                if (plugin.getJobManager() != null) {
                    jobData = plugin.getJobManager().getPlayerJobData(player.getUniqueId());
                }

                String specialReward = reward.specialItems.isEmpty() ? null :
                        reward.specialItems.get(0).getType().name();
                String playerJob = jobData != null ? jobData.getJobType().name() : "NONE";

                statement.setString(1, player.getUniqueId().toString());
                statement.setLong(2, reward.money);
                statement.setInt(3, reward.experience);
                statement.setString(4, specialReward);
                statement.setString(5, playerJob);

                statement.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "드래곤 처치 기록 저장 실패: " + player.getName(), e);
            }
        });
    }

    /**
     * 플레이어 드래곤 처치 통계 조회
     */
    public CompletableFuture<DragonStats> getPlayerDragonStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) as kill_count, SUM(reward_money) as total_money, " +
                                 "SUM(reward_exp) as total_exp, MAX(kill_time) as last_kill " +
                                 "FROM dragon_kills WHERE player_uuid = ?")) {

                statement.setString(1, uuid.toString());

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return new DragonStats(
                                rs.getInt("kill_count"),
                                rs.getLong("total_money"),
                                rs.getInt("total_exp"),
                                rs.getTimestamp("last_kill")
                        );
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "드래곤 처치 통계 조회 실패: " + uuid, e);
            }

            return new DragonStats(0, 0L, 0, null);
        });
    }

    /**
     * 서버 전체 드래곤 처치 통계
     */
    public CompletableFuture<GlobalDragonStats> getGlobalDragonStats() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = plugin.getDatabaseManager().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) as total_kills, COUNT(DISTINCT player_uuid) as unique_killers, " +
                                 "SUM(reward_money) as total_money_distributed FROM dragon_kills")) {

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return new GlobalDragonStats(
                                rs.getInt("total_kills"),
                                rs.getInt("unique_killers"),
                                rs.getLong("total_money_distributed")
                        );
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "전역 드래곤 통계 조회 실패", e);
            }

            return new GlobalDragonStats(0, 0, 0L);
        });
    }

    /**
     * 캐시 정리
     */
    public void cleanupCache() {
        try {
            killDataCache.clear();
            dragonFighters.clear();
            plugin.getLogger().info("DragonRewardManager 캐시 정리 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "드래곤 보상 시스템 캐시 정리 중 오류", e);
        }
    }

    /**
     * 시스템 종료
     */
    public void shutdown() {
        try {
            cleanupCache();
            plugin.getLogger().info("DragonRewardManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "DragonRewardManager 종료 중 오류", e);
        }
    }

    // Getter 메서드들
    public long getBaseMoneyReward() {
        return baseMoneyReward;
    }

    public int getBaseExpReward() {
        return baseExpReward;
    }

    /**
     * 드래곤 보상 데이터 클래스
     */
    public static class DragonReward {
        public long money = 0;
        public int experience = 0;
        public List<ItemStack> specialItems = new ArrayList<>();
    }

    /**
     * 드래곤 처치 데이터 클래스
     */
    public static class DragonKillData {
        private final UUID playerId;
        private final long killTime;
        private final long rewardMoney;
        private final int rewardExp;

        public DragonKillData(UUID playerId, long killTime, long rewardMoney, int rewardExp) {
            this.playerId = playerId;
            this.killTime = killTime;
            this.rewardMoney = rewardMoney;
            this.rewardExp = rewardExp;
        }

        public UUID getPlayerId() { return playerId; }
        public long getKillTime() { return killTime; }
        public long getRewardMoney() { return rewardMoney; }
        public int getRewardExp() { return rewardExp; }
    }

    /**
     * 플레이어 드래곤 통계 클래스
     */
    public static class DragonStats {
        private final int killCount;
        private final long totalMoney;
        private final int totalExp;
        private final java.sql.Timestamp lastKill;

        public DragonStats(int killCount, long totalMoney, int totalExp, java.sql.Timestamp lastKill) {
            this.killCount = killCount;
            this.totalMoney = totalMoney;
            this.totalExp = totalExp;
            this.lastKill = lastKill;
        }

        public int getKillCount() { return killCount; }
        public long getTotalMoney() { return totalMoney; }
        public int getTotalExp() { return totalExp; }
        public java.sql.Timestamp getLastKill() { return lastKill; }
    }

    /**
     * 전역 드래곤 통계 클래스
     */
    public static class GlobalDragonStats {
        private final int totalKills;
        private final int uniqueKillers;
        private final long totalMoneyDistributed;

        public GlobalDragonStats(int totalKills, int uniqueKillers, long totalMoneyDistributed) {
            this.totalKills = totalKills;
            this.uniqueKillers = uniqueKillers;
            this.totalMoneyDistributed = totalMoneyDistributed;
        }

        public int getTotalKills() { return totalKills; }
        public int getUniqueKillers() { return uniqueKillers; }
        public long getTotalMoneyDistributed() { return totalMoneyDistributed; }
    }
}