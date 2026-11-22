package com.odisseymc.odisseydungueons.wave;

import com.odisseymc.odisseydungueons.api.DungeonWaveStartEvent;
import com.odisseymc.odisseydungueons.hooks.HookManager;
import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.model.*;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles wave progression, arming proximity spawners, and mob spawning with difficulty multipliers.
 */
public class WaveManager {

    public static final ThreadLocal<Boolean> SPAWNING_DUNGEON_MOB = ThreadLocal.withInitial(() -> false);

    private final JavaPlugin plugin;
    private final HookManager hookManager;

    public WaveManager(JavaPlugin plugin, HookManager hookManager) {
        this.plugin = plugin;
        this.hookManager = hookManager;
    }

    public void startWave(DungeonInstance instance, int waveIndex) {
        if (instance == null || instance.getWorld() == null) {
            return;
        }

        Dungeon dungeon = instance.getDungeon();
        List<WaveConfig> waves = dungeon.getWaves();
        if (waveIndex <= 0 || waveIndex > waves.size()) {
            return;
        }

        WaveConfig waveConfig = waves.get(waveIndex - 1);
        instance.setCurrentWaveIndex(waveIndex);

        // Broadcast wave start
        String announcement = waveConfig.getAnnouncement().replace("%wave%", String.valueOf(waveIndex));
        instance.broadcast("&e----------------------------------------");
        instance.broadcast(announcement);
        instance.broadcast("&e----------------------------------------");

        for (java.util.UUID uuid : instance.getPlayers()) {
            org.bukkit.entity.Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(TextUtil.colorize(announcement), TextUtil.colorize("&7¡Prepárate para combatir!"), 10, 40, 10);
            }
        }

        Bukkit.getPluginManager().callEvent(new DungeonWaveStartEvent(instance, waveIndex));

        Difficulty diff = instance.getDifficulty();
        double countMult = diff.getMobCountMultiplier();
        double hpMult = diff.getMobHpMultiplier();
        double dmgMult = diff.getMobDamageMultiplier();

        // Check if this instance has physical mob spawners placed
        boolean hasMobSpawners = instance.getInstanceSpawners().values().stream()
                .anyMatch(s -> s.getType() == DungeonSpawn.SpawnType.MOB);

        if (hasMobSpawners) {
            // PROXIMITY SYSTEM: Arm spawners! Mobs emerge when players approach each spawner
            instance.armWaveSpawners(waveConfig, countMult);
        } else {
            // Fallback: direct spawn if no spawners exist
            List<DungeonSpawn> mobSpawns = dungeon.getMobSpawns();
            if (mobSpawns.isEmpty() && dungeon.getPlayerSpawn() != null) {
                mobSpawns = List.of(dungeon.getPlayerSpawn());
            }

            for (MobConfig mobConfig : waveConfig.getMobs()) {
                double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
                if (roll > mobConfig.getSpawnChance()) continue;

                int spawnAmount = (int) Math.max(1, Math.round(mobConfig.getAmount() * countMult));
                for (int i = 0; i < spawnAmount; i++) {
                    Location spawnLoc = resolveSpawnLocation(instance, mobConfig, mobSpawns);
                    if (spawnLoc == null) continue;
                    spawnSingleMob(instance, mobConfig, spawnLoc, hpMult, dmgMult);
                }
            }

            // Guarantee at least 1 mob spawns so the wave does not stall
            if (instance.getAliveMobCount() == 0 && !waveConfig.getMobs().isEmpty()) {
                MobConfig fbMob = waveConfig.getMobs().get(0);
                Location spawnLoc = resolveSpawnLocation(instance, fbMob, mobSpawns);
                spawnSingleMob(instance, fbMob, spawnLoc, hpMult, dmgMult);
            }
        }
    }

    private Location resolveSpawnLocation(DungeonInstance instance, MobConfig mobConfig, List<DungeonSpawn> mobSpawns) {
        if (!mobConfig.getSpecificSpawnId().isEmpty()) {
            for (DungeonSpawn s : mobSpawns) {
                if (s.getId().equalsIgnoreCase(mobConfig.getSpecificSpawnId())) {
                    return s.toInstanceLocation(instance.getWorld());
                }
            }
        }
        if (!mobSpawns.isEmpty()) {
            int idx = ThreadLocalRandom.current().nextInt(mobSpawns.size());
            return mobSpawns.get(idx).toInstanceLocation(instance.getWorld());
        }
        return instance.getWorld().getSpawnLocation();
    }

    public Entity spawnSingleMob(DungeonInstance instance, MobConfig config, Location loc, double hpMult, double dmgMult) {
        if (loc == null || loc.getWorld() == null) return null;
        loc.getChunk().load(true);

        Entity spawnedEntity = null;

        try {
            SPAWNING_DUNGEON_MOB.set(true);

            if (config.getMobType() == MobType.MYTHICMOBS && hookManager.getMythicMobsHook().isEnabled()) {
                spawnedEntity = hookManager.getMythicMobsHook().spawnMythicMob(config.getMythicMobId(), loc);
            }

            if (spawnedEntity == null) {
                try {
                    spawnedEntity = loc.getWorld().spawnEntity(loc, config.getVanillaType());
                } catch (Exception e) {
                    try {
                        spawnedEntity = loc.getWorld().spawnEntity(loc.clone().add(0, 1.0, 0), config.getVanillaType());
                    } catch (Exception ignored) {}
                }
            }
        } finally {
            SPAWNING_DUNGEON_MOB.set(false);
        }

        if (spawnedEntity == null) {
            return null;
        }

        if (spawnedEntity instanceof LivingEntity living) {
            // Apply stats
            double finalHp = config.getHealth() * hpMult;
            AttributeInstance maxHpAttr = living.getAttribute(Attribute.MAX_HEALTH);
            if (maxHpAttr != null) {
                maxHpAttr.setBaseValue(finalHp);
                living.setHealth(finalHp);
            }

            AttributeInstance dmgAttr = living.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(config.getDamage() * dmgMult);
            }

            AttributeInstance spdAttr = living.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spdAttr != null && config.getSpeed() > 0) {
                spdAttr.setBaseValue(config.getSpeed());
            }

            if (!config.getCustomName().isEmpty()) {
                living.setCustomName(TextUtil.colorize(config.getCustomName()));
                living.setCustomNameVisible(true);
            }

            living.setRemoveWhenFarAway(false);
        }

        instance.registerMob(spawnedEntity);
        return spawnedEntity;
    }
}
