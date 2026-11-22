package com.odisseymc.odisseydungueons.instance;

import com.odisseymc.odisseydungueons.api.DungeonEndEvent;
import com.odisseymc.odisseydungueons.api.DungeonStartEvent;
import com.odisseymc.odisseydungueons.boss.BossManager;
import com.odisseymc.odisseydungueons.config.ConfigManager;
import com.odisseymc.odisseydungueons.config.MessageManager;
import com.odisseymc.odisseydungueons.model.Difficulty;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.model.DungeonSpawn;
import com.odisseymc.odisseydungueons.model.DungeonState;
import com.odisseymc.odisseydungueons.rewards.RewardManager;
import com.odisseymc.odisseydungueons.setup.SpawnerManager;
import com.odisseymc.odisseydungueons.util.TextUtil;
import com.odisseymc.odisseydungueons.wave.WaveManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * High-performance orchestrator for isolated private dungeon instances,
 * managing ephemeral world cloning, player routing, timer tasks, and cleanup.
 */
public class InstanceManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private RewardManager rewardManager;
    private WaveManager waveManager;
    private BossManager bossManager;
    private SpawnerManager spawnerManager;

    private final Map<String, DungeonInstance> activeInstances = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerInstanceMap = new ConcurrentHashMap<>();
    private final AtomicInteger instanceCounter = new AtomicInteger(1);

    public InstanceManager(JavaPlugin plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public void setDependencies(RewardManager rewardManager, WaveManager waveManager, BossManager bossManager) {
        this.rewardManager = rewardManager;
        this.waveManager = waveManager;
        this.bossManager = bossManager;
    }

    public void setSpawnerManager(SpawnerManager spawnerManager) {
        this.spawnerManager = spawnerManager;
    }

    public void cleanupOrphanedInstances() {
        File container = Bukkit.getWorldContainer();
        if (container != null && container.isDirectory()) {
            File[] files = container.listFiles((dir, name) -> name.startsWith("inst_"));
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        plugin.getLogger().info("[OdisseyDungueons] Limpiando mundo de instancia huérfano: " + file.getName());
                        WorldInstanceCloner.deleteDirectory(file);
                    }
                }
            }


        }
        File instancesDir = configManager.getInstancesFolder();
        if (instancesDir.exists() && instancesDir.isDirectory()) {
            File[] files = instancesDir.listFiles((dir, name) -> name.startsWith("inst_"));
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        WorldInstanceCloner.deleteDirectory(file);
                    }
                }
            }


        }
    }

    public DungeonInstance startInstance(Dungeon dungeon, Difficulty difficulty, List<Player> players, boolean testMode) {
        if (dungeon == null || players == null || players.isEmpty()) {
            return null;
        }

        // Verify players are not already in a dungeon
        for (Player p : players) {
            if (playerInstanceMap.containsKey(p.getUniqueId())) {
                p.sendMessage(TextUtil.colorize("&cYa estás dentro de una dungeon activa."));
                return null;
            }
        }

        // Requirement 1: World must be explicitly established!
        String worldName = dungeon.getWorldName();
        if (worldName == null || worldName.trim().isEmpty() || worldName.equalsIgnoreCase("none")) {
            plugin.getLogger().warning("[OdisseyDungueons] La mazmorra " + dungeon.getName() + " no tiene un mundo asignado.");
            for (Player p : players) {
                p.sendMessage(TextUtil.colorize("&c[OdisseyDungueons] Error: La mazmorra &f" + dungeon.getName() + " &cno tiene un mundo asignado."));
                p.sendMessage(TextUtil.colorize("&eDebes asignarle un mundo en el menú (&f/dungeons gui&e) o usar &f/dungeons setspawn &een el mapa deseado."));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return null;
        }

        // Requirement 2: Player spawn must be established!
        if (dungeon.getPlayerSpawn() == null) {
            plugin.getLogger().warning("[OdisseyDungueons] La mazmorra " + dungeon.getName() + " no tiene spawn de jugadores.");
            for (Player p : players) {
                p.sendMessage(TextUtil.colorize("&c[OdisseyDungueons] Error: La mazmorra &f" + dungeon.getName() + " &cno tiene punto de spawn de jugadores."));
                p.sendMessage(TextUtil.colorize("&ePárate en el lugar deseado del mapa y ejecuta: &f/dungeons setspawn " + dungeon.getName()));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return null;
        }

        // 1. Resolve template world
        World templateWorld = Bukkit.getWorld(worldName);
        if (templateWorld == null) {
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().equalsIgnoreCase(worldName)) {
                    templateWorld = w;
                    break;
                }
            }
        }
        if (templateWorld == null) {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists() && worldFolder.isDirectory()) {
                try {
                    templateWorld = Bukkit.createWorld(new WorldCreator(worldName));
                } catch (Exception e) {
                    plugin.getLogger().warning("[OdisseyDungueons] Error al cargar mundo plantilla " + worldName + ": " + e.getMessage());
                }
            }
        }

        if (templateWorld == null) {
            plugin.getLogger().severe("[OdisseyDungueons] No se pudo encontrar el mundo '" + worldName + "' para la mazmorra " + dungeon.getName());
            for (Player p : players) {
                p.sendMessage(TextUtil.colorize("&c[OdisseyDungueons] Error: No se encontró el mundo &e" + worldName + " &cconfigurado para esta mazmorra."));
            }
            return null;
        }

        String instId = "inst_" + dungeon.getName().toLowerCase() + "_" + System.currentTimeMillis() + "_" + instanceCounter.getAndIncrement();

        World instanceWorld = null;
        if (testMode) {
            // In test mode, run directly in the template world
            instanceWorld = templateWorld;
        } else {
            // Production mode: clone the template world for private instances
            File templateFolder = templateWorld.getWorldFolder();
            try {
                templateWorld.save();
            } catch (Exception ignored) {}

            File targetWorldFolder = new File(Bukkit.getWorldContainer(), instId);
            boolean copied = WorldInstanceCloner.copyWorld(templateFolder, targetWorldFolder);

            if (copied) {
                WorldCreator creator = new WorldCreator(instId);
                creator.copy(templateWorld);
                instanceWorld = creator.createWorld();
            }

            if (instanceWorld == null) {
                plugin.getLogger().warning("[OdisseyDungueons] No se pudo clonar mundo para la instancia. Usando mundo base: " + templateWorld.getName());
                instanceWorld = templateWorld;
            } else {
                instanceWorld.setAutoSave(false);
                instanceWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                instanceWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
                instanceWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            }
        }

        // Ensure dungeon has waves configured
        if (dungeon.getWaves().isEmpty()) {
            dungeon.initDefaultWaves();
        }

        // Ensure world difficulty allows hostile mob spawning
        if (instanceWorld.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            instanceWorld.setDifficulty(org.bukkit.Difficulty.NORMAL);
        }

        DungeonInstance instance = new DungeonInstance(instId, dungeon, difficulty, instanceWorld, testMode);
        activeInstances.put(instId, instance);

        if (spawnerManager != null) {
            spawnerManager.initializeInstanceSpawners(instance);
        }

        // Resolve spawn location: prioritize configured player spawn
        Location spawnLoc = null;
        if (dungeon.getPlayerSpawn() != null) {
            DungeonSpawn pSpawn = dungeon.getPlayerSpawn();
            spawnLoc = new Location(instanceWorld, pSpawn.getX(), pSpawn.getY(), pSpawn.getZ(), pSpawn.getYaw(), pSpawn.getPitch());
        } else {
            Location tSpawn = templateWorld.getSpawnLocation();
            spawnLoc = new Location(instanceWorld, tSpawn.getX(), tSpawn.getY(), tSpawn.getZ(), tSpawn.getYaw(), tSpawn.getPitch());
        }

        // Ensure chunk is loaded before teleporting
        if (spawnLoc.getWorld() != null) {
            spawnLoc.getChunk().load(true);
        }

        // Teleport players into the instance
        for (Player p : players) {
            instance.addPlayer(p);
            playerInstanceMap.put(p.getUniqueId(), instId);
            p.teleport(spawnLoc);
            p.sendMessage(TextUtil.colorize("&a&l¡Has entrado a la mazmorra: " + dungeon.getDisplayName() + "!"));
            p.sendMessage(TextUtil.colorize("&7Dificultad: " + difficulty.getDisplayName() + (testMode ? " &8[&eMODO PRUEBA&8]" : "")));
            p.sendMessage(TextUtil.colorize("&7Mundo: &e" + instanceWorld.getName() + " &7| Coordenadas: &fX: " +
                    String.format("%.1f", spawnLoc.getX()) + ", Y: " +
                    String.format("%.1f", spawnLoc.getY()) + ", Z: " +
                    String.format("%.1f", spawnLoc.getZ())));
        }

        instance.setState(DungeonState.IN_USE);

        // Start 1-second interval countdown timer task
        BukkitTask timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            instance.decrementRemainingSeconds();
            int remaining = instance.getRemainingSeconds();

            if (remaining <= 0) {
                handleInstanceDefeat(instance, "&c¡Se ha agotado el tiempo límite de la dungeon!");
                return;
            }

            // Watchdog: check for exploded/dead/unloaded mobs to prevent getting stuck
            if (instance.getState() == DungeonState.IN_USE) {
                boolean mobRemoved = false;
                for (UUID mobUuid : new ArrayList<>(instance.getAliveMobUuids())) {
                    Entity ent = Bukkit.getEntity(mobUuid);
                    if (ent == null || !ent.isValid() || ent.isDead()) {
                        instance.unregisterMobFromSpawner(mobUuid);
                        instance.unregisterMob(mobUuid);
                        mobRemoved = true;
                    }
                }
                if (mobRemoved) {
                    int aliveCount = instance.getAliveMobCount();
                    int curWave = instance.getCurrentWaveIndex();
                    if (!instance.hasPhysicalSpawners() && aliveCount <= 0 && curWave > 0) {
                        Bukkit.getPluginManager().callEvent(new com.odisseymc.odisseydungueons.api.DungeonWaveClearEvent(instance, curWave));
                        advanceWave(instance);
                    }
                }
            }

            // Action bar for players
            for (UUID uuid : instance.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    if (instance.getBossEntity() != null) {
                        p.sendActionBar(TextUtil.colorize("&c&lJEFE ACTIVO &8| &eTiempo: &f" + TextUtil.formatDuration(remaining)));
                    } else {
                        p.sendActionBar(TextUtil.colorize("&bOleada " + instance.getCurrentWaveIndex() + "/" + instance.getTotalWaves() +
                                " &8| &cEnemigos: &f" + instance.getAliveMobCount() +
                                " &8| &eTiempo: &f" + TextUtil.formatDuration(remaining)));
                    }
                }
            }


        }, 20L, 20L);

        instance.setTimerTask(timerTask);

        // Call event
        Bukkit.getPluginManager().callEvent(new DungeonStartEvent(instance));

        // Start first wave after 3 seconds with error-handling
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (activeInstances.containsKey(instId)) {
                    if (instance.hasPhysicalSpawners()) {
                        instance.broadcast("&a&l¡Los spawners de la mazmorra se han activado! &e¡Avanza por las salas para combatir!");
                    } else if (!dungeon.getWaves().isEmpty()) {
                        waveManager.startWave(instance, 1);
                    } else if (dungeon.getBossConfig() != null) {
                        bossManager.spawnBoss(instance);
                    } else {
                        handleInstanceVictory(instance);
                    }
                }
            }

 catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "[OdisseyDungueons] Error crítico al iniciar la oleada 1 en " + dungeon.getName(), t);
                instance.broadcast("&cError al iniciar oleada 1: " + t.getMessage());
            }
        }, 60L);

        return instance;
    }

    public void advanceWave(DungeonInstance instance) {
        if (instance == null || !activeInstances.containsKey(instance.getInstanceId())) {
            return;
        }

        int nextWave = instance.getCurrentWaveIndex() + 1;
        int totalWaves = instance.getTotalWaves();

        if (nextWave <= totalWaves) {
            int delaySeconds = 5;
            if (nextWave - 1 < instance.getDungeon().getWaves().size()) {
                delaySeconds = instance.getDungeon().getWaves().get(nextWave - 1).getDelaySeconds();
            }

            instance.broadcast("&a&l¡Oleada completada! &7Siguiente oleada en &f" + delaySeconds + " &7segundos...");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (activeInstances.containsKey(instance.getInstanceId())) {
                    waveManager.startWave(instance, nextWave);
                }
            }, delaySeconds * 20L);
        } else {
            // All waves cleared: spawn boss or win!
            if (instance.getDungeon().getBossConfig() != null) {
                boolean hasBossSpawner = instance.getInstanceSpawners().values().stream()
                        .anyMatch(s -> s.getType() == DungeonSpawn.SpawnType.BOSS);

                if (hasBossSpawner) {
                    instance.armBossSpawner();
                    instance.broadcast("&6&l¡Todas las oleadas han sido derrotadas!");
                    instance.broadcast("&c&l¡Avanza a la sala del JEFE FINAL para comenzar la batalla!");
                } else {
                    instance.broadcast("&6&l¡Todas las oleadas han sido derrotadas! &c¡Prepárate para el JEFE FINAL!");
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (activeInstances.containsKey(instance.getInstanceId())) {
                            bossManager.spawnBoss(instance);
                        }
                    }, 80L);
                }
            } else {
                // Sin boss: si hay cofre de recompensa, esperar a que se acerquen
                if (instance.getRewardChestLocation() != null) {
                    instance.setAwaitingChestApproach(true);
                    instance.broadcast("&6&l★ ¡TODAS LAS OLEADAS HAN SIDO DERROTADAS! ★");
                    instance.broadcast("&e¡Una poderosa vibración ancestral resuena bajo el suelo! &7Acércate a la zona del cofre...");
                    for (UUID puuid : instance.getPlayers()) {
                        Player p = Bukkit.getPlayer(puuid);
                        if (p != null && p.isOnline()) {
                            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
                            p.sendTitle(TextUtil.colorize("&6&l¡EL SUELO RETUMBA!"), TextUtil.colorize("&eAcércate al epicentro de la sala"), 10, 50, 20);
                        }
                    }
                } else {
                    handleInstanceVictory(instance);
                }
            }
        }
    }

    public void handleInstanceVictory(DungeonInstance instance) {
        if (instance == null || !activeInstances.containsKey(instance.getInstanceId())) {
            return;
        }

        instance.setState(DungeonState.IDLE);
        instance.broadcast("&a&l========================================");
        instance.broadcast("&a&l          ¡MAZMORRA COMPLETADA!         ");
        instance.broadcast("&a&l   ¡Han triunfado sobre la oscuridad!   ");
        instance.broadcast("&a&l========================================");

        for (UUID uuid : instance.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(TextUtil.colorize("&a&l¡VICTORIA!"), TextUtil.colorize("&fHas completado la mazmorra con éxito"), 10, 60, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }

        // Grant rewards if not using a physical chest
        if (!instance.isTestMode()) {
            if (instance.getRewardChestLocation() == null) {
                rewardManager.distributeRewards(instance);
            }
            instance.getDungeon().triggerCooldown();
            configManager.saveDungeon(instance.getDungeon());
        }

        Bukkit.getPluginManager().callEvent(new DungeonEndEvent(instance, true));

        int victoryDelay = configManager.getMainConfig() != null ? configManager.getMainConfig().getInt("instances.victory-teleport-delay", 20) : 20;

        // Teleport out and clean up after grace period
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            endInstance(instance);
        }, victoryDelay * 20L);
    }

    public void handleInstanceDefeat(DungeonInstance instance, String reason) {
        if (instance == null || !activeInstances.containsKey(instance.getInstanceId())) {
            return;
        }

        instance.setState(DungeonState.IDLE);
        instance.broadcast("&c&l========================================");
        instance.broadcast("&c&l            ¡DERROTA EN MAZMORRA!       ");
        instance.broadcast("&c" + reason);
        instance.broadcast("&c&l========================================");

        for (UUID uuid : instance.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle(TextUtil.colorize("&c&l¡HAS CAÍDO!"), TextUtil.colorize("&7" + reason), 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }

        Bukkit.getPluginManager().callEvent(new DungeonEndEvent(instance, false));

        // Clean up
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            endInstance(instance);
        }, 100L);
    }

    public void endInstance(DungeonInstance instance) {
        if (instance == null) return;

        activeInstances.remove(instance.getInstanceId());
        instance.cancelTimer();
        instance.destroyBossBar();

        // Determine exit/lobby location
        Location exitLoc = null;
        if (instance.getDungeon().getLobbySpawn() != null) {
            exitLoc = instance.getDungeon().getLobbySpawn().toBukkitLocation();
        }
        if (exitLoc == null || exitLoc.getWorld() == null) {
            exitLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        // Teleport all players out safely
        for (UUID uuid : instance.getPlayers()) {
            playerInstanceMap.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    p.setFlySpeed(0.1f);
                }
                p.teleport(exitLoc);
                p.sendMessage(TextUtil.colorize("&eHas sido teletransportado fuera de la dungeon."));
            }
        }

        // Clean entities and restore blocks
        instance.cleanupEntities();
        instance.restoreBlocks();

        // Refresh all editor holograms in template worlds so they immediately re-appear for admins
        if (spawnerManager != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnerManager.refreshAllEditorHolograms();
            }, 2L);
        }

        // Safe unload and folder deletion (only for ephemeral cloned instances)
        World world = instance.getWorld();
        if (world != null && !instance.isTestMode() && world.getName().startsWith("inst_")) {
            File worldFolder = world.getWorldFolder();
            Bukkit.unloadWorld(world, false);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                WorldInstanceCloner.deleteDirectory(worldFolder);
            }, 60L);
        }
    }

    public void shutdownAll() {
        for (DungeonInstance instance : new ArrayList<>(activeInstances.values())) {
            endInstance(instance);
        }
        activeInstances.clear();
        playerInstanceMap.clear();
    }

    public DungeonInstance findInstanceByPlayer(UUID uuid) {
        String id = playerInstanceMap.get(uuid);
        return (id != null) ? activeInstances.get(id) : null;
    }

    public void removePlayerFromTracking(UUID uuid) {
        if (uuid != null) {
            playerInstanceMap.remove(uuid);
        }
    }

    public DungeonInstance findInstanceByEntity(Entity entity) {
        if (entity == null) return null;
        UUID uuid = entity.getUniqueId();
        for (DungeonInstance inst : activeInstances.values()) {
            if (inst.getAliveMobUuids().contains(uuid)) {
                return inst;
            }
            if (inst.getBossEntity() != null && inst.getBossEntity().getUniqueId().equals(uuid)) {
                return inst;
            }
        }
        return null;
    }

    public DungeonInstance findInstanceByWorld(World world) {
        if (world == null) return null;
        for (DungeonInstance inst : activeInstances.values()) {
            if (inst.getWorld() != null && inst.getWorld().getName().equalsIgnoreCase(world.getName())) {
                return inst;
            }
        }
        return null;
    }

    public DungeonInstance findInstanceByDungeon(String dungeonName) {
        for (DungeonInstance inst : activeInstances.values()) {
            if (inst.getDungeon().getName().equalsIgnoreCase(dungeonName)) {
                return inst;
            }
        }
        return null;
    }

    public Collection<DungeonInstance> getActiveInstances() {
        return activeInstances.values();
    }
}
