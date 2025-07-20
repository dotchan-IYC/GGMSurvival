// 완전한 JobManager.java - 강화된 직업 선택 검증 로직
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
                    player_name = VALUES(player_name),
                    job_type = VALUES(job_type),
                    last_updated = CURRENT_TIMESTAMP
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setString(3, jobType.name());

                    int rowsAffected = stmt.executeUpdate();

                    if (rowsAffected > 0) {
                        // 캐시 업데이트
                        playerJobs.put(uuid, jobType);
                        plugin.getLogger().info("직업 설정 성공: " + playerName + " -> " + jobType.name());
                        return true;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("직업 설정 실패: " + e.getMessage());
            }
            return false;
        }).orTimeout(10, TimeUnit.SECONDS);
    }

    /**
     * 검증을 포함한 직업 설정 (강화된 버전)
     */
    public CompletableFuture<Boolean> setPlayerJobWithValidation(UUID uuid, String playerName, JobType jobType) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = databaseManager.getConnection()) {
                // 1. 현재 직업 상태를 한 번 더 확인
                String checkSql = "SELECT job_type FROM ggm_player_jobs WHERE uuid = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, uuid.toString());
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            String existingJob = rs.getString("job_type");
                            if (!existingJob.equals("NONE")) {
                                plugin.getLogger().warning("직업 선택 차단: " + playerName + "이(가) 이미 " + existingJob + " 직업을 보유중");
                                return false; // 이미 직업이 있음
                            }
                        }
                    }
                }

                // 2. 직업이 없는 것이 확인되면 INSERT 또는 UPDATE
                String sql = """
                    INSERT INTO ggm_player_jobs (uuid, player_name, job_type) 
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE 
                    player_name = VALUES(player_name),
                    job_type = CASE 
                        WHEN job_type = 'NONE' THEN VALUES(job_type)
                        ELSE job_type
                    END,
                    last_updated = CURRENT_TIMESTAMP
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setString(3, jobType.name());

                    int rowsAffected = stmt.executeUpdate();

                    if (rowsAffected > 0) {
                        plugin.getLogger().info("직업 설정 성공: " + playerName + " -> " + jobType.name());

                        // 3. 설정 후 재검증
                        try (PreparedStatement verifyStmt = conn.prepareStatement(checkSql)) {
                            verifyStmt.setString(1, uuid.toString());
                            try (ResultSet verifyRs = verifyStmt.executeQuery()) {
                                if (verifyRs.next()) {
                                    String finalJob = verifyRs.getString("job_type");
                                    if (finalJob.equals(jobType.name())) {
                                        // 캐시 업데이트
                                        playerJobs.put(uuid, jobType);
                                        return true; // 성공
                                    } else {
                                        plugin.getLogger().warning("직업 설정 후 검증 실패: 예상=" + jobType.name() + ", 실제=" + finalJob);
                                        return false;
                                    }
                                }
                            }
                        }
                    }

                    return false;
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("직업 설정 중 데이터베이스 오류: " + e.getMessage());
                return false;
            } catch (Exception e) {
                plugin.getLogger().severe("직업 설정 중 예외 발생: " + e.getMessage());
                return false;
            }
        }).orTimeout(10, TimeUnit.SECONDS);
    }

    /**
     * 안전한 직업 변경 확인 메서드
     */
    public boolean canChangeJob(UUID uuid) {
        try {
            JobType currentJob = getCachedJob(uuid);
            if (currentJob != null && currentJob != JobType.NONE) {
                return false; // 이미 직업이 있으면 변경 불가
            }

            // 캐시에 없으면 DB에서 확인
            CompletableFuture<JobType> future = getPlayerJob(uuid);
            JobType dbJob = future.get(3, TimeUnit.SECONDS); // 3초 타임아웃

            return dbJob == JobType.NONE;

        } catch (Exception e) {
            plugin.getLogger().warning("직업 변경 가능 여부 확인 실패: " + e.getMessage());
            return false; // 오류 시 안전하게 false 반환
        }
    }

    /**
     * 캐시에서 직업 조회
     */
    public JobType getCachedJob(UUID uuid) {
        return playerJobs.get(uuid);
    }

    /**
     * 플레이어가 직업을 선택했는지 확인
     */
    public CompletableFuture<Boolean> hasSelectedJob(UUID uuid) {
        JobType cachedJob = getCachedJob(uuid);
        if (cachedJob != null && cachedJob != JobType.NONE) {
            return CompletableFuture.completedFuture(true);
        }

        return getPlayerJob(uuid).thenApply(jobType -> {
            playerJobs.put(uuid, jobType); // 캐시 업데이트
            return jobType != JobType.NONE;
        });
    }

    /**
     * 직업 효과 주기적 적용 작업 시작
     */
    private void startJobEffectTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    applyJobEffects(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("직업 효과 적용 중 오류: " + e.getMessage());
                }
            }
        }, 20L, 40L); // 1초 후 시작, 2초마다 실행
    }

    /**
     * 플레이어에게 직업 효과 적용
     */
    public void applyJobEffects(Player player) {
        JobType jobType = getCachedJob(player.getUniqueId());
        if (jobType == null || jobType == JobType.NONE) return;

        try {
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
            }
        } catch (Exception e) {
            plugin.getLogger().warning("직업 효과 적용 오류 (" + jobType.name() + "): " + e.getMessage());
        }
    }

    /**
     * 탱커 효과 적용
     */
    private void applyTankEffects(Player player) {
        // 흉갑 착용 시 체력 증가
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && chestplate.getType().name().contains("CHESTPLATE")) {
            double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            double targetHealth = 24.0; // 기본 20 + 4 = 24 (하트 2개 추가)

            if (Math.abs(maxHealth - targetHealth) > 0.1) {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(targetHealth);
                lastMaxHealth.put(player.getUniqueId(), targetHealth);
            }
        } else {
            // 흉갑을 벗었을 때 원래 체력으로 복구
            double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            if (maxHealth > 20.0) {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
                if (player.getHealth() > 20.0) {
                    player.setHealth(20.0);
                }
            }
        }
    }

    /**
     * 검사 효과 적용 (패시브)
     */
    private void applyWarriorEffects(Player player) {
        // 검사는 공격 시에만 효과가 적용되므로 여기서는 특별한 처리 없음
        // 실제 효과는 EntityDamageByEntityEvent에서 처리
    }

    /**
     * 궁수 효과 적용
     */
    private void applyArcherEffects(Player player) {
        // 가죽부츠 착용 시 이동속도 증가
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && boots.getType() == Material.LEATHER_BOOTS) {
            // 이동속도 효과 적용 (레벨 1)
            PotionEffect speedEffect = new PotionEffect(PotionEffectType.SPEED, 100, 0, true, false);
            player.addPotionEffect(speedEffect);
        }
    }

    /**
     * 방패 사용 이벤트 (탱커 전용)
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        JobType jobType = getCachedJob(player.getUniqueId());

        if (jobType != JobType.TANK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SHIELD) return;

        // 우클릭 시에만 발동
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;

        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // 쿨다운 확인 (3초)
        if (shieldCooldowns.containsKey(uuid)) {
            long lastUse = shieldCooldowns.get(uuid);
            if (currentTime - lastUse < 3000) {
                long remaining = 3000 - (currentTime - lastUse);
                player.sendActionBar("§c방패 회복 쿨다운: " + (remaining / 1000 + 1) + "초");
                return;
            }
        }

        // 체력 회복 (0.5하트 = 1.0 체력)
        double currentHealth = player.getHealth();
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

        if (currentHealth < maxHealth) {
            double newHealth = Math.min(maxHealth, currentHealth + 1.0);
            player.setHealth(newHealth);
            player.sendActionBar("§a방패로 체력을 회복했습니다! (+0.5하트)");

            // 쿨다운 설정
            shieldCooldowns.put(uuid, currentTime);

            plugin.getLogger().info("탱커 " + player.getName() + "이(가) 방패로 체력을 회복했습니다.");
        } else {
            player.sendActionBar("§c이미 체력이 가득 찼습니다!");
        }
    }

    /**
     * 피해 감소 이벤트 (탱커 전용)
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        JobType jobType = getCachedJob(player.getUniqueId());

        if (jobType != JobType.TANK) return;

        // 흉갑 착용 시에만 피해 감소
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && chestplate.getType().name().contains("CHESTPLATE")) {
            double damage = event.getDamage();
            double reducedDamage = damage * 0.9; // 10% 피해 감소

            event.setDamage(reducedDamage);

            if (damage != reducedDamage) {
                player.sendActionBar("§6탱커 효과: 피해 10% 감소!");
            }
        }
    }

    /**
     * 활 쏘기 이벤트 (궁수 전용)
     */
    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        JobType jobType = getCachedJob(player.getUniqueId());

        if (jobType != JobType.ARCHER) return;

        // 20% 확률로 화살 절약
        if (Math.random() < 0.2) { // 20% 확률
            arrowSaveQueue.add(player.getUniqueId());
            player.sendActionBar("§a궁수 효과: 화살 절약!");
        }
    }

    /**
     * 아이템 내구도 이벤트 (직업별 처리)
     */
    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        JobType jobType = getCachedJob(player.getUniqueId());

        if (jobType == null || jobType == JobType.NONE) return;

        try {
            if (jobType == JobType.WARRIOR) {
                // 검사: 검 내구도 소모 15% 확률로 방지
                ItemStack item = event.getItem();
                if (item.getType().name().contains("SWORD")) {
                    if (Math.random() < 0.15) { // 15% 확률
                        event.setCancelled(true);
                        player.sendActionBar("§6검사 효과: 내구도 보호!");
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
     * 플레이어 접속 이벤트
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 비동기로 직업 정보 로드
        getPlayerJob(uuid).thenAccept(jobType -> {
            playerJobs.put(uuid, jobType);

            // 메인 스레드에서 효과 적용
            Bukkit.getScheduler().runTask(plugin, () -> {
                applyJobEffects(player);
            });
        });
    }

    /**
     * 플레이어 퇴장 이벤트
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // 캐시 정리
        playerJobs.remove(uuid);
        shieldCooldowns.remove(uuid);
        lastMaxHealth.remove(uuid);
        arrowSaveQueue.remove(uuid);
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