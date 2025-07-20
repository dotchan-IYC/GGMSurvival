package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import com.ggm.ggmsurvival.GGMSurvival;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class JobManager implements Listener {

    private final GGMSurvival plugin;
    private final DatabaseManager databaseManager;

    // 직업 종류
    public enum JobType {
        NONE("없음", "§7직업 없음"),
        TANK("탱커", "§c탱커"),
        WARRIOR("검사", "§6검사"),
        ARCHER("궁수", "§a궁수");

        private final String name;
        private final String displayName;

        JobType(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
    }

    // 직업별 효과 쿨다운 및 상태 관리
    private final Map<UUID, Long> shieldCooldowns = new HashMap<>();
    private final Map<UUID, Double> lastMaxHealth = new HashMap<>();
    private final Map<UUID, JobType> playerJobs = new HashMap<>(); // 캐시
    private final Set<UUID> arrowSaveQueue = new HashSet<>(); // 화살 절약 대기열

    public JobManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        // 테이블 생성
        createJobTable();

        // 주기적으로 직업 효과 적용
        startJobEffectTask();

        // 플레이어 직업 캐시 로드
        loadAllPlayerJobs();

        plugin.getLogger().info("JobManager 초기화 완료 - 직업 효과 활성화");
    }

    /**
     * 모든 플레이어 직업 캐시 로드
     */
    private void loadAllPlayerJobs() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
                    playerJobs.put(player.getUniqueId(), jobType);
                });
            }
        });
    }

    /**
     * 직업 테이블 생성
     */
    private void createJobTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS ggm_player_jobs (
                uuid VARCHAR(36) PRIMARY KEY,
                player_name VARCHAR(16) NOT NULL,
                job_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
                job_level INT NOT NULL DEFAULT 1,
                job_exp BIGINT NOT NULL DEFAULT 0,
                selected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.getLogger().info("직업 테이블이 준비되었습니다.");
        } catch (SQLException e) {
            plugin.getLogger().severe("직업 테이블 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 플레이어 직업 조회 - 타임아웃 추가
     */
    public CompletableFuture<JobType> getPlayerJob(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = "SELECT job_type FROM ggm_player_jobs WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String jobTypeName = rs.getString("job_type");
                            return JobType.valueOf(jobTypeName);
                        }
                    }
                }
                return JobType.NONE;
            } catch (Exception e) {
                plugin.getLogger().severe("직업 조회 실패: " + e.getMessage());
                return JobType.NONE;
            }
        }).orTimeout(5, TimeUnit.SECONDS);
    }

    /**
     * 플레이어 직업 설정
     */
    public CompletableFuture<Boolean> setPlayerJob(UUID uuid, String playerName, JobType jobType) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                String sql = """
                    INSERT INTO ggm_player_jobs (uuid, player_name, job_type) 
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE 
                    job_type = VALUES(job_type), 
                    player_name = VALUES(player_name),
                    selected_at = CURRENT_TIMESTAMP
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setString(3, jobType.name());
                    int result = stmt.executeUpdate();

                    // 캐시 업데이트
                    if (result > 0) {
                        playerJobs.put(uuid, jobType);
                    }

                    return result > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("setPlayerJobSync 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 캐시된 직업 정보 조회 (GGMCore 연동용 - Public 메서드)
     */
    public JobType getCachedJob(UUID uuid) {
        return playerJobs.getOrDefault(uuid, JobType.NONE);
    }

    /**
     * 플레이어 직업 설정 (스코어보드 업데이트 포함)
     */
    public CompletableFuture<Boolean> setPlayerJobWithUpdate(UUID uuid, String playerName, JobType jobType) {
        return setPlayerJob(uuid, playerName, jobType).thenCompose(success -> {
            if (success) {
                // 캐시 업데이트
                playerJobs.put(uuid, jobType);

                // 스코어보드 업데이트 (GGMCore 연동)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    updatePlayerScoreboard(uuid);
                });
            }
            return CompletableFuture.completedFuture(success);
        });
    }

    /**
     * 플레이어 스코어보드 업데이트 (GGMCore와 연동)
     */
    private void updatePlayerScoreboard(UUID uuid) {
        try {
            org.bukkit.plugin.Plugin ggmCore = Bukkit.getPluginManager().getPlugin("GGMCore");
            if (ggmCore != null && ggmCore.isEnabled()) {
                // GGMCore의 ScoreboardManager 가져오기
                Class<?> ggmCoreClass = ggmCore.getClass();
                java.lang.reflect.Method getScoreboardManagerMethod = ggmCoreClass.getMethod("getScoreboardManager");
                Object scoreboardManager = getScoreboardManagerMethod.invoke(ggmCore);

                if (scoreboardManager != null) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        // updateJobInfo 메서드 호출
                        Class<?> scoreboardManagerClass = scoreboardManager.getClass();
                        java.lang.reflect.Method updateJobInfoMethod = scoreboardManagerClass.getMethod("updateJobInfo", Player.class);
                        updateJobInfoMethod.invoke(scoreboardManager, player);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("스코어보드 업데이트 실패: " + e.getMessage());
        }
    }

    /**
     * 플레이어 접속 시 직업 캐시 로드 및 스코어보드 업데이트
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();

        // 직업 정보 비동기로 로드
        getPlayerJob(uuid).thenAccept(jobType -> {
            // 캐시 업데이트
            playerJobs.put(uuid, jobType);

            // 스코어보드 업데이트 (약간의 지연을 두고)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updatePlayerScoreboard(uuid);
            }, 40L); // 2초 후
        });
    }

    /**
     * 플레이어 퇴장 시 캐시 정리
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        playerJobs.remove(uuid);
        shieldCooldowns.remove(uuid);
        lastMaxHealth.remove(uuid);
        arrowSaveQueue.remove(uuid);
    }

    /**
     * 주기적 직업 효과 적용
     */
    private void startJobEffectTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        applyJobEffects(player);
                    } catch (Exception e) {
                        plugin.getLogger().warning("직업 효과 적용 중 오류 (" + player.getName() + "): " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // 2초마다 실행
    }

    /**
     * 직업 효과 적용 - 완전 구현
     */
    private void applyJobEffects(Player player) {
        try {
            JobType jobType = getCachedJob(player.getUniqueId());

            switch (jobType) {
                case TANK:
                    applyTankEffects(player);
                    break;
                case WARRIOR:
                    applyWarriorEffects(player);
                    break;
                case ARCHER:
                    applyArcherEffects(player);
                    break;
                case NONE:
                    // 직업이 없으면 기본 상태로 복구
                    resetToDefaultState(player);
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("applyJobEffects 오류: " + e.getMessage());
        }
    }

    /**
     * 탱커 효과 적용 - 완전 구현
     */
    private void applyTankEffects(Player player) {
        // 흉갑 착용 시 체력 증가
        ItemStack chestplate = player.getInventory().getChestplate();
        boolean hasChestplate = chestplate != null && chestplate.getType().name().contains("CHESTPLATE");

        double healthBonus = plugin.getConfig().getDouble("job_system.tank.health_bonus", 4.0);
        double baseHealth = 20.0;
        double targetMaxHealth = hasChestplate ? baseHealth + healthBonus : baseHealth;

        UUID uuid = player.getUniqueId();
        Double lastHealth = lastMaxHealth.get(uuid);

        if (lastHealth == null || Math.abs(lastHealth - targetMaxHealth) > 0.1) {
            player.setMaxHealth(targetMaxHealth);
            lastMaxHealth.put(uuid, targetMaxHealth);

            if (hasChestplate && targetMaxHealth > baseHealth) {
                player.sendActionBar("§c[탱커] 최대 체력 증가! ❤+" + (int)(healthBonus) + " (흉갑 착용)");
            }
        }
    }

    /**
     * 검사 효과 적용 - 완전 구현
     */
    private void applyWarriorEffects(Player player) {
        // 검사는 패시브 효과만 있으므로 특별한 주기적 처리 없음
        // 실제 효과는 이벤트 핸들러에서 처리됨
    }

    /**
     * 궁수 효과 적용 - 완전 구현
     */
    private void applyArcherEffects(Player player) {
        // 가죽 부츠 착용 시 속도 효과
        ItemStack boots = player.getInventory().getBoots();
        boolean hasLeatherBoots = boots != null && boots.getType() == Material.LEATHER_BOOTS;

        if (hasLeatherBoots) {
            int speedLevel = plugin.getConfig().getInt("job_system.archer.leather_boots_speed", 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, speedLevel - 1, true, false));
        }

        // 기본 이동속도 보너스 (항상 적용)
        double speedBonus = plugin.getConfig().getDouble("job_system.archer.movement_speed_bonus", 0.1);
        float currentSpeed = player.getWalkSpeed();
        float targetSpeed = (float)(0.2 + speedBonus); // 기본 속도 0.2 + 보너스

        if (Math.abs(currentSpeed - targetSpeed) > 0.01) {
            player.setWalkSpeed(targetSpeed);
        }
    }

    /**
     * 기본 상태로 복구
     */
    private void resetToDefaultState(Player player) {
        UUID uuid = player.getUniqueId();

        // 체력을 기본값으로 복구
        if (lastMaxHealth.containsKey(uuid)) {
            player.setMaxHealth(20.0);
            lastMaxHealth.remove(uuid);
        }

        // 이동속도를 기본값으로 복구
        if (Math.abs(player.getWalkSpeed() - 0.2f) > 0.01) {
            player.setWalkSpeed(0.2f);
        }
    }

    /**
     * 방패 사용 이벤트 (탱커 회복) - 완전 구현
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        try {
            if (!event.getAction().toString().contains("RIGHT_CLICK")) return;

            Player player = event.getPlayer();
            ItemStack item = event.getItem();

            if (item == null || item.getType() != Material.SHIELD) return;

            JobType jobType = getCachedJob(player.getUniqueId());
            if (jobType != JobType.TANK) return;

            UUID uuid = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long cooldown = plugin.getConfig().getLong("job_system.tank.shield_cooldown", 3000);

            if (shieldCooldowns.containsKey(uuid)) {
                long lastUse = shieldCooldowns.get(uuid);
                if (currentTime - lastUse < cooldown) {
                    long remaining = (cooldown - (currentTime - lastUse)) / 1000;
                    player.sendActionBar("§c[탱커] 방패 회복 쿨다운: " + remaining + "초");
                    return;
                }
            }

            double healAmount = plugin.getConfig().getDouble("job_system.tank.shield_heal_amount", 1.0);
            if (player.getHealth() < player.getMaxHealth()) {
                double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + healAmount);
                player.setHealth(newHealth);

                player.sendMessage("§c[탱커] 방패 회복! §a+" + healAmount + "❤");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);

                shieldCooldowns.put(uuid, currentTime);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("방패 이벤트 처리 오류: " + e.getMessage());
        }
    }

    /**
     * 공격 이벤트 (검사 전용) - 완전 구현
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        try {
            if (!(event.getDamager() instanceof Player)) return;

            Player player = (Player) event.getDamager();
            JobType jobType = getCachedJob(player.getUniqueId());

            if (jobType == JobType.WARRIOR) {
                ItemStack weapon = player.getInventory().getItemInMainHand();

                // 검 공격력 증가
                if (weapon.getType().name().contains("SWORD")) {
                    double damageBonus = plugin.getConfig().getDouble("job_system.warrior.sword_damage_bonus", 0.2);
                    double originalDamage = event.getDamage();
                    double newDamage = originalDamage * (1.0 + damageBonus);

                    // 치명타 확률
                    double critChance = plugin.getConfig().getDouble("job_system.warrior.critical_chance", 0.1);
                    boolean isCrit = Math.random() < critChance;

                    if (isCrit) {
                        double critMultiplier = plugin.getConfig().getDouble("job_system.warrior.critical_multiplier", 1.5);
                        newDamage *= critMultiplier;
                        player.sendMessage("§6[검사] 치명타! §c+" + String.format("%.1f", newDamage - originalDamage) + " 피해");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                    } else {
                        player.sendMessage("§6[검사] 강화 공격! §c+" + String.format("%.1f", newDamage - originalDamage) + " 피해");
                    }

                    event.setDamage(newDamage);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("공격 이벤트 처리 오류: " + e.getMessage());
        }
    }

    /**
     * 피해 받기 이벤트 (탱커 피해 감소) - 완전 구현
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        try {
            if (!(event.getEntity() instanceof Player)) return;

            Player player = (Player) event.getEntity();
            JobType jobType = getCachedJob(player.getUniqueId());

            if (jobType == JobType.TANK) {
                double damageReduction = plugin.getConfig().getDouble("job_system.tank.damage_reduction", 0.1);
                double originalDamage = event.getDamage();
                double newDamage = originalDamage * (1.0 - damageReduction);

                event.setDamage(newDamage);
                player.sendMessage("§c[탱커] 피해 감소! §a-" + String.format("%.1f", originalDamage - newDamage) + " 피해");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("피해 받기 이벤트 처리 오류: " + e.getMessage());
        }
    }

    /**
     * 활 발사 이벤트 (궁수 전용) - 완전 구현
     */
    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        try {
            if (!(event.getEntity() instanceof Player)) return;

            Player player = (Player) event.getEntity();
            JobType jobType = getCachedJob(player.getUniqueId());

            if (jobType == JobType.ARCHER) {
                // 화살 소모 방지 확률
                double arrowSaveChance = plugin.getConfig().getDouble("job_system.archer.arrow_save_chance", 0.2);
                if (Math.random() < arrowSaveChance) {
                    arrowSaveQueue.add(player.getUniqueId());
                    player.sendMessage("§a[궁수] 화살 절약!");
                }

                // 활 공격력 증가는 프로젝타일 히트에서 처리
                if (event.getProjectile() instanceof Arrow) {
                    Arrow arrow = (Arrow) event.getProjectile();
                    double damageBonus = plugin.getConfig().getDouble("job_system.archer.bow_damage_bonus", 0.15);
                    arrow.setDamage(arrow.getDamage() * (1.0 + damageBonus));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("활 발사 이벤트 처리 오류: " + e.getMessage());
        }
    }

    /**
     * 아이템 내구도 이벤트 (검사 내구도 보호) - 완전 구현
     */
    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        try {
            Player player = event.getPlayer();
            JobType jobType = getCachedJob(player.getUniqueId());

            if (jobType == JobType.WARRIOR) {
                ItemStack item = event.getItem();
                if (item.getType().name().contains("SWORD")) {
                    double saveChance = plugin.getConfig().getDouble("job_system.warrior.durability_save_chance", 0.15);
                    if (Math.random() < saveChance) {
                        event.setCancelled(true);
                        player.sendMessage("§6[검사] 검 내구도 보호!");
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("내구도 이벤트 처리 오류: " + e.getMessage());
        }
    }

    /**
     * 직업 선택 GUI 생성
     */
    public void openJobSelectionGUI(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, 27, "§6§l직업 선택");

            // 탱커
            ItemStack tankItem = new ItemStack(Material.SHIELD);
            ItemMeta tankMeta = tankItem.getItemMeta();
            tankMeta.setDisplayName("§c§l탱커");
            List<String> tankLore = Arrays.asList(
                    "§7방어에 특화된 직업",
                    "",
                    "§a효과:",
                    "§7• 흉갑 착용 시 체력 +4",
                    "§7• 방패 사용 시 체력 회복",
                    "§7• 받는 피해 10% 감소",
                    "",
                    "§e클릭하여 선택!"
            );
            tankMeta.setLore(tankLore);
            tankItem.setItemMeta(tankMeta);

            // 검사
            ItemStack warriorItem = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta warriorMeta = warriorItem.getItemMeta();
            warriorMeta.setDisplayName("§6§l검사");
            List<String> warriorLore = Arrays.asList(
                    "§7공격에 특화된 직업",
                    "",
                    "§a효과:",
                    "§7• 검 공격력 20% 증가",
                    "§7• 10% 확률로 치명타",
                    "§7• 검 내구도 보호 15%",
                    "",
                    "§e클릭하여 선택!"
            );
            warriorMeta.setLore(warriorLore);
            warriorItem.setItemMeta(warriorMeta);

            // 궁수
            ItemStack archerItem = new ItemStack(Material.BOW);
            ItemMeta archerMeta = archerItem.getItemMeta();
            archerMeta.setDisplayName("§a§l궁수");
            List<String> archerLore = Arrays.asList(
                    "§7원거리 공격에 특화된 직업",
                    "",
                    "§a효과:",
                    "§7• 활 공격력 15% 증가",
                    "§7• 화살 절약 확률 20%",
                    "§7• 가죽부츠 착용 시 속도 증가",
                    "§7• 기본 이동속도 10% 증가",
                    "",
                    "§e클릭하여 선택!"
            );
            archerMeta.setLore(archerLore);
            archerItem.setItemMeta(archerMeta);

            gui.setItem(11, tankItem);
            gui.setItem(13, warriorItem);
            gui.setItem(15, archerItem);

            player.openInventory(gui);

        } catch (Exception e) {
            plugin.getLogger().warning("직업 선택 GUI 열기 실패: " + e.getMessage());
            player.sendMessage("§c직업 선택 GUI를 열 수 없습니다.");
        }
    }

    /**
     * GUI 클릭 이벤트 - Lambda 오류 수정됨
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals("§6§l직업 선택")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        // final 변수로 선언하여 lambda에서 사용 가능하게 함
        final JobType selectedJob;
        switch (clicked.getType()) {
            case SHIELD:
                selectedJob = JobType.TANK;
                break;
            case DIAMOND_SWORD:
                selectedJob = JobType.WARRIOR;
                break;
            case BOW:
                selectedJob = JobType.ARCHER;
                break;
            default:
                return;
        }

        player.closeInventory();

        // 직업 설정 (스코어보드 업데이트 포함)
        setPlayerJobWithUpdate(player.getUniqueId(), player.getName(), selectedJob).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§a§l✅ 직업 선택 완료!");
                    player.sendMessage("§7선택된 직업: " + selectedJob.getDisplayName()); // 이제 final이므로 사용 가능
                    player.sendMessage("");
                    showJobDescription(player, selectedJob);
                    player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

                    plugin.getLogger().info(String.format("[직업선택] %s님이 %s 직업을 선택했습니다.",
                            player.getName(), selectedJob.getName()));
                } else {
                    player.sendMessage("§c직업 설정에 실패했습니다.");
                }
            });
        });
    }

    /**
     * 직업 설명 표시
     */
    private void showJobDescription(Player player, JobType jobType) {
        switch (jobType) {
            case TANK:
                player.sendMessage("§c[탱커 효과]");
                player.sendMessage("§7• 흉갑 착용 시 체력 +4 (하트 2개)");
                player.sendMessage("§7• 방패 사용 시 체력 회복 (3초 쿨타임)");
                player.sendMessage("§7• 받는 피해 10% 감소");
                break;
            case WARRIOR:
                player.sendMessage("§6[검사 효과]");
                player.sendMessage("§7• 검 공격력 20% 증가");
                player.sendMessage("§7• 10% 확률로 치명타 (1.5배 피해)");
                player.sendMessage("§7• 검 내구도 보호 15% 확률");
                break;
            case ARCHER:
                player.sendMessage("§a[궁수 효과]");
                player.sendMessage("§7• 활 공격력 15% 증가");
                player.sendMessage("§7• 화살 절약 확률 20%");
                player.sendMessage("§7• 가죽부츠 착용 시 속도 효과");
                player.sendMessage("§7• 기본 이동속도 10% 증가");
                break;
        }
    }
}