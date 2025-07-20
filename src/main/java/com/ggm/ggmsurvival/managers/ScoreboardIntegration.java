// 스코어보드 연동 시스템 - GGMCore와 연동
package com.ggm.ggmsurvival.managers;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class ScoreboardIntegration implements Listener {

    private final GGMSurvival plugin;
    private final JobManager jobManager;

    // GGMCore 연동 변수
    private Plugin ggmCore;
    private Object scoreboardManager;
    private Method retryJobSystemIntegrationMethod;
    private boolean coreAvailable = false;

    public ScoreboardIntegration(GGMSurvival plugin) {
        this.plugin = plugin;
        this.jobManager = plugin.getJobManager();

        // GGMCore 연동 초기화
        initializeGGMCoreIntegration();

        plugin.getLogger().info("스코어보드 연동 시스템 초기화 완료");
    }

    /**
     * GGMCore와의 연동 초기화
     */
    private void initializeGGMCoreIntegration() {
        try {
            ggmCore = Bukkit.getPluginManager().getPlugin("GGMCore");

            if (ggmCore != null && ggmCore.isEnabled()) {
                // GGMCore의 ScoreboardManager 가져오기
                Class<?> ggmCoreClass = ggmCore.getClass();
                Method getScoreboardManagerMethod = ggmCoreClass.getMethod("getScoreboardManager");
                scoreboardManager = getScoreboardManagerMethod.invoke(ggmCore);

                if (scoreboardManager != null) {
                    // retryJobSystemIntegration 메소드 가져오기
                    Class<?> scoreboardManagerClass = scoreboardManager.getClass();
                    retryJobSystemIntegrationMethod = scoreboardManagerClass.getMethod("retryJobSystemIntegration");

                    coreAvailable = true;
                    plugin.getLogger().info("✓ GGMCore 스코어보드와 연동 성공!");

                    // 즉시 연동 시도
                    retryScoreboardIntegration();
                }
            } else {
                plugin.getLogger().warning("GGMCore 플러그인을 찾을 수 없습니다. 스코어보드 직업 표시가 되지 않을 수 있습니다.");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("GGMCore 스코어보드 연동 실패: " + e.getMessage());
            coreAvailable = false;
        }
    }

    /**
     * 스코어보드 연동 재시도
     */
    public void retryScoreboardIntegration() {
        if (coreAvailable && retryJobSystemIntegrationMethod != null) {
            try {
                retryJobSystemIntegrationMethod.invoke(scoreboardManager);
                plugin.getLogger().info("스코어보드 직업 시스템 연동 재시도 완료");
            } catch (Exception e) {
                plugin.getLogger().warning("스코어보드 연동 재시도 실패: " + e.getMessage());
            }
        }
    }

    /**
     * 플레이어 접속시 스코어보드 연동 확인
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 잠시 후 스코어보드 연동 재시도
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            retryScoreboardIntegration();
        }, 40L); // 2초 후
    }

    /**
     * 직업 변경시 스코어보드 업데이트 알림
     */
    public void notifyJobChange(Player player) {
        if (!coreAvailable) return;

        try {
            // GGMCore 스코어보드 업데이트를 트리거
            retryScoreboardIntegration();

            // 해당 플레이어의 스코어보드 강제 업데이트
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 추가 업데이트 로직이 필요하면 여기에
            }, 20L);

        } catch (Exception e) {
            plugin.getLogger().warning("직업 변경 스코어보드 알림 실패: " + e.getMessage());
        }
    }

    /**
     * 레벨업시 스코어보드 업데이트 알림
     */
    public void notifyLevelUp(Player player) {
        notifyJobChange(player); // 동일한 로직 사용
    }

    /**
     * GGMCore 연동 상태 확인
     */
    public boolean isGGMCoreAvailable() {
        return coreAvailable;
    }
}