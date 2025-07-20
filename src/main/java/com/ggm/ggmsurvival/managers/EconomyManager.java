package com.ggm.ggmsurvival.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.ggm.ggmsurvival.GGMSurvival;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * GGMCore의 경제 시스템과 연동하는 매니저
 * GGMCore가 없으면 기본 더미 시스템 사용
 */
public class EconomyManager {

    private final GGMSurvival plugin;
    private Object ggmCoreEconomyManager = null;
    private boolean ggmCoreAvailable = false;

    public EconomyManager(GGMSurvival plugin) {
        this.plugin = plugin;
        initializeGGMCoreConnection();
    }

    /**
     * GGMCore 연결 초기화
     */
    private void initializeGGMCoreConnection() {
        try {
            Plugin ggmCore = Bukkit.getPluginManager().getPlugin("GGMCore");

            if (ggmCore != null && ggmCore.isEnabled()) {
                // GGMCore의 EconomyManager 가져오기
                Class<?> ggmCoreClass = ggmCore.getClass();
                Method getEconomyManagerMethod = ggmCoreClass.getMethod("getEconomyManager");
                ggmCoreEconomyManager = getEconomyManagerMethod.invoke(ggmCore);
                ggmCoreAvailable = true;

                plugin.getLogger().info("GGMCore 경제 시스템과 연동되었습니다!");
            } else {
                plugin.getLogger().warning("GGMCore를 찾을 수 없습니다. 더미 경제 시스템을 사용합니다.");
                ggmCoreAvailable = false;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("GGMCore 연동 실패: " + e.getMessage());
            ggmCoreAvailable = false;
        }
    }

    /**
     * 플레이어 G 잔액 조회
     */
    public CompletableFuture<Long> getBalance(UUID uuid) {
        if (ggmCoreAvailable && ggmCoreEconomyManager != null) {
            try {
                Method getBalanceMethod = ggmCoreEconomyManager.getClass().getMethod("getBalance", UUID.class);
                Object result = getBalanceMethod.invoke(ggmCoreEconomyManager, uuid);

                if (result instanceof CompletableFuture) {
                    return (CompletableFuture<Long>) result;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("GGMCore 잔액 조회 실패: " + e.getMessage());
            }
        }

        // 더미 시스템 - 항상 10,000G 반환
        return CompletableFuture.completedFuture(10000L);
    }

    /**
     * 플레이어에게 G 추가
     */
    public CompletableFuture<Boolean> addMoney(UUID uuid, long amount) {
        if (ggmCoreAvailable && ggmCoreEconomyManager != null) {
            try {
                Method addMoneyMethod = ggmCoreEconomyManager.getClass().getMethod("addMoney", UUID.class, long.class);
                Object result = addMoneyMethod.invoke(ggmCoreEconomyManager, uuid, amount);

                if (result instanceof CompletableFuture) {
                    return (CompletableFuture<Boolean>) result;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("GGMCore G 추가 실패: " + e.getMessage());
            }
        }

        // 더미 시스템 - 항상 성공
        plugin.getLogger().info(String.format("[더미] 플레이어 %s에게 %dG 추가", uuid.toString(), amount));
        return CompletableFuture.completedFuture(true);
    }

    /**
     * 플레이어 G 차감
     */
    public CompletableFuture<Boolean> removeMoney(UUID uuid, long amount) {
        if (ggmCoreAvailable && ggmCoreEconomyManager != null) {
            try {
                Method removeMoneyMethod = ggmCoreEconomyManager.getClass().getMethod("removeMoney", UUID.class, long.class);
                Object result = removeMoneyMethod.invoke(ggmCoreEconomyManager, uuid, amount);

                if (result instanceof CompletableFuture) {
                    return (CompletableFuture<Boolean>) result;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("GGMCore G 차감 실패: " + e.getMessage());
            }
        }

        // 더미 시스템 - 항상 성공
        plugin.getLogger().info(String.format("[더미] 플레이어 %s에게서 %dG 차감", uuid.toString(), amount));
        return CompletableFuture.completedFuture(true);
    }

    /**
     * G 잔액 설정 (관리자 전용)
     */
    public CompletableFuture<Boolean> setBalance(UUID uuid, long amount) {
        if (ggmCoreAvailable && ggmCoreEconomyManager != null) {
            try {
                Method setBalanceMethod = ggmCoreEconomyManager.getClass().getMethod("setBalance", UUID.class, long.class);
                Object result = setBalanceMethod.invoke(ggmCoreEconomyManager, uuid, amount);

                if (result instanceof CompletableFuture) {
                    return (CompletableFuture<Boolean>) result;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("GGMCore 잔액 설정 실패: " + e.getMessage());
            }
        }

        // 더미 시스템 - 항상 성공
        plugin.getLogger().info(String.format("[더미] 플레이어 %s의 잔액을 %dG로 설정", uuid.toString(), amount));
        return CompletableFuture.completedFuture(true);
    }

    /**
     * G 송금 (플레이어 간)
     */
    public CompletableFuture<Boolean> transferMoney(UUID fromUuid, UUID toUuid, long amount) {
        if (ggmCoreAvailable && ggmCoreEconomyManager != null) {
            try {
                Method transferMethod = ggmCoreEconomyManager.getClass().getMethod("transferMoney", UUID.class, UUID.class, long.class);
                Object result = transferMethod.invoke(ggmCoreEconomyManager, fromUuid, toUuid, amount);

                if (result instanceof CompletableFuture) {
                    // TransferResult를 Boolean으로 변환
                    CompletableFuture<?> transferResult = (CompletableFuture<?>) result;
                    return transferResult.thenApply(res -> {
                        try {
                            Method isSuccessMethod = res.getClass().getMethod("isSuccess");
                            return (Boolean) isSuccessMethod.invoke(res);
                        } catch (Exception e) {
                            plugin.getLogger().severe("송금 결과 처리 실패: " + e.getMessage());
                            return false;
                        }
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().severe("GGMCore 송금 실패: " + e.getMessage());
            }
        }

        // 더미 시스템 - 항상 성공
        plugin.getLogger().info(String.format("[더미] %s -> %s: %dG 송금", fromUuid.toString(), toUuid.toString(), amount));
        return CompletableFuture.completedFuture(true);
    }

    /**
     * 금액 포맷팅
     */
    public String formatMoney(long amount) {
        if (ggmCoreAvailable && ggmCoreEconomyManager != null) {
            try {
                Method formatMoneyMethod = ggmCoreEconomyManager.getClass().getMethod("formatMoney", long.class);
                Object result = formatMoneyMethod.invoke(ggmCoreEconomyManager, amount);
                return (String) result;
            } catch (Exception e) {
                // 실패 시 기본 포맷팅 사용
            }
        }

        // 기본 포맷팅
        return String.format("%,d", amount);
    }

    /**
     * 플레이어에게 메시지 전송 (GGMCore 스타일)
     */
    public void sendMessage(Player player, String message) {
        if (ggmCoreAvailable && ggmCoreEconomyManager != null) {
            try {
                // GGMCore의 sendMessage 사용 시도
                Method sendMessageMethod = ggmCoreEconomyManager.getClass().getMethod("sendMessage", Player.class, String.class, Object[].class);
                sendMessageMethod.invoke(ggmCoreEconomyManager, player, message, new Object[0]);
                return;
            } catch (Exception e) {
                // 실패 시 기본 메시지 사용
            }
        }

        // 기본 메시지 (GGM 스타일)
        String prefix = "§6[GGM야생] §f";
        player.sendMessage(prefix + message);
    }

    /**
     * G 알림 메시지
     */
    public void notifyMoneyChange(Player player, long amount, String reason) {
        String symbol = amount > 0 ? "§a+" : "§c";
        String message = String.format("%s %s%sG §7(%s)",
                player.getName(), symbol, formatMoney(Math.abs(amount)), reason);

        sendMessage(player, message);
    }

    /**
     * 플레이어의 G 충분한지 확인
     */
    public CompletableFuture<Boolean> hasEnoughMoney(UUID uuid, long amount) {
        return getBalance(uuid).thenApply(balance -> balance >= amount);
    }

    /**
     * GGMCore 연결 상태 확인
     */
    public boolean isGGMCoreConnected() {
        return ggmCoreAvailable;
    }

    /**
     * 연결 재시도
     */
    public void reconnectToGGMCore() {
        plugin.getLogger().info("GGMCore 재연결 시도 중...");
        initializeGGMCoreConnection();
    }

    /**
     * 경제 시스템 상태 정보
     */
    public void printEconomyStatus() {
        plugin.getLogger().info("=== 경제 시스템 상태 ===");
        plugin.getLogger().info("GGMCore 연동: " + (ggmCoreAvailable ? "§a활성화" : "§c비활성화"));

        if (ggmCoreAvailable) {
            plugin.getLogger().info("경제 시스템: GGMCore 통합 G 시스템");
        } else {
            plugin.getLogger().info("경제 시스템: 더미 시스템 (테스트용)");
            plugin.getLogger().warning("실제 서버에서는 GGMCore가 필요합니다!");
        }
        plugin.getLogger().info("====================");
    }
}