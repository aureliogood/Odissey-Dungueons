package com.odisseymc.odisseydungueons.model;

import org.bukkit.entity.EntityType;

/**
 * Configuration definition for a normal or custom mob.
 */
public class MobConfig {

    private final String id;
    private final MobType mobType;
    private final EntityType vanillaType;
    private final String mythicMobId;
    private final int amount;
    private final double health;
    private final double damage;
    private final double speed;
    private final String customName;
    private final double spawnChance;
    private final int wave;
    private final String specificSpawnId;

    public MobConfig(String id, MobType mobType, EntityType vanillaType, String mythicMobId,
                     int amount, double health, double damage, double speed,
                     String customName, double spawnChance, int wave, String specificSpawnId) {
        this.id = id;
        this.mobType = mobType != null ? mobType : MobType.VANILLA;
        this.vanillaType = vanillaType != null ? vanillaType : EntityType.ZOMBIE;
        this.mythicMobId = mythicMobId != null ? mythicMobId : "";
        this.amount = Math.max(1, amount);
        this.health = Math.max(1.0, health);
        this.damage = Math.max(1.0, damage);
        this.speed = speed > 0 ? speed : 0.25;
        this.customName = customName != null ? customName : "";
        this.spawnChance = spawnChance > 0 ? spawnChance : 100.0;
        this.wave = Math.max(1, wave);
        this.specificSpawnId = specificSpawnId != null ? specificSpawnId : "";
    }

    public String getId() {
        return id;
    }

    public MobType getMobType() {
        return mobType;
    }

    public EntityType getVanillaType() {
        return vanillaType;
    }

    public String getMythicMobId() {
        return mythicMobId;
    }

    public int getAmount() {
        return amount;
    }

    public double getHealth() {
        return health;
    }

    public double getDamage() {
        return damage;
    }

    public double getSpeed() {
        return speed;
    }

    public String getCustomName() {
        return customName;
    }

    public double getSpawnChance() {
        return spawnChance;
    }

    public int getWave() {
        return wave;
    }

    public String getSpecificSpawnId() {
        return specificSpawnId;
    }
}
