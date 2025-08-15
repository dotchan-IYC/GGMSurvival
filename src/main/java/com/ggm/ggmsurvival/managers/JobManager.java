// 완전 안정화된 JobManager.java
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class JobManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;

    // Thread-Safe 컬렉션들
    private final ConcurrentHashMap<UUID, JobType> jobTypes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> jobLevels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> jobExperience = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> shieldDefenseCooldown = new ConcurrentHashMap<>();

    // 경험치 및 몬스터 설정 (불변 맵)
    private final Map<Integer, Integer> expRequirements;
    private final Map<EntityType, Integer> monsterExp;

    // 스케줄러 관리
    private BukkitTask expBarUpdateTask;
    private final Set<UUID> playersNeedingExpUpdate = ConcurrentHashMap.newKeySet();

    // 스코어보드 통합
    private ScoreboardIntegration scoreboardIntegration;

    // 성능 최적화를 위한 캐시
    private final Map<UUID, Long> lastExpGainTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> cachedActionBars = new ConcurrentHashMap<>();

    // 키 상수들
    private final NamespacedKey archerSpeedKey;

    public JobManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.archerSpeedKey = new NamespacedKey(plugin, "archer_speed");

        // 불변 맵 초기화
        this.expRequirements = initializeExpRequirements();
        this.monsterExp = initializeMonsterExp();

        try {
            // 데이터베이스 테이블 생성
            createJobTables();

            // 스코어보드 통합 초기화
            initializeScoreboardIntegration();

            // 경험치바 업데이트 태스크 시작
            startExpBarUpdateTask();

            plugin.getLogger().info("JobManager 안정화 초기화 완료");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "JobManager 초기화 실패", e);
            throw new RuntimeException("JobManager 초기화 실패", e);
        }
    }

    /**
     * 직업 타입 열거형
     */
    public enum JobType {
        NONE("직업 없음", "7"),
        TANK("탱커", "9"),
        WARRIOR("검사", "c"),
        ARCHER("궁수", "a");

        private final String displayName;
        private final String colorCode;

        JobType(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return "§" + colorCode;
        }
    }

    /**
     * 스코어보드 통합 인터페이스
     */
    public interface ScoreboardIntegration {
        void notifyJobChange(Player player);
        void notifyLevelUp(Player player);
    }

    /**
     * 경험치 요구량 초기화 (불변)
     */
    private Map<Integer, Integer> initializeExpRequirements() {
        Map<Integer, Integer> requirements = new HashMap<>();
        requirements.put(1, 100);     // 1레벨: 100exp
        requirements.put(2, 250);     // 2레벨: 250exp
        requirements.put(3, 500);     // 3레벨: 500exp
        requirements.put(4, 800);     // 4레벨: 800exp
        requirements.put(5, 1200);    // 5레벨: 1200exp (특수 효과)
        requirements.put(6, 1700);    // 6레벨: 1700exp
        requirements.put(7, 2300);    // 7레벨: 2300exp
        requirements.put(8, 3000);    // 8레벨: 3000exp
        requirements.put(9, 3800);    // 9레벨: 3800exp
        requirements.put(10, 4700);   // 10레벨: 4700exp (만렙)
        return Collections.unmodifiableMap(requirements);
    }

    /**
     * 몬스터별 경험치 초기화 (불변)
     */
    private Map<EntityType, Integer> initializeMonsterExp() {
        Map<EntityType, Integer> exp = new HashMap<>();

        // 일반 몬스터
        exp.put(EntityType.ZOMBIE, 10);
        exp.put(EntityType.SKELETON, 10);
        exp.put(EntityType.SPIDER, 8);
        exp.put(EntityType.CREEPER, 12);
        exp.put(EntityType.SLIME, 5);

        // 중간 몬스터
        exp.put(EntityType.ENDERMAN, 25);
        exp.put(EntityType.WITCH, 20);
        exp.put(EntityType.PILLAGER, 15);
        exp.put(EntityType.VINDICATOR, 18);

        // 강한 몬스터
        exp.put(EntityType.WITHER_SKELETON, 40);
        exp.put(EntityType.BLAZE, 30);
        exp.put(EntityType.GHAST, 35);

        // 보스 몬스터
        exp.put(EntityType.ENDER_DRAGON, 1000);
        exp.put(EntityType.WITHER, 500);
        exp.put(EntityType.ELDER_GUARDIAN, 200);

        return Collections.unmodifiableMap(exp);
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
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_job_type (job_type),
                    INDEX idx_job_level (job_level)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("직업 데이터베이스 테이블 생성 완료");

        } catch (SQLException e) {
            throw new RuntimeException("직업 테이블 생성 실패", e);
        }
    }

    /**
     * 스코어보드 통합 초기화
     */
    private void initializeScoreboardIntegration() {
        this.scoreboardIntegration = new ScoreboardIntegration() {
            @Override
            public void notifyJobChange(Player player) {
                // GGMCore의 스코어보드 업데이트 (있다면)
                if (plugin.getScoreboardIntegration() != null) {
                    try {
                        plugin.getScoreboardIntegration().notifyJobChange(player);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "스코어보드 직업 변경 알림 실패: " + player.getName(), e);
                    }
                }
            }

            @Override
            public void notifyLevelUp(Player player) {
                if (plugin.getScoreboardIntegration() != null) {
                    try {
                        plugin.getScoreboardIntegration().notifyLevelUp(player);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "스코어보드 레벨업 알림 실패: " + player.getName(), e);
                    }
                }
            }
        };
    }

    /**
     * 경험치바 업데이트 태스크 시작
     */
    private void startExpBarUpdateTask() {
        expBarUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.isShuttingDown()) {
                    cancel();
                    return;
                }

                try {
                    // 업데이트가 필요한 플레이어들만 처리
                    Iterator<UUID> iterator = playersNeedingExpUpdate.iterator();
                    while (iterator.hasNext()) {
                        UUID uuid = iterator.next();
                        Player player = Bukkit.getPlayer(uuid);

                        if (player != null && player.isOnline()) {
                            updatePlayerExpBarSafe(player);
                        }

                        iterator.remove();
                    }

                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "경험치바 업데이트 중 오류", e);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1초마다 실행
    }

    /**
     * 플레이어 접속 이벤트
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            // 비동기로 플레이어 데이터 로드
            CompletableFuture.runAsync(() -> loadPlayerJobData(player))
                    .thenRun(() -> {
                        // 메인 스레드에서 효과 적용
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            applyJobEffects(player);
                            markForExpUpdate(player);
                        });
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.WARNING,
                                "플레이어 직업 데이터 로드 실패: " + player.getName(), throwable);
                        return null;
                    });

            // 직업 선택 안내 (지연)
            scheduleJobSelectionReminder(player);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 접속 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 플레이어 퇴장 이벤트
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        try {
            // 비동기로 데이터 저장
            CompletableFuture.runAsync(() -> savePlayerJobData(player));

            // 패시브 효과 정리
            removeArcherSpeedBonus(player);

            // 캐시에서 제거
            jobTypes.remove(uuid);
            jobLevels.remove(uuid);
            jobExperience.remove(uuid);
            shieldDefenseCooldown.remove(uuid);
            lastExpGainTime.remove(uuid);
            cachedActionBars.remove(uuid);
            playersNeedingExpUpdate.remove(uuid);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 퇴장 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 몬스터 처치 이벤트 - 경험치 획득
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null || getJobType(killer) == JobType.NONE) return;

        try {
            int exp = monsterExp.getOrDefault(entity.getType(), 0);
            if (exp > 0) {
                // 중복 경험치 획득 방지 (1초 쿨다운)
                long currentTime = System.currentTimeMillis();
                Long lastTime = lastExpGainTime.get(killer.getUniqueId());

                if (lastTime != null && currentTime - lastTime < 1000) {
                    return; // 쿨다운 중
                }

                lastExpGainTime.put(killer.getUniqueId(), currentTime);
                addJobExperience(killer, exp, entity.getType());
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "몬스터 처치 경험치 처리 중 오류: " + killer.getName(), e);
        }
    }

    /**
     * 방패 방어 이벤트 - 탱커 패시브
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onShieldDefense(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        try {
            if (getJobType(player) != JobType.TANK) return;
            if (getJobLevel(player) < 5) return;

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();

            boolean hasShield = mainHand.getType() == Material.SHIELD ||
                    offHand.getType() == Material.SHIELD;

            if (!hasShield) return;

            // 방패 방어 확률 체크 (방패 효과)
            if (ThreadLocalRandom.current().nextDouble() < 0.3) { // 30% 확률
                UUID uuid = player.getUniqueId();
                long currentTime = System.currentTimeMillis();

                Long lastDefense = shieldDefenseCooldown.get(uuid);
                if (lastDefense != null && currentTime - lastDefense < 3000) {
                    return; // 3초 쿨다운
                }

                shieldDefenseCooldown.put(uuid, currentTime);

                // 체력 회복
                double healAmount = plugin.getConfig().getDouble("job_system.jobs.tank.shield_heal_amount", 1.0);
                double currentHealth = player.getHealth();
                double maxHealth = player.getMaxHealth();

                if (currentHealth < maxHealth) {
                    player.setHealth(Math.min(maxHealth, currentHealth + healAmount));

                    // 메시지 표시
                    String message = plugin.getConfig().getString("messages.tank_shield_heal",
                                    "방패 방어! +{amount} 체력 회복")
                            .replace("{amount}", String.valueOf((int)healAmount));

                    player.sendActionBar("§9" + message);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "방패 방어 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 검 공격 이벤트 - 검사 패시브
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwordAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        try {
            if (getJobType(player) != JobType.WARRIOR) return;

            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (!isSword(weapon.getType())) return;

            int level = getJobLevel(player);

            // 레벨 10 크리티컬 효과
            if (level >= 10) {
                int criticalChance = plugin.getConfig().getInt("job_system.jobs.warrior.level10_critical_chance", 10);

                if (ThreadLocalRandom.current().nextInt(100) < criticalChance) {
                    double multiplier = plugin.getConfig().getDouble("job_system.jobs.warrior.critical_multiplier", 2.5);
                    event.setDamage(event.getDamage() * multiplier);

                    String message = plugin.getConfig().getString("messages.warrior_critical_hit",
                            "크리티컬 발동! 2.5배 데미지!");
                    player.sendActionBar("§c" + message);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "검 공격 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 활 발사 이벤트 - 궁수 패시브
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        try {
            if (getJobType(player) != JobType.ARCHER) return;
            if (getJobLevel(player) < 10) return;

            // 10레벨 트리플 샷
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    Arrow originalArrow = (Arrow) event.getProjectile();

                    // 추가 화살 2발 발사
                    for (int i = 0; i < 2; i++) {
                        Arrow extraArrow = player.launchProjectile(Arrow.class, originalArrow.getVelocity());
                        extraArrow.setShooter(player);

                        // 약간의 각도 변화
                        double angleOffset = (i + 1) * 0.1 * (i % 2 == 0 ? 1 : -1);
                        extraArrow.setVelocity(extraArrow.getVelocity().rotateAroundY(angleOffset));
                    }

                    String message = plugin.getConfig().getString("messages.archer_triple_shot",
                            "트리플 샷! 화살 3발 발사!");
                    player.sendActionBar("§a" + message);

                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "트리플 샷 처리 중 오류: " + player.getName(), e);
                }
            }, 1L);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "활 발사 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 직업 경험치 추가 - Thread-Safe
     */
    public void addJobExperience(Player player, int exp, EntityType monster) {
        UUID uuid = player.getUniqueId();

        synchronized (this) { // 동기화로 데이터 일관성 보장
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

            // 비동기로 데이터 저장
            CompletableFuture.runAsync(() -> savePlayerJobData(player));

            // 경험치바 업데이트 마크
            markForExpUpdate(player);

            // 경험치 획득 메시지 (타이틀로)
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    player.sendTitle("§a+ " + exp + " 경험치",
                            "§7" + monster.name(), 5, 20, 5);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "경험치 메시지 표시 실패: " + player.getName(), e);
                }
            });
        }
    }

    /**
     * 직업 레벨업 - 메인 스레드에서 실행
     */
    private void levelUpJob(Player player) {
        UUID uuid = player.getUniqueId();
        int currentLevel = getJobLevel(player);
        int newLevel = currentLevel + 1;

        jobLevels.put(uuid, newLevel);

        // 메인 스레드에서 UI 업데이트
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // 레벨업 메시지
                player.sendMessage("§6==========================================");
                player.sendMessage("§e직업 레벨업! §f" + currentLevel + " → " + newLevel);
                player.sendMessage("§7직업 능력이 강화되었습니다!");

                // 특수 레벨 메시지
                if (newLevel == 5) {
                    player.sendMessage("§a레벨 5 달성! 특수 능력이 해제되었습니다!");
                } else if (newLevel == 10) {
                    String message = plugin.getConfig().getString("messages.job_max_level",
                            "만렙 10 달성! 최강의 능력을 얻었습니다!");
                    player.sendMessage("§6" + message);

                    // 직업별 만렙 메시지
                    JobType job = getJobType(player);
                    switch (job) {
                        case TANK:
                            String tankMsg = plugin.getConfig().getString("messages.tank_max_level_effect",
                                    "탱커 만렙: 흉갑 착용시 체력 +4칸 (28HP)");
                            player.sendMessage("§9" + tankMsg);
                            break;
                        case WARRIOR:
                            player.sendMessage("§c검사 만렙: 검 공격시 10% 크리티컬 확률!");
                            break;
                        case ARCHER:
                            player.sendMessage("§a궁수 만렙: 활 발사시 화살 3발 동시 발사!");
                            break;
                    }
                }

                player.sendMessage("§6==========================================");

                // 효과음
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // 직업 효과 재적용
                applyJobEffects(player);

                // 스코어보드 업데이트
                if (scoreboardIntegration != null) {
                    scoreboardIntegration.notifyLevelUp(player);
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "레벨업 처리 중 오류: " + player.getName(), e);
            }
        });
    }

    /**
     * 직업 효과 적용 - 메인 스레드에서만 호출
     */
    public void applyJobEffects(Player player) {
        try {
            JobType job = getJobType(player);
            int level = getJobLevel(player);

            if (job == JobType.NONE) return;

            // 탱커 체력 보너스
            if (job == JobType.TANK && level >= 5) {
                applyTankHealthBonus(player, level);
            }

            // 궁수 이동속도 보너스 (가죽장화 착용 시)
            if (job == JobType.ARCHER && level >= 5) {
                checkAndApplyArcherSpeedBonus(player);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 효과 적용 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 탱커 체력 보너스 적용
     */
    private void applyTankHealthBonus(Player player, int level) {
        try {
            ItemStack chestplate = player.getInventory().getChestplate();
            if (chestplate == null || !chestplate.getType().name().contains("CHESTPLATE")) {
                return;
            }

            AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (healthAttr == null) return;

            // 기존 탱커 보너스 제거
            healthAttr.getModifiers().stream()
                    .filter(mod -> mod.getName().equals("tank_health_bonus"))
                    .findFirst()
                    .ifPresent(healthAttr::removeModifier);

            // 새 보너스 적용
            double healthBonus = level >= 10 ? 8.0 : 4.0; // 10레벨: +4칸, 5레벨: +2칸

            UUID modifierUUID = UUID.nameUUIDFromBytes("tank_health_bonus".getBytes());
            AttributeModifier healthModifier = new AttributeModifier(
                    modifierUUID,
                    "tank_health_bonus",
                    healthBonus,
                    AttributeModifier.Operation.ADD_NUMBER
            );

            healthAttr.addModifier(healthModifier);

            // 현재 체력이 새로운 최대 체력을 초과하지 않도록 조정
            if (player.getHealth() > healthAttr.getValue()) {
                player.setHealth(healthAttr.getValue());
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "탱커 체력 보너스 적용 실패: " + player.getName(), e);
        }
    }

    /**
     * 궁수 이동속도 보너스 확인 및 적용
     */
    private void checkAndApplyArcherSpeedBonus(Player player) {
        try {
            ItemStack boots = player.getInventory().getBoots();
            boolean hasLeatherBoots = boots != null && boots.getType() == Material.LEATHER_BOOTS;

            AttributeInstance speedAttr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speedAttr == null) return;

            // 기존 궁수 속도 보너스 제거
            speedAttr.getModifiers().stream()
                    .filter(mod -> mod.getName().equals("archer_leather_boots_speed"))
                    .findFirst()
                    .ifPresent(speedAttr::removeModifier);

            // 가죽장화 착용 시 속도 보너스 적용
            if (hasLeatherBoots) {
                applyArcherSpeedBonus(player);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "궁수 속도 보너스 확인 실패: " + player.getName(), e);
        }
    }

    /**
     * 궁수 패시브 이동속도 보너스 적용
     */
    private void applyArcherSpeedBonus(Player player) {
        try {
            AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (attribute == null) return;

            UUID modifierUUID = UUID.nameUUIDFromBytes(archerSpeedKey.toString().getBytes());

            AttributeModifier speedModifier = new AttributeModifier(
                    modifierUUID,
                    "archer_leather_boots_speed",
                    0.02, // 20% 증가
                    AttributeModifier.Operation.ADD_NUMBER
            );

            attribute.addModifier(speedModifier);

            String message = plugin.getConfig().getString("messages.archer_speed_boost",
                    "가죽장화 패시브: 이동속도 +20%");
            player.sendActionBar("§a" + message);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "궁수 속도 보너스 적용 실패: " + player.getName(), e);
        }
    }

    /**
     * 궁수 패시브 이동속도 보너스 제거
     */
    private void removeArcherSpeedBonus(Player player) {
        try {
            AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (attribute == null) return;

            attribute.getModifiers().stream()
                    .filter(modifier -> modifier.getName().equals("archer_leather_boots_speed"))
                    .findFirst()
                    .ifPresent(attribute::removeModifier);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "궁수 속도 보너스 제거 실패: " + player.getName(), e);
        }
    }

    /**
     * 경험치바 업데이트 마크
     */
    private void markForExpUpdate(Player player) {
        playersNeedingExpUpdate.add(player.getUniqueId());
    }

    /**
     * 안전한 경험치바 업데이트
     */
    private void updatePlayerExpBarSafe(Player player) {
        try {
            JobType job = getJobType(player);
            int level = getJobLevel(player);
            int exp = getJobExperience(player);

            if (job == JobType.NONE) {
                player.setLevel(0);
                player.setExp(0.0f);
                return;
            }

            if (level >= 10) {
                player.setLevel(10);
                player.setExp(1.0f);

                String actionBar = String.format("%s%s §7MAX LEVEL §6✨",
                        job.getColor(), job.getDisplayName());

                // 캐시된 액션바와 비교하여 불필요한 업데이트 방지
                String cached = cachedActionBars.get(player.getUniqueId());
                if (!actionBar.equals(cached)) {
                    player.sendActionBar(actionBar);
                    cachedActionBars.put(player.getUniqueId(), actionBar);
                }
            } else {
                int currentLevelExp = expRequirements.getOrDefault(level, 0);
                int nextLevelExp = expRequirements.getOrDefault(level + 1, 1000);
                int remainingExp = Math.max(0, exp - currentLevelExp);
                int expForNextLevel = nextLevelExp - currentLevelExp;

                player.setLevel(level);
                float expProgress = Math.min(1.0f, (float) remainingExp / expForNextLevel);
                player.setExp(expProgress);

                String actionBar = String.format("%s%s §7Lv.%d §f[§a%d§7/§e%d§f] §7다음 레벨까지 §c%d경험치",
                        job.getColor(), job.getDisplayName(), level, remainingExp, expForNextLevel,
                        expForNextLevel - remainingExp);

                String cached = cachedActionBars.get(player.getUniqueId());
                if (!actionBar.equals(cached)) {
                    player.sendActionBar(actionBar);
                    cachedActionBars.put(player.getUniqueId(), actionBar);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "경험치바 업데이트 실패: " + player.getName(), e);
        }
    }

    /**
     * 직업 선택 안내 스케줄
     */
    private void scheduleJobSelectionReminder(Player player) {
        if (!plugin.getConfig().getBoolean("job_system.force_job_selection", true)) {
            return;
        }

        int delay = plugin.getConfig().getInt("job_system.job_selection_delay", 60);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && getJobType(player) == JobType.NONE) {
                player.sendMessage("§6==========================================");
                player.sendMessage("§e직업을 선택하세요!");
                player.sendMessage("§7직업을 선택하면 전투에서 더 강해집니다!");
                player.sendMessage("§7몬스터를 처치하여 직업 레벨을 올리세요!");
                player.sendMessage("§6새로운 기능: §f경험치바 UI & 10렙 만렙 효과!");
                player.sendMessage("§a명령어: §f/job select");
                player.sendMessage("§6==========================================");
            }
        }, delay * 20L);
    }

    /**
     * 검인지 확인
     */
    private boolean isSword(Material material) {
        return material.name().contains("SWORD");
    }

    // Public API 메서드들

    public JobType getJobType(Player player) {
        return jobTypes.getOrDefault(player.getUniqueId(), JobType.NONE);
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

        // 비동기로 저장
        CompletableFuture.runAsync(() -> savePlayerJobData(player));

        // 메인 스레드에서 효과 적용
        Bukkit.getScheduler().runTask(plugin, () -> {
            applyJobEffects(player);
            markForExpUpdate(player);

            if (scoreboardIntegration != null) {
                scoreboardIntegration.notifyJobChange(player);
            }
        });

        return true;
    }

    public void resetJob(Player player) {
        UUID uuid = player.getUniqueId();
        jobTypes.put(uuid, JobType.NONE);
        jobLevels.put(uuid, 1);
        jobExperience.put(uuid, 0);

        // 모든 직업 효과 제거
        try {
            AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(20.0);
            }

            if (player.getHealth() > 20.0) {
                player.setHealth(20.0);
            }

            removeArcherSpeedBonus(player);

            player.setLevel(0);
            player.setExp(0.0f);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 초기화 중 오류: " + player.getName(), e);
        }

        // 비동기로 저장
        CompletableFuture.runAsync(() -> savePlayerJobData(player));

        if (scoreboardIntegration != null) {
            scoreboardIntegration.notifyJobChange(player);
        }
    }

    public void setJobLevel(Player player, int level) {
        if (level < 1 || level > 10) return;

        UUID uuid = player.getUniqueId();
        jobLevels.put(uuid, level);

        if (level > 1) {
            int exp = expRequirements.getOrDefault(level, 0);
            jobExperience.put(uuid, exp);
        } else {
            jobExperience.put(uuid, 0);
        }

        // 비동기로 저장
        CompletableFuture.runAsync(() -> savePlayerJobData(player));

        // 메인 스레드에서 효과 적용
        Bukkit.getScheduler().runTask(plugin, () -> {
            applyJobEffects(player);
            markForExpUpdate(player);

            if (scoreboardIntegration != null) {
                scoreboardIntegration.notifyLevelUp(player);
            }
        });

        player.sendMessage("§a직업 레벨이 " + level + "로 설정되었습니다!");
    }

    public void addJobExperience(Player player, int exp) {
        addJobExperience(player, exp, EntityType.ZOMBIE); // 기본값
    }

    public void showJobInfo(Player player) {
        JobType job = getJobType(player);
        int level = getJobLevel(player);
        int exp = getJobExperience(player);

        player.sendMessage("§6==========================================");
        player.sendMessage("§e직업 정보 (새로운 UI)");
        player.sendMessage("§7직업: " + job.getColor() + job.getDisplayName());
        player.sendMessage("§7레벨: §f" + level + " / 10" +
                (level == 10 ? " §6✨ MAX" : ""));

        if (level < 10) {
            int currentLevelExp = expRequirements.getOrDefault(level, 0);
            int nextLevelExp = expRequirements.getOrDefault(level + 1, 1000);
            int remainingExp = Math.max(0, exp - currentLevelExp);
            int expForNextLevel = nextLevelExp - currentLevelExp;

            player.sendMessage("§7경험치: §a" + remainingExp + " §7/ §e" + expForNextLevel);
            player.sendMessage("§7다음 레벨까지: §c" + (expForNextLevel - remainingExp) + " 경험치");
        }

        player.sendMessage("§6==========================================");
    }

    /**
     * 플레이어 직업 데이터 로드
     */
    public void loadPlayerJobData(Player player) {
        UUID uuid = player.getUniqueId();

        try (Connection connection = databaseManager.getConnection()) {
            String sql = "SELECT job_type, job_level, job_experience FROM player_jobs WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        JobType jobType = JobType.valueOf(rs.getString("job_type"));
                        int jobLevel = rs.getInt("job_level");
                        int jobExp = rs.getInt("job_experience");

                        jobTypes.put(uuid, jobType);
                        jobLevels.put(uuid, jobLevel);
                        jobExperience.put(uuid, jobExp);
                    } else {
                        // 신규 플레이어
                        jobTypes.put(uuid, JobType.NONE);
                        jobLevels.put(uuid, 1);
                        jobExperience.put(uuid, 0);
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 직업 데이터 로드 실패: " + player.getName(), e);

            // 기본값 설정
            jobTypes.put(uuid, JobType.NONE);
            jobLevels.put(uuid, 1);
            jobExperience.put(uuid, 0);
        }
    }

    /**
     * 플레이어 직업 데이터 저장
     */
    public void savePlayerJobData(Player player) {
        UUID uuid = player.getUniqueId();

        try (Connection connection = databaseManager.getConnection()) {
            String sql = """
                INSERT INTO player_jobs (uuid, job_type, job_level, job_experience) 
                VALUES (?, ?, ?, ?) 
                ON DUPLICATE KEY UPDATE 
                job_type = VALUES(job_type), 
                job_level = VALUES(job_level), 
                job_experience = VALUES(job_experience),
                updated_at = CURRENT_TIMESTAMP
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, getJobType(player).name());
                stmt.setInt(3, getJobLevel(player));
                stmt.setInt(4, getJobExperience(player));

                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 직업 데이터 저장 실패: " + player.getName(), e);
        }
    }

    /**
     * 캐시 정리
     */
    public void cleanupCache() {
        try {
            // 오프라인 플레이어 데이터 정리
            Set<UUID> onlineUUIDs = new HashSet<>();
            Bukkit.getOnlinePlayers().forEach(player -> onlineUUIDs.add(player.getUniqueId()));

            jobTypes.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            jobLevels.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            jobExperience.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            shieldDefenseCooldown.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            lastExpGainTime.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            cachedActionBars.keySet().removeIf(uuid -> !onlineUUIDs.contains(uuid));
            playersNeedingExpUpdate.removeIf(uuid -> !onlineUUIDs.contains(uuid));

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "캐시 정리 중 오류", e);
        }
    }

    /**
     * JobManager 종료
     */
    public void onDisable() {
        try {
            // 경험치바 업데이트 태스크 중지
            if (expBarUpdateTask != null && !expBarUpdateTask.isCancelled()) {
                expBarUpdateTask.cancel();
            }

            // 모든 온라인 플레이어 데이터 저장
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    savePlayerJobData(player);
                    removeArcherSpeedBonus(player);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "플레이어 종료 처리 실패: " + player.getName(), e);
                }
            }

            // 캐시 정리
            cleanupCache();

            plugin.getLogger().info("JobManager 안전하게 종료됨");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "JobManager 종료 중 오류", e);
        }
    }

    // Getter 메서드들
    public ScoreboardIntegration getScoreboardIntegration() {
        return scoreboardIntegration;
    }
}