package com.odisseymc.odisseydungueons.combat;

import com.odisseymc.odisseydungueons.api.DungeonBossKillEvent;
import com.odisseymc.odisseydungueons.api.DungeonWaveClearEvent;
import com.odisseymc.odisseydungueons.config.MessageManager;
import com.odisseymc.odisseydungueons.instance.BlockStateRecord;
import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.instance.InstanceManager;
import com.odisseymc.odisseydungueons.model.BossConfig;
import com.odisseymc.odisseydungueons.trigger.TriggerManager;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces strict combat rules: zero mob drops, zero XP, creeper explosions without block damage,
 * instance entity targeting isolation, and block restoration tracking.
 */
public class DungeonCombatListener implements Listener {

    private final InstanceManager instanceManager;
    private final MessageManager messageManager;
    private TriggerManager triggerManager;

    public DungeonCombatListener(InstanceManager instanceManager, MessageManager messageManager) {
        this.instanceManager = instanceManager;
        this.messageManager = messageManager;
    }

    public void setTriggerManager(TriggerManager triggerManager) {
        this.triggerManager = triggerManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        DungeonInstance instance = instanceManager.findInstanceByEntity(entity);
        if (instance == null) {
            return;
        }

        // CRITICAL REQUIREMENT: Mobs must not drop any items or experience
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Check if the entity was the final boss
        if (instance.getBossEntity() != null && instance.getBossEntity().getUniqueId().equals(entity.getUniqueId())) {
            Player killer = entity.getKiller();
            BossConfig bossConfig = instance.getDungeon().getBossConfig();
            if (bossConfig != null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("boss", bossConfig.getName());
                ph.put("player", killer != null ? killer.getName() : "Equipo");
                String deathMsg = bossConfig.getDeathMessage().replace("%boss%", bossConfig.getName());
                instance.broadcast(deathMsg);
            }

            Bukkit.getPluginManager().callEvent(new DungeonBossKillEvent(instance, entity, killer));

            // Si hay cofre de recompensa, esperar a que los jugadores se acerquen para la cinemática
            if (instance.getRewardChestLocation() != null) {
                instance.setAwaitingChestApproach(true);
                instance.broadcast("&6&l★ ¡EL JEFE FINAL HA SIDO DERROTADO! ★");
                instance.broadcast("&e¡Una poderosa vibración ancestral resuena bajo el suelo! &7Acércate a la zona del cofre...");
                for (UUID puuid : instance.getPlayers()) {
                    Player p = Bukkit.getPlayer(puuid);
                    if (p != null && p.isOnline()) {
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
                        p.sendTitle(TextUtil.colorize("&6&l¡EL SUELO RETUMBA!"), TextUtil.colorize("&eAcércate al epicentro de la sala"), 10, 50, 20);
                    }
                }
            } else {
                instanceManager.handleInstanceVictory(instance);
            }
            return;
        }

        // Unregister mob from instance and check wave progress
        handleMobDefeated(instance, entity, entity.getUniqueId());
    }

    public void handleMobDefeated(DungeonInstance instance, Entity entity, java.util.UUID uuid) {
        if (instance == null) return;

        UUID mobUuid = (entity != null) ? entity.getUniqueId() : uuid;
        if (mobUuid != null) {
            instance.unregisterMobFromSpawner(mobUuid);
            instance.unregisterMob(mobUuid);
        }

        int remaining = instance.getAliveMobCount();
        int currentWave = instance.getCurrentWaveIndex();

        // Highlight last 2 enemies with Glowing effect so players can locate them easily
        if (remaining <= 2 && remaining > 0) {
            for (UUID mUuid : instance.getAliveMobUuids()) {
                Entity mEnt = Bukkit.getEntity(mUuid);
                if (mEnt != null && mEnt.isValid() && !mEnt.isDead()) {
                    mEnt.setGlowing(true);
                }
            }
            for (UUID pUuid : instance.getPlayers()) {
                Player p = Bukkit.getPlayer(pUuid);
                if (p != null && p.isOnline()) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
                    p.sendActionBar(TextUtil.colorize("&e&l[RADAR] &c¡Últimos " + remaining + " enemigos resaltados con brillo!"));
                }
            }
        }

        // If not using physical spawners and all enemies eliminated, advance global wave
        if (!instance.hasPhysicalSpawners() && remaining <= 0 && instance.getState() == com.odisseymc.odisseydungueons.model.DungeonState.IN_USE) {
            Bukkit.getPluginManager().callEvent(new DungeonWaveClearEvent(instance, currentWave));
            if (triggerManager != null) {
                triggerManager.handleMobClear(instance);
            }
            instanceManager.advanceWave(instance);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        DungeonInstance instance = (entity != null) ? instanceManager.findInstanceByEntity(entity) : null;
        if (instance == null && event.getLocation().getWorld() != null) {
            instance = instanceManager.findInstanceByWorld(event.getLocation().getWorld());
        }

        if (instance != null) {
            // CRITICAL REQUIREMENT: Creepers damage players but DO NOT damage blocks or terrain
            event.blockList().clear();
            event.setYield(0f);

            // If the entity that exploded was a registered dungeon mob (e.g. Creeper detonation), count as defeated
            if (entity != null && instance.getAliveMobUuids().contains(entity.getUniqueId())) {
                handleMobDefeated(instance, entity, entity.getUniqueId());
            }
        }
    }

    /**
     * Garantiza que los mobs de mazmorras aparezcan incluso si WorldGuard tiene la flag 'mob-spawning deny'.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Mobs invocados explícitamente por el sistema de mazmorras
        if (com.odisseymc.odisseydungueons.wave.WaveManager.SPAWNING_DUNGEON_MOB.get()) {
            event.setCancelled(false);
            return;
        }

        // Si el evento fue cancelado (por ejemplo, por WorldGuard mob-spawning deny)
        // pero ocurre dentro de un mundo de instancia activa de mazmorra, permitir el spawn
        if (event.isCancelled() && instanceManager != null && event.getLocation().getWorld() != null) {
            DungeonInstance instance = instanceManager.findInstanceByWorld(event.getLocation().getWorld());
            if (instance != null && instance.getState() == com.odisseymc.odisseydungueons.model.DungeonState.IN_USE) {
                if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM ||
                    event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
                    event.setCancelled(false);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity mob = event.getEntity();
        DungeonInstance instance = instanceManager.findInstanceByEntity(mob);
        if (instance != null) {
            if (event.getTarget() instanceof Player targetPlayer) {
                // If player is not part of this instance, cancel target
                if (!instance.containsPlayer(targetPlayer.getUniqueId())) {
                    event.setCancelled(true);
                }
            } else if (event.getTarget() != null) {
                // Do not target friendly entities or outside entities
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        DungeonInstance victimInstance = instanceManager.findInstanceByEntity(victim);
        if (victimInstance == null && victim.getWorld() != null) {
            victimInstance = instanceManager.findInstanceByWorld(victim.getWorld());
        }

        if (victimInstance != null) {
            // If damager is a player
            if (damager instanceof Player p) {
                if (!victimInstance.containsPlayer(p.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (instance != null) {
            instance.recordBlockModification(new BlockStateRecord(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (instance != null) {
            instance.recordBlockModification(new BlockStateRecord(event.getBlockReplacedState().getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonInstance instance = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (instance == null) return;

        instance.broadcast("&c☠ El jugador &f" + player.getName() + " &cha caído en la mazmorra.");

        boolean anyAlive = false;
        for (UUID uuid : instance.getPlayers()) {
            if (!uuid.equals(player.getUniqueId())) {
                Player other = Bukkit.getPlayer(uuid);
                if (other != null && other.isOnline() && !other.isDead() && other.getWorld().equals(instance.getWorld())) {
                    anyAlive = true;
                    break;
                }
            }
        }

        if (!anyAlive) {
            instanceManager.handleInstanceDefeat(instance, "&cTodos los miembros del equipo han sido derrotados.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (instance == null) return;

        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.setFlySpeed(0.1f);
        }

        org.bukkit.Location lobby = null;
        if (instance.getDungeon().getLobbySpawn() != null) {
            lobby = instance.getDungeon().getLobbySpawn().toBukkitLocation();
        }
        if (lobby == null || lobby.getWorld() == null) {
            lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        player.teleport(lobby);

        instance.removePlayer(player);
        instanceManager.removePlayerFromTracking(player.getUniqueId());

        if (instance.getPlayerCount() == 0) {
            instanceManager.endInstance(instance);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (instance != null) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                player.setFlySpeed(0.1f);
            }
            org.bukkit.Location lobby = null;
            if (instance.getDungeon().getLobbySpawn() != null) {
                lobby = instance.getDungeon().getLobbySpawn().toBukkitLocation();
            }
            if (lobby == null || lobby.getWorld() == null) {
                lobby = Bukkit.getWorlds().get(0).getSpawnLocation();
            }
            event.setRespawnLocation(lobby);
            instance.removePlayer(player);
            instanceManager.removePlayerFromTracking(player.getUniqueId());
        }
    }
}
