package com.odisseymc.odisseydungueons.model;

import org.bukkit.entity.EntityType;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the dungeon's final boss or mini-bosses.
 */
public class BossConfig {

    private final String name;
    private final MobType mobType;
    private final EntityType vanillaType;
    private final String mythicMobId;
    private final double health;
    private final double damage;
    private final String modelId;
    private final String spawnMessage;
    private final String deathMessage;
    private final List<RewardItem> rewards;

    public BossConfig(String name, MobType mobType, EntityType vanillaType, String mythicMobId,
                      double health, double damage, String modelId,
                      String spawnMessage, String deathMessage, List<RewardItem> rewards) {
        this.name = name != null ? name : "&c&lJEFE FINAL";
        this.mobType = mobType != null ? mobType : MobType.VANILLA;
        this.vanillaType = vanillaType != null ? vanillaType : EntityType.WITHER_SKELETON;
        this.mythicMobId = mythicMobId != null ? mythicMobId : "";
        this.health = Math.max(10.0, health);
        this.damage = Math.max(1.0, damage);
        this.modelId = modelId != null ? modelId : "";
        this.spawnMessage = spawnMessage != null ? spawnMessage : "&c&l¡El jefe final %boss% ha despertado!";
        this.deathMessage = deathMessage != null ? deathMessage : "&a&l¡El jefe final %boss% ha sido derrotado!";
        this.rewards = rewards != null ? rewards : new ArrayList<>();
    }

    public BossConfig(String name, MobType mobType, EntityType vanillaType, String mythicMobId,
                      double health, double damage, String modelId,
                      String spawnMessage, String deathMessage) {
        this(name, mobType, vanillaType, mythicMobId, health, damage, modelId, spawnMessage, deathMessage, new ArrayList<>());
    }

    public String getName() {
        return name;
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

    public double getHealth() {
        return health;
    }

    public double getDamage() {
        return damage;
    }

    public String getModelId() {
        return modelId;
    }

    public String getSpawnMessage() {
        return spawnMessage;
    }

    public String getDeathMessage() {
        return deathMessage;
    }

    public List<RewardItem> getRewards() {
        return rewards;
    }
}
