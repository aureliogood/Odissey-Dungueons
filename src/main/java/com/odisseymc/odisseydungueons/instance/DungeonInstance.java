package com.odisseymc.odisseydungueons.instance;

import com.odisseymc.odisseydungueons.model.Difficulty;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.model.DungeonSpawn;
import com.odisseymc.odisseydungueons.model.DungeonState;
import com.odisseymc.odisseydungueons.model.MobConfig;
import com.odisseymc.odisseydungueons.model.WaveConfig;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an isolated private running dungeon instance for a party or player,
 * including per-spawner state tracking, dynamic proximity activation, and hologram cleanup.
 */
public class DungeonInstance {

    public static class InstanceSpawner {
        private final String id;
        private final Location location;
        private final DungeonSpawn.SpawnType type;
        private boolean armed;
        private boolean triggered;
        private TextDisplay hologram;
        private final List<MobConfig> pendingMobs = new ArrayList<>();
        private String customStatus = "";

        // Per-spawner wave tracking
        private int currentWave = 0;
        private int maxWaves = 3;
        private boolean fullCleared = false;
        private boolean waveTransitioning = false;
        private boolean waveMobsSpawned = false;
        private final Set<UUID> spawnerAliveMobs = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        private final Map<Integer, List<MobConfig>> waveMobs = new java.util.concurrent.ConcurrentHashMap<>();

        public InstanceSpawner(String id, Location location, DungeonSpawn.SpawnType type) {
            this.id = id;
            this.location = location;
            this.type = type;
            this.armed = false;
            this.triggered = false;
            this.maxWaves = (type == DungeonSpawn.SpawnType.BOSS) ? 10 : 3;
        }

        public String getId() {
            return id;
        }

        public Location getLocation() {
            return location;
        }

        public DungeonSpawn.SpawnType getType() {
            return type;
        }

        public boolean isArmed() {
            return armed;
        }

        public void setArmed(boolean armed) {
            this.armed = armed;
        }

        public boolean isTriggered() {
            return triggered;
        }

        public void setTriggered(boolean triggered) {
            this.triggered = triggered;
        }

        public TextDisplay getHologram() {
            return hologram;
        }

        public void setHologram(TextDisplay hologram) {
            this.hologram = hologram;
        }

        public List<MobConfig> getPendingMobs() {
            return pendingMobs;
        }

        public String getCustomStatus() {
            return customStatus;
        }

        public void setCustomStatus(String customStatus) {
            this.customStatus = customStatus;
        }

        public int getCurrentWave() {
            return currentWave;
        }

        public void setCurrentWave(int currentWave) {
            this.currentWave = currentWave;
        }

        public int getMaxWaves() {
            return maxWaves;
        }

        public void setMaxWaves(int maxWaves) {
            this.maxWaves = maxWaves;
        }

        public boolean isFullCleared() {
            return fullCleared;
        }

        public void setFullCleared(boolean fullCleared) {
            this.fullCleared = fullCleared;
        }

        public boolean isWaveTransitioning() {
            return waveTransitioning;
        }

        public void setWaveTransitioning(boolean waveTransitioning) {
            this.waveTransitioning = waveTransitioning;
        }

        public boolean isWaveMobsSpawned() {
            return waveMobsSpawned;
        }

        public void setWaveMobsSpawned(boolean waveMobsSpawned) {
            this.waveMobsSpawned = waveMobsSpawned;
        }

        public Set<UUID> getSpawnerAliveMobs() {
            return spawnerAliveMobs;
        }

        public int getSpawnerAliveMobCount() {
            return spawnerAliveMobs.size();
        }

        public void registerSpawnerMob(UUID uuid) {
            if (uuid != null) spawnerAliveMobs.add(uuid);
        }

        public void unregisterSpawnerMob(UUID uuid) {
            if (uuid != null) spawnerAliveMobs.remove(uuid);
        }

        public Map<Integer, List<MobConfig>> getWaveMobs() {
            return waveMobs;
        }
    }

    private final String instanceId;
    private final Dungeon dungeon;
    private final Difficulty difficulty;
    private final World world;
    private final Set<UUID> players = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> aliveMobUuids = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, BlockStateRecord> modifiedBlocks = new ConcurrentHashMap<>();
    private final Set<String> triggeredIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, InstanceSpawner> instanceSpawners = new ConcurrentHashMap<>();
    private final Map<UUID, String> mobSpawnerMap = new ConcurrentHashMap<>();

    // Reward chest state
    private boolean rewardChestUnlocked = false;
    private boolean awaitingChestApproach = false;
    private boolean rewardChestEmerging = false;
    private boolean rewardChestEmerged = false;
    private final Set<UUID> claimedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private TextDisplay rewardChestHologram;
    private TextDisplay lockedChestHologram;
    private Location rewardChestLocation;
    private org.bukkit.entity.BlockDisplay emergingChestDisplay;

    private DungeonState state = DungeonState.IDLE;
    private Entity bossEntity = null;
    private int currentWaveIndex = 0;
    private int totalWaves = 0;
    private int remainingSeconds = 0;
    private boolean testMode = false;

    private BukkitTask timerTask;

    public DungeonInstance(String instanceId, Dungeon dungeon, Difficulty difficulty, World world, boolean testMode) {
        this.instanceId = instanceId;
        this.dungeon = dungeon;
        this.difficulty = difficulty != null ? difficulty : Difficulty.NORMAL;
        this.world = world;
        this.testMode = testMode;
        this.totalWaves = dungeon.getWaves().size();
        this.remainingSeconds = dungeon.getDurationSeconds();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Dungeon getDungeon() {
        return dungeon;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public World getWorld() {
        return world;
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public boolean containsPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public void addPlayer(Player player) {
        if (player == null) return;
        players.add(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        if (player == null) return;
        players.remove(player.getUniqueId());
    }

    public Set<UUID> getAliveMobUuids() {
        return aliveMobUuids;
    }

    public int getAliveMobCount() {
        return aliveMobUuids.size();
    }

    public void registerMob(Entity entity) {
        if (entity != null) {
            aliveMobUuids.add(entity.getUniqueId());
        }
    }

    public void unregisterMob(Entity entity) {
        if (entity != null) {
            aliveMobUuids.remove(entity.getUniqueId());
        }
    }

    public void unregisterMob(UUID uuid) {
        if (uuid != null) {
            aliveMobUuids.remove(uuid);
        }
    }

    public Entity getBossEntity() {
        return bossEntity;
    }

    public void setBossEntity(Entity bossEntity) {
        this.bossEntity = bossEntity;
    }

    public DungeonState getState() {
        return state;
    }

    public void setState(DungeonState state) {
        this.state = state;
    }

    public int getCurrentWaveIndex() {
        return currentWaveIndex;
    }

    public void setCurrentWaveIndex(int currentWaveIndex) {
        this.currentWaveIndex = currentWaveIndex;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public void decrementRemainingSeconds() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }
    }

    public boolean isTestMode() {
        return testMode;
    }

    public org.bukkit.boss.BossBar getWaveBossBar() {
        return null;
    }

    public BukkitTask getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public Map<String, BlockStateRecord> getModifiedBlocks() {
        return modifiedBlocks;
    }

    public void recordBlockModification(BlockStateRecord record) {
        String key = record.getX() + ";" + record.getY() + ";" + record.getZ();
        modifiedBlocks.putIfAbsent(key, record);
    }

    public boolean hasTriggered(String triggerId) {
        return triggeredIds.contains(triggerId);
    }

    public void markTriggered(String triggerId) {
        triggeredIds.add(triggerId);
    }

    public Map<String, InstanceSpawner> getInstanceSpawners() {
        return instanceSpawners;
    }

    public void addInstanceSpawner(InstanceSpawner spawner) {
        if (spawner != null) {
            instanceSpawners.put(spawner.getId(), spawner);
        }
    }

    public void armWaveSpawners(WaveConfig waveConfig, double countMultiplier) {
        List<InstanceSpawner> mobSpawners = new ArrayList<>();
        for (InstanceSpawner is : instanceSpawners.values()) {
            if (is.getType() == DungeonSpawn.SpawnType.MOB) {
                is.getPendingMobs().clear();
                is.setTriggered(false);
                is.setArmed(true);
                mobSpawners.add(is);
            }
        }

        if (mobSpawners.isEmpty() || waveConfig == null) return;

        // Distribute mobs among available mob spawners
        int spawnerIdx = 0;
        for (MobConfig mobConfig : waveConfig.getMobs()) {
            double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll > mobConfig.getSpawnChance()) continue;

            int amount = (int) Math.max(1, Math.round(mobConfig.getAmount() * countMultiplier));
            for (int i = 0; i < amount; i++) {
                InstanceSpawner targetSpawner = mobSpawners.get(spawnerIdx % mobSpawners.size());
                targetSpawner.getPendingMobs().add(mobConfig);
                spawnerIdx++;
            }
        }
    }

    public void armBossSpawner() {
        for (InstanceSpawner is : instanceSpawners.values()) {
            if (is.getType() == DungeonSpawn.SpawnType.BOSS) {
                is.setArmed(true);
                is.setTriggered(false);
            } else {
                is.setArmed(false);
            }
        }
    }

    // ===== Per-Spawner Mob Tracking =====

    public void registerMobForSpawner(Entity entity, String spawnerId) {
        if (entity != null && spawnerId != null) {
            UUID uuid = entity.getUniqueId();
            aliveMobUuids.add(uuid);
            mobSpawnerMap.put(uuid, spawnerId);
            InstanceSpawner spawner = instanceSpawners.get(spawnerId);
            if (spawner != null) {
                spawner.registerSpawnerMob(uuid);
            }
        }
    }

    public String getSpawnerIdForMob(UUID mobUuid) {
        return mobSpawnerMap.get(mobUuid);
    }

    public void unregisterMobFromSpawner(UUID mobUuid) {
        if (mobUuid == null) return;
        String spawnerId = mobSpawnerMap.remove(mobUuid);
        aliveMobUuids.remove(mobUuid);
        if (spawnerId != null) {
            InstanceSpawner spawner = instanceSpawners.get(spawnerId);
            if (spawner != null) {
                spawner.unregisterSpawnerMob(mobUuid);
            }
        }
    }

    public boolean hasPhysicalSpawners() {
        return !instanceSpawners.isEmpty();
    }

    public boolean areAllMobSpawnersCleared() {
        boolean hasAny = false;
        for (InstanceSpawner s : instanceSpawners.values()) {
            if (s.getType() == DungeonSpawn.SpawnType.MOB) {
                hasAny = true;
                if (!s.isFullCleared()) return false;
            }
        }
        return hasAny;
    }

    public boolean isBossSpawnerCleared() {
        for (InstanceSpawner s : instanceSpawners.values()) {
            if (s.getType() == DungeonSpawn.SpawnType.BOSS && s.isFullCleared()) {
                return true;
            }
        }
        return false;
    }

    // ===== Reward Chest =====

    public boolean isRewardChestUnlocked() {
        return rewardChestUnlocked;
    }

    public void setRewardChestUnlocked(boolean unlocked) {
        this.rewardChestUnlocked = unlocked;
    }

    public Set<UUID> getClaimedPlayers() {
        return claimedPlayers;
    }

    public boolean hasPlayerClaimed(UUID uuid) {
        return claimedPlayers.contains(uuid);
    }

    public void markPlayerClaimed(UUID uuid) {
        claimedPlayers.add(uuid);
    }

    public TextDisplay getRewardChestHologram() {
        return rewardChestHologram;
    }

    public void setRewardChestHologram(TextDisplay hologram) {
        this.rewardChestHologram = hologram;
    }

    public TextDisplay getLockedChestHologram() {
        return lockedChestHologram;
    }

    public void setLockedChestHologram(TextDisplay hologram) {
        this.lockedChestHologram = hologram;
    }

    public Location getRewardChestLocation() {
        return rewardChestLocation;
    }

    public void setRewardChestLocation(Location loc) {
        this.rewardChestLocation = loc;
    }

    public boolean isAwaitingChestApproach() {
        return awaitingChestApproach;
    }

    public void setAwaitingChestApproach(boolean awaitingChestApproach) {
        this.awaitingChestApproach = awaitingChestApproach;
    }

    public boolean isRewardChestEmerging() {
        return rewardChestEmerging;
    }

    public void setRewardChestEmerging(boolean rewardChestEmerging) {
        this.rewardChestEmerging = rewardChestEmerging;
    }

    public boolean isRewardChestEmerged() {
        return rewardChestEmerged;
    }

    public void setRewardChestEmerged(boolean rewardChestEmerged) {
        this.rewardChestEmerged = rewardChestEmerged;
    }

    public org.bukkit.entity.BlockDisplay getEmergingChestDisplay() {
        return emergingChestDisplay;
    }

    public void setEmergingChestDisplay(org.bukkit.entity.BlockDisplay emergingChestDisplay) {
        this.emergingChestDisplay = emergingChestDisplay;
    }

    // ===== Pre-load Spawner Waves =====

    public void loadSpawnerWaves(List<WaveConfig> waveConfigs, double countMultiplier) {
        List<InstanceSpawner> mobSpawners = new ArrayList<>();
        for (InstanceSpawner is : instanceSpawners.values()) {
            if (is.getType() == DungeonSpawn.SpawnType.MOB) {
                is.setArmed(true);
                is.setTriggered(false);
                is.setCurrentWave(0);
                is.setFullCleared(false);
                is.getWaveMobs().clear();
                is.getPendingMobs().clear();
                mobSpawners.add(is);
            }
        }

        if (mobSpawners.isEmpty() || waveConfigs == null || waveConfigs.isEmpty()) return;

        int maxW = mobSpawners.get(0).getMaxWaves();
        for (int w = 0; w < Math.min(waveConfigs.size(), maxW); w++) {
            WaveConfig wc = waveConfigs.get(w);
            int waveNum = w + 1;

            int spawnerIdx = 0;
            for (MobConfig mc : wc.getMobs()) {
                double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble(0.0, 100.0);
                if (roll > mc.getSpawnChance()) continue;

                int amount = (int) Math.max(1, Math.round(mc.getAmount() * countMultiplier));
                for (int i = 0; i < amount; i++) {
                    InstanceSpawner target = mobSpawners.get(spawnerIdx % mobSpawners.size());
                    target.getWaveMobs().computeIfAbsent(waveNum, k -> new ArrayList<>()).add(mc);
                    spawnerIdx++;
                }
            }
        }
    }

    public void loadBossHordeWaves(List<MobConfig> normalMobs, double countMultiplier, int waves) {
        for (InstanceSpawner is : instanceSpawners.values()) {
            if (is.getType() == DungeonSpawn.SpawnType.BOSS) {
                is.setMaxWaves(waves);
                is.setCurrentWave(0);
                is.setFullCleared(false);
                is.getWaveMobs().clear();

                if (normalMobs == null || normalMobs.isEmpty()) return;

                for (int w = 1; w <= waves; w++) {
                    List<MobConfig> waveMobList = new ArrayList<>();
                    double waveScale = 1.0 + (w - 1) * 0.12;
                    for (MobConfig mc : normalMobs) {
                        int amount = (int) Math.max(1, Math.round(mc.getAmount() * countMultiplier * waveScale));
                        for (int i = 0; i < amount; i++) {
                            waveMobList.add(mc);
                        }
                    }
                    is.getWaveMobs().put(w, waveMobList);
                }
            }
        }
    }

    public void cleanInstanceSpawners() {
        for (InstanceSpawner is : instanceSpawners.values()) {
            if (is.getHologram() != null && is.getHologram().isValid()) {
                is.getHologram().remove();
            }
        }
        instanceSpawners.clear();
    }

    public void broadcast(String message) {
        if (message == null || message.isEmpty()) return;
        String colorized = TextUtil.colorize(message);
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(colorized);
            }
        }
    }

    public void cleanupEntities() {
        cleanInstanceSpawners();

        // Clean reward chest hologram
        if (rewardChestHologram != null && rewardChestHologram.isValid()) {
            rewardChestHologram.remove();
            rewardChestHologram = null;
        }

        // Clean locked chest hologram
        if (lockedChestHologram != null && lockedChestHologram.isValid()) {
            lockedChestHologram.remove();
            lockedChestHologram = null;
        }

        // Clean reward chest physical block if present
        if (rewardChestLocation != null && rewardChestLocation.getWorld() != null) {
            org.bukkit.block.Block cb = rewardChestLocation.getBlock();
            if (cb.getType() == Material.CHEST) {
                cb.setType(Material.AIR);
            }
        }

        if (emergingChestDisplay != null && emergingChestDisplay.isValid()) {
            emergingChestDisplay.remove();
            emergingChestDisplay = null;
        }

        if (world == null) return;

        // Clear alive mobs tracked
        for (UUID uuid : aliveMobUuids) {
            Entity ent = Bukkit.getEntity(uuid);
            if (ent != null && !ent.isDead()) {
                ent.remove();
            }
        }
        aliveMobUuids.clear();

        if (bossEntity != null && !bossEntity.isDead()) {
            bossEntity.remove();
            bossEntity = null;
        }

        // Clean any leftover items or monsters in the instance world
        if (!testMode && world.getName().startsWith("inst_")) {
            for (Entity ent : world.getEntities()) {
                if (!(ent instanceof Player)) {
                    ent.remove();
                }
            }
        } else {
            // Test mode or base template world: only remove monsters, items, and projectiles (never editor holograms!)
            for (Entity ent : world.getEntities()) {
                if (ent instanceof org.bukkit.entity.Monster ||
                        ent instanceof org.bukkit.entity.Item ||
                        ent instanceof org.bukkit.entity.Projectile) {
                    ent.remove();
                }
            }
        }
    }

    public void restoreBlocks() {
        if (world == null) return;
        for (BlockStateRecord record : modifiedBlocks.values()) {
            record.restore(world.getBlockAt(record.getX(), record.getY(), record.getZ()));
        }
        modifiedBlocks.clear();
    }

    public void destroyBossBar() {
        // BossBar completely removed per user request
    }

    public void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }
}
