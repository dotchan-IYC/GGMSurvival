// 완전 안정화된 UpgradeCommand.java
package com.ggm.ggmsurvival.commands;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.EnchantUpgradeManager;
import com.ggm.ggmsurvival.managers.AxeSpeedManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 완전 안정화된 강화 명령어 처리기
 * - 검, 도끼, 활, 흉갑만 강화 가능
 * - 강력한 예외 처리
 * - GUI 시스템 포함
 * - 관리자 명령어 지원
 */
public class UpgradeCommand implements CommandExecutor, TabCompleter {

    private final GGMSurvival plugin;
    private final EnchantUpgradeManager upgradeManager;
    private final AxeSpeedManager axeSpeedManager;

    // 명령어 권한 맵
    private final Map<String, String> commandPermissions;

    public UpgradeCommand(GGMSurvival plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getEnchantUpgradeManager();
        this.axeSpeedManager = plugin.getAxeSpeedManager();

        // 명령어별 권한 초기화
        this.commandPermissions = initializePermissions();

        if (upgradeManager == null) {
            plugin.getLogger().warning("EnchantUpgradeManager가 null입니다. 강화 명령어가 제한될 수 있습니다.");
        }
    }

    /**
     * 명령어별 권한 초기화
     */
    private Map<String, String> initializePermissions() {
        Map<String, String> permissions = new HashMap<>();
        permissions.put("info", "ggm.upgrade");
        permissions.put("guide", "ggm.upgrade");
        permissions.put("rates", "ggm.upgrade");
        permissions.put("gui", "ggm.upgrade");
        permissions.put("direct", "ggm.upgrade");
        permissions.put("items", "ggm.upgrade");
        permissions.put("axespeed", "ggm.upgrade");
        permissions.put("reload", "ggm.upgrade.admin");
        permissions.put("stats", "ggm.upgrade.admin");
        return Collections.unmodifiableMap(permissions);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // 플러그인 상태 확인
            if (plugin.isShuttingDown()) {
                sender.sendMessage("§c서버가 종료 중입니다. 잠시 후 다시 시도해주세요.");
                return true;
            }

            // 강화 시스템 활성화 확인
            if (!plugin.isFeatureEnabled("upgrade_system")) {
                sender.sendMessage("§c강화 시스템이 이 서버에서 비활성화되어 있습니다.");
                return true;
            }

            // EnchantUpgradeManager 상태 확인
            if (upgradeManager == null) {
                sender.sendMessage("§c강화 시스템이 비활성화되어 있습니다.");
                return true;
            }

            // 기본 사용법
            if (args.length == 0) {
                showMainHelp(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();

            // 권한 확인
            if (!hasPermissionForCommand(sender, subCommand)) {
                sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.");
                return true;
            }

            // 하위 명령어 처리
            switch (subCommand) {
                case "info":
                case "check":
                    return handleInfoCommand(sender);

                case "guide":
                case "도움말":
                    return handleGuideCommand(sender);

                case "rates":
                case "확률":
                    return handleRatesCommand(sender);

                case "gui":
                case "메뉴":
                    return handleGUICommand(sender);

                case "direct":
                case "강화":
                    return handleDirectCommand(sender);

                case "items":
                case "아이템":
                    return handleItemsCommand(sender);

                case "axespeed":
                case "도끼속도":
                    return handleAxeSpeedCommand(sender);

                case "reload":
                    return handleReloadCommand(sender);

                case "stats":
                case "통계":
                    return handleStatsCommand(sender);

                case "help":
                    return handleDetailedHelpCommand(sender);

                default:
                    sender.sendMessage("§c알 수 없는 명령어입니다. §7/upgrade help §c를 참고하세요.");
                    return true;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "강화 명령어 처리 중 오류: " + sender.getName() + " - " + String.join(" ", args), e);
            sender.sendMessage("§c명령어 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요.");
            return true;
        }
    }

    /**
     * 강화 정보 명령어 처리
     */
    private boolean handleInfoCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        try {
            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (mainHand == null || mainHand.getType() == Material.AIR) {
                player.sendMessage("§c강화할 아이템을 들고 사용해주세요!");
                return true;
            }

            // 강화 정보 표시
            upgradeManager.showUpgradeInfo(player, mainHand);

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 정보 명령어 처리 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c정보 조회 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 강화 가이드 명령어 처리
     */
    private boolean handleGuideCommand(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l강화 시스템 가이드");
            sender.sendMessage("");
            sender.sendMessage("§a강화 가능한 아이템:");
            sender.sendMessage("§7• 검류 (모든 티어)");
            sender.sendMessage("§7• 도끼류 (모든 티어)");
            sender.sendMessage("§7• 활/쇠뇌");
            sender.sendMessage("§7• 흉갑류 (모든 티어)");
            sender.sendMessage("");
            sender.sendMessage("§a강화 방법:");
            sender.sendMessage("§7• 인첸트 테이블 사용");
            sender.sendMessage("§7• /upgrade gui 명령어");
            sender.sendMessage("§7• /upgrade direct 명령어");
            sender.sendMessage("");
            sender.sendMessage("§a강화 효과:");
            sender.sendMessage("§7• 검/활: §a공격력 +3%/레벨");
            sender.sendMessage("§7• 도끼: §a공격속도 +2%/레벨");
            sender.sendMessage("§7• 흉갑: §a방어력 +3%/레벨");
            sender.sendMessage("");
            sender.sendMessage("§6★ 10강 특수 효과:");
            sender.sendMessage("§7• 검: §c출혈 효과");
            sender.sendMessage("§7• 도끼: §6발화 효과");
            sender.sendMessage("§7• 활: §a화염 화살");
            sender.sendMessage("§7• 흉갑: §9가시 효과");
            sender.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 가이드 명령어 처리 중 오류: " + sender.getName(), e);
            return true;
        }
    }

    /**
     * 강화 확률 명령어 처리
     */
    private boolean handleRatesCommand(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l강화 성공률 & 파괴율");
            sender.sendMessage("");
            sender.sendMessage("§a성공률:");
            sender.sendMessage("§7• 1~3강: §a95%");
            sender.sendMessage("§7• 4~5강: §e80%");
            sender.sendMessage("§7• 6~7강: §660%");
            sender.sendMessage("§7• 8~9강: §c40%");
            sender.sendMessage("§7• 10강: §4§l20%");
            sender.sendMessage("");
            sender.sendMessage("§c파괴/하락률:");
            sender.sendMessage("§7• 1~4강: §a파괴되지 않음");
            sender.sendMessage("§7• 5~7강: §e10% (1강 하락)");
            sender.sendMessage("§7• 8~9강: §c20% (1강 하락)");
            sender.sendMessage("§7• 10강: §4§l30% (1강 하락)");
            sender.sendMessage("");
            sender.sendMessage("§7강화 실패 시 레벨이 유지되거나 하락합니다.");
            sender.sendMessage("§7아이템이 완전히 파괴되지는 않습니다!");
            sender.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 확률 명령어 처리 중 오류: " + sender.getName(), e);
            return true;
        }
    }

    /**
     * 강화 GUI 명령어 처리
     */
    private boolean handleGUICommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        try {
            // GUI 사용 허용 여부 확인
            boolean allowGUI = plugin.getConfig().getBoolean("upgrade_system.upgrade_methods.command_gui", true);
            if (!allowGUI) {
                player.sendMessage("§cGUI를 통한 강화가 비활성화되어 있습니다.");
                player.sendMessage("§7인첸트 테이블을 사용하거나 /upgrade direct 명령어를 사용해주세요.");
                return true;
            }

            openUpgradeGUI(player);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 GUI 명령어 처리 중 오류: " + sender.getName(), e);
            sender.sendMessage("§cGUI를 열 수 없습니다.");
            return true;
        }
    }

    /**
     * 직접 강화 명령어 처리
     */
    private boolean handleDirectCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        try {
            // 직접 강화 허용 여부 확인
            boolean allowDirect = plugin.getConfig().getBoolean("upgrade_system.upgrade_methods.direct_command", true);
            if (!allowDirect) {
                player.sendMessage("§c명령어를 통한 직접 강화가 비활성화되어 있습니다.");
                player.sendMessage("§7인첸트 테이블을 사용해주세요.");
                return true;
            }

            ItemStack mainHand = player.getInventory().getItemInMainHand();

            if (mainHand == null || mainHand.getType() == Material.AIR) {
                player.sendMessage("§c강화할 아이템을 들고 사용해주세요!");
                return true;
            }

            // 쿨다운 확인
            if (upgradeManager.isOnCooldown(player)) {
                player.sendMessage("§c잠시 후 다시 시도해주세요. (쿨다운: 1초)");
                return true;
            }

            // 강화 실행
            boolean success = upgradeManager.performUpgrade(player, mainHand);

            if (success) {
                plugin.getLogger().info(String.format("[강화성공] %s이(가) %s을(를) 강화했습니다.",
                        player.getName(), mainHand.getType().name()));
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "직접 강화 명령어 처리 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c강화 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 강화 가능 아이템 목록 명령어 처리
     */
    private boolean handleItemsCommand(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l강화 가능한 아이템 목록");
            sender.sendMessage("");
            sender.sendMessage("§c§l검류:");
            sender.sendMessage("§7• 나무검, 돌검, 철검, 금검, 다이아검, 네더라이트검");
            sender.sendMessage("");
            sender.sendMessage("§6§l도끼류:");
            sender.sendMessage("§7• 나무도끼, 돌도끼, 철도끼, 금도끼, 다이아도끼, 네더라이트도끼");
            sender.sendMessage("");
            sender.sendMessage("§a§l활류:");
            sender.sendMessage("§7• 활, 쇠뇌");
            sender.sendMessage("");
            sender.sendMessage("§9§l흉갑류:");
            sender.sendMessage("§7• 가죽흉갑, 사슬흉갑, 철흉갑, 금흉갑, 다이아흉갑, 네더라이트흉갑");
            sender.sendMessage("");
            sender.sendMessage("§c주의: §7나무 장비는 강화할 수 없습니다!");
            sender.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 아이템 목록 명령어 처리 중 오류: " + sender.getName(), e);
            return true;
        }
    }

    /**
     * 도끼 속도 명령어 처리
     */
    private boolean handleAxeSpeedCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        try {
            if (axeSpeedManager == null) {
                player.sendMessage("§c도끼 속도 시스템이 비활성화되어 있습니다.");
                return true;
            }

            axeSpeedManager.showAxeSpeedInfo(player);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "도끼 속도 명령어 처리 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c도끼 속도 정보 조회 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 설정 리로드 명령어 처리 (관리자 전용)
     */
    private boolean handleReloadCommand(CommandSender sender) {
        try {
            plugin.reloadConfig();

            // 도끼 속도 시스템 리로드
            if (axeSpeedManager != null) {
                axeSpeedManager.reloadConfig();
            }

            sender.sendMessage("§a강화 시스템 설정이 리로드되었습니다!");

            plugin.getLogger().info(sender.getName() + "이(가) 강화 시스템 설정을 리로드했습니다.");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "설정 리로드 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c설정 리로드 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 통계 명령어 처리 (관리자 전용)
     */
    private boolean handleStatsCommand(CommandSender sender) {
        try {
            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l강화 시스템 통계");
            sender.sendMessage("");

            // 시스템 상태
            sender.sendMessage("§a시스템 상태:");
            sender.sendMessage("§7• 강화 시스템: §a활성화");
            sender.sendMessage("§7• 도끼 속도 시스템: " + (axeSpeedManager != null ? "§a활성화" : "§c비활성화"));

            // 강화 방법 설정
            sender.sendMessage("");
            sender.sendMessage("§a강화 방법 설정:");
            sender.sendMessage("§7• 인첸트 테이블: " +
                    (plugin.getConfig().getBoolean("upgrade_system.upgrade_methods.enchant_table", true) ? "§a허용" : "§c차단"));
            sender.sendMessage("§7• GUI 명령어: " +
                    (plugin.getConfig().getBoolean("upgrade_system.upgrade_methods.command_gui", true) ? "§a허용" : "§c차단"));
            sender.sendMessage("§7• 직접 명령어: " +
                    (plugin.getConfig().getBoolean("upgrade_system.upgrade_methods.direct_command", true) ? "§a허용" : "§c차단"));

            // 온라인 플레이어 수
            sender.sendMessage("");
            sender.sendMessage("§a서버 정보:");
            sender.sendMessage("§7• 온라인 플레이어: §f" + Bukkit.getOnlinePlayers().size() + "명");

            // 도끼 속도 통계 (가능한 경우)
            if (axeSpeedManager != null) {
                sender.sendMessage("§7• 도끼 속도 보너스 범위: §f0% ~ " +
                        String.format("%.1f%%", axeSpeedManager.getMaxSpeedBonus() * 100));
            }

            sender.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "통계 명령어 처리 중 오류: " + sender.getName(), e);
            sender.sendMessage("§c통계 조회 중 오류가 발생했습니다.");
            return true;
        }
    }

    /**
     * 상세 도움말 명령어 처리
     */
    private boolean handleDetailedHelpCommand(CommandSender sender) {
        try {
            boolean isAdmin = sender.hasPermission("ggm.upgrade.admin");

            sender.sendMessage("§6==========================================");
            sender.sendMessage("§e§l강화 시스템 상세 도움말");
            sender.sendMessage("");
            sender.sendMessage("§a플레이어 명령어:");
            sender.sendMessage("§7• §e/upgrade info §7- 손에 든 아이템 강화 정보");
            sender.sendMessage("§7• §e/upgrade guide §7- 강화 시스템 가이드");
            sender.sendMessage("§7• §e/upgrade rates §7- 강화 성공률 & 파괴율");
            sender.sendMessage("§7• §e/upgrade gui §7- 강화 GUI 열기");
            sender.sendMessage("§7• §e/upgrade direct §7- 직접 강화 실행");
            sender.sendMessage("§7• §e/upgrade items §7- 강화 가능한 아이템 목록");
            sender.sendMessage("§7• §e/upgrade axespeed §7- 도끼 속도 정보");

            if (isAdmin) {
                sender.sendMessage("");
                sender.sendMessage("§c관리자 명령어:");
                sender.sendMessage("§7• §e/upgrade reload §7- 설정 리로드");
                sender.sendMessage("§7• §e/upgrade stats §7- 시스템 통계 조회");
            }

            sender.sendMessage("");
            sender.sendMessage("§a강화 팁:");
            sender.sendMessage("§7• 4강까지는 파괴되지 않습니다");
            sender.sendMessage("§7• 10강 달성 시 특수 효과가 부여됩니다");
            sender.sendMessage("§7• 도끼는 강화할수록 공격속도가 빨라집니다");
            sender.sendMessage("§6==========================================");

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "상세 도움말 처리 중 오류: " + sender.getName(), e);
            return true;
        }
    }

    /**
     * 강화 GUI 열기
     */
    private void openUpgradeGUI(Player player) {
        try {
            Inventory gui = Bukkit.createInventory(null, 27, "§6§l강화 시스템");

            // 정보 아이템
            ItemStack infoItem = new ItemStack(Material.BOOK);
            ItemMeta infoMeta = infoItem.getItemMeta();
            infoMeta.setDisplayName("§e§l강화 정보");
            infoMeta.setLore(Arrays.asList(
                    "§7강화할 아이템을 들고",
                    "§7아래 버튼을 클릭하세요!",
                    "",
                    "§a강화 가능: §7검, 도끼, 활, 흉갑",
                    "§c최대 강화: §710강"
            ));
            infoItem.setItemMeta(infoMeta);

            // 강화 실행 아이템
            ItemStack upgradeItem = new ItemStack(Material.ANVIL);
            ItemMeta upgradeMeta = upgradeItem.getItemMeta();
            upgradeMeta.setDisplayName("§a§l강화 실행");
            upgradeMeta.setLore(Arrays.asList(
                    "§7클릭하여 손에 든 아이템을 강화합니다",
                    "",
                    "§e필요 조건:",
                    "§7• 강화 가능한 아이템을 들고 있어야 함",
                    "§7• 충분한 경험치 보유",
                    "",
                    "§c주의: 실패 시 레벨이 하락할 수 있습니다!"
            ));
            upgradeItem.setItemMeta(upgradeMeta);

            // 성공률 확인 아이템
            ItemStack rateItem = new ItemStack(Material.PAPER);
            ItemMeta rateMeta = rateItem.getItemMeta();
            rateMeta.setDisplayName("§6§l성공률 확인");
            rateMeta.setLore(Arrays.asList(
                    "§7클릭하여 강화 성공률을 확인합니다",
                    "",
                    "§71~3강: §a95%",
                    "§74~5강: §e80%",
                    "§76~7강: §660%",
                    "§78~9강: §c40%",
                    "§710강: §4§l20%"
            ));
            rateItem.setItemMeta(rateMeta);

            // 가이드 아이템
            ItemStack guideItem = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta guideMeta = guideItem.getItemMeta();
            guideMeta.setDisplayName("§b§l강화 가이드");
            guideMeta.setLore(Arrays.asList(
                    "§7클릭하여 강화 가이드를 확인합니다",
                    "",
                    "§a강화 효과:",
                    "§7• 검/활: 공격력 증가",
                    "§7• 도끼: 공격속도 증가",
                    "§7• 흉갑: 방어력 증가",
                    "",
                    "§610강 특수 효과 있음!"
            ));
            guideItem.setItemMeta(guideMeta);

            // 아이템 배치
            gui.setItem(10, infoItem);
            gui.setItem(12, upgradeItem);
            gui.setItem(14, rateItem);
            gui.setItem(16, guideItem);

            // 장식 아이템
            ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta glassMeta = glassPane.getItemMeta();
            glassMeta.setDisplayName(" ");
            glassPane.setItemMeta(glassMeta);

            // 테두리 채우기
            for (int i = 0; i < 27; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, glassPane);
                }
            }

            // GUI 열기
            player.openInventory(gui);
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "강화 GUI 열기 실패: " + player.getName(), e);
            player.sendMessage("§cGUI를 열 수 없습니다. 명령어를 사용해주세요: /upgrade direct");
        }
    }

    /**
     * 메인 도움말 표시
     */
    private void showMainHelp(CommandSender sender) {
        sender.sendMessage("§6==========================================");
        sender.sendMessage("§e§l강화 시스템");
        sender.sendMessage("");
        sender.sendMessage("§a주요 명령어:");
        sender.sendMessage("§7• §e/upgrade info §7- 아이템 정보 확인");
        sender.sendMessage("§7• §e/upgrade gui §7- 강화 메뉴 열기");
        sender.sendMessage("§7• §e/upgrade direct §7- 직접 강화");
        sender.sendMessage("§7• §e/upgrade guide §7- 강화 가이드");
        sender.sendMessage("§7• §e/upgrade help §7- 상세 도움말");
        sender.sendMessage("");
        sender.sendMessage("§a강화 가능: §7검, 도끼, 활, 흉갑만");
        sender.sendMessage("§c최대 강화: §710강 (특수 효과 부여)");
        sender.sendMessage("§6==========================================");
    }

    /**
     * 명령어별 권한 확인
     */
    private boolean hasPermissionForCommand(CommandSender sender, String subCommand) {
        String permission = commandPermissions.get(subCommand);
        if (permission == null) {
            return true; // 권한이 정의되지 않은 명령어는 허용
        }

        return sender.hasPermission(permission);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            if (args.length == 1) {
                // 첫 번째 인수: 하위 명령어
                List<String> subCommands = Arrays.asList(
                        "info", "guide", "rates", "gui", "direct", "items", "axespeed", "help");

                // 관리자 명령어 추가
                if (sender.hasPermission("ggm.upgrade.admin")) {
                    subCommands = new ArrayList<>(subCommands);
                    subCommands.addAll(Arrays.asList("reload", "stats"));
                }

                return subCommands.stream()
                        .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }

            return new ArrayList<>();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "탭 완성 처리 중 오류: " + sender.getName(), e);
            return new ArrayList<>();
        }
    }
}