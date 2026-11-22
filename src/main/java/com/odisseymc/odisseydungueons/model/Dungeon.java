package com.odisseymc.odisseydungueons.model;

import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.*;

/**
 * Root model representing a configured dungeon template.
 */
public class Dungeon {

    private final String name;
    private String displayName;
    private List<String> description;
    private boolean enabled;
    private long cooldownSeconds;
    private int durationSeconds;
    private int minPlayers;
    private int maxPlayers;
    private String worldName;
    private DungeonSpawn playerSpawn;
    private DungeonSpawn lobbySpawn;
    private final List<DungeonSpawn> mobSpawns = new ArrayList<>();
    private DungeonSpawn bossSpawn;
    private DungeonSpawn rewardChestSpawn;
    private final List<MobConfig> normalMobs = new ArrayList<>();
    private BossConfig bossConfig;
    private final List<WaveConfig> waves = new ArrayList<>();
    private final List<DungeonRoom> rooms = new ArrayList<>();
    private final List<RewardItem> rewards = new ArrayList<>();
    private final Map<Difficulty, List<RewardItem>> difficultyRewards = new HashMap<>();

    // Cooldown tracking: epoch timestamp when cooldown finishes
    private long cooldownExpireTimestamp = 0L;

    public Dungeon(String name) {
        this.name = name;
        this.displayName = "&b&l" + name;
        this.description = new ArrayList<>(Arrays.asList("&7¡Una emocionante mazmorra llena de peligros!", "&7Reúne a tu equipo para desafiar al jefe."));
        this.enabled = true;
        this.cooldownSeconds = 3600; // 60 minutes default
        this.durationSeconds = 900;  // 15 minutes default
        this.minPlayers = 1;
        this.maxPlayers = 4;
        this.worldName = null; // No default world! A world must be explicitly assigned.
    }

    public void initDefaultWaves() {
        if (!waves.isEmpty()) {
            return;
        }

        // Default Wave 1
        WaveConfig wave1 = new WaveConfig(1, 5, "&e&l[OLEADA 1] &a¡Los monstruos emergen de las sombras!");
        wave1.getMobs().add(new MobConfig("zombie_w1", MobType.VANILLA, EntityType.ZOMBIE, "", 4, 25.0, 3.0, 0.25, "&2Zombi de Mazmorra", 100.0, 1, ""));
        wave1.getMobs().add(new MobConfig("spider_w1", MobType.VANILLA, EntityType.SPIDER, "", 2, 18.0, 3.0, 0.30, "&8Araña Acechadora", 100.0, 1, ""));
        waves.add(wave1);

        // Default Wave 2
        WaveConfig wave2 = new WaveConfig(2, 6, "&e&l[OLEADA 2] &c¡La horda se fortalece!");
        wave2.getMobs().add(new MobConfig("skeleton_w2", MobType.VANILLA, EntityType.SKELETON, "", 4, 25.0, 4.0, 0.25, "&7Esqueleto Guerrero", 100.0, 2, ""));
        wave2.getMobs().add(new MobConfig("cave_spider_w2", MobType.VANILLA, EntityType.CAVE_SPIDER, "", 3, 16.0, 3.0, 0.30, "&2Araña Venenosa", 100.0, 2, ""));
        wave2.getMobs().add(new MobConfig("zombie_w2", MobType.VANILLA, EntityType.ZOMBIE, "", 3, 30.0, 4.0, 0.25, "&2Zombi Feroz", 100.0, 2, ""));
        waves.add(wave2);

        // Default Wave 3
        WaveConfig wave3 = new WaveConfig(3, 8, "&e&l[OLEADA 3 - FINAL] &4¡Oleada final antes del Jefe!");
        wave3.getMobs().add(new MobConfig("creeper_w3", MobType.VANILLA, EntityType.CREEPER, "", 2, 20.0, 6.0, 0.25, "&aCreeper Inestable", 100.0, 3, ""));
        wave3.getMobs().add(new MobConfig("skeleton_w3", MobType.VANILLA, EntityType.SKELETON, "", 4, 30.0, 4.0, 0.25, "&7Esqueleto Certero", 100.0, 3, ""));
        wave3.getMobs().add(new MobConfig("zombie_w3", MobType.VANILLA, EntityType.ZOMBIE, "", 5, 35.0, 5.0, 0.25, "&2Zombi Destructor", 100.0, 3, ""));
        waves.add(wave3);

        if (normalMobs.isEmpty()) {
            normalMobs.add(new MobConfig("def_zombie", MobType.VANILLA, EntityType.ZOMBIE, "", 4, 25.0, 3.0, 0.25, "&2Zombi de Mazmorra", 100.0, 1, ""));
            normalMobs.add(new MobConfig("def_skeleton", MobType.VANILLA, EntityType.SKELETON, "", 4, 25.0, 4.0, 0.25, "&7Esqueleto Guerrero", 100.0, 2, ""));
            normalMobs.add(new MobConfig("def_creeper", MobType.VANILLA, EntityType.CREEPER, "", 2, 20.0, 6.0, 0.25, "&aCreeper Inestable", 100.0, 3, ""));
            normalMobs.add(new MobConfig("def_spider", MobType.VANILLA, EntityType.SPIDER, "", 2, 18.0, 3.0, 0.30, "&8Araña Acechadora", 100.0, 1, ""));
            normalMobs.add(new MobConfig("def_cave_spider", MobType.VANILLA, EntityType.CAVE_SPIDER, "", 3, 16.0, 3.0, 0.30, "&2Araña Venenosa", 100.0, 2, ""));
        }

        if (bossConfig == null) {
            bossConfig = new BossConfig(
                    "&c&lSeñor de la Cripta",
                    MobType.VANILLA,
                    EntityType.WITHER_SKELETON,
                    "",
                    200.0,
                    8.0,
                    "",
                    "&c&l¡EL JEFE FINAL HA APARECIDO! ¡DERROTA AL SEÑOR DE LA CRIPTA!",
                    "&6&l¡EL JEFE FINAL HA SIDO DERROTADO!"
            );
        }
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public DungeonSpawn getPlayerSpawn() {
        return playerSpawn;
    }

    public void setPlayerSpawn(DungeonSpawn playerSpawn) {
        this.playerSpawn = playerSpawn;
    }

    public DungeonSpawn getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(DungeonSpawn lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public List<DungeonSpawn> getMobSpawns() {
        return mobSpawns;
    }

    public void addMobSpawn(DungeonSpawn spawn) {
        mobSpawns.removeIf(s -> s.getId().equalsIgnoreCase(spawn.getId()));
        mobSpawns.add(spawn);
    }

    public boolean removeMobSpawn(String id) {
        return mobSpawns.removeIf(s -> s.getId().equalsIgnoreCase(id));
    }

    public DungeonSpawn getBossSpawn() {
        return bossSpawn;
    }

    public void setBossSpawn(DungeonSpawn bossSpawn) {
        this.bossSpawn = bossSpawn;
    }

    public DungeonSpawn getRewardChestSpawn() {
        return rewardChestSpawn;
    }

    public void setRewardChestSpawn(DungeonSpawn rewardChestSpawn) {
        this.rewardChestSpawn = rewardChestSpawn;
    }

    public List<MobConfig> getNormalMobs() {
        return normalMobs;
    }

    public BossConfig getBossConfig() {
        return bossConfig;
    }

    public void setBossConfig(BossConfig bossConfig) {
        this.bossConfig = bossConfig;
    }

    public List<WaveConfig> getWaves() {
        return waves;
    }

    public List<DungeonRoom> getRooms() {
        return rooms;
    }

    public List<RewardItem> getRewards() {
        return rewards;
    }

    public Map<Difficulty, List<RewardItem>> getDifficultyRewards() {
        return difficultyRewards;
    }

    public List<RewardItem> getRewardsForDifficulty(Difficulty diff) {
        List<RewardItem> diffList = difficultyRewards.get(diff);
        if (diffList != null && !diffList.isEmpty()) {
            return diffList;
        }
        return rewards;
    }

    // Cooldown management
    public long getCooldownExpireTimestamp() {
        return cooldownExpireTimestamp;
    }

    public void setCooldownExpireTimestamp(long timestamp) {
        this.cooldownExpireTimestamp = timestamp;
    }

    public boolean isOnCooldown() {
        return System.currentTimeMillis() < cooldownExpireTimestamp;
    }

    public long getRemainingCooldownMillis() {
        return Math.max(0, cooldownExpireTimestamp - System.currentTimeMillis());
    }

    public long getRemainingCooldownSeconds() {
        return getRemainingCooldownMillis() / 1000;
    }

    public String getFormattedCooldown() {
        return TextUtil.formatDuration(getRemainingCooldownSeconds());
    }

    public void triggerCooldown() {
        if (cooldownSeconds > 0) {
            this.cooldownExpireTimestamp = System.currentTimeMillis() + (cooldownSeconds * 1000L);
        } else {
            this.cooldownExpireTimestamp = 0;
        }
    }

    public void resetCooldown() {
        this.cooldownExpireTimestamp = 0;
    }
}
