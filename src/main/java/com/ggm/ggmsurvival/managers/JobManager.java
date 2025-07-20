package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.ggm.ggmsurvival.GGMSurvival;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    // 직업별 효과 쿨다운
    private final Map<UUID, Long> shieldCooldowns = new HashMap<>();

    public JobManager(GGMSurvival plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();

        // 테이블 생성
        createJobTable();

        // 주기적으로 직업 효과 적용
        startJobEffectTask();
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
     * 플레이어 직업 조회
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
        });
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
                    return result > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("직업 설정 실패: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 직업 선택 GUI 열기
     */
    public void openJobSelectionGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6§l직업 선택");

        // 탱커
        ItemStack tankItem = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta tankMeta = tankItem.getItemMeta();
        tankMeta.setDisplayName("§c§l탱커");
        List<String> tankLore = new ArrayList<>();
        tankLore.add("§7체력과 방어에 특화된 직업");
        tankLore.add("");
        tankLore.add("§e효과:");
        tankLore.add("§7• 흉갑 착용 시 체력 증가");
        tankLore.add("§7• 방패 사용 시 체력 0.5 회복");
        tankLore.add("§7• 받는 피해 감소");
        tankLore.add("");
        tankLore.add("§a클릭하여 선택");
        tankMeta.setLore(tankLore);
        tankItem.setItemMeta(tankMeta);
        gui.setItem(11, tankItem);

        // 검사
        ItemStack warriorItem = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta warriorMeta = warriorItem.getItemMeta();
        warriorMeta.setDisplayName("§6§l검사");
        List<String> warriorLore = new ArrayList<>();
        warriorLore.add("§7검술에 특화된 공격 직업");
        warriorLore.add("");
        warriorLore.add("§e효과:");
        warriorLore.add("§7• 검 종류 사용 시 공격력 증가");
        warriorLore.add("§7• 치명타 확률 증가");
        warriorLore.add("§7• 검 내구도 소모 감소");
        warriorLore.add("");
        warriorLore.add("§a클릭하여 선택");
        warriorMeta.setLore(warriorLore);
        warriorItem.setItemMeta(warriorMeta);
        gui.setItem(13, warriorItem);

        // 궁수
        ItemStack archerItem = new ItemStack(Material.BOW);
        ItemMeta archerMeta = archerItem.getItemMeta();
        archerMeta.setDisplayName("§a§l궁수");
        List<String> archerLore = new ArrayList<>();
        archerLore.add("§7원거리 공격에 특화된 직업");
        archerLore.add("");
        archerLore.add("§e효과:");
        archerLore.add("§7• 활 사용 시 공격력 증가");
        archerLore.add("§7• 가죽부츠 착용 시 이동속도 증가");
        archerLore.add("§7• 화살 소모 확률 감소");
        archerLore.add("");
        archerLore.add("§a클릭하여 선택");
        archerMeta.setLore(archerLore);
        archerItem.setItemMeta(archerMeta);
        gui.setItem(15, archerItem);

        // 취소 아이템
        ItemStack cancelItem = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName("§c나중에 선택하기");
        cancelItem.setItemMeta(cancelMeta);
        gui.setItem(22, cancelItem);

        player.openInventory(gui);
    }

    /**
     * GUI 클릭 이벤트
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        if (!event.getView().getTitle().equals("§6§l직업 선택")) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        Material material = clickedItem.getType();

        if (material == Material.IRON_CHESTPLATE) {
            selectJob(player, JobType.TANK);
        } else if (material == Material.DIAMOND_SWORD) {
            selectJob(player, JobType.WARRIOR);
        } else if (material == Material.BOW) {
            selectJob(player, JobType.ARCHER);
        } else if (material == Material.BARRIER) {
            player.closeInventory();
            player.sendMessage("§e직업 선택을 나중에 하실 수 있습니다.");
            player.sendMessage("§7언제든지 /job 명령어로 선택하세요!");
        }
    }

    /**
     * 직업 선택 처리
     */
    private void selectJob(Player player, JobType jobType) {
        player.closeInventory();

        setPlayerJob(player.getUniqueId(), player.getName(), jobType)
                .thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            player.sendMessage("§a§l축하합니다! " + jobType.getDisplayName() + " §a§l직업을 선택하셨습니다!");
                            player.sendMessage("");
                            player.sendMessage("§e이제 직업 특성 효과를 받을 수 있습니다:");

                            switch (jobType) {
                                case TANK:
                                    player.sendMessage("§7• 흉갑 착용 시 체력 증가");
                                    player.sendMessage("§7• 방패 사용 시 체력 회복");
                                    break;
                                case WARRIOR:
                                    player.sendMessage("§7• 검 사용 시 공격력 증가");
                                    player.sendMessage("§7• 치명타 확률 증가");
                                    break;
                                case ARCHER:
                                    player.sendMessage("§7• 활 사용 시 공격력 증가");
                                    player.sendMessage("§7• 가죽부츠 착용 시 이동속도 증가");
                                    break;
                            }

                            player.sendMessage("");
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                            // 효과음
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                            plugin.getLogger().info(String.format("[직업선택] %s: %s 선택",
                                    player.getName(), jobType.getDisplayName()));
                        } else {
                            player.sendMessage("§c직업 선택 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
                        }
                    });
                });
    }

    /**
     * 주기적 직업 효과 적용
     */
    private void startJobEffectTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyJobEffects(player);
            }
        }, 20L, 40L); // 2초마다 실행
    }

    /**
     * 플레이어에게 직업 효과 적용
     */
    private void applyJobEffects(Player player) {
        getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
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
            });
        });
    }

    /**
     * 탱커 효과 적용
     */
    private void applyTankEffects(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();

        // 흉갑 착용 시 체력 증가
        if (chestplate != null && chestplate.getType().name().contains("CHESTPLATE")) {
            double maxHealth = player.getMaxHealth();
            if (maxHealth < 24.0) { // 기본 20 + 4 = 24
                player.setMaxHealth(24.0);
            }

            // 체력 재생 효과
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false));
        }
    }

    /**
     * 검사 효과 적용 (공격 시에만 적용)
     */
    private void applyWarriorEffects(Player player) {
        // 검사는 공격 시 효과가 적용되므로 여기서는 패시브 효과만
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon != null && weapon.getType().name().contains("SWORD")) {
            // 힘 효과 (약간)
            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 60, 0, true, false));
        }
    }

    /**
     * 궁수 효과 적용
     */
    private void applyArcherEffects(Player player) {
        ItemStack boots = player.getInventory().getBoots();

        // 가죽부츠 착용 시 이동속도 증가
        if (boots != null && boots.getType() == Material.LEATHER_BOOTS) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false));
        }
    }

    /**
     * 공격 이벤트 - 직업별 공격 보너스
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
            switch (jobType) {
                case WARRIOR:
                    // 검 사용 시 공격력 증가
                    if (weapon != null && weapon.getType().name().contains("SWORD")) {
                        double damage = event.getDamage();
                        event.setDamage(damage * 1.2); // 20% 증가

                        // 치명타 확률 (10%)
                        if (Math.random() < 0.1) {
                            event.setDamage(damage * 1.5);
                            player.sendMessage("§6⚔ 치명타!");
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                        }
                    }
                    break;

                case ARCHER:
                    // 활 사용 시 공격력 증가 (화살 공격)
                    if (event.getCause().name().contains("PROJECTILE")) {
                        double damage = event.getDamage();
                        event.setDamage(damage * 1.15); // 15% 증가
                    }
                    break;
            }
        });
    }

    /**
     * 방패 사용 이벤트 - 탱커 체력 회복
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.SHIELD) return;

        getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
            if (jobType == JobType.TANK) {
                UUID uuid = player.getUniqueId();
                long currentTime = System.currentTimeMillis();

                // 쿨다운 확인 (3초)
                if (shieldCooldowns.containsKey(uuid)) {
                    long lastUse = shieldCooldowns.get(uuid);
                    if (currentTime - lastUse < 3000) {
                        return;
                    }
                }

                // 체력 회복
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double health = player.getHealth();
                    double maxHealth = player.getMaxHealth();

                    if (health < maxHealth) {
                        player.setHealth(Math.min(maxHealth, health + 1.0)); // 0.5하트 회복
                        player.sendMessage("§c🛡 방패 사용으로 체력이 회복되었습니다!");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
                    }
                });

                shieldCooldowns.put(uuid, currentTime);
            }
        });
    }

    /**
     * 플레이어가 직업을 선택했는지 확인
     */
    public CompletableFuture<Boolean> hasSelectedJob(UUID uuid) {
        return getPlayerJob(uuid).thenApply(jobType -> jobType != JobType.NONE);
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