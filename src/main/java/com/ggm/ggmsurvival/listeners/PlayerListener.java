// 완전 안정화된 PlayerListener.java
package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import com.ggm.ggmsurvival.managers.JobManager.JobType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * 완전 안정화된 플레이어 리스너
 * - 모든 예외 처리 강화
 * - 스레드 안전성 보장
 * - 메모리 누수 방지
 */
public class PlayerListener implements Listener {

    private final GGMSurvival plugin;

    public PlayerListener(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    /**
     * 플레이어 접속 이벤트 - 최고 우선순위로 처리
     */
    @EventHandler(priority = EventPriority.LOWEST) // 가장 먼저 처리
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            plugin.getLogger().info(player.getName() + "이(가) 서버에 접속했습니다.");

            // 플러그인이 초기화되지 않았다면 대기
            if (!plugin.isInitialized()) {
                waitForInitializationAndProcess(player);
                return;
            }

            // 데이터베이스에 플레이어 등록 (비동기)
            registerPlayerAsync(player);

            // 직업 시스템 처리
            handleJobSystemOnJoin(player);

            // 환영 메시지 (지연 후 표시)
            scheduleWelcomeMessage(player);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "플레이어 접속 처리 중 치명적 오류: " + player.getName(), e);

            // 오류 발생 시 플레이어에게 알림
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§c접속 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
                }
            }, 20L);
        }
    }

    /**
     * 플레이어 퇴장 이벤트 - 안전한 정리
     */
    @EventHandler(priority = EventPriority.HIGHEST) // 가장 나중에 처리 (다른 플러그인 처리 후)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        try {
            plugin.getLogger().info(player.getName() + "이(가) 서버에서 퇴장했습니다.");

            // JobManager가 활성화되어 있다면 정리는 JobManager에서 처리
            // 여기서는 추가적인 정리만 수행

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 퇴장 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 장비 변경 감지 - 직업 패시브 효과 재적용
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.isShuttingDown()) return;

        try {
            // 장비 슬롯 변경 감지
            if (isArmorSlot(event.getSlot()) || event.getSlot() == player.getInventory().getHeldItemSlot()) {

                // 1틱 후 직업 효과 재적용 (장비 변경이 완료된 후)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && plugin.getJobManager() != null) {
                        try {
                            plugin.getJobManager().applyJobEffects(player);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "장비 변경 후 직업 효과 재적용 실패: " + player.getName(), e);
                        }
                    }
                }, 1L);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "인벤토리 클릭 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 핫바 아이템 변경 감지
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        if (plugin.isShuttingDown()) return;

        try {
            // 1틱 후 직업 효과 재적용 (아이템 변경이 완료된 후)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && plugin.getJobManager() != null) {
                    try {
                        plugin.getJobManager().applyJobEffects(player);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "핫바 변경 후 직업 효과 재적용 실패: " + player.getName(), e);
                    }
                }
            }, 1L);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "핫바 아이템 변경 처리 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 플러그인 초기화 대기 후 처리
     */
    private void waitForInitializationAndProcess(Player player) {
        new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = 10; // 5초 대기

            @Override
            public void run() {
                attempts++;

                if (plugin.isInitialized()) {
                    // 초기화 완료 - 정상 처리
                    try {
                        registerPlayerAsync(player);
                        handleJobSystemOnJoin(player);
                        scheduleWelcomeMessage(player);

                        if (player.isOnline()) {
                            player.sendMessage("§a서버 준비가 완료되었습니다!");
                        }

                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "초기화 대기 후 플레이어 처리 실패: " + player.getName(), e);
                    }
                    cancel();

                } else if (attempts >= maxAttempts) {
                    // 초기화 시간 초과
                    plugin.getLogger().warning(
                            "플러그인 초기화 시간 초과 - 플레이어 처리 제한: " + player.getName());

                    if (player.isOnline()) {
                        player.sendMessage("§c서버 준비 중입니다. 잠시 후 다시 시도해주세요.");
                    }
                    cancel();

                } else if (!player.isOnline()) {
                    // 플레이어가 이미 나감
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // 0.5초마다 확인
    }

    /**
     * 비동기 플레이어 등록
     */
    private void registerPlayerAsync(Player player) {
        if (plugin.getDatabaseManager() == null) {
            plugin.getLogger().warning("DatabaseManager가 null입니다 - 플레이어 등록 불가");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().createOrUpdatePlayer(
                        player.getUniqueId(), player.getName());

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "비동기 플레이어 등록 실패: " + player.getName(), e);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING,
                    "플레이어 등록 중 예상치 못한 오류: " + player.getName(), throwable);
            return null;
        });
    }

    /**
     * 직업 시스템 접속 처리
     */
    private void handleJobSystemOnJoin(Player player) {
        if (!plugin.isFeatureEnabled("job_system")) {
            return;
        }

        JobManager jobManager = plugin.getJobManager();
        if (jobManager == null) {
            plugin.getLogger().warning("JobManager가 null입니다 - 직업 시스템 처리 불가");
            return;
        }

        try {
            // 강제 직업 선택이 활성화된 경우
            boolean forceJobSelection = plugin.getConfig().getBoolean(
                    "job_system.force_job_selection", true);

            if (forceJobSelection) {
                int delay = plugin.getConfig().getInt(
                        "job_system.job_selection_delay", 60);

                // 지연 후 직업 선택 확인
                scheduleJobSelectionCheck(player, delay);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 시스템 접속 처리 실패: " + player.getName(), e);
        }
    }

    /**
     * 직업 선택 확인 스케줄
     */
    private void scheduleJobSelectionCheck(Player player, int delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || plugin.isShuttingDown()) {
                    return;
                }

                try {
                    checkJobSelection(player);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "직업 선택 확인 실패: " + player.getName(), e);
                }
            }
        }.runTaskLater(plugin, delay * 20L);
    }

    /**
     * 직업 선택 확인 및 안내 - 수정된 안전한 버전
     */
    private void checkJobSelection(Player player) {
        try {
            JobManager jobManager = plugin.getJobManager();
            if (jobManager == null) {
                plugin.getLogger().warning("JobManager가 null입니다 - 직업 선택 확인 불가");
                return;
            }

            // 플레이어가 여전히 온라인인지 확인
            if (!player.isOnline()) {
                return;
            }

            // getJobType을 사용하여 직업 확인 (수정된 부분)
            JobType currentJob = jobManager.getJobType(player);

            if (currentJob == JobType.NONE) {
                // 직업 미선택 플레이어에게 안내
                sendJobSelectionReminder(player);

                // 1분 후 다시 알림
                scheduleFollowUpReminder(player);

            } else {
                // 직업이 있는 플레이어에게 환영 메시지
                sendWelcomeMessageWithJob(player, currentJob, jobManager);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "직업 선택 확인 중 오류: " + player.getName(), e);
        }
    }

    /**
     * 후속 알림 스케줄
     */
    private void scheduleFollowUpReminder(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || plugin.isShuttingDown()) {
                    return;
                }

                try {
                    JobManager jobManager = plugin.getJobManager();
                    if (jobManager != null) {
                        JobType stillNoJob = jobManager.getJobType(player);
                        if (stillNoJob == JobType.NONE) {
                            player.sendMessage("§e직업을 선택하여 특수 능력을 활용하세요!");
                            player.sendMessage("§7명령어: §a/job select");
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "후속 알림 처리 실패: " + player.getName(), e);
                }
            }
        }.runTaskLater(plugin, 1200L); // 1분 후
    }

    /**
     * 직업 선택 안내 메시지
     */
    private void sendJobSelectionReminder(Player player) {
        try {
            player.sendMessage("§6==========================================");
            player.sendMessage("§e직업을 선택하세요!");
            player.sendMessage("");
            player.sendMessage("§7직업을 선택하면:");
            player.sendMessage("§7• 각 직업별 고유 능력 획득");
            player.sendMessage("§7• 몬스터 처치로 경험치 획득");
            player.sendMessage("§7• 레벨 5 달성 시 특수 능력 해제");
            player.sendMessage("§7• 최대 10레벨까지 성장 가능");
            player.sendMessage("");
            player.sendMessage("§c주의: 직업 선택 후 변경은 불가능합니다!");
            player.sendMessage("§a명령어: §f/job select");
            player.sendMessage("§6==========================================");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직업 선택 안내 메시지 전송 실패: " + player.getName(), e);
        }
    }

    /**
     * 직업이 있는 플레이어 환영 메시지
     */
    private void sendWelcomeMessageWithJob(Player player, JobType job, JobManager jobManager) {
        try {
            int level = jobManager.getJobLevel(player);
            int experience = jobManager.getJobExperience(player);

            player.sendMessage("§a환영합니다! " + job.getColor() + job.getDisplayName() +
                    " §a직업으로 플레이하시는군요!");
            player.sendMessage("§7레벨: §f" + level + " §7경험치: §f" + experience);

            // 레벨별 안내
            if (level < 5) {
                player.sendMessage("§7레벨 5가 되면 특수 능력이 해제됩니다!");
            } else if (level < 10) {
                player.sendMessage("§6레벨 10이 되면 최강의 능력을 얻습니다!");
            } else {
                player.sendMessage("§6만렙 달성! 최강의 능력을 보유하고 있습니다!");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "환영 메시지 전송 실패: " + player.getName(), e);
        }
    }

    /**
     * 환영 메시지 스케줄
     */
    private void scheduleWelcomeMessage(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || plugin.isShuttingDown()) {
                    return;
                }

                try {
                    sendServerWelcomeMessage(player);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "환영 메시지 스케줄 실패: " + player.getName(), e);
                }
            }
        }.runTaskLater(plugin, 40L); // 2초 후
    }

    /**
     * 서버 환영 메시지
     */
    private void sendServerWelcomeMessage(Player player) {
        try {
            // 서버 타입에 따른 환영 메시지
            int port = plugin.getServer().getPort();
            String serverType = getServerTypeName(port);

            player.sendMessage("");
            player.sendMessage("§6==========================================");
            player.sendMessage("§e" + serverType + "에 오신 것을 환영합니다!");
            player.sendMessage("§7플레이어: §f" + player.getName());

            // 활성화된 기능 안내
            showAvailableFeatures(player);

            player.sendMessage("§6==========================================");
            player.sendMessage("");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "서버 환영 메시지 전송 실패: " + player.getName(), e);
        }
    }

    /**
     * 서버 타입 이름 반환
     */
    private String getServerTypeName(int port) {
        switch (port) {
            case 25565: return "GGM 로비 서버";
            case 25566: return "GGM 건축 서버";
            case 25567: return "GGM 야생 서버";
            case 25568: return "GGM 마을 서버";
            default: return "GGM 서버";
        }
    }

    /**
     * 사용 가능한 기능 안내
     */
    private void showAvailableFeatures(Player player) {
        try {
            // 직업 시스템
            if (plugin.isFeatureEnabled("job_system")) {
                player.sendMessage("§a• 직업 시스템: §f/job");
            }

            // 강화 시스템
            if (plugin.isFeatureEnabled("upgrade_system")) {
                player.sendMessage("§a• 강화 시스템: §f/upgrade");
            }

            // 드래곤 보상
            if (plugin.isFeatureEnabled("dragon_reward")) {
                player.sendMessage("§a• 드래곤 보상: §f/dragon");
            }

            // NPC 교환
            if (plugin.isFeatureEnabled("npc_trading")) {
                player.sendMessage("§a• NPC 교환: §f/trade");
            }

            // 기본 명령어
            player.sendMessage("§a• 서버 정보: §f/survival");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "기능 안내 표시 실패: " + player.getName(), e);
        }
    }

    /**
     * 방어구 슬롯인지 확인
     */
    private boolean isArmorSlot(int slot) {
        // 방어구 슬롯: 36(부츠), 37(레깅스), 38(흉갑), 39(헬멧)
        return slot >= 36 && slot <= 39;
    }

    /**
     * 아이템이 방어구인지 확인
     */
    private boolean isArmor(ItemStack item) {
        if (item == null) return false;

        String typeName = item.getType().name();
        return typeName.contains("HELMET") ||
                typeName.contains("CHESTPLATE") ||
                typeName.contains("LEGGINGS") ||
                typeName.contains("BOOTS");
    }

    /**
     * 아이템이 무기인지 확인
     */
    private boolean isWeapon(ItemStack item) {
        if (item == null) return false;

        String typeName = item.getType().name();
        return typeName.contains("SWORD") ||
                typeName.contains("AXE") ||
                typeName.contains("BOW") ||
                typeName.contains("CROSSBOW") ||
                typeName.contains("TRIDENT");
    }

    /**
     * 디버그 정보 로그 (설정에 따라)
     */
    private void logDebugInfo(String message, Player player) {
        if (plugin.getConfig().getBoolean("debug.enabled", false) &&
                plugin.getConfig().getBoolean("debug.log_player_events", false)) {

            plugin.getLogger().info("[DEBUG] " + message + " (플레이어: " + player.getName() + ")");
        }
    }
}