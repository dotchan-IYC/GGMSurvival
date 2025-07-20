// 완전한 JobCommand.java - 직업이 없을 때만 변경 가능
package com.ggm.ggmsurvival.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

public class JobCommand implements CommandExecutor, Listener {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    public JobCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();

        // 이벤트 리스너 등록
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getLogger().info("JobCommand 초기화 완료 - JobManager: " + (jobManager != null ? "OK" : "NULL"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        // JobManager 확인
        if (jobManager == null) {
            player.sendMessage("§c직업 시스템이 초기화되지 않았습니다!");
            plugin.getLogger().severe("JobManager가 null입니다!");
            return true;
        }

        if (args.length == 0) {
            showCurrentJobSafe(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        plugin.getLogger().info("JobCommand 실행: " + subCommand + " by " + player.getName());

        switch (subCommand) {
            case "select":
            case "선택":
                openJobSelectionFixed(player);
                break;
            case "info":
            case "정보":
                showDetailedJobInfo(player);
                break;
            case "list":
            case "목록":
                showJobList(player);
                break;
            case "reset":
                if (!player.hasPermission("ggm.job.admin")) {
                    player.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /job reset <플레이어>");
                    return true;
                }
                resetPlayerJob(player, args[1]);
                break;
            case "help":
            case "도움말":
                sendHelp(player);
                break;
            case "debug":
                showDebugInfo(player);
                break;
            case "testgui":
                openTestGUI(player);
                break;
            default:
                showCurrentJobSafe(player);
                break;
        }

        return true;
    }

    /**
     * 수정된 직업 선택 - 직업이 없을 때만 선택 가능
     */
    private void openJobSelectionFixed(Player player) {
        try {
            player.sendMessage("§e직업 선택 가능 여부를 확인하는 중...");
            plugin.getLogger().info("직업 선택 가능 여부 확인: " + player.getName());

            // 현재 플레이어가 직업을 가지고 있는지 확인
            jobManager.hasSelectedJob(player.getUniqueId()).thenAccept(hasJob -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (hasJob) {
                        // 이미 직업이 있는 경우 - 변경 불가
                        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§e§l⚠️ 직업 변경 불가");
                        player.sendMessage("");
                        player.sendMessage("§c이미 직업을 선택하셨습니다!");
                        player.sendMessage("§7직업은 한 번 선택하면 변경할 수 없습니다.");
                        player.sendMessage("");
                        player.sendMessage("§a현재 직업 정보: §f/job info");
                        player.sendMessage("§7관리자에게 문의하여 직업 초기화를 요청하세요.");
                        player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                        // 현재 직업 정보도 표시
                        showCurrentJobSafe(player);
                    } else {
                        // 직업이 없는 경우 - 선택 가능
                        player.sendMessage("§a직업이 없어 선택이 가능합니다!");
                        player.sendMessage("§e직업 선택 GUI를 엽니다!");
                        openJobSelectionGUI(player);
                    }
                });
            }).exceptionally(throwable -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c직업 확인 중 오류 발생: " + throwable.getMessage());
                    plugin.getLogger().severe("직업 확인 오류: " + throwable.getMessage());
                });
                return null;
            });

        } catch (Exception e) {
            player.sendMessage("§c직업 선택 처리 중 오류 발생: " + e.getMessage());
            plugin.getLogger().severe("직업 선택 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 수정된 직업 선택 GUI - 추가 검증 포함
     */
    private void openJobSelectionGUI(Player player) {
        try {
            plugin.getLogger().info("GUI 생성 시작: " + player.getName());

            // 한 번 더 검증 (안전장치)
            jobManager.getPlayerJob(player.getUniqueId()).thenAccept(currentJob -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (currentJob != JobManager.JobType.NONE) {
                        player.sendMessage("§c직업 선택 중 오류: 이미 직업이 있습니다! (" + currentJob.getDisplayName() + ")");
                        return;
                    }

                    // 27칸 인벤토리 생성
                    Inventory gui = Bukkit.createInventory(null, 27, "§6§l직업 선택");

                    // 탱커 아이템
                    ItemStack tankItem = createJobItem(
                            Material.IRON_CHESTPLATE,
                            "§c§l탱커",
                            Arrays.asList(
                                    "§7방어와 체력에 특화된 근접 전투 직업",
                                    "",
                                    "§a§l효과:",
                                    "§7• 흉갑 착용 시 체력 +2하트",
                                    "§7• 방패 사용 시 체력 0.5하트 회복",
                                    "§7• 받는 피해 10% 감소",
                                    "",
                                    "§e클릭하여 선택!",
                                    "§c⚠️ 선택 후 변경 불가능!"
                            )
                    );

                    // 검사 아이템
                    ItemStack warriorItem = createJobItem(
                            Material.IRON_SWORD,
                            "§6§l검사",
                            Arrays.asList(
                                    "§7검술에 특화된 공격적인 근접 전투 직업",
                                    "",
                                    "§a§l효과:",
                                    "§7• 검 공격력 +20%",
                                    "§7• 치명타 확률 10%",
                                    "§7• 검 내구도 소모 확률 15% 감소",
                                    "",
                                    "§e클릭하여 선택!",
                                    "§c⚠️ 선택 후 변경 불가능!"
                            )
                    );

                    // 궁수 아이템
                    ItemStack archerItem = createJobItem(
                            Material.BOW,
                            "§a§l궁수",
                            Arrays.asList(
                                    "§7원거리 공격과 기동성에 특화된 직업",
                                    "",
                                    "§a§l효과:",
                                    "§7• 활 공격력 +15%",
                                    "§7• 화살 소모 확률 20% 감소",
                                    "§7• 가죽부츠 착용 시 이동속도 증가",
                                    "",
                                    "§e클릭하여 선택!",
                                    "§c⚠️ 선택 후 변경 불가능!"
                            )
                    );

                    // 경고 아이템 (가운데 하단)
                    ItemStack warningItem = createJobItem(
                            Material.BARRIER,
                            "§c§l⚠️ 중요한 안내",
                            Arrays.asList(
                                    "§e직업은 한 번 선택하면 변경할 수 없습니다!",
                                    "",
                                    "§7• 각 직업은 고유한 특수 능력을 제공합니다",
                                    "§7• 플레이 스타일을 고려하여 신중하게 선택하세요",
                                    "§7• 관리자만 직업을 초기화할 수 있습니다",
                                    "",
                                    "§a충분히 고민한 후 선택하시기 바랍니다!"
                            )
                    );

                    // GUI에 아이템 배치
                    gui.setItem(10, tankItem);     // 탱커 (왼쪽)
                    gui.setItem(13, warriorItem);  // 검사 (가운데)
                    gui.setItem(16, archerItem);   // 궁수 (오른쪽)
                    gui.setItem(22, warningItem);  // 경고 (가운데 하단)

                    // 장식용 유리판 추가
                    ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                    ItemMeta glassMeta = glass.getItemMeta();
                    glassMeta.setDisplayName("§0");
                    glass.setItemMeta(glassMeta);

                    // 빈 칸에 유리판 채우기
                    for (int i = 0; i < 27; i++) {
                        if (gui.getItem(i) == null) {
                            gui.setItem(i, glass);
                        }
                    }

                    player.openInventory(gui);
                    player.sendMessage("§a직업 선택 GUI가 열렸습니다!");
                    plugin.getLogger().info("직업 선택 GUI 열기 성공: " + player.getName());
                });
            }).exceptionally(throwable -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c직업 선택 GUI 열기 실패: " + throwable.getMessage());
                    plugin.getLogger().severe("GUI 열기 실패: " + throwable.getMessage());
                });
                return null;
            });

        } catch (Exception e) {
            player.sendMessage("§c직업 선택 GUI 처리 중 오류: " + e.getMessage());
            plugin.getLogger().severe("openJobSelectionGUI 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 직업 아이템 생성 헬퍼 메서드
     */
    private ItemStack createJobItem(Material material, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * GUI 클릭 이벤트 처리 - 강화된 검증 로직
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // 직업 선택 GUI 확인
        if (event.getView().getTitle().equals("§6§l직업 선택")) {
            event.setCancelled(true); // 모든 클릭 차단

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            try {
                plugin.getLogger().info("직업 선택 GUI 클릭: " + player.getName() + " -> " + clickedItem.getType());

                // 클릭한 아이템에 따른 직업 결정
                JobManager.JobType selectedJob = null;

                switch (clickedItem.getType()) {
                    case IRON_CHESTPLATE:
                        selectedJob = JobManager.JobType.TANK;
                        break;
                    case IRON_SWORD:
                        selectedJob = JobManager.JobType.WARRIOR;
                        break;
                    case BOW:
                        selectedJob = JobManager.JobType.ARCHER;
                        break;
                    case BARRIER:
                        // 경고 아이템 클릭 시 도움말 표시
                        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§c§l⚠️ 직업 선택 안내");
                        player.sendMessage("");
                        player.sendMessage("§7• 직업은 한 번 선택하면 §c변경할 수 없습니다§7!");
                        player.sendMessage("§7• 각 직업은 고유한 특수 능력을 제공합니다");
                        player.sendMessage("§7• 플레이 스타일을 신중히 고려하여 선택하세요");
                        player.sendMessage("§7• 관리자만 직업을 초기화할 수 있습니다");
                        player.sendMessage("");
                        player.sendMessage("§a위의 직업 아이템을 클릭하여 선택하세요!");
                        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        return;
                    default:
                        // 다른 아이템 클릭 시 아무 동작 안 함
                        return;
                }

                if (selectedJob == null) return;

                final JobManager.JobType finalJob = selectedJob;

                // 직업 선택 전 마지막 검증
                player.sendMessage("§e선택하신 직업: " + finalJob.getDisplayName());
                player.sendMessage("§e직업 선택 가능 여부를 확인하는 중...");

                // 비동기로 현재 직업 상태 확인
                jobManager.getPlayerJob(player.getUniqueId()).thenAccept(currentJob -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (currentJob != JobManager.JobType.NONE) {
                            // 이미 직업이 있는 경우
                            player.closeInventory();
                            player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            player.sendMessage("§e§l❌ 직업 선택 차단");
                            player.sendMessage("");
                            player.sendMessage("§c이미 직업을 보유하고 있습니다!");
                            player.sendMessage("§7현재 직업: " + currentJob.getDisplayName());
                            player.sendMessage("§7직업 변경은 불가능합니다.");
                            player.sendMessage("");
                            player.sendMessage("§7관리자에게 문의하여 직업 초기화를 요청하세요.");
                            player.sendMessage("§c━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                            plugin.getLogger().warning("직업 선택 차단: " + player.getName() + "이(가) 이미 " + currentJob.name() + " 직업을 보유중");
                            return;
                        }

                        // 직업이 없는 경우 - 최종 확인 메시지
                        player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§e§l🔔 최종 확인");
                        player.sendMessage("");
                        player.sendMessage("§7선택하려는 직업: " + finalJob.getDisplayName());
                        player.sendMessage("");
                        player.sendMessage("§c⚠️ 주의: 직업은 선택 후 변경할 수 없습니다!");
                        player.sendMessage("§a정말로 " + finalJob.getDisplayName() + "§a을(를) 선택하시겠습니까?");
                        player.sendMessage("");
                        player.sendMessage("§e5초 후 자동으로 선택됩니다...");
                        player.sendMessage("§7취소하려면 인벤토리를 닫으세요.");
                        player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                        // 5초 후 자동 선택
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline() &&
                                        player.getOpenInventory().getTitle().equals("§6§l직업 선택")) {

                                    player.sendMessage("§a" + finalJob.getDisplayName() + " 직업 선택을 진행합니다!");
                                    selectJobDirectly(player, finalJob);
                                }
                            }
                        }.runTaskLater(plugin, 100L); // 5초 (100 ticks)
                    });
                }).exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c직업 확인 중 오류 발생: " + throwable.getMessage());
                        player.closeInventory();
                    });
                    plugin.getLogger().severe("GUI 클릭 시 직업 확인 오류: " + throwable.getMessage());
                    return null;
                });

            } catch (Exception e) {
                player.sendMessage("§c직업 선택 처리 중 오류 발생: " + e.getMessage());
                plugin.getLogger().severe("직업 선택 클릭 처리 오류: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 직접 직업 선택 처리
     */
    private void selectJobDirectly(Player player, JobManager.JobType jobType) {
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
                            player.sendMessage("");
                            player.sendMessage("§c⚠️ 주의: 직업은 변경할 수 없습니다!");
                            player.sendMessage("§a━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                            // 사운드 효과
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                            // 즉시 직업 효과 적용
                            jobManager.applyJobEffects(player);

                            plugin.getLogger().info(player.getName() + "이(가) " + jobType.getName() + " 직업을 선택했습니다.");
                        } else {
                            player.sendMessage("§c직업 선택에 실패했습니다. 이미 다른 직업을 보유하고 있거나 오류가 발생했습니다.");
                            plugin.getLogger().warning(player.getName() + "의 직업 선택이 실패했습니다 - 검증 실패");
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
     * 검증을 포함한 직업 설정 (비동기 스레드에서 호출)
     */
    private boolean setPlayerJobSync(UUID uuid, String playerName, JobManager.JobType jobType) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // 1. 현재 직업 상태를 한 번 더 확인
            String checkSql = "SELECT job_type FROM ggm_player_jobs WHERE uuid = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, uuid.toString());
                try (var rs = checkStmt.executeQuery()) {
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
                        try (var verifyRs = verifyStmt.executeQuery()) {
                            if (verifyRs.next()) {
                                String finalJob = verifyRs.getString("job_type");
                                if (finalJob.equals(jobType.name())) {
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
    }

    /**
     * 현재 직업 안전하게 표시
     */
    private void showCurrentJobSafe(Player player) {
        try {
            jobManager.getPlayerJob(player.getUniqueId()).thenAccept(jobType -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§e§l👤 현재 직업 정보");
                    player.sendMessage("");

                    if (jobType != JobManager.JobType.NONE) {
                        player.sendMessage("§7현재 직업: " + jobType.getDisplayName());
                        player.sendMessage("§a특수 능력이 활성화되어 있습니다!");
                        player.sendMessage("");
                        player.sendMessage("§e자세한 정보: §f/job info");
                        player.sendMessage("§c직업 변경은 불가능합니다.");
                    } else {
                        player.sendMessage("§c현재 직업이 없습니다!");
                        player.sendMessage("");
                        player.sendMessage("§7/job select 명령어로 직업을 선택하세요.");
                        player.sendMessage("");
                        player.sendMessage("§a§l💡 직업 선택의 이점:");
                        player.sendMessage("§7• 각 직업별 특수 능력 획득");
                        player.sendMessage("§7• 전투/채굴/탐험에서 보너스");
                        player.sendMessage("§7• 야생 서버만의 특별한 경험!");
                    }

                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                });
            }).exceptionally(throwable -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c직업 정보를 불러오는 중 오류가 발생했습니다.");
                });
                return null;
            });

        } catch (Exception e) {
            player.sendMessage("§c직업 정보 표시 중 오류: " + e.getMessage());
            plugin.getLogger().severe("showCurrentJobSafe 오류: " + e.getMessage());
        }
    }

    /**
     * 상세 직업 정보 표시
     */
    private void showDetailedJobInfo(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l야생 서버 직업 안내");
        player.sendMessage("");

        player.sendMessage("§c§l🛡 탱커 (TANK)");
        player.sendMessage("§7방어와 체력에 특화된 근접 전투 직업");
        player.sendMessage("§7효과: 흉갑 착용 시 체력 증가, 방패로 체력 회복");
        player.sendMessage("§7추천: 몬스터와 정면 대결을 즐기는 플레이어");
        player.sendMessage("");

        player.sendMessage("§6§l⚔ 검사 (WARRIOR)");
        player.sendMessage("§7검술에 특화된 공격적인 근접 전투 직업");
        player.sendMessage("§7효과: 검 공격력 증가, 치명타 확률 증가");
        player.sendMessage("§7추천: 높은 데미지로 빠르게 적을 처치하고 싶은 플레이어");
        player.sendMessage("");

        player.sendMessage("§a§l🏹 궁수 (ARCHER)");
        player.sendMessage("§7원거리 공격과 기동성에 특화된 직업");
        player.sendMessage("§7효과: 활 공격력 증가, 가죽부츠 착용 시 이동속도 증가");
        player.sendMessage("§7추천: 안전한 거리에서 전투하고 빠른 이동을 원하는 플레이어");
        player.sendMessage("");

        player.sendMessage("§e§l⚠️ 중요한 안내:");
        player.sendMessage("§7• 직업은 §c한 번 선택하면 변경할 수 없습니다§7!");
        player.sendMessage("§7• 각 직업은 고유한 플레이 스타일을 제공합니다");
        player.sendMessage("§7• 신중하게 선택하시기 바랍니다");

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 직업 목록 표시
     */
    private void showJobList(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l사용 가능한 직업");
        player.sendMessage("");
        player.sendMessage("§c탱커 §7- 방어형 근접 전투 직업");
        player.sendMessage("§6검사 §7- 공격형 근접 전투 직업");
        player.sendMessage("§a궁수 §7- 원거리 전투 직업");
        player.sendMessage("");
        player.sendMessage("§7자세한 정보: §e/job info");
        player.sendMessage("§7직업 선택: §e/job select");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 플레이어 직업 초기화 (OP 전용)
     */
    private void resetPlayerJob(Player sender, String targetName) {
        try {
            Player target = plugin.getServer().getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + targetName);
                return;
            }

            // 직접 데이터베이스 업데이트 시도
            sender.sendMessage("§e" + targetName + "의 직업을 초기화하는 중...");

            // 간단한 방법: JobManager 통해 NONE 설정
            jobManager.setPlayerJob(target.getUniqueId(), target.getName(), JobManager.JobType.NONE)
                    .thenAccept(success -> {
                        if (success) {
                            sender.sendMessage("§a" + targetName + "의 직업을 초기화했습니다.");
                            target.sendMessage("§e관리자에 의해 직업이 초기화되었습니다.");
                            target.sendMessage("§7다시 /job select 명령어로 직업을 선택할 수 있습니다.");
                            plugin.getLogger().info(sender.getName() + "이(가) " + targetName + "의 직업을 초기화했습니다.");
                        } else {
                            sender.sendMessage("§c직업 초기화에 실패했습니다.");
                        }
                    })
                    .exceptionally(throwable -> {
                        sender.sendMessage("§c직업 초기화 중 오류: " + throwable.getMessage());
                        plugin.getLogger().severe("직업 초기화 오류: " + throwable.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            sender.sendMessage("§c직업 초기화 중 예외 발생: " + e.getMessage());
            plugin.getLogger().severe("resetPlayerJob 예외: " + e.getMessage());
        }
    }

    /**
     * 디버그 정보 표시
     */
    private void showDebugInfo(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l🔧 직업 시스템 디버그 정보");
        player.sendMessage("");
        player.sendMessage("§7Plugin: " + (plugin != null ? "§aOK" : "§cNULL"));
        player.sendMessage("§7JobManager: " + (jobManager != null ? "§aOK" : "§cNULL"));
        player.sendMessage("§7Player UUID: " + player.getUniqueId());
        player.sendMessage("§7Player Name: " + player.getName());
        player.sendMessage("");
        player.sendMessage("§7테스트 명령어:");
        player.sendMessage("§e/job testgui §7- 간단한 GUI 테스트");
        player.sendMessage("§e/job select §7- 직업 선택 (수정됨)");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * 테스트용 간단한 GUI
     */
    private void openTestGUI(Player player) {
        try {
            Inventory testGui = Bukkit.createInventory(null, 9, "§aTest GUI");
            testGui.setItem(4, new ItemStack(Material.DIAMOND));
            player.openInventory(testGui);
            player.sendMessage("§a테스트 GUI 열기 성공!");
            plugin.getLogger().info("테스트 GUI 성공: " + player.getName());
        } catch (Exception e) {
            player.sendMessage("§c테스트 GUI 실패: " + e.getMessage());
            plugin.getLogger().severe("테스트 GUI 실패: " + e.getMessage());
        }
    }

    /**
     * 도움말 표시
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§l직업 시스템 명령어");
        player.sendMessage("");
        player.sendMessage("§7/job §f- 현재 직업 정보 확인");
        player.sendMessage("§7/job select §f- 직업 선택 GUI 열기");
        player.sendMessage("§7/job info §f- 모든 직업 상세 정보");
        player.sendMessage("§7/job list §f- 직업 목록 확인");
        player.sendMessage("§7/job debug §f- 디버그 정보 확인");
        player.sendMessage("§7/job testgui §f- 테스트 GUI 열기");

        if (player.hasPermission("ggm.job.admin")) {
            player.sendMessage("");
            player.sendMessage("§c관리자 명령어:");
            player.sendMessage("§7/job reset <플레이어> §f- 직업 초기화");
        }

        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}