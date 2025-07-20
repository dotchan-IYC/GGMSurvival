// 업데이트된 JobManager.java - 경험치바 UI & 10렙 만렙 효과
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JobManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;

    // 직업 타입
    public enum JobType {
        NONE("무직", "§7", Material.BARRIER),
        TANK("탱커", "§9", Material.SHIELD),
        WARRIOR("검사", "§c", Material.DIAMOND_SWORD),
        ARCHER("궁수", "§a", Material.BOW);

        private final String displayName;
        private final String color;
        private final Material icon;

        JobType(String displayName, String color, Material icon) {
            this.displayName = displayName;
            this.color = color;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        public Material getIcon() { return icon; }
    }

    // 플레이어별 직업 레벨 캐시
    private final Map<UUID, Integer> jobLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> jobExperience = new ConcurrentHashMap<>();
    private final Map<UUID, JobType> jobTypes = new ConcurrentHashMap<>();

    // 방패 방어 쿨다운
    private final Map<UUID, Long> shieldDefenseCooldown = new ConcurrentHashMap<>();

    // 레벨업에 필요한 경험치 (레벨별)
    private final Map<Integer, Integer> expRequirements = new HashMap<>();

    // 몬스터별 경험치
    private final Map<EntityType, Integer> monsterExp = new HashMap<>();

    // 경험치바 업데이트 태스크
    private BukkitRunnable expBarTask;

    // 궁수 이동속도 패시브 효과용 NamespacedKey
    private final NamespacedKey archerSpeedKey;

    // 스코어보드 연동 시스템
    private ScoreboardIntegration scoreboardIntegration;

    public JobManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.archerSpeedKey = new NamespacedKey(plugin, "archer_leather_boots_speed");

        // 초기화
        initializeExpRequirements();
        initializeMonsterExp();
        createJobTables();
        startExpBarUpdateTask();

        // 스코어보드 연동 시스템 초기화 (약간 지연)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            scoreboardIntegration = new ScoreboardIntegration(plugin);
            plugin.getServer().getPluginManager().registerEvents(scoreboardIntegration, plugin);
        }, 60L); // 3초 후 초기화

        plugin.getLogger().info("새로운 직업레벨 시스템 초기화 완료 - 경험치바 UI & 10렙 만렙 효과 (패시브)");
    }

    /**
     * 경험치바 UI 업데이트 태스크 시작
     */
    private void startExpBarUpdateTask() {
        expBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 경험치바 업데이트
                    updatePlayerExpBar(player);

                    // 직업 효과 지속 적용 (특히 궁수 가죽장화 효과)
                    if (getJobType(player) != JobType.NONE) {
                        applyJobEffects(player);
                    }
                }
            }
        };
        expBarTask.runTaskTimer(plugin, 0L, 20L); // 1초마다 업데이트
    }

    /**
     * 플레이어 경험치바 업데이트
     */
    private void updatePlayerExpBar(Player player) {
        JobType job = getJobType(player);
        if (job == JobType.NONE) return;

        int level = getJobLevel(player);
        int exp = getJobExperience(player);

        if (level >= 10) {
            // 만렙 달성
            player.setLevel(10);
            player.setExp(1.0f);

            // 액션바에 만렙 표시
            player.sendActionBar("§6✨ " + job.getColor() + job.getDisplayName() + " §6만렙 달성! ✨");
        } else {
            int currentLevelExp = expRequirements.getOrDefault(level, 0);
            int nextLevelExp = expRequirements.getOrDefault(level + 1, 1000);
            int remainingExp = Math.max(0, exp - currentLevelExp);
            int expForNextLevel = nextLevelExp - currentLevelExp;

            // 레벨과 경험치바 설정
            player.setLevel(level);
            float expProgress = (float) remainingExp / expForNextLevel;
            player.setExp(Math.min(1.0f, expProgress));

            // 액션바에 상세 정보 표시
            String actionBar = String.format("%s%s §7Lv.%d §f[§a%d§7/§e%d§f] §7다음 레벨까지 §c%d경험치",
                    job.getColor(), job.getDisplayName(), level, remainingExp, expForNextLevel, expForNextLevel - remainingExp);
            player.sendActionBar(actionBar);
        }
    }

    /**
     * 레벨업 경험치 요구량 초기화
     */
    private void initializeExpRequirements() {
        expRequirements.put(1, 100);     // 1레벨: 100exp
        expRequirements.put(2, 250);     // 2레벨: 250exp
        expRequirements.put(3, 500);     // 3레벨: 500exp
        expRequirements.put(4, 800);     // 4레벨: 800exp
        expRequirements.put(5, 1200);    // 5레벨: 1200exp (특수 효과)
        expRequirements.put(6, 1700);    // 6레벨: 1700exp
        expRequirements.put(7, 2300);    // 7레벨: 2300exp
        expRequirements.put(8, 3000);    // 8레벨: 3000exp
        expRequirements.put(9, 3800);    // 9레벨: 3800exp
        expRequirements.put(10, 4700);   // 10레벨: 4700exp (만렙)
    }

    /**
     * 몬스터별 경험치 초기화
     */
    private void initializeMonsterExp() {
        // 일반 몬스터
        monsterExp.put(EntityType.ZOMBIE, 10);
        monsterExp.put(EntityType.SKELETON, 10);
        monsterExp.put(EntityType.SPIDER, 8);
        monsterExp.put(EntityType.CREEPER, 12);
        monsterExp.put(EntityType.SLIME, 5);

        // 중간 몬스터
        monsterExp.put(EntityType.ENDERMAN, 25);
        monsterExp.put(EntityType.WITCH, 20);
        monsterExp.put(EntityType.PILLAGER, 15);
        monsterExp.put(EntityType.VINDICATOR, 18);

        // 강한 몬스터
        monsterExp.put(EntityType.WITHER_SKELETON, 40);
        monsterExp.put(EntityType.BLAZE, 30);
        monsterExp.put(EntityType.GHAST, 35);

        // 보스 몬스터
        monsterExp.put(EntityType.ENDER_DRAGON, 1000);
        monsterExp.put(EntityType.WITHER, 500);
        monsterExp.put(EntityType.ELDER_GUARDIAN, 200);
    }

    /**
     * 데이터베이스 테이블 생성
     */
    private void createJobTables() {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS player_jobs (
                    uuid VARCHAR(36) PRIMARY KEY,
                    job_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
                    job_level INT NOT NULL DEFAULT 1,
                    job_experience INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("직업 레벨 시스템 데이터베이스 테이블 준비 완료");

        } catch (SQLException e) {
            plugin.getLogger().severe("직업 데이터베이스 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 플레이어 접속 시 직업 정보 로드
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadPlayerJobData(player);

        // 60초 후 직업 선택 안내
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && getJobType(player) == JobType.NONE) {
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§l⚔️ 직업을 선택하세요!");
                player.sendMessage("§7직업을 선택하면 전투에서 더 강해집니다!");
                player.sendMessage("§7몬스터를 처치하여 직업 레벨을 올리세요!");
                player.sendMessage("§6새로운 기능: §f경험치바 UI & 10렙 만렙 효과!");
                player.sendMessage("§a명령어: §f/job select");
                player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }
        }, 1200L); // 60초
    }

    /**
     * 플레이어 퇴장 시 직업 정보 저장
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        savePlayerJobData(player);

        // 패시브 효과 정리
        removeArcherSpeedBonus(player);

        // 캐시에서 제거
        UUID uuid = player.getUniqueId();
        jobTypes.remove(uuid);
        jobLevels.remove(uuid);
        jobExperience.remove(uuid);
        shieldDefenseCooldown.remove(uuid);
    }

    /**
     * 몬스터 처치 이벤트 - 경험치 획득
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;
        if (getJobType(killer) == JobType.NONE) return;

        // 몬스터별 경험치 확인
        int exp = monsterExp.getOrDefault(entity.getType(), 0);
        if (exp > 0) {
            addJobExperience(killer, exp);

            // 경험치 획득 메시지 (채팅 대신 타이틀로)
            killer.sendTitle("§a+ " + exp + " 경험치", "§7" + entity.getType().name(), 5, 20, 5);
        }
    }

    /**
     * 직업 경험치 추가
     */
    public void addJobExperience(Player player, int exp) {
        UUID uuid = player.getUniqueId();
        int currentLevel = getJobLevel(player);
        int currentExp = getJobExperience(player);

        if (currentLevel >= 10) return; // 만렙

        int newExp = currentExp + exp;
        jobExperience.put(uuid, newExp);

        // 레벨업 확인
        int requiredExp = expRequirements.getOrDefault(currentLevel + 1, Integer.MAX_VALUE);

        if (newExp >= requiredExp && currentLevel < 10) {
            levelUpJob(player);
        }

        // 경험치 저장
        savePlayerJobData(player);

        // 즉시 경험치바 업데이트
        updatePlayerExpBar(player);
    }

    /**
     * 직업 레벨업
     */
    private void levelUpJob(Player player) {
        UUID uuid = player.getUniqueId();
        int currentLevel = getJobLevel(player);
        int newLevel = currentLevel + 1;

        jobLevels.put(uuid, newLevel);

        // 레벨업 메시지
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e⚡ 직업 레벨업! §f" + currentLevel + " → " + newLevel);
        player.sendMessage("§7직업 능력이 강화되었습니다!");

        JobType job = getJobType(player);
        switch (job) {
            case TANK:
                player.sendMessage("§9탱커 §7- 방어력과 체력 회복 강화");
                if (newLevel == 5) player.sendMessage("§6레벨 5: 흉갑 착용시 체력 +2칸");
                if (newLevel == 10) player.sendMessage("§6★ 만렙 달성: 흉갑 착용시 추가 +2칸 (총 4칸)");
                break;
            case WARRIOR:
                player.sendMessage("§c검사 §7- 검 공격력 강화");
                if (newLevel == 5) player.sendMessage("§6레벨 5: 검 사용시 공격속도 증가");
                if (newLevel == 10) player.sendMessage("§6★ 만렙 달성: 10% 확률 크리티컬 (2.5배 데미지)");
                break;
            case ARCHER:
                player.sendMessage("§a궁수 §7- 활 공격력과 이동속도 강화");
                if (newLevel == 5) player.sendMessage("§6레벨 5: 가죽장화 착용시 이동속도 +20%");
                if (newLevel == 10) player.sendMessage("§6★ 만렙 달성: 50% 확률로 화살 3발 발사");
                break;
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 레벨업 효과
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK,
                player.getLocation().add(0, 1, 0), 30, 1, 1, 1);

        // 직업 효과 재적용
        applyJobEffects(player);

        // 스코어보드 업데이트 알림
        if (scoreboardIntegration != null) {
            scoreboardIntegration.notifyLevelUp(player);
        }

        plugin.getLogger().info(String.format("[직업레벨업] %s: %s %d레벨 달성",
                player.getName(), job.getDisplayName(), newLevel));
    }

    /**
     * 공격 이벤트 - 직업별 데미지 보너스 적용 + 10렙 효과
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player player = (Player) event.getDamager();
        JobType job = getJobType(player);

        if (job == JobType.NONE) return;

        int level = getJobLevel(player);
        ItemStack weapon = player.getInventory().getItemInMainHand();
        double damage = event.getDamage();
        double bonus = 0;

        switch (job) {
            case WARRIOR:
                // 검 사용 시 직업레벨당 위력 증가
                if (isSword(weapon.getType())) {
                    bonus = damage * (level * 0.05); // 레벨당 5% 증가

                    // 레벨 5 이상 시 공격속도 효과
                    if (level >= 5) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FAST_DIGGING, 60, 0));
                        }, 1L);
                    }

                    // 10렙 만렙 효과: 10% 크리티컬 (2.5배 데미지)
                    if (level == 10) {
                        Random random = new Random();
                        if (random.nextInt(100) < 10) { // 10% 확률
                            double criticalDamage = damage * 2.5;
                            event.setDamage(criticalDamage);

                            // 크리티컬 효과
                            player.sendTitle("§c§l크리티컬!", "§62.5배 데미지!", 5, 20, 5);
                            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1.0f, 2.0f);
                            player.getWorld().spawnParticle(Particle.CRIT,
                                    event.getEntity().getLocation().add(0, 1, 0), 20, 1, 1, 1);

                            plugin.getLogger().info(player.getName() + "이(가) 크리티컬 발동! (" + String.format("%.1f", criticalDamage) + " 데미지)");
                            return; // bonus 적용하지 않고 리턴
                        }
                    }
                }
                break;

            case ARCHER:
                // 활 사용 시 직업레벨당 위력 증가 (투사체는 별도 처리)
                if (weapon.getType() == Material.BOW || weapon.getType() == Material.CROSSBOW) {
                    bonus = damage * (level * 0.04); // 레벨당 4% 증가
                }
                break;
        }

        if (bonus > 0) {
            event.setDamage(damage + bonus);
        }
    }

    /**
     * 활 발사 이벤트 - 궁수 10렙 화살 3발 효과
     */
    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        JobType job = getJobType(player);

        if (job != JobType.ARCHER) return;

        int level = getJobLevel(player);

        // 10렙 만렙 효과: 50% 확률로 화살 3발
        if (level == 10) {
            Random random = new Random();
            if (random.nextInt(100) < 50) { // 50% 확률

                // 추가 화살 2발 발사
                Projectile originalArrow = (Projectile) event.getProjectile();
                Vector direction = originalArrow.getVelocity();

                // 약간 다른 각도로 2발 더 발사
                for (int i = 0; i < 2; i++) {
                    Projectile extraArrow = player.launchProjectile(Arrow.class);
                    Vector modifiedDirection = direction.clone();

                    // 각도 약간 조정 (5도씩)
                    double angle = Math.toRadians((i + 1) * 5 * (i % 2 == 0 ? 1 : -1));
                    modifiedDirection.rotateAroundY(angle);

                    extraArrow.setVelocity(modifiedDirection);
                    extraArrow.setShooter(player);
                }

                // 효과 표시
                player.sendActionBar("§a💫 트리플 샷 발동! §73발의 화살!");
                player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.5f);
                player.getWorld().spawnParticle(Particle.CRIT_MAGIC,
                        player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);

                plugin.getLogger().info(player.getName() + "이(가) 트리플 샷 발동!");
            }
        }
    }

    /**
     * 피해 이벤트 - 탱커 방패 방어 시 체력 회복
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        JobType job = getJobType(player);

        if (job != JobType.TANK) return;
        if (!player.isBlocking()) return; // 방패로 막고 있지 않음

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastDefense = shieldDefenseCooldown.getOrDefault(uuid, 0L);

        // 3초 쿨다운
        if (now - lastDefense < 3000) return;

        shieldDefenseCooldown.put(uuid, now);

        // 직업레벨에 따른 체력 회복
        int level = getJobLevel(player);
        double healAmount = 1.0 + (level * 0.5); // 레벨당 0.5 체력 추가 회복

        double currentHealth = player.getHealth();
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double newHealth = Math.min(maxHealth, currentHealth + healAmount);

        player.setHealth(newHealth);

        // 회복 효과
        player.sendActionBar("§9🛡️ 방패 방어! §a+" + String.format("%.1f", healAmount) + " 체력 회복");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        player.getWorld().spawnParticle(Particle.HEART,
                player.getLocation().add(0, 2, 0), 3, 0.5, 0.3, 0.5);
    }

    /**
     * 아이템 장착 이벤트 - 레벨 5+ & 10렙 특수 효과
     */
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        // 다음 틱에 효과 적용 (아이템 변경 후)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyJobEffects(player);
        }, 1L);
    }

    /**
     * 인벤토리 클릭 이벤트 - 장비 변경 감지
     */
    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // 장비 슬롯 변경 감지 (부츠 슬롯 포함)
        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
            // 다음 틱에 효과 적용 (장비 변경 후)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyJobEffects(player);
            }, 1L);
        }
    }

    /**
     * 직업 효과 적용 - 5렙 & 10렙 효과
     */
    public void applyJobEffects(Player player) {
        JobType job = getJobType(player);
        int level = getJobLevel(player);

        if (job == JobType.NONE) return;

        switch (job) {
            case TANK:
                applyTankEffects(player, level);
                break;
            case WARRIOR:
                applyWarriorEffects(player, level);
                break;
            case ARCHER:
                applyArcherEffects(player, level);
                break;
        }
    }

    /**
     * 탱커 효과 적용 (5렙: +2칸, 10렙: +4칸)
     */
    private void applyTankEffects(Player player, int level) {
        ItemStack chestplate = player.getInventory().getChestplate();

        // 흉갑 착용 시 체력 증가
        if (chestplate != null && chestplate.getType().name().contains("CHESTPLATE")) {
            double bonusHealth = 0;

            if (level >= 5 && level < 10) {
                bonusHealth = 4.0; // 5렙: +2칸 (24HP)
            } else if (level >= 10) {
                bonusHealth = 8.0; // 10렙: +4칸 (28HP)
            }

            if (bonusHealth > 0) {
                double maxHealth = 20.0 + bonusHealth;
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
            }
        } else {
            // 흉갑을 벗으면 원래 체력으로
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
            if (player.getHealth() > 20.0) {
                player.setHealth(20.0);
            }
        }
    }

    /**
     * 검사 효과 적용 (5렙: 공격속도, 10렙: 크리티컬)
     */
    private void applyWarriorEffects(Player player, int level) {
        // 5렙+ 공격속도는 공격 이벤트에서 처리
        // 10렙 크리티컬은 공격 이벤트에서 처리
    }

    /**
     * 궁수 효과 적용 (5렙: 가죽장화 착용 중 패시브 이동속도, 10렙: 트리플 샷)
     */
    private void applyArcherEffects(Player player, int level) {
        if (level < 5) {
            // 5레벨 미만이면 기존 속도 효과 제거
            removeArcherSpeedBonus(player);
            return;
        }

        ItemStack boots = player.getInventory().getBoots();

        // 가죽장화 착용 중 패시브 이동속도 +20%
        if (boots != null && boots.getType() == Material.LEATHER_BOOTS) {
            applyArcherSpeedBonus(player);
        } else {
            // 가죽장화를 벗었을 때 패시브 효과 제거
            removeArcherSpeedBonus(player);
        }

        // 10렙 트리플 샷은 활 발사 이벤트에서 처리
    }

    /**
     * 궁수 패시브 이동속도 보너스 적용
     */
    private void applyArcherSpeedBonus(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) return;

        // 기존 궁수 속도 modifier가 있는지 확인
        AttributeModifier existingModifier = null;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getName().equals("archer_leather_boots_speed")) {
                existingModifier = modifier;
                break;
            }
        }

        // 이미 적용되어 있으면 무시
        if (existingModifier != null) return;

        // 새로운 패시브 속도 보너스 적용 (20% 증가)
        // NamespacedKey를 UUID로 변환
        UUID modifierUUID = UUID.nameUUIDFromBytes(archerSpeedKey.toString().getBytes());

        AttributeModifier speedModifier = new AttributeModifier(
                modifierUUID,  // UUID로 변환
                "archer_leather_boots_speed",
                0.02, // 기본 이동속도 0.1에 0.02 추가 = 20% 증가
                AttributeModifier.Operation.ADD_NUMBER
        );

        attribute.addModifier(speedModifier);

        // 첫 적용시 메시지
        player.sendActionBar("§a🏃 가죽장화 패시브: 이동속도 +20%");
    }

    /**
     * 궁수 패시브 이동속도 보너스 제거
     */
    private void removeArcherSpeedBonus(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) return;

        // 궁수 속도 modifier 찾아서 제거
        AttributeModifier toRemove = null;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getName().equals("archer_leather_boots_speed")) {
                toRemove = modifier;
                break;
            }
        }

        if (toRemove != null) {
            attribute.removeModifier(toRemove);
            player.sendActionBar("§7가죽장화 패시브 효과 해제");
        }
    }

    /**
     * 검인지 확인
     */
    private boolean isSword(Material material) {
        return material.name().contains("SWORD");
    }

    /**
     * 플레이어 직업 데이터 로드
     */
    private void loadPlayerJobData(Player player) {
        UUID uuid = player.getUniqueId();

        try (Connection connection = databaseManager.getConnection()) {
            String sql = "SELECT job_type, job_level, job_experience FROM player_jobs WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        JobType job = JobType.valueOf(rs.getString("job_type"));
                        int level = rs.getInt("job_level");
                        int exp = rs.getInt("job_experience");

                        jobTypes.put(uuid, job);
                        jobLevels.put(uuid, level);
                        jobExperience.put(uuid, exp);
                    } else {
                        // 새 플레이어
                        jobTypes.put(uuid, JobType.NONE);
                        jobLevels.put(uuid, 1);
                        jobExperience.put(uuid, 0);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("플레이어 직업 데이터 로드 실패: " + e.getMessage());
            // 기본값 설정
            jobTypes.put(uuid, JobType.NONE);
            jobLevels.put(uuid, 1);
            jobExperience.put(uuid, 0);
        }
    }

    /**
     * 플레이어 직업 데이터 저장
     */
    private void savePlayerJobData(Player player) {
        UUID uuid = player.getUniqueId();
        JobType job = jobTypes.getOrDefault(uuid, JobType.NONE);
        int level = jobLevels.getOrDefault(uuid, 1);
        int exp = jobExperience.getOrDefault(uuid, 0);

        try (Connection connection = databaseManager.getConnection()) {
            String sql = """
                INSERT INTO player_jobs (uuid, job_type, job_level, job_experience) 
                VALUES (?, ?, ?, ?) 
                ON DUPLICATE KEY UPDATE 
                job_type = VALUES(job_type), 
                job_level = VALUES(job_level), 
                job_experience = VALUES(job_experience)
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, job.name());
                stmt.setInt(3, level);
                stmt.setInt(4, exp);
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("플레이어 직업 데이터 저장 실패: " + e.getMessage());
        }
    }

    // 공개 메서드들...

    public JobType getJobType(Player player) {
        return jobTypes.getOrDefault(player.getUniqueId(), JobType.NONE);
    }

    /**
     * 스코어보드 연동용 메소드 - GGMCore에서 호출
     */
    public JobType getCachedJob(UUID uuid) {
        return jobTypes.getOrDefault(uuid, JobType.NONE);
    }

    /**
     * 스코어보드용 직업 표시 이름 가져오기
     */
    public String getJobDisplayForScoreboard(Player player) {
        JobType job = getJobType(player);
        int level = getJobLevel(player);

        if (job == JobType.NONE) {
            return "§7직업 없음";
        }

        String color = job.getColor();
        String name = job.getDisplayName();

        if (level >= 10) {
            return color + "★" + name + " §6만렙";
        } else {
            return color + name + " §fLv." + level;
        }
    }

    public int getJobLevel(Player player) {
        return jobLevels.getOrDefault(player.getUniqueId(), 1);
    }

    public int getJobExperience(Player player) {
        return jobExperience.getOrDefault(player.getUniqueId(), 0);
    }

    public boolean setJobType(Player player, JobType job) {
        if (getJobType(player) != JobType.NONE) {
            return false; // 이미 직업이 있음
        }

        UUID uuid = player.getUniqueId();
        jobTypes.put(uuid, job);
        jobLevels.put(uuid, 1);
        jobExperience.put(uuid, 0);

        savePlayerJobData(player);
        applyJobEffects(player);

        // 스코어보드 업데이트 알림
        if (scoreboardIntegration != null) {
            scoreboardIntegration.notifyJobChange(player);
        }

        return true;
    }

    /**
     * 직업 초기화 (관리자용)
     */
    public void resetJob(Player player) {
        UUID uuid = player.getUniqueId();
        jobTypes.put(uuid, JobType.NONE);
        jobLevels.put(uuid, 1);
        jobExperience.put(uuid, 0);

        // 모든 직업 효과 제거
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        if (player.getHealth() > 20.0) {
            player.setHealth(20.0);
        }

        // 궁수 패시브 이동속도 효과 제거
        removeArcherSpeedBonus(player);

        // 경험치바 초기화
        player.setLevel(0);
        player.setExp(0.0f);

        savePlayerJobData(player);

        // 스코어보드 업데이트 알림
        if (scoreboardIntegration != null) {
            scoreboardIntegration.notifyJobChange(player);
        }
    }

    /**
     * 직업 레벨 설정 (관리자용)
     */
    public void setJobLevel(Player player, int level) {
        if (level < 1 || level > 10) return;

        UUID uuid = player.getUniqueId();
        jobLevels.put(uuid, level);

        // 해당 레벨에 맞는 경험치 설정
        if (level > 1) {
            int exp = expRequirements.getOrDefault(level, 0);
            jobExperience.put(uuid, exp);
        } else {
            jobExperience.put(uuid, 0);
        }

        savePlayerJobData(player);
        applyJobEffects(player);
        updatePlayerExpBar(player);

        // 스코어보드 업데이트 알림
        if (scoreboardIntegration != null) {
            scoreboardIntegration.notifyLevelUp(player);
        }

        player.sendMessage("§a직업 레벨이 " + level + "로 설정되었습니다!");
    }

    /**
     * 직업 정보 표시
     */
    public void showJobInfo(Player player) {
        JobType job = getJobType(player);
        int level = getJobLevel(player);
        int exp = getJobExperience(player);

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l⚔️ 직업 정보 (새로운 UI)");
        player.sendMessage("§7직업: " + job.getColor() + job.getDisplayName());
        player.sendMessage("§7레벨: §f" + level + " / 10" + (level == 10 ? " §6★만렙★" : ""));

        if (level < 10) {
            int nextExp = expRequirements.getOrDefault(level + 1, 0);
            int remaining = nextExp - exp;
            player.sendMessage("§7경험치: §f" + exp + " / " + nextExp + " §7(§c-" + remaining + "§7)");
        } else {
            player.sendMessage("§7경험치: §6만렙 달성!");
        }

        // 특수 효과 표시
        player.sendMessage("");
        if (level >= 5) {
            player.sendMessage("§a✓ 레벨 5 특수 능력 활성화");
        }
        if (level >= 10) {
            player.sendMessage("§6★ 만렙 특수 능력 활성화");
            switch (job) {
                case TANK:
                    player.sendMessage("§9  • 흉갑 착용시 체력 +4칸 (28HP)");
                    break;
                case WARRIOR:
                    player.sendMessage("§c  • 검 공격시 10% 크리티컬 (2.5배)");
                    break;
                case ARCHER:
                    player.sendMessage("§a  • 50% 확률 화살 3발 발사");
                    break;
            }
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 스코어보드 연동 시스템 getter
     */
    public ScoreboardIntegration getScoreboardIntegration() {
        return scoreboardIntegration;
    }

    /**
     * 플러그인 종료 시 정리
     */
    public void onDisable() {
        if (expBarTask != null) {
            expBarTask.cancel();
        }
    }
}