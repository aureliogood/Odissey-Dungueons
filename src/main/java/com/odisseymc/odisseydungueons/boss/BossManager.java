package com.odisseymc.odisseydungueons.boss;

import com.odisseymc.odisseydungueons.api.DungeonBossSpawnEvent;
import com.odisseymc.odisseydungueons.hooks.HookManager;
import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.model.*;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Handles spawning, scaling, and lifecycle of the final boss in a dungeon,
 * with support for targeted proximity spawner locations.
 */
public class BossManager {

    private final JavaPlugin plugin;
    private final HookManager hookManager;

    public BossManager(JavaPlugin plugin, HookManager hookManager) {
        this.plugin = plugin;
        this.hookManager = hookManager;
    }

    public void spawnBoss(DungeonInstance instance) {
        if (instance == null || instance.getWorld() == null) {
            return;
        }

        Dungeon dungeon = instance.getDungeon();
        Location spawnLoc = null;
        if (dungeon.getBossSpawn() != null) {
            spawnLoc = dungeon.getBossSpawn().toInstanceLocation(instance.getWorld());
        } else if (!dungeon.getMobSpawns().isEmpty()) {
            spawnLoc = dungeon.getMobSpawns().get(0).toInstanceLocation(instance.getWorld());
        } else {
            spawnLoc = instance.getWorld().getSpawnLocation();
        }

        spawnBossAt(instance, spawnLoc);
    }

    public void spawnBossAt(DungeonInstance instance, Location spawnLoc) {
        if (instance == null || instance.getWorld() == null || spawnLoc == null) {
            return;
        }

        Dungeon dungeon = instance.getDungeon();
        BossConfig bossConfig = dungeon.getBossConfig();
        if (bossConfig == null) {
            return;
        }

        if (spawnLoc.getWorld() != null) {
            spawnLoc.getChunk().load(true);
        }

        Difficulty diff = instance.getDifficulty();
        double hp = bossConfig.getHealth() * diff.getBossHpMultiplier();
        double dmg = bossConfig.getDamage() * diff.getBossDamageMultiplier();

        Entity bossEntity = null;

        try {
            com.odisseymc.odisseydungueons.wave.WaveManager.SPAWNING_DUNGEON_MOB.set(true);

            // MythicMobs check
            if (bossConfig.getMobType() == MobType.MYTHICMOBS && hookManager.getMythicMobsHook().isEnabled()) {
                bossEntity = hookManager.getMythicMobsHook().spawnMythicMob(bossConfig.getMythicMobId(), spawnLoc);
            }

            // Vanilla fallback
            if (bossEntity == null) {
                bossEntity = spawnLoc.getWorld().spawnEntity(spawnLoc, bossConfig.getVanillaType());
            }
        } finally {
            com.odisseymc.odisseydungueons.wave.WaveManager.SPAWNING_DUNGEON_MOB.set(false);
        }

        if (bossEntity instanceof LivingEntity living) {
            AttributeInstance maxHpAttr = living.getAttribute(Attribute.MAX_HEALTH);
            if (maxHpAttr != null) {
                maxHpAttr.setBaseValue(hp);
                living.setHealth(hp);
            }

            AttributeInstance dmgAttr = living.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(dmg);
            }

            living.setCustomName(TextUtil.colorize(bossConfig.getName()));
            living.setCustomNameVisible(true);
            living.setRemoveWhenFarAway(false);
        }

        // Apply ModelEngine or BetterModel if configured
        if (!bossConfig.getModelId().isEmpty()) {
            hookManager.getModelEngineHook().applyModel(bossEntity, bossConfig.getModelId());
            hookManager.getBetterModelHook().applyModel(bossEntity, bossConfig.getModelId());
        }

        instance.setBossEntity(bossEntity);
        instance.setState(DungeonState.IN_USE);

        // Broadcast spawn message
        String msg = bossConfig.getSpawnMessage().replace("%boss%", bossConfig.getName());
        instance.broadcast("&4&l----------------------------------------");
        instance.broadcast(msg);
        instance.broadcast("&4&l----------------------------------------");

        for (UUID uuid : instance.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(TextUtil.colorize(bossConfig.getName()), TextUtil.colorize("&c¡Ha comenzado la batalla final!"), 10, 50, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
            }
        }

        Bukkit.getPluginManager().callEvent(new DungeonBossSpawnEvent(instance, bossEntity));
    }
}
