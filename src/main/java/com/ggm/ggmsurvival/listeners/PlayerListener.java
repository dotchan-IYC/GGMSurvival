package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import com.ggm.ggmsurvival.managers.JobManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final GGMSurvival plugin;

    public PlayerListener(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 야생 서버 전용 환영 메시지
        if (plugin.isFeatureEnabled("job_system") && plugin.isFeatureEnabled("upgrade_system")) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("GGM 야생 서버에 오신 것을 환영합니다!");
                player.sendMessage("");
                player.sendMessage("이용 가능한 기능:");
                player.sendMessage("• 직업 시스템 - 탱커/검사/궁수 선택");
                player.sendMessage("• 강화 시스템 - 검/도끼/활/흉갑 강화");
                player.sendMessage("• 드래곤 보상 - 매일 100,000G");
                player.sendMessage("• NPC 교환 - 아이템을 G로 판매");
                player.sendMessage("• 엔더 리셋 - 매일 12시 자동 초기화");
                player.sendMessage("");
                player.sendMessage("주요 명령어:");
                player.sendMessage("/job select - 직업선택  /upgrade gui - 아이템강화");
                player.sendMessage("/trade - NPC교환  /enderreset info - 엔더정보");
                player.sendMessage("");
                player.sendMessage("새로운 모험을 시작하세요!");
                player.sendMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }, 20L);

            // 직업 선택 안내 (접속 후 3초)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getJobManager() != null) {
                    // getJobType을 사용하여 직업 확인 (hasJob 메서드 대신)
                    JobManager.JobType currentJob = plugin.getJobManager().getJobType(player);
                    if (currentJob == JobManager.JobType.NONE) {
                        player.sendMessage("아직 직업을 선택하지 않으셨네요!");
                        player.sendMessage("/job select 명령어로 직업을 선택하고 특수 능력을 받아보세요!");
                    }
                }
            }, 60L);
        }

        plugin.getLogger().info(player.getName() + "이(가) 야생 서버에 접속했습니다.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 플레이어 데이터 저장 (JobManager에서 자동 처리)
        plugin.getLogger().info(player.getName() + "이(가) 야생 서버를 떠났습니다.");
    }
}