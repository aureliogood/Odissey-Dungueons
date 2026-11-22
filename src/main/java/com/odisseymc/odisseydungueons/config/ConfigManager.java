package com.odisseymc.odisseydungueons.config;

import com.odisseymc.odisseydungueons.model.*;
import com.odisseymc.odisseydungueons.util.CuboidRegion;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages plugin configuration files, GUI templates, and individual dungeon configs.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration mainConfig;
    private FileConfiguration dungeonsRegistryConfig;
    private final File dungeonsFolder;
    private final File guisFolder;
    private final File instancesFolder;
    private final File backupsFolder;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dungeonsFolder = new File(plugin.getDataFolder(), "dungeons");
        this.guisFolder = new File(plugin.getDataFolder(), "guis");
        this.instancesFolder = new File(plugin.getDataFolder(), "instances");
        this.backupsFolder = new File(plugin.getDataFolder(), "backups");
    }

    public void initialize() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        dungeonsFolder.mkdirs();
        guisFolder.mkdirs();
        instancesFolder.mkdirs();
        backupsFolder.mkdirs();

        saveDefaultFile("config.yml");
        saveDefaultFile("messages.yml");
        saveDefaultFile("dungeons.yml");

        // Automatically merge new options from jar into disk if updating jar
        updateFileDefaults("config.yml");
        updateFileDefaults("messages.yml");
        updateFileDefaults("dungeons.yml");

        // Copy GUI templates
        String[] guiFiles = {
                "main-menu.yml",
                "dungeon-menu.yml",
                "mobs-menu.yml",
                "boss-menu.yml",
                "rewards-menu.yml",
                "difficulty-menu.yml",
                "settings-menu.yml"
        };
        for (String guiFile : guiFiles) {
            saveDefaultSubFile("guis", guiFile);
            updateSubFileDefaults("guis", guiFile);
        }

        // Copy default dungeons if folder is empty
        File[] existingDungeons = dungeonsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (existingDungeons == null || existingDungeons.length == 0) {
            saveDefaultSubFile("dungeons", "prueba.yml");
            saveDefaultSubFile("dungeons", "ejemplo.yml");
        }

        loadConfigs();
    }

    public void loadConfigs() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        mainConfig = YamlConfiguration.loadConfiguration(configFile);

        File registryFile = new File(plugin.getDataFolder(), "dungeons.yml");
        dungeonsRegistryConfig = YamlConfiguration.loadConfiguration(registryFile);
    }

    private void saveDefaultFile(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private void saveDefaultSubFile(String subDir, String fileName) {
        File targetFile = new File(new File(plugin.getDataFolder(), subDir), fileName);
        if (!targetFile.exists()) {
            String resPath = subDir + "/" + fileName;
            InputStream in = plugin.getResource(resPath);
            if (in != null) {
                try {
                    Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not save default " + resPath, e);
                }
            }
        }
    }

    public void updateFileDefaults(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveDefaultFile(resourcePath);
            return;
        }

        try {
            YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(file);
            InputStream in = plugin.getResource(resourcePath);
            if (in != null) {
                YamlConfiguration jarConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                boolean modified = false;
                for (String key : jarConfig.getKeys(true)) {
                    if (!diskConfig.contains(key)) {
                        diskConfig.set(key, jarConfig.get(key));
                        modified = true;
                    }
                }
                if (modified) {
                    diskConfig.save(file);
                    plugin.getLogger().info("[OdisseyDungueons] Actualizado automáticamente con nuevas opciones: " + resourcePath);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[OdisseyDungueons] Error al actualizar archivo " + resourcePath + ": " + e.getMessage());
        }
    }

    public void updateSubFileDefaults(String subDir, String fileName) {
        File targetFile = new File(new File(plugin.getDataFolder(), subDir), fileName);
        String resPath = subDir + "/" + fileName;
        if (!targetFile.exists()) {
            saveDefaultSubFile(subDir, fileName);
            return;
        }

        try {
            YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(targetFile);
            InputStream in = plugin.getResource(resPath);
            if (in != null) {
                YamlConfiguration jarConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                boolean modified = false;
                for (String key : jarConfig.getKeys(true)) {
                    if (!diskConfig.contains(key)) {
                        diskConfig.set(key, jarConfig.get(key));
                        modified = true;
                    }
                }
                if (modified) {
                    diskConfig.save(targetFile);
                }
            }
        } catch (Exception ignored) {}
    }

    public List<Dungeon> loadAllDungeons() {
        List<Dungeon> list = new ArrayList<>();
        File[] files = dungeonsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return list;

        for (File file : files) {
            try {
                Dungeon dungeon = loadDungeonFromFile(file);
                if (dungeon != null) {
                    list.add(dungeon);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load dungeon file: " + file.getName(), e);
            }
        }
        return list;
    }

    public Dungeon loadDungeonFromFile(File file) {
        String baseName = file.getName().replace(".yml", "");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        Dungeon dungeon = new Dungeon(baseName);
        dungeon.setDisplayName(yaml.getString("display-name", "&b&l" + baseName));
        dungeon.setDescription(yaml.getStringList("description"));
        dungeon.setEnabled(yaml.getBoolean("enabled", true));
        dungeon.setCooldownSeconds(yaml.getLong("cooldown-seconds", 3600));
        dungeon.setDurationSeconds(yaml.getInt("duration-seconds", 900));
        dungeon.setMinPlayers(yaml.getInt("min-players", 1));
        dungeon.setMaxPlayers(yaml.getInt("max-players", 4));
        dungeon.setWorldName(yaml.getString("world", null));

        // Cooldown expiration epoch
        dungeon.setCooldownExpireTimestamp(yaml.getLong("cooldown-expire-timestamp", 0L));

        // Spawns
        if (yaml.contains("spawns.player")) {
            if (yaml.isConfigurationSection("spawns.player")) {
                ConfigurationSection s = yaml.getConfigurationSection("spawns.player");
                String w = s.getString("world", dungeon.getWorldName());
                double x = s.getDouble("x", 0);
                double y = s.getDouble("y", 64);
                double z = s.getDouble("z", 0);
                float yaw = (float) s.getDouble("yaw", 0);
                float pitch = (float) s.getDouble("pitch", 0);
                dungeon.setPlayerSpawn(new DungeonSpawn("player", DungeonSpawn.SpawnType.PLAYER, w, x, y, z, yaw, pitch));
            } else {
                dungeon.setPlayerSpawn(DungeonSpawn.deserialize("player", yaml.getString("spawns.player")));
            }
        }
        if (yaml.contains("spawns.lobby")) {
            if (yaml.isConfigurationSection("spawns.lobby")) {
                ConfigurationSection s = yaml.getConfigurationSection("spawns.lobby");
                String w = s.getString("world", dungeon.getWorldName());
                double x = s.getDouble("x", 0);
                double y = s.getDouble("y", 64);
                double z = s.getDouble("z", 0);
                float yaw = (float) s.getDouble("yaw", 0);
                float pitch = (float) s.getDouble("pitch", 0);
                dungeon.setLobbySpawn(new DungeonSpawn("lobby", DungeonSpawn.SpawnType.LOBBY, w, x, y, z, yaw, pitch));
            } else {
                dungeon.setLobbySpawn(DungeonSpawn.deserialize("lobby", yaml.getString("spawns.lobby")));
            }
        }
        if (yaml.contains("spawns.boss")) {
            if (yaml.isConfigurationSection("spawns.boss")) {
                ConfigurationSection s = yaml.getConfigurationSection("spawns.boss");
                String w = s.getString("world", dungeon.getWorldName());
                double x = s.getDouble("x", 0);
                double y = s.getDouble("y", 64);
                double z = s.getDouble("z", 0);
                float yaw = (float) s.getDouble("yaw", 0);
                float pitch = (float) s.getDouble("pitch", 0);
                dungeon.setBossSpawn(new DungeonSpawn("boss", DungeonSpawn.SpawnType.BOSS, w, x, y, z, yaw, pitch));
            } else {
                dungeon.setBossSpawn(DungeonSpawn.deserialize("boss", yaml.getString("spawns.boss")));
            }
        }

        if (yaml.contains("spawns.reward-chest")) {
            if (yaml.isConfigurationSection("spawns.reward-chest")) {
                ConfigurationSection s = yaml.getConfigurationSection("spawns.reward-chest");
                String w = s.getString("world", dungeon.getWorldName());
                double x = s.getDouble("x", 0);
                double y = s.getDouble("y", 64);
                double z = s.getDouble("z", 0);
                float yaw = (float) s.getDouble("yaw", 0);
                float pitch = (float) s.getDouble("pitch", 0);
                dungeon.setRewardChestSpawn(new DungeonSpawn("reward_chest", DungeonSpawn.SpawnType.REWARD_CHEST, w, x, y, z, yaw, pitch));
            } else {
                dungeon.setRewardChestSpawn(DungeonSpawn.deserialize("reward_chest", yaml.getString("spawns.reward-chest")));
            }
        }

        if (yaml.isConfigurationSection("spawns.mobs")) {
            ConfigurationSection sec = yaml.getConfigurationSection("spawns.mobs");
            for (String key : sec.getKeys(false)) {
                dungeon.addMobSpawn(DungeonSpawn.deserialize(key, sec.getString(key)));
            }
        }

        // Normal mobs
        if (yaml.isConfigurationSection("mobs")) {
            ConfigurationSection sec = yaml.getConfigurationSection("mobs");
            for (String key : sec.getKeys(false)) {
                MobType type = parseMobType(sec.getString(key + ".mob-type"), MobType.VANILLA);
                EntityType entityType = parseEntityType(sec.getString(key + ".entity-type"), EntityType.ZOMBIE);
                String mythicId = sec.getString(key + ".mythic-id", "");
                int amount = sec.getInt(key + ".amount", 1);
                double hp = sec.getDouble(key + ".health", 20.0);
                double dmg = sec.getDouble(key + ".damage", 3.0);
                double spd = sec.getDouble(key + ".speed", 0.25);
                String name = sec.getString(key + ".custom-name", "");
                double chance = sec.getDouble(key + ".chance", 100.0);
                int wave = sec.getInt(key + ".wave", 1);
                String spawnId = sec.getString(key + ".spawn-id", "");

                dungeon.getNormalMobs().add(new MobConfig(key, type, entityType, mythicId, amount, hp, dmg, spd, name, chance, wave, spawnId));
            }
        }

        // Boss
        if (yaml.isConfigurationSection("boss")) {
            ConfigurationSection sec = yaml.getConfigurationSection("boss");
            String bName = sec.getString("name", "&c&lBOSS");
            MobType bType = parseMobType(sec.getString("mob-type"), MobType.VANILLA);
            EntityType bEntity = parseEntityType(sec.getString("entity-type"), EntityType.WITHER_SKELETON);
            String bMythic = sec.getString("mythic-id", "");
            double bHp = sec.getDouble("health", 150.0);
            double bDmg = sec.getDouble("damage", 8.0);
            String bModel = sec.getString("model-id", "");
            String bSpawnMsg = sec.getString("spawn-message", "&c&l¡El jefe final %boss% ha aparecido!");
            String bDeathMsg = sec.getString("death-message", "&a&l¡El jefe final %boss% ha sido derrotado!");

            List<RewardItem> bossRewards = loadRewardsSection(sec.getConfigurationSection("rewards"));
            dungeon.setBossConfig(new BossConfig(bName, bType, bEntity, bMythic, bHp, bDmg, bModel, bSpawnMsg, bDeathMsg, bossRewards));
        }

        // Waves
        if (yaml.isConfigurationSection("waves")) {
            ConfigurationSection sec = yaml.getConfigurationSection("waves");
            for (String waveKey : sec.getKeys(false)) {
                int waveNum = 1;
                try {
                    waveNum = Integer.parseInt(waveKey);
                } catch (NumberFormatException ignored) {}
                int delay = sec.getInt(waveKey + ".delay-seconds", 5);
                String announcement = sec.getString(waveKey + ".announcement", "&6&lOLEADA &e" + waveNum);
                List<MobConfig> waveMobs = new ArrayList<>();

                if (sec.isConfigurationSection(waveKey + ".mobs")) {
                    ConfigurationSection mobSec = sec.getConfigurationSection(waveKey + ".mobs");
                    for (String mKey : mobSec.getKeys(false)) {
                        MobType type = parseMobType(mobSec.getString(mKey + ".mob-type"), MobType.VANILLA);
                        EntityType entityType = parseEntityType(mobSec.getString(mKey + ".entity-type"), EntityType.ZOMBIE);
                        String mythicId = mobSec.getString(mKey + ".mythic-id", "");
                        int amount = mobSec.getInt(mKey + ".amount", 1);
                        double hp = mobSec.getDouble(mKey + ".health", 20.0);
                        double dmg = mobSec.getDouble(mKey + ".damage", 3.0);
                        double spd = mobSec.getDouble(mKey + ".speed", 0.25);
                        String name = mobSec.getString(mKey + ".custom-name", "");
                        double chance = mobSec.getDouble(mKey + ".chance", 100.0);
                        String spawnId = mobSec.getString(mKey + ".spawn-id", "");

                        waveMobs.add(new MobConfig(mKey, type, entityType, mythicId, amount, hp, dmg, spd, name, chance, waveNum, spawnId));
                    }
                }
                dungeon.getWaves().add(new WaveConfig(waveNum, waveMobs, delay, announcement));
            }
        }

        // Rooms
        if (yaml.isConfigurationSection("rooms")) {
            ConfigurationSection sec = yaml.getConfigurationSection("rooms");
            for (String rKey : sec.getKeys(false)) {
                String rName = sec.getString(rKey + ".name", rKey);
                int order = sec.getInt(rKey + ".order", 1);
                CuboidRegion bounds = null;
                if (sec.contains(rKey + ".bounds")) {
                    bounds = CuboidRegion.deserialize(sec.getString(rKey + ".bounds"));
                }
                List<DungeonSpawn> roomSpawns = new ArrayList<>();
                List<DoorBlock> roomDoors = new ArrayList<>();
                List<DungeonTrigger> roomTriggers = new ArrayList<>();

                // Triggers
                if (sec.isConfigurationSection(rKey + ".triggers")) {
                    ConfigurationSection tSec = sec.getConfigurationSection(rKey + ".triggers");
                    for (String tKey : tSec.getKeys(false)) {
                        DungeonTrigger.TriggerType tType = DungeonTrigger.TriggerType.valueOf(tSec.getString(tKey + ".type", "ENTER_REGION").toUpperCase());
                        CuboidRegion tRegion = null;
                        if (tSec.contains(tKey + ".region")) {
                            tRegion = CuboidRegion.deserialize(tSec.getString(tKey + ".region"));
                        }
                        String targetBlock = tSec.getString(tKey + ".target-block", "");
                        boolean oneTime = tSec.getBoolean(tKey + ".one-time", true);

                        List<TriggerAction> actions = new ArrayList<>();
                        if (tSec.isConfigurationSection(tKey + ".actions")) {
                            ConfigurationSection aSec = tSec.getConfigurationSection(tKey + ".actions");
                            for (String aKey : aSec.getKeys(false)) {
                                TriggerAction.ActionType aType = TriggerAction.ActionType.valueOf(aSec.getString(aKey + ".type", "OPEN_DOOR").toUpperCase());
                                Map<String, String> params = new HashMap<>();
                                for (String pKey : aSec.getConfigurationSection(aKey).getKeys(false)) {
                                    if (!pKey.equalsIgnoreCase("type")) {
                                        params.put(pKey, aSec.getString(aKey + "." + pKey));
                                    }
                                }
                                actions.add(new TriggerAction(aType, params));
                            }
                        }
                        roomTriggers.add(new DungeonTrigger(tKey, tType, tRegion, targetBlock, actions, oneTime));
                    }
                }

                dungeon.getRooms().add(new DungeonRoom(rKey, rName, order, bounds, roomSpawns, roomDoors, roomTriggers));
            }
        }

        // Global Rewards
        if (yaml.isConfigurationSection("rewards")) {
            dungeon.getRewards().addAll(loadRewardsSection(yaml.getConfigurationSection("rewards")));
        }

        // Difficulty-specific rewards
        for (Difficulty diff : Difficulty.values()) {
            String path = "difficulty-rewards." + diff.name().toLowerCase();
            if (yaml.isConfigurationSection(path)) {
                dungeon.getDifficultyRewards().put(diff, loadRewardsSection(yaml.getConfigurationSection(path)));
            }
        }

        // Auto-initialize default waves and boss if none configured
        if (dungeon.getWaves().isEmpty()) {
            dungeon.initDefaultWaves();
        }

        return dungeon;
    }

    private List<RewardItem> loadRewardsSection(ConfigurationSection sec) {
        List<RewardItem> list = new ArrayList<>();
        if (sec == null) return list;

        for (String key : sec.getKeys(false)) {
            RewardItem.RewardType type = RewardItem.RewardType.valueOf(sec.getString(key + ".type", "VAULT_MONEY").toUpperCase());
            String val = sec.getString(key + ".value", "100");
            int amount = sec.getInt(key + ".amount", 1);
            double chance = sec.getDouble(key + ".chance", 100.0);
            String name = sec.getString(key + ".display-name", "");
            Material mat = Material.matchMaterial(sec.getString(key + ".material", "CHEST"));
            if (mat == null) mat = Material.CHEST;
            List<String> lore = sec.getStringList(key + ".lore");

            list.add(new RewardItem(key, type, val, amount, chance, name, mat, lore));
        }
        return list;
    }

    public void saveDungeon(Dungeon dungeon) {
        File file = new File(dungeonsFolder, dungeon.getName().toLowerCase() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("display-name", dungeon.getDisplayName());
        yaml.set("description", dungeon.getDescription());
        yaml.set("enabled", dungeon.isEnabled());
        yaml.set("cooldown-seconds", dungeon.getCooldownSeconds());
        yaml.set("duration-seconds", dungeon.getDurationSeconds());
        yaml.set("min-players", dungeon.getMinPlayers());
        yaml.set("max-players", dungeon.getMaxPlayers());
        yaml.set("world", dungeon.getWorldName());
        yaml.set("cooldown-expire-timestamp", dungeon.getCooldownExpireTimestamp());

        // Spawns
        yaml.set("spawns.player", dungeon.getPlayerSpawn() != null ? dungeon.getPlayerSpawn().serialize() : null);
        yaml.set("spawns.lobby", dungeon.getLobbySpawn() != null ? dungeon.getLobbySpawn().serialize() : null);
        yaml.set("spawns.boss", dungeon.getBossSpawn() != null ? dungeon.getBossSpawn().serialize() : null);
        yaml.set("spawns.reward-chest", dungeon.getRewardChestSpawn() != null ? dungeon.getRewardChestSpawn().serialize() : null);

        yaml.set("spawns.mobs", null);
        for (DungeonSpawn spawn : dungeon.getMobSpawns()) {
            yaml.set("spawns.mobs." + spawn.getId(), spawn.serialize());
        }

        // Normal mobs
        yaml.set("mobs", null);
        for (MobConfig mob : dungeon.getNormalMobs()) {
            String path = "mobs." + mob.getId() + ".";
            yaml.set(path + "mob-type", mob.getMobType().name());
            yaml.set(path + "entity-type", mob.getVanillaType().name());
            yaml.set(path + "mythic-id", mob.getMythicMobId());
            yaml.set(path + "amount", mob.getAmount());
            yaml.set(path + "health", mob.getHealth());
            yaml.set(path + "damage", mob.getDamage());
            yaml.set(path + "speed", mob.getSpeed());
            yaml.set(path + "custom-name", mob.getCustomName());
            yaml.set(path + "chance", mob.getSpawnChance());
            yaml.set(path + "wave", mob.getWave());
            yaml.set(path + "spawn-id", mob.getSpecificSpawnId());
        }

        // Boss
        if (dungeon.getBossConfig() != null) {
            BossConfig boss = dungeon.getBossConfig();
            yaml.set("boss.name", boss.getName());
            yaml.set("boss.mob-type", boss.getMobType().name());
            yaml.set("boss.entity-type", boss.getVanillaType().name());
            yaml.set("boss.mythic-id", boss.getMythicMobId());
            yaml.set("boss.health", boss.getHealth());
            yaml.set("boss.damage", boss.getDamage());
            yaml.set("boss.model-id", boss.getModelId());
            yaml.set("boss.spawn-message", boss.getSpawnMessage());
            yaml.set("boss.death-message", boss.getDeathMessage());
            saveRewardsSection(yaml.createSection("boss.rewards"), boss.getRewards());
        } else {
            yaml.set("boss", null);
        }

        // Waves
        yaml.set("waves", null);
        for (WaveConfig wave : dungeon.getWaves()) {
            String wPath = "waves." + wave.getWaveNumber() + ".";
            yaml.set(wPath + "delay-seconds", wave.getDelaySeconds());
            yaml.set(wPath + "announcement", wave.getAnnouncement());
            for (MobConfig mob : wave.getMobs()) {
                String mPath = wPath + "mobs." + mob.getId() + ".";
                yaml.set(mPath + "mob-type", mob.getMobType().name());
                yaml.set(mPath + "entity-type", mob.getVanillaType().name());
                yaml.set(mPath + "mythic-id", mob.getMythicMobId());
                yaml.set(mPath + "amount", mob.getAmount());
                yaml.set(mPath + "health", mob.getHealth());
                yaml.set(mPath + "damage", mob.getDamage());
                yaml.set(mPath + "speed", mob.getSpeed());
                yaml.set(mPath + "custom-name", mob.getCustomName());
                yaml.set(mPath + "chance", mob.getSpawnChance());
                yaml.set(mPath + "spawn-id", mob.getSpecificSpawnId());
            }
        }

        // Rooms
        yaml.set("rooms", null);
        for (DungeonRoom room : dungeon.getRooms()) {
            String rPath = "rooms." + room.getId() + ".";
            yaml.set(rPath + "name", room.getName());
            yaml.set(rPath + "order", room.getOrder());
            if (room.getBounds() != null) {
                yaml.set(rPath + "bounds", room.getBounds().serialize());
            }
        }

        // Global Rewards
        saveRewardsSection(yaml.createSection("rewards"), dungeon.getRewards());

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save dungeon file: " + file.getName(), e);
        }
    }

    private void saveRewardsSection(ConfigurationSection sec, List<RewardItem> rewards) {
        if (sec == null || rewards == null) return;
        for (RewardItem reward : rewards) {
            String rPath = reward.getId() + ".";
            sec.set(rPath + "type", reward.getType().name());
            sec.set(rPath + "value", reward.getValue());
            sec.set(rPath + "amount", reward.getAmount());
            sec.set(rPath + "chance", reward.getChancePercentage());
            sec.set(rPath + "display-name", reward.getDisplayName());
            sec.set(rPath + "material", reward.getMaterial().name());
            sec.set(rPath + "lore", reward.getLore());
        }
    }

    public boolean deleteDungeonFile(String name) {
        File file = new File(dungeonsFolder, name.toLowerCase() + ".yml");
        return file.exists() && file.delete();
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public File getGuisFolder() {
        return guisFolder;
    }

    public File getInstancesFolder() {
        return instancesFolder;
    }

    public File getBackupsFolder() {
        return backupsFolder;
    }

    private MobType parseMobType(String val, MobType def) {
        if (val == null) return def;
        try {
            return MobType.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[OdisseyDungueons] Tipo de mob desconocido: '" + val + "', usando " + def);
            return def;
        }
    }

    private EntityType parseEntityType(String val, EntityType def) {
        if (val == null) return def;
        try {
            return EntityType.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[OdisseyDungueons] EntityType desconocido: '" + val + "', usando " + def);
            return def;
        }
    }
}
