// 완성된 JobManager.java - 직업 효과 완전 구현
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
                plugin.getLogger().severe("직업 설정 실패: " + e.getMessage());
                return false;
            }
        }).orTimeout(5, TimeUnit.SECONDS);
    }

    /**
     * 캐시된 직업 정보 가져오기 (빠른 접근)
     */
    private JobType getCachedJob(UUID uuid) {
        return playerJobs.getOrDefault(uuid, JobType.NONE);
    }

    /**
     * 직업 선택 GUI 열기
     */
    public void openJobSelectionGUI(Player player) {
        try {
            plugin.getLogger().info("JobManager에서 GUI 열기 시작: " + player.getName());

            Inventory gui = Bukkit.createInventory(null, 27, "§6§l직업 선택");

            // 탱커
            ItemStack tankItem = new ItemStack(Material.IRON_CHESTPLATE);
            ItemMeta tankMeta = tankItem.getItemMeta();
            if (tankMeta != null) {
                tankMeta.setDisplayName("§c§l탱커");
                List<String> tankLore = Arrays.asList(
                        "§7방어와 체력에 특화된 근접 전투 직업",
                        "",
                        "§a§l효과:",
                        "§7• 흉갑 착용 시 체력 +2하트",
                        "§7• 방패 사용 시 체력 0.5하트 회복",
                        "§7• 받는 피해 10% 감소",
                        "",
                        "§e클릭하여 선택!"
                );
                tankMeta.setLore(tankLore);
                tankItem.setItemMeta(tankMeta);
            }

            // 검사
            ItemStack warriorItem = new ItemStack(Material.IRON_SWORD);
            ItemMeta warriorMeta = warriorItem.getItemMeta();
            if (warriorMeta != null) {
                warriorMeta.setDisplayName("§6§l검사");
                List<String> warriorLore = Arrays.asList(
                        "§7검술에 특화된 공격적인 근접 전투 직업",
                        "",
                        "§a§l효과:",
                        "§7• 검 공격력 +20%",
                        "§7• 치명타 확률 10%",
                        "§7• 검 내구도 소모 확률 15% 감소",
                        "",
                        "§e클릭하여 선택!"
                );
                warriorMeta.setLore(warriorLore);
                warriorItem.setItemMeta(warriorMeta);
            }

            // 궁수
            ItemStack archerItem = new ItemStack(Material.BOW);
            ItemMeta archerMeta = archerItem.getItemMeta();
            if (archerMeta != null) {
                archerMeta.setDisplayName("§a§l궁수");
                List<String> archerLore = Arrays.asList(
                        "§7원거리 공격과 기동성에 특화된 직업",
                        "",
                        "§a§l효과:",
                        "§7• 활 공격력 +15%",
                        "§7• 가죽부츠 착용 시 이동속도 증가",
                        "§7• 화살 소모 확률 20% 감소",
                        "",
                        "§e클릭하여 선택!"
                );
                archerMeta.setLore(archerLore);
                archerItem.setItemMeta(archerMeta);
            }

            // 취소 버튼
            ItemStack cancelItem = new ItemStack(Material.BARRIER);
            ItemMeta cancelMeta = cancelItem.getItemMeta();
            if (cancelMeta != null) {
                cancelMeta.setDisplayName("§c§l취소");
                cancelMeta.setLore(Arrays.asList("§7직업 선택을 취소합니다"));
                cancelItem.setItemMeta(cancelMeta);
            }

            // GUI에 아이템 배치
            gui.setItem(11, tankItem);
            gui.setItem(13, warriorItem);
            gui.setItem(15, archerItem);
            gui.setItem(22, cancelItem);

            player.openInventory(gui);
            plugin.getLogger().info("JobManager GUI 열기 성공: " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().severe("JobManager GUI 열기 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * GUI 클릭 이벤트 처리
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null) return;
        if (!event.getView().getTitle().equals("§6§l직업 선택")) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String displayName = clickedItem.getItemMeta().getDisplayName();

        plugin.getLogger().info("직업 선택 클릭: " + displayName + " by " + player.getName());

        try {
            if (displayName.equals("§c§l탱커")) {
                selectJobDirectly(player, JobType.TANK);
            } else if (displayName.equals("§6§l검사")) {
                selectJobDirectly(player, JobType.WARRIOR);
            } else if (displayName.equals("§a§l궁수")) {
                selectJobDirectly(player, JobType.ARCHER);
            } else if (displayName.equals("§c§l취소")) {
                player.closeInventory();
                player.sendMessage("§7직업 선택을 취소했습니다.");
            }
        } catch (Exception e) {
            player.sendMessage("§c직업 선택 처리 중 오류 발생: " + e.getMessage());
            plugin.getLogger().severe("직업 선택 클릭 처리 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 직접 직업 선택 처리
     */
    private void selectJobDirectly(Player player, JobType jobType) {
        try {
            player.sendMessage("§e" + jobType.getDisplayName() + " 직업을 선택하는 중...");
            plugin.getLogger().info("직업 선택 처리: " + player.getName() + " -> " + jobType.name());

            // 비동기로 DB 저장
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    boolean success = setPlayerJobSync(player.getUniqueId(), player.getName(), jobType);

                    // 메인 스레드에서 응답 처리
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            player.closeInventory();
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            player.sendMessage("§e§l🎉 직업 선택 완료!");
                            player.sendMessage("");
                            player.sendMessage("§7선택한 직업: " + jobType.getDisplayName());
                            player.sendMessage("§a이제 특수 능력을 사용할 수 있습니다!");
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                            // 사운드 효과
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                            // 즉시 직업 효과 적용
                            applyJobEffects(player);

                            plugin.getLogger().info(player.getName() + "이(가) " + jobType.getName() + " 직업을 선택했습니다.");
                        } else {
                            player.sendMessage("§c직업 선택에 실패했습니다. 다시 시도해주세요.");
                        }
                    });

                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c직업 선택 중 오류 발생: " + e.getMessage());
                    });
                    plugin.getLogger().severe("직업 선택 DB 처리 오류: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            player.sendMessage("§c직업 선택 처리 실패: " + e.getMessage());
            plugin.getLogger().severe("selectJobDirectly 오류: " + e.getMessage());
        }
    }

    /**
     * 동기적 직업 설정 (비동기 스레드에서 호출)
     */
    private boolean setPlayerJobSync(UUID uuid, String playerName, JobType jobType) {
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

        // 체력이 변경되어야 하는 경우에만 업데이트
        if (lastHealth == null || Math.abs(lastHealth - targetMaxHealth) > 0.1) {
            player.setMaxHealth(targetMaxHealth);
            lastMaxHealth.put(uuid, targetMaxHealth);

            // 현재 체력이 새로운 최대 체력을 초과하면 조정
            if (player.getHealth() > targetMaxHealth) {
                player.setHealth(targetMaxHealth);
            }

            // 체력 증가 시 메시지
            if (hasChestplate && (lastHealth == null || lastHealth < targetMaxHealth)) {
                player.sendMessage("§c[탱커] 흉갑 착용으로 체력이 증가했습니다! (+" + (int)(healthBonus/2) + "하트)");
            }
        }
    }

    /**
     * 검사 효과 적용
     */
    private void applyWarriorEffects(Player player) {
        // 검사 효과는 주로 이벤트 기반이므로 여기서는 특별한 처리 없음
        // 필요시 지속 효과를 여기에 추가 가능
    }

    /**
     * 궁수 효과 적용
     */
    private void applyArcherEffects(Player player) {
        // 가죽부츠 착용 시 이동속도 증가
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && boots.getType() == Material.LEATHER_BOOTS) {
            int speedLevel = plugin.getConfig().getInt("job_system.archer.leather_boots_speed", 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, speedLevel - 1, true, false));
        }
    }

    /**
     * 기본 상태로 복구 (직업 없음)
     */
    private void resetToDefaultState(Player player) {
        // 체력을 기본값으로 복구
        if (player.getMaxHealth() != 20.0) {
            player.setMaxHealth(20.0);
            if (player.getHealth() > 20.0) {
                player.setHealth(20.0);
            }
            lastMaxHealth.remove(player.getUniqueId());
        }
    }

    /**
     * 방패 사용 이벤트 (탱커 전용) - 완전 구현
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        try {
            Player player = event.getPlayer();
            ItemStack item = event.getItem();

            if (item == null || item.getType() != Material.SHIELD) return;
            if (!event.getAction().name().contains("RIGHT_CLICK")) return;

            JobType jobType = getCachedJob(player.getUniqueId());
            if (jobType != JobType.TANK) return;

            UUID uuid = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long cooldown = plugin.getConfig().getLong("job_system.tank.shield_cooldown", 3000);

            // 쿨다운 확인
            if (shieldCooldowns.containsKey(uuid)) {
                long lastUse = shieldCooldowns.get(uuid);
                if (currentTime - lastUse < cooldown) {
                    return; // 쿨다운 중
                }
            }

            // 체력 회복
            double healAmount = plugin.getConfig().getDouble("job_system.tank.shield_heal_amount", 1.0);
            double currentHealth = player.getHealth();
            double maxHealth = player.getMaxHealth();

            if (currentHealth < maxHealth) {
                double newHealth = Math.min(maxHealth, currentHealth + healAmount);
                player.setHealth(newHealth);

                // 이팩트
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
            } else if (jobType == JobType.ARCHER) {
                // 궁수도 활 내구도 보호 (추가 기능)
                ItemStack item = event.getItem();
                if (item.getType() == Material.BOW) {
                    // 화살 절약 큐에 있으면 내구도도 보호
                    if (arrowSaveQueue.remove(player.getUniqueId())) {
                        event.setCancelled(true);

                        // 화살도 복구
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            ItemStack arrows = new ItemStack(Material.ARROW, 1);
                            if (player.getInventory().firstEmpty() != -1) {
                                player.getInventory().addItem(arrows);
                            } else {
                                player.getWorld().dropItem(player.getLocation(), arrows);
                            }
                        }, 1L);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("아이템 내구도 이벤트 처리 오류: " + e.getMessage());
        }
    }

    /**
     * 플레이어가 직업을 선택했는지 확인
     */
    public CompletableFuture<Boolean> hasSelectedJob(UUID uuid) {
        JobType cachedJob = getCachedJob(uuid);
        if (cachedJob != JobType.NONE) {
            return CompletableFuture.completedFuture(true);
        }

        return getPlayerJob(uuid).thenApply(jobType -> {
            playerJobs.put(uuid, jobType); // 캐시 업데이트
            return jobType != JobType.NONE;
        });
    }

    /**
     * 직업 정보 포맷팅
     */
    public String formatJobInfo(JobType jobType) {
        switch (jobType) {
            case TANK:
                return "§c탱커 §7- 방어와 체력에 특화";
            case WARRIOR:
                return "§6검사 §7- 검술에 특화된 공격";
            case ARCHER:
                return "§a궁수 §7- 원거리 공격에 특화";
            default:
                return "§7직업 없음";
        }
    }
}