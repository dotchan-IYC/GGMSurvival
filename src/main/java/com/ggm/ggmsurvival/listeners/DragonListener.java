package com.ggm.ggmsurvival.listeners;

import com.ggm.ggmsurvival.GGMSurvival;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 드래곤 관련 이벤트 리스너
 * - 드래곤 처치 감지
 * - 드래곤 전투 참여자 추적
 * - 보상 지급 처리
 */
public class DragonListener implements Listener {

    private final GGMSurvival plugin;

    // 드래곤별 전투 참여자 추적
    private final ConcurrentHashMap<UUID, Set<UUID>> dragonFighters = new ConcurrentHashMap<>();

    public DragonListener(GGMSurvival plugin) {
        this.plugin = plugin;
    }

    /**
     * 엔더 드래곤 공격 이벤트 - 전투 참여자 추적
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        EnderDragon dragon = (EnderDragon) event.getEntity();
        Player attacker = (Player) event.getDamager();

        try {
            // 드래곤 전투 참여자로 등록
            dragonFighters.computeIfAbsent(dragon.getUniqueId(), k -> new HashSet<>())
                    .add(attacker.getUniqueId());

            // 첫 공격 시 메시지
            if (dragonFighters.get(dragon.getUniqueId()).size() == 1) {
                attacker.sendMessage("§5[드래곤 전투] 엔더 드래곤과의 전투가 시작되었습니다!");
                attacker.sendMessage("§7처치 시 특별한 보상을 받을 수 있습니다.");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "드래곤 전투 참여자 추적 중 오류", e);
        }
    }

    /**
     * 엔더 드래곤 처치 이벤트
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        EnderDragon dragon = (EnderDragon) event.getEntity();
        Player killer = dragon.getKiller();

        try {
            // 처치자가 없으면 마지막 공격자 찾기
            if (killer == null) {
                Set<UUID> fighters = dragonFighters.get(dragon.getUniqueId());
                if (fighters != null && !fighters.isEmpty()) {
                    // 첫 번째 참여자를 처치자로 설정 (임시)
                    UUID firstFighter = fighters.iterator().next();
                    killer = plugin.getServer().getPlayer(firstFighter);
                }
            }

            if (killer == null) {
                plugin.getLogger().warning("엔더 드래곤이 처치되었지만 처치자를 찾을 수 없습니다.");
                return;
            }

            // 드래곤 보상 시스템이 활성화되어 있으면 보상 지급
            if (plugin.getDragonRewardManager() != null) {
                plugin.getDragonRewardManager().handleDragonKill(dragon, killer);
            }

            // 기본 드롭 제거 (커스텀 보상으로 대체)
            event.getDrops().clear();
            event.setDroppedExp(0);

            // 전투 참여자 데이터 정리
            dragonFighters.remove(dragon.getUniqueId());

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "드래곤 처치 이벤트 처리 중 오류", e);
        }
    }
}