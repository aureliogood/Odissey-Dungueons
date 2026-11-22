package com.odisseymc.odisseydungueons.setup;

import com.odisseymc.odisseydungueons.boss.BossManager;
import com.odisseymc.odisseydungueons.config.ConfigManager;
import com.odisseymc.odisseydungueons.config.MessageManager;
import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.instance.InstanceManager;
import com.odisseymc.odisseydungueons.model.*;
import com.odisseymc.odisseydungueons.util.TextUtil;
import com.odisseymc.odisseydungueons.wave.WaveManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Consumer;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Manages physical Spawner blocks (Material.SPAWNER) for Mob and Boss spawning,
 * modern TextDisplay holograms with full PlaceholderAPI and internal variables support,
 * and proximity-based activation to eliminate lag and prevent skipping rooms.
 */
public class SpawnerManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Function<String, Dungeon> dungeonLookup;
    private final Supplier<Collection<Dungeon>> allDungeonsSupplier;

    private InstanceManager instanceManager;
    private WaveManager waveManager;
    private BossManager bossManager;
    private com.odisseymc.odisseydungueons.rewards.RewardManager rewardManager;

    private final NamespacedKey spawnerTypeKey;
    private final NamespacedKey spawnerDungeonKey;
    private final NamespacedKey hologramKey;

    // Track active holograms in editor/template worlds: key is "world_x_y_z"
    private final Map<String, TextDisplay> editorHolograms = new ConcurrentHashMap<>();
    private final Set<UUID> activeCinematicPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private BukkitTask tickTask;

    public SpawnerManager(JavaPlugin plugin,
                          ConfigManager configManager,
                          MessageManager messageManager,
                          Function<String, Dungeon> dungeonLookup,
                          Supplier<Collection<Dungeon>> allDungeonsSupplier) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.dungeonLookup = dungeonLookup;
        this.allDungeonsSupplier = allDungeonsSupplier;

        this.spawnerTypeKey = new NamespacedKey(plugin, "odissey_spawner_type");
        this.spawnerDungeonKey = new NamespacedKey(plugin, "odissey_spawner_dungeon");
        this.hologramKey = new NamespacedKey(plugin, "odissey_hologram");
    }

    public void setDependencies(InstanceManager instanceManager, WaveManager waveManager, BossManager bossManager) {
        this.instanceManager = instanceManager;
        this.waveManager = waveManager;
        this.bossManager = bossManager;
    }

    public void setRewardManager(com.odisseymc.odisseydungueons.rewards.RewardManager rewardManager) {
        this.rewardManager = rewardManager;
    }

    public void start() {
        // Load initial editor holograms for all registered dungeons
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAllEditorHolograms, 40L);

        // Run proximity and hologram ticker every 10 ticks (0.5 seconds)
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        cleanupAllHolograms();
    }

    /* ==============================================================
     *                      SPAWNER ITEM FACTORY
     * ============================================================== */

    public ItemStack createMobSpawner(String dungeonName) {
        Dungeon dungeon = dungeonLookup.apply(dungeonName);
        FileConfiguration cfg = configManager.getMainConfig();

        double radius = cfg != null ? cfg.getDouble("spawners.activation-radius", 14.0) : 14.0;
        String rawName = cfg != null ? cfg.getString("spawners.mob-spawner.item-name", "&b&lSPAWNER DE MOBS &8[&e%dungeon%&8]") : "&b&lSPAWNER DE MOBS &8[&e%dungeon%&8]";
        List<String> rawLore = cfg != null ? cfg.getStringList("spawners.mob-spawner.item-lore") : Collections.emptyList();

        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(rawName.replace("%dungeon%", dungeonName)));
            List<String> lore = new ArrayList<>();
            for (String l : rawLore) {
                lore.add(TextUtil.colorize(l.replace("%dungeon%", dungeonName).replace("%radius%", String.valueOf((int) radius))));
            }
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(spawnerTypeKey, PersistentDataType.STRING, "MOB");
            pdc.set(spawnerDungeonKey, PersistentDataType.STRING, dungeonName);
            spawner.setItemMeta(meta);
        }
        return spawner;
    }

    public ItemStack createBossSpawner(String dungeonName) {
        Dungeon dungeon = dungeonLookup.apply(dungeonName);
        FileConfiguration cfg = configManager.getMainConfig();

        double radius = cfg != null ? cfg.getDouble("spawners.activation-radius", 14.0) : 14.0;
        String rawName = cfg != null ? cfg.getString("spawners.boss-spawner.item-name", "&c&lSPAWNER DE JEFE FINAL &8[&e%dungeon%&8]") : "&c&lSPAWNER DE JEFE FINAL &8[&e%dungeon%&8]";
        List<String> rawLore = cfg != null ? cfg.getStringList("spawners.boss-spawner.item-lore") : Collections.emptyList();

        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(rawName.replace("%dungeon%", dungeonName)));
            List<String> lore = new ArrayList<>();
            for (String l : rawLore) {
                lore.add(TextUtil.colorize(l.replace("%dungeon%", dungeonName).replace("%radius%", String.valueOf((int) radius))));
            }
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(spawnerTypeKey, PersistentDataType.STRING, "BOSS");
            pdc.set(spawnerDungeonKey, PersistentDataType.STRING, dungeonName);
            spawner.setItemMeta(meta);
        }
        return spawner;
    }

    public ItemStack createRewardChest(String dungeonName) {
        Dungeon dungeon = dungeonLookup.apply(dungeonName);
        FileConfiguration cfg = configManager.getMainConfig();

        String rawName = cfg != null ? cfg.getString("reward-chest.item-name", "&6&lCOFRE DE RECOMPENSAS &8[&e%dungeon%&8]") : "&6&lCOFRE DE RECOMPENSAS &8[&e%dungeon%&8]";
        List<String> rawLore = cfg != null ? cfg.getStringList("reward-chest.item-lore") : Collections.emptyList();

        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta meta = chest.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(rawName.replace("%dungeon%", dungeonName)));
            List<String> lore = new ArrayList<>();
            for (String l : rawLore) {
                lore.add(TextUtil.colorize(l.replace("%dungeon%", dungeonName)));
            }
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(spawnerTypeKey, PersistentDataType.STRING, "REWARD_CHEST");
            pdc.set(spawnerDungeonKey, PersistentDataType.STRING, dungeonName);
            chest.setItemMeta(meta);
        }
        return chest;
    }

    // Legacy aliases for backward compatibility
    public ItemStack createMobWand(String dungeonName) {
        return createMobSpawner(dungeonName);
    }

    public ItemStack createBossWand(String dungeonName) {
        return createBossSpawner(dungeonName);
    }

    /* ==============================================================
     *                 EVENT HANDLERS: PLACE & BREAK
     * ============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        if (item == null) return;
        if (item.getType() != Material.SPAWNER && item.getType() != Material.CHEST) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(spawnerTypeKey, PersistentDataType.STRING) || !pdc.has(spawnerDungeonKey, PersistentDataType.STRING)) {
            return;
        }

        if (!player.hasPermission("odisseydungueons.admin")) {
            messageManager.send(player, "no-permission");
            event.setCancelled(true);
            return;
        }

        String type = pdc.get(spawnerTypeKey, PersistentDataType.STRING);
        String dungeonName = pdc.get(spawnerDungeonKey, PersistentDataType.STRING);
        Dungeon dungeon = dungeonLookup.apply(dungeonName);

        if (dungeon == null) {
            messageManager.send(player, "dungeon-not-found", Map.of("dungeon", dungeonName));
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlockPlaced();
        Location loc = block.getLocation();

        // Configure CreatureSpawner visual icon and neutralize vanilla automatic spawning
        if (block.getType() == Material.SPAWNER) {
            BlockState state = block.getState();
            if (state instanceof CreatureSpawner cs) {
                if ("BOSS".equalsIgnoreCase(type)) {
                    EntityType bossIcon = (dungeon.getBossConfig() != null && dungeon.getBossConfig().getVanillaType() != null)
                            ? dungeon.getBossConfig().getVanillaType()
                            : EntityType.WITHER_SKELETON;
                    cs.setSpawnedType(bossIcon);
                } else {
                    cs.setSpawnedType(EntityType.ZOMBIE);
                }
                cs.setRequiredPlayerRange(0);
                cs.setMaxNearbyEntities(0);
                cs.setDelay(999999);
                cs.update(true, false);
            }
        }

        // Auto-assign world if not set
        if (dungeon.getWorldName() == null || dungeon.getWorldName().trim().isEmpty() || dungeon.getWorldName().equalsIgnoreCase("none")) {
            dungeon.setWorldName(block.getWorld().getName());
            player.sendMessage(TextUtil.colorize("&a&l[ODISSEY DUNGUEONS] &aMundo de la mazmorra asignado automáticamente a: &e" + block.getWorld().getName()));
        }

        Location spawnLoc = loc.clone().add(0.5, 0.0, 0.5);
        spawnLoc.setYaw(player.getLocation().getYaw());
        spawnLoc.setPitch(0f);

        if ("MOB".equalsIgnoreCase(type)) {
            String spawnId = "spawner_mob_" + (dungeon.getMobSpawns().size() + 1) + "_" + (System.currentTimeMillis() % 1000);
            DungeonSpawn spawn = new DungeonSpawn(spawnId, DungeonSpawn.SpawnType.MOB, spawnLoc);
            dungeon.addMobSpawn(spawn);
            configManager.saveDungeon(dungeon);

            createOrUpdateEditorHologram(dungeon, spawn);

            messageManager.send(player, "spawner-mob-placed", Map.of("dungeon", dungeon.getName()));
            player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
            loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);

        } else if ("BOSS".equalsIgnoreCase(type)) {
            // Remove previous boss spawner hologram if replacing
            if (dungeon.getBossSpawn() != null) {
                removeEditorHologramAt(dungeon.getBossSpawn());
            }

            DungeonSpawn bossSpawn = new DungeonSpawn("spawner_boss", DungeonSpawn.SpawnType.BOSS, spawnLoc);
            dungeon.setBossSpawn(bossSpawn);
            configManager.saveDungeon(dungeon);

            createOrUpdateEditorHologram(dungeon, bossSpawn);

            messageManager.send(player, "spawner-boss-placed", Map.of("dungeon", dungeon.getName()));
            player.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.2f);
            loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, 0.05);

        } else if ("REWARD_CHEST".equalsIgnoreCase(type)) {
            if (dungeon.getRewardChestSpawn() != null) {
                removeEditorHologramAt(dungeon.getRewardChestSpawn());
            }

            DungeonSpawn chestSpawn = new DungeonSpawn("reward_chest", DungeonSpawn.SpawnType.REWARD_CHEST, spawnLoc);
            dungeon.setRewardChestSpawn(chestSpawn);
            configManager.saveDungeon(dungeon);

            createOrUpdateEditorHologram(dungeon, chestSpawn);

            messageManager.send(player, "reward-chest-placed", Map.of("dungeon", dungeon.getName()));
            player.playSound(loc, Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, 0.05);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER && block.getType() != Material.CHEST) return;

        Player player = event.getPlayer();
        Location blockLoc = block.getLocation();

        // 1. Check if broken inside an active dungeon instance
        if (instanceManager != null) {
            DungeonInstance inst = instanceManager.findInstanceByWorld(block.getWorld());
            if (inst != null) {
                event.setCancelled(true);
                if (block.getType() == Material.CHEST) {
                    messageManager.send(player, "reward-chest-cant-break");
                } else {
                    messageManager.send(player, "spawner-cant-break");
                }
                return;
            }
        }

        // 2. Check if this belongs to any configured dungeon
        for (Dungeon dungeon : allDungeonsSupplier.get()) {
            if (dungeon.getWorldName() == null || !dungeon.getWorldName().equalsIgnoreCase(block.getWorld().getName())) {
                continue;
            }

            // Check Reward Chest
            if (block.getType() == Material.CHEST && dungeon.getRewardChestSpawn() != null && matchesBlock(dungeon.getRewardChestSpawn(), blockLoc)) {
                if (!player.hasPermission("odisseydungueons.admin")) {
                    event.setCancelled(true);
                    messageManager.send(player, "no-permission");
                    return;
                }

                if (!player.isSneaking() && player.getGameMode() != GameMode.CREATIVE) {
                    event.setCancelled(true);
                    messageManager.send(player, "reward-chest-remove-help");
                    return;
                }

                dungeon.setRewardChestSpawn(null);
                configManager.saveDungeon(dungeon);
                removeEditorHologramAt(blockLoc);

                messageManager.send(player, "reward-chest-removed", Map.of("dungeon", dungeon.getName()));
                player.playSound(blockLoc, Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.2f);
                return;
            }

            // Check Mob Spawner
            if (block.getType() == Material.SPAWNER) {
                DungeonSpawn matchedMobSpawn = null;
                for (DungeonSpawn s : dungeon.getMobSpawns()) {
                    if (matchesBlock(s, blockLoc)) {
                        matchedMobSpawn = s;
                        break;
                    }
                }

                if (matchedMobSpawn != null) {
                    if (!player.hasPermission("odisseydungueons.admin")) {
                        event.setCancelled(true);
                        messageManager.send(player, "no-permission");
                        return;
                    }

                    if (!player.isSneaking() && player.getGameMode() != GameMode.CREATIVE) {
                        event.setCancelled(true);
                        messageManager.send(player, "spawner-remove-help");
                        return;
                    }

                    dungeon.removeMobSpawn(matchedMobSpawn.getId());
                    configManager.saveDungeon(dungeon);
                    removeEditorHologramAt(matchedMobSpawn);

                    messageManager.send(player, "spawner-removed", Map.of("dungeon", dungeon.getName()));
                    player.playSound(blockLoc, Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.2f);
                    return;
                }

                // Check Boss Spawner
                if (dungeon.getBossSpawn() != null && matchesBlock(dungeon.getBossSpawn(), blockLoc)) {
                    if (!player.hasPermission("odisseydungueons.admin")) {
                        event.setCancelled(true);
                        messageManager.send(player, "no-permission");
                        return;
                    }

                    if (!player.isSneaking() && player.getGameMode() != GameMode.CREATIVE) {
                        event.setCancelled(true);
                        messageManager.send(player, "spawner-remove-help");
                        return;
                    }

                    dungeon.setBossSpawn(null);
                    configManager.saveDungeon(dungeon);
                    removeEditorHologramAt(blockLoc);

                    messageManager.send(player, "spawner-removed", Map.of("dungeon", dungeon.getName()));
                    player.playSound(blockLoc, Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.2f);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        Player player = event.getPlayer();
        if (instanceManager == null) return;

        DungeonInstance instance = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (instance == null || instance.getWorld() == null) return;

        Location chestLoc = instance.getRewardChestLocation();
        if (chestLoc == null && instance.getDungeon().getRewardChestSpawn() != null) {
            DungeonSpawn s = instance.getDungeon().getRewardChestSpawn();
            chestLoc = new Location(instance.getWorld(), Math.floor(s.getX()) + 0.5, Math.floor(s.getY()), Math.floor(s.getZ()) + 0.5);
            instance.setRewardChestLocation(chestLoc);
        }

        if (chestLoc == null) return;

        if (matchesBlock(instance.getDungeon().getRewardChestSpawn(), block.getLocation()) ||
                (block.getX() == chestLoc.getBlockX() && block.getY() == chestLoc.getBlockY() && block.getZ() == chestLoc.getBlockZ())) {

            event.setCancelled(true);

            if (!instance.isRewardChestUnlocked()) {
                messageManager.send(player, "reward-chest-locked");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (instance.hasPlayerClaimed(player.getUniqueId())) {
                messageManager.send(player, "reward-chest-already-claimed");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Claim rewards!
            instance.markPlayerClaimed(player.getUniqueId());
            if (rewardManager != null && instance.getDungeon() != null) {
                rewardManager.givePlayerRewards(player, instance.getDungeon(), instance.getDifficulty(),
                        instance.getDungeon().getRewardsForDifficulty(instance.getDifficulty()));
            }

            messageManager.send(player, "reward-chest-claimed");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            block.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, block.getLocation().clone().add(0.5, 1.0, 0.5), 35, 0.4, 0.5, 0.4, 0.1);

            // Verificar si todos los jugadores de la instancia ya reclamaron su recompensa
            boolean allClaimed = true;
            for (UUID u : instance.getPlayers()) {
                if (!instance.hasPlayerClaimed(u)) {
                    allClaimed = false;
                    break;
                }
            }

            // Si todos reclamaron (o el único jugador reclamó), el cofre queda vacío y desaparece
            if (allClaimed) {
                block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().clone().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, Material.CHEST.createBlockData());
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
                block.setType(Material.AIR);
                if (instance.getRewardChestHologram() != null && instance.getRewardChestHologram().isValid()) {
                    instance.getRewardChestHologram().remove();
                    instance.setRewardChestHologram(null);
                }
                instance.broadcast("&6&l[ODISSEY DUNGUEONS] &a¡El cofre de recompensas ha sido vaciado por completo!");
            }
        }
    }

    private Location getNaturalSpawnLocation(Location spLoc) {
        if (spLoc == null || spLoc.getWorld() == null) return spLoc;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        double angle = rng.nextDouble(0, 2 * Math.PI);
        double dist = rng.nextDouble(1.0, 2.5);
        double offsetX = Math.cos(angle) * dist;
        double offsetZ = Math.sin(angle) * dist;
        Location target = spLoc.clone().add(offsetX, 0.0, offsetZ);

        Block block = target.getBlock();
        if (block.getType().isSolid()) {
            target.add(0, 1.0, 0);
        } else if (target.clone().add(0, -1.0, 0).getBlock().isPassable()) {
            target.add(0, -0.5, 0);
        }
        return target;
    }

    private boolean matchesBlock(DungeonSpawn spawn, Location loc) {
        if (spawn == null || loc == null) return false;
        return (int) Math.floor(spawn.getX()) == loc.getBlockX() &&
                (int) Math.floor(spawn.getY()) == loc.getBlockY() &&
                (int) Math.floor(spawn.getZ()) == loc.getBlockZ();
    }

    /* ==============================================================
     *                 HOLOGRAM CREATION & UPDATE (TextDisplay)
     * ============================================================== */

    public TextDisplay createHologram(Location loc, List<String> textLines) {
        if (loc == null || loc.getWorld() == null) return null;

        FileConfiguration cfg = configManager.getMainConfig();
        double heightOffset = cfg != null ? cfg.getDouble("spawners.hologram-height", 1.35) : 1.35;
        Location holoLoc = loc.clone().add(0.0, heightOffset, 0.0);

        // Ensure chunk is loaded
        if (!holoLoc.getChunk().isLoaded()) {
            holoLoc.getChunk().load(false);
        }

        try {
            TextDisplay display = holoLoc.getWorld().spawn(holoLoc, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                entity.setShadowed(true);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // 100% transparent background
                entity.setSeeThrough(false);
                entity.setPersistent(false);
                entity.getPersistentDataContainer().set(hologramKey, PersistentDataType.BYTE, (byte) 1);

                String formatted = formatLines(textLines, null, null, null, "");
                entity.setText(formatted);
            });
            return display;
        } catch (Throwable t) {
            plugin.getLogger().warning("[OdisseyDungueons] No se pudo generar TextDisplay: " + t.getMessage());
            return null;
        }
    }

    public void updateHologram(TextDisplay display, List<String> textLines, Dungeon dungeon, DungeonInstance instance, DungeonSpawn spawn, String status) {
        updateHologram(display, textLines, dungeon, instance, null, spawn, status);
    }

    public void updateHologram(TextDisplay display, List<String> textLines, Dungeon dungeon, DungeonInstance instance, DungeonInstance.InstanceSpawner spawner, DungeonSpawn spawn, String status) {
        if (display == null || !display.isValid()) return;
        String formatted = formatLines(textLines, dungeon, instance, spawner, spawn, status);
        display.setText(formatted);
    }

    public String formatLines(List<String> rawLines, Dungeon dungeon, DungeonInstance instance, DungeonSpawn spawn, String status) {
        return formatLines(rawLines, dungeon, instance, null, spawn, status);
    }

    public String formatLines(List<String> rawLines, Dungeon dungeon, DungeonInstance instance, DungeonInstance.InstanceSpawner spawner, DungeonSpawn spawn, String status) {
        if (rawLines == null || rawLines.isEmpty()) return "";

        List<String> formatted = new ArrayList<>();
        Player samplePlayer = (instance != null && !instance.getPlayers().isEmpty())
                ? Bukkit.getPlayer(instance.getPlayers().iterator().next())
                : null;

        String dungeonName = (dungeon != null) ? dungeon.getName() : "";
        String dungeonDisplay = (dungeon != null) ? dungeon.getDisplayName() : "";
        String waveStr = (instance != null) ? String.valueOf(instance.getCurrentWaveIndex()) : "1";
        String totalWavesStr = (dungeon != null && !dungeon.getWaves().isEmpty())
                ? String.valueOf(dungeon.getWaves().size())
                : (instance != null ? String.valueOf(instance.getTotalWaves()) : "1");
        String spawnerWaveStr = (spawner != null) ? String.valueOf(spawner.getCurrentWave()) : "1";
        String bossWaveStr = (spawner != null) ? String.valueOf(spawner.getCurrentWave()) : "1";
        String remainingMobsStr = (spawner != null)
                ? String.valueOf(spawner.getSpawnerAliveMobCount())
                : ((instance != null) ? String.valueOf(instance.getAliveMobCount()) : "0");

        String bossName = (dungeon != null && dungeon.getBossConfig() != null) ? dungeon.getBossConfig().getName() : "Jefe";
        String bossHpStr = "0";
        String bossMaxHpStr = (dungeon != null && dungeon.getBossConfig() != null) ? String.format("%.0f", dungeon.getBossConfig().getHealth()) : "0";
        if (instance != null && instance.getBossEntity() instanceof LivingEntity le && !le.isDead()) {
            bossHpStr = String.format("%.0f", le.getHealth());
        }

        String spawnId = (spawn != null) ? spawn.getId() : "";
        FileConfiguration cfg = configManager.getMainConfig();
        double radius = cfg != null ? cfg.getDouble("spawners.activation-radius", 14.0) : 14.0;

        for (String line : rawLines) {
            String l = line
                    .replace("%dungeon%", dungeonName)
                    .replace("%dungeon_display%", dungeonDisplay)
                    .replace("%wave%", waveStr)
                    .replace("%total_waves%", totalWavesStr)
                    .replace("%spawner_wave%", spawnerWaveStr)
                    .replace("%boss_wave%", bossWaveStr)
                    .replace("%remaining_mobs%", remainingMobsStr)
                    .replace("%boss_name%", bossName)
                    .replace("%boss_health%", bossHpStr)
                    .replace("%boss_max_health%", bossMaxHpStr)
                    .replace("%status%", status)
                    .replace("%spawner_id%", spawnId)
                    .replace("%radius%", String.valueOf((int) radius));

            // PlaceholderAPI support for user-defined placeholders
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    l = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(samplePlayer, l);
                } catch (Throwable ignored) {}
            }

            formatted.add(TextUtil.colorize(l));
        }

        return String.join("\n", formatted);
    }

    public void createOrUpdateEditorHologram(Dungeon dungeon, DungeonSpawn spawn) {
        if (dungeon == null || spawn == null) return;
        Location loc = spawn.toBukkitLocation();
        if (loc == null || loc.getWorld() == null) return;

        String key = loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
        TextDisplay existing = editorHolograms.get(key);
        if (existing != null && existing.isValid()) {
            existing.remove();
        }

        FileConfiguration cfg = configManager.getMainConfig();
        List<String> template = null;
        if (spawn.getType() == DungeonSpawn.SpawnType.REWARD_CHEST) {
            template = (cfg != null ? cfg.getStringList("reward-chest.hologram-editor") : null);
        } else if (spawn.getType() == DungeonSpawn.SpawnType.BOSS) {
            template = (cfg != null ? cfg.getStringList("spawners.boss-spawner.hologram-editor") : null);
        } else {
            template = (cfg != null ? cfg.getStringList("spawners.mob-spawner.hologram-editor") : null);
        }

        if (template == null || template.isEmpty()) {
            template = Arrays.asList("&b&l⚔ SPAWNER DE MAZMORRA ⚔", "&7Mazmorra: &e%dungeon%", "&a✔ Listo");
        }

        Location holoBase = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5);
        removeHologramsAt(holoBase);
        TextDisplay display = createHologram(holoBase, template);
        if (display != null) {
            updateHologram(display, template, dungeon, null, spawn, "✔ Listo");
            editorHolograms.put(key, display);
        }
    }

    public void removeEditorHologramAt(DungeonSpawn spawn) {
        if (spawn == null) return;
        Location loc = spawn.toBukkitLocation();
        if (loc == null || loc.getWorld() == null) return;
        removeEditorHologramAt(loc);
    }

    public void removeEditorHologramAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String key = loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
        TextDisplay display = editorHolograms.remove(key);
        if (display != null && display.isValid()) {
            display.remove();
        }
        removeHologramsAt(loc);
    }

    public void removeHologramsAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        FileConfiguration cfg = configManager.getMainConfig();
        double heightOffset = cfg != null ? cfg.getDouble("spawners.hologram-height", 1.35) : 1.35;
        Location holoTarget = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY() + heightOffset, loc.getBlockZ() + 0.5);
        for (Entity e : loc.getWorld().getNearbyEntities(holoTarget, 1.5, 1.5, 1.5)) {
            if (e instanceof TextDisplay) {
                e.remove();
            }
        }
    }

    public void refreshAllEditorHolograms() {
        cleanupEditorHolograms();
        for (Dungeon d : allDungeonsSupplier.get()) {
            if (d.getWorldName() == null) continue;
            World world = Bukkit.getWorld(d.getWorldName());
            if (world == null) continue;

            for (DungeonSpawn s : d.getMobSpawns()) {
                createOrUpdateEditorHologram(d, s);
            }
            if (d.getBossSpawn() != null) {
                createOrUpdateEditorHologram(d, d.getBossSpawn());
            }
            if (d.getRewardChestSpawn() != null) {
                createOrUpdateEditorHologram(d, d.getRewardChestSpawn());
            }
        }
    }

    public void cleanupEditorHolograms() {
        for (TextDisplay display : editorHolograms.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        editorHolograms.clear();
    }

    public void cleanupAllHolograms() {
        cleanupEditorHolograms();

        // Remove any orphaned holograms tagged with our key across all worlds
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(TextDisplay.class)) {
                if (e.getPersistentDataContainer().has(hologramKey, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
    }

    /* ==============================================================
     *            PROXIMITY DETECTION & INSTANCE TICKER
     * ============================================================== */

    private void tick() {
        FileConfiguration cfg = configManager.getMainConfig();
        double radius = cfg != null ? cfg.getDouble("spawners.activation-radius", 14.0) : 14.0;
        double radiusSq = radius * radius;

        String statusWaiting = cfg != null ? cfg.getString("spawners.status.waiting_players", "&e&l[¡ACÉRCATE PARA INVOCAR!]") : "&e&l[¡ACÉRCATE PARA INVOCAR!]";
        String statusActive = cfg != null ? cfg.getString("spawners.status.active", "&a&l[¡EN COMBATE!]") : "&a&l[¡EN COMBATE!]";
        String statusCleared = cfg != null ? cfg.getString("spawners.status.cleared", "&2✔ Sala completada") : "&2✔ Sala completada";
        String statusBossWaiting = cfg != null ? cfg.getString("spawners.status.boss_ready", "&c&l[¡ACÉRCATE PARA INICIAR LA HORDA!]") : "&c&l[¡ACÉRCATE PARA INICIAR LA HORDA!]";
        String statusBossFighting = cfg != null ? cfg.getString("spawners.status.boss_fighting", "&4&l[¡COMBATE INTENSO!]") : "&4&l[¡COMBATE INTENSO!]";
        String statusBossDefeated = cfg != null ? cfg.getString("spawners.status.boss_defeated", "&6&l[★ HORDA DERROTADA ★]") : "&6&l[★ HORDA DERROTADA ★]";

        List<String> mobHoloTemplate = cfg != null ? cfg.getStringList("spawners.mob-spawner.hologram-active") : Collections.emptyList();
        List<String> bossHoloTemplate = cfg != null ? cfg.getStringList("spawners.boss-spawner.hologram-active") : Collections.emptyList();

        if (instanceManager == null) return;

        // Process all active dungeon instances
        for (DungeonInstance instance : instanceManager.getActiveInstances()) {
            if (instance.getState() != DungeonState.IN_USE || instance.getWorld() == null) {
                continue;
            }

            Dungeon dungeon = instance.getDungeon();
            List<Player> instancePlayers = new ArrayList<>();
            for (UUID uuid : instance.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.getWorld().equals(instance.getWorld())) {
                    instancePlayers.add(p);
                }
            }

            if (instancePlayers.isEmpty()) continue;

            // Proximity check for Chest Emergence Cinematic
            if (instance.isAwaitingChestApproach() && !instance.isRewardChestEmerging() && !instance.isRewardChestEmerged()) {
                Location cLoc = instance.getRewardChestLocation();
                if (cLoc != null && cLoc.getWorld() != null) {
                    // Mystical rune energy circling on the ground where the chest will emerge
                    cLoc.getWorld().spawnParticle(Particle.PORTAL, cLoc.clone().add(0.5, 0.15, 0.5), 10, 0.45, 0.05, 0.45, 0.05);
                    cLoc.getWorld().spawnParticle(Particle.FLAME, cLoc.clone().add(0.5, 0.15, 0.5), 2, 0.25, 0.05, 0.25, 0.01);

                    boolean playerNear = false;
                    for (Player p : instancePlayers) {
                        if (p.getLocation().distanceSquared(cLoc) <= 64.0) { // within 8 blocks
                            playerNear = true;
                            break;
                        }
                    }

                    if (playerNear) {
                        instance.setAwaitingChestApproach(false);
                        triggerChestEmergenceCinematic(instance, cLoc);
                    }
                }
            }

            // Highlight last 2 enemies with glowing outline
            int totalAlive = instance.getAliveMobCount();
            boolean fewAlive = totalAlive <= 2 && totalAlive > 0;
            for (UUID mobUuid : instance.getAliveMobUuids()) {
                Entity ent = Bukkit.getEntity(mobUuid);
                if (ent != null && ent.isValid() && !ent.isDead()) {
                    boolean shouldGlow = fewAlive;
                    String spawnerId = instance.getSpawnerIdForMob(mobUuid);
                    if (spawnerId != null) {
                        DungeonInstance.InstanceSpawner is = instance.getInstanceSpawners().get(spawnerId);
                        if (is != null && is.getSpawnerAliveMobCount() <= 2 && is.getSpawnerAliveMobCount() > 0) {
                            shouldGlow = true;
                        }
                    }
                    if (ent.isGlowing() != shouldGlow) {
                        ent.setGlowing(shouldGlow);
                    }
                }
            }

            com.odisseymc.odisseydungueons.model.Difficulty diff = instance.getDifficulty();
            double hpMult = diff.getMobHpMultiplier();
            double dmgMult = diff.getMobDamageMultiplier();

            // Check each spawner inside this running instance
            for (DungeonInstance.InstanceSpawner spawner : instance.getInstanceSpawners().values()) {
                Location spLoc = spawner.getLocation();
                if (spLoc.getWorld() == null) continue;

                // Ambient particle effects around spawner
                if (spawner.getType() == DungeonSpawn.SpawnType.MOB) {
                    spLoc.getWorld().spawnParticle(Particle.FLAME, spLoc.clone().add(0, 0.4, 0), 2, 0.15, 0.15, 0.15, 0.01);
                } else {
                    spLoc.getWorld().spawnParticle(Particle.FLAME, spLoc.clone().add(0, 0.4, 0), 3, 0.2, 0.2, 0.2, 0.02);
                }

                // Check distance to closest player
                double minDistanceSq = Double.MAX_VALUE;
                for (Player player : instancePlayers) {
                    double d2 = player.getLocation().distanceSquared(spLoc);
                    if (d2 < minDistanceSq) {
                        minDistanceSq = d2;
                    }
                }

                boolean inRadius = minDistanceSq <= radiusSq;

                // ==============================================================
                // MOB SPAWNER LOGIC (3 WAVES PER SPAWNER)
                // ==============================================================
                if (spawner.getType() == DungeonSpawn.SpawnType.MOB) {
                    // Proximity activation: Wave 1
                    if (spawner.isArmed() && !spawner.isTriggered() && !spawner.isFullCleared() && inRadius) {
                        spawner.setTriggered(true);
                        spawner.setCurrentWave(1);

                        spLoc.getWorld().spawnParticle(Particle.CLOUD, spLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.05);
                        spLoc.getWorld().playSound(spLoc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.8f);

                        List<MobConfig> wave1Mobs = spawner.getWaveMobs().get(1);
                        if (wave1Mobs == null || wave1Mobs.isEmpty()) {
                            wave1Mobs = spawner.getPendingMobs();
                        }
                        if (wave1Mobs == null || wave1Mobs.isEmpty()) {
                            wave1Mobs = dungeon.getNormalMobs();
                        }

                        int spawnedCount = 0;
                        if (waveManager != null && wave1Mobs != null) {
                            for (MobConfig mc : wave1Mobs) {
                                Location naturalLoc = getNaturalSpawnLocation(spLoc);
                                Entity spawned = waveManager.spawnSingleMob(instance, mc, naturalLoc, hpMult, dmgMult);
                                if (spawned != null) {
                                    instance.registerMobForSpawner(spawned, spawner.getId());
                                    spawnedCount++;
                                }
                            }
                        }
                        if (spawnedCount > 0) {
                            spawner.setWaveMobsSpawned(true);
                        }

                        spawner.setCustomStatus(statusActive);
                    }

                    // Wave advancement (Wave 1 -> Wave 2 -> Wave 3)
                    if (spawner.isTriggered() && !spawner.isFullCleared() && !spawner.isWaveTransitioning()) {
                        if (spawner.isWaveMobsSpawned() && spawner.getSpawnerAliveMobCount() == 0) {
                            if (spawner.getCurrentWave() < spawner.getMaxWaves()) {
                                spawner.setWaveTransitioning(true);
                                spawner.setWaveMobsSpawned(false);
                                int nextWave = spawner.getCurrentWave() + 1;
                                spawner.setCurrentWave(nextWave);

                                spLoc.getWorld().spawnParticle(Particle.CLOUD, spLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.05);
                                spLoc.getWorld().playSound(spLoc, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.8f);

                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    if (instance.getState() != DungeonState.IN_USE || spLoc.getWorld() == null) {
                                        spawner.setWaveTransitioning(false);
                                        return;
                                    }

                                    List<MobConfig> nextMobs = spawner.getWaveMobs().get(nextWave);
                                    if (nextMobs == null || nextMobs.isEmpty()) {
                                        nextMobs = dungeon.getNormalMobs();
                                    }

                                    int nextSpawnedCount = 0;
                                    if (waveManager != null && nextMobs != null) {
                                        for (MobConfig mc : nextMobs) {
                                            Location naturalLoc = getNaturalSpawnLocation(spLoc);
                                            Entity spawned = waveManager.spawnSingleMob(instance, mc, naturalLoc, hpMult, dmgMult);
                                            if (spawned != null) {
                                                instance.registerMobForSpawner(spawned, spawner.getId());
                                                nextSpawnedCount++;
                                            }
                                        }
                                    }
                                    if (nextSpawnedCount > 0) {
                                        spawner.setWaveMobsSpawned(true);
                                    }

                                    spawner.setWaveTransitioning(false);
                                }, 40L);
                            } else {
                                // Spawner fully cleared!
                                spawner.setFullCleared(true);
                                spawner.setCustomStatus(statusCleared);
                                spLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spLoc.clone().add(0, 0.8, 0), 20, 0.4, 0.4, 0.4, 0.05);
                                spLoc.getWorld().playSound(spLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);

                                // If all mob spawners in this instance are cleared, arm the boss spawner!
                                if (instance.areAllMobSpawnersCleared()) {
                                    if (dungeon.getBossSpawn() != null) {
                                        instance.armBossSpawner();
                                        instance.broadcast(messageManager.get("normal-spawners-cleared"));
                                        for (UUID puuid : instance.getPlayers()) {
                                            Player p = Bukkit.getPlayer(puuid);
                                            if (p != null && p.isOnline()) {
                                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                                            }
                                        }
                                    } else {
                                        // No hay spawner de boss: la mazmorra se completa al limpiar las salas
                                        if (instance.getRewardChestLocation() != null) {
                                            instance.setAwaitingChestApproach(true);
                                            instance.broadcast("&6&l★ ¡TODAS LAS SALAS HAN SIDO COMPLETADAS! ★");
                                            instance.broadcast("&e¡Una poderosa vibración ancestral resuena bajo el suelo! &7Acércate a la zona del cofre...");
                                            for (UUID puuid : instance.getPlayers()) {
                                                Player p = Bukkit.getPlayer(puuid);
                                                if (p != null && p.isOnline()) {
                                                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
                                                    p.sendTitle(TextUtil.colorize("&6&l¡EL SUELO RETUMBA!"), TextUtil.colorize("&eAcércate al epicentro de la sala"), 10, 50, 20);
                                                }
                                            }
                                        } else {
                                            instanceManager.handleInstanceVictory(instance);
                                        }
                                    }
                                }
                            }
                        } else if (!spawner.isWaveMobsSpawned() && spawner.getSpawnerAliveMobCount() == 0) {
                            // Reintentar spawn de la oleada actual si aún no ha aparecido ningún mob
                            List<MobConfig> retryMobs = spawner.getWaveMobs().get(spawner.getCurrentWave());
                            if (retryMobs == null || retryMobs.isEmpty()) {
                                retryMobs = dungeon.getNormalMobs();
                            }
                            if (waveManager != null && retryMobs != null) {
                                int retryCount = 0;
                                for (MobConfig mc : retryMobs) {
                                    Location naturalLoc = getNaturalSpawnLocation(spLoc);
                                    Entity spawned = waveManager.spawnSingleMob(instance, mc, naturalLoc, hpMult, dmgMult);
                                    if (spawned != null) {
                                        instance.registerMobForSpawner(spawned, spawner.getId());
                                        retryCount++;
                                    }
                                }
                                if (retryCount > 0) {
                                    spawner.setWaveMobsSpawned(true);
                                }
                            }
                        }
                    }
                }

                // ==============================================================
                // BOSS SPAWNER LOGIC (10 WAVES HORDE)
                // ==============================================================
                if (spawner.getType() == DungeonSpawn.SpawnType.BOSS) {
                    // Proximity activation: Wave 1 of 10
                    if (spawner.isArmed() && !spawner.isTriggered() && !spawner.isFullCleared() && inRadius) {
                        spawner.setTriggered(true);
                        spawner.setCurrentWave(1);

                        spLoc.getWorld().strikeLightningEffect(spLoc);
                        spLoc.getWorld().spawnParticle(Particle.FLAME, spLoc.clone().add(0, 1.0, 0), 40, 0.5, 0.8, 0.5, 0.08);
                        spLoc.getWorld().playSound(spLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                        spLoc.getWorld().playSound(spLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.9f);

                        instance.broadcast(messageManager.get("boss-wave-announcement", Map.of("wave", "1")));

                        List<MobConfig> horde1 = spawner.getWaveMobs().get(1);
                        if (horde1 == null || horde1.isEmpty()) {
                            horde1 = dungeon.getNormalMobs();
                        }

                        int hordeSpawnedCount = 0;
                        if (waveManager != null && horde1 != null) {
                            for (MobConfig mc : horde1) {
                                Location naturalLoc = getNaturalSpawnLocation(spLoc);
                                Entity spawned = waveManager.spawnSingleMob(instance, mc, naturalLoc, hpMult, dmgMult);
                                if (spawned != null) {
                                    instance.registerMobForSpawner(spawned, spawner.getId());
                                    hordeSpawnedCount++;
                                }
                            }
                        }
                        if (hordeSpawnedCount > 0) {
                            spawner.setWaveMobsSpawned(true);
                        }

                        spawner.setCustomStatus(statusBossFighting);
                    }

                    // Boss Horde wave advancement (Wave 1 through 10)
                    if (spawner.isTriggered() && !spawner.isFullCleared() && !spawner.isWaveTransitioning()) {
                        if (spawner.isWaveMobsSpawned() && spawner.getSpawnerAliveMobCount() == 0) {
                            if (spawner.getCurrentWave() < 10) {
                                spawner.setWaveTransitioning(true);
                                spawner.setWaveMobsSpawned(false);
                                int nextWave = spawner.getCurrentWave() + 1;
                                spawner.setCurrentWave(nextWave);

                                instance.broadcast(messageManager.get("boss-wave-cleared", Map.of("wave", String.valueOf(nextWave - 1), "delay", "3")));

                                for (UUID puuid : instance.getPlayers()) {
                                    Player p = Bukkit.getPlayer(puuid);
                                    if (p != null && p.isOnline()) {
                                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                                    }
                                }

                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    if (instance.getState() != DungeonState.IN_USE || spLoc.getWorld() == null) {
                                        spawner.setWaveTransitioning(false);
                                        return;
                                    }

                                    instance.broadcast(messageManager.get("boss-wave-announcement", Map.of("wave", String.valueOf(nextWave))));

                                    List<MobConfig> nextHorde = spawner.getWaveMobs().get(nextWave);
                                    if (nextHorde == null || nextHorde.isEmpty()) {
                                        nextHorde = dungeon.getNormalMobs();
                                    }

                                    double waveHpMult = hpMult * (1.0 + (nextWave - 1) * 0.08);
                                    double waveDmgMult = dmgMult * (1.0 + (nextWave - 1) * 0.05);

                                    spLoc.getWorld().strikeLightningEffect(spLoc);
                                    spLoc.getWorld().playSound(spLoc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);

                                    int nextHordeSpawned = 0;
                                    if (waveManager != null && nextHorde != null) {
                                        for (MobConfig mc : nextHorde) {
                                            Location naturalLoc = getNaturalSpawnLocation(spLoc);
                                            Entity spawned = waveManager.spawnSingleMob(instance, mc, naturalLoc, waveHpMult, waveDmgMult);
                                            if (spawned != null) {
                                                instance.registerMobForSpawner(spawned, spawner.getId());
                                                nextHordeSpawned++;
                                            }
                                        }
                                    }
                                    if (nextHordeSpawned > 0) {
                                        spawner.setWaveMobsSpawned(true);
                                    }

                                    spawner.setWaveTransitioning(false);
                                }, 60L); // 3 seconds delay as advertised in message
                            } else {
                                // ALL 10 WAVES DEFEATED!
                                spawner.setFullCleared(true);
                                spawner.setCustomStatus(statusBossDefeated);

                                instance.broadcast(messageManager.get("boss-horde-defeated"));

                                if (instance.getRewardChestLocation() != null) {
                                    // Awaiting approach to trigger cinematic!
                                    instance.setAwaitingChestApproach(true);
                                    instance.broadcast("&6&l★ ¡LA HORDA FINAL HA SIDO ERRADICADA! ★");
                                    instance.broadcast("&e¡Una poderosa vibración ancestral resuena bajo el suelo! &7Acércate a la zona del cofre...");

                                    for (UUID puuid : instance.getPlayers()) {
                                        Player p = Bukkit.getPlayer(puuid);
                                        if (p != null && p.isOnline()) {
                                            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
                                            p.sendTitle(TextUtil.colorize("&6&l¡EL SUELO RETUMBA!"), TextUtil.colorize("&eAcércate al epicentro de la sala"), 10, 50, 20);
                                        }
                                    }
                                } else {
                                    instanceManager.handleInstanceVictory(instance);
                                }
                            }
                        } else if (!spawner.isWaveMobsSpawned() && spawner.getSpawnerAliveMobCount() == 0) {
                            // Reintentar spawn de la horda actual si aún no ha aparecido ningún mob
                            List<MobConfig> retryHorde = spawner.getWaveMobs().get(spawner.getCurrentWave());
                            if (retryHorde == null || retryHorde.isEmpty()) {
                                retryHorde = dungeon.getNormalMobs();
                            }
                            if (waveManager != null && retryHorde != null) {
                                int retryCount = 0;
                                double retryHpMult = hpMult * (1.0 + (spawner.getCurrentWave() - 1) * 0.08);
                                double retryDmgMult = dmgMult * (1.0 + (spawner.getCurrentWave() - 1) * 0.05);
                                for (MobConfig mc : retryHorde) {
                                    Location naturalLoc = getNaturalSpawnLocation(spLoc);
                                    Entity spawned = waveManager.spawnSingleMob(instance, mc, naturalLoc, retryHpMult, retryDmgMult);
                                    if (spawned != null) {
                                        instance.registerMobForSpawner(spawned, spawner.getId());
                                        retryCount++;
                                    }
                                }
                                if (retryCount > 0) {
                                    spawner.setWaveMobsSpawned(true);
                                }
                            }
                        }
                    }
                }

                // Determine display status for hologram
                String displayStatus = spawner.getCustomStatus();
                if (spawner.getType() == DungeonSpawn.SpawnType.MOB) {
                    if (spawner.isFullCleared()) {
                        displayStatus = statusCleared;
                    } else if (!spawner.isArmed()) {
                        displayStatus = cfg != null ? cfg.getString("spawners.status.idle", "&8[En espera]") : "&8[En espera]";
                    } else if (!spawner.isTriggered()) {
                        displayStatus = statusWaiting;
                    } else {
                        displayStatus = statusActive;
                    }
                } else if (spawner.getType() == DungeonSpawn.SpawnType.BOSS) {
                    if (spawner.isFullCleared()) {
                        displayStatus = statusBossDefeated;
                    } else if (!spawner.isArmed()) {
                        displayStatus = cfg != null ? cfg.getString("spawners.status.boss_waiting_waves", "&8[Limpia las salas previas]") : "&8[Limpia las salas previas]";
                    } else if (!spawner.isTriggered()) {
                        displayStatus = statusBossWaiting;
                    } else {
                        displayStatus = statusBossFighting;
                    }
                }

                // Update spawner hologram text
                if (spawner.getHologram() != null && spawner.getHologram().isValid()) {
                    List<String> template = (spawner.getType() == DungeonSpawn.SpawnType.BOSS) ? bossHoloTemplate : mobHoloTemplate;
                    updateHologram(spawner.getHologram(), template, dungeon, instance, spawner, null, displayStatus);
                }
            }
        }
    }

    public void initializeInstanceSpawners(DungeonInstance instance) {
        if (instance == null || instance.getWorld() == null) return;
        Dungeon dungeon = instance.getDungeon();

        FileConfiguration cfg = configManager.getMainConfig();
        List<String> mobTemplate = cfg != null ? cfg.getStringList("spawners.mob-spawner.hologram-active") : Collections.emptyList();
        List<String> bossTemplate = cfg != null ? cfg.getStringList("spawners.boss-spawner.hologram-active") : Collections.emptyList();
        String initialStatus = cfg != null ? cfg.getString("spawners.status.idle", "&8[En espera]") : "&8[En espera]";

        // 1. Initialize mob spawners
        for (DungeonSpawn spawn : dungeon.getMobSpawns()) {
            Location instLoc = new Location(instance.getWorld(), Math.floor(spawn.getX()) + 0.5, Math.floor(spawn.getY()), Math.floor(spawn.getZ()) + 0.5);
            removeEditorHologramAt(instLoc);
            removeHologramsAt(instLoc);

            DungeonInstance.InstanceSpawner is = new DungeonInstance.InstanceSpawner(spawn.getId(), instLoc, DungeonSpawn.SpawnType.MOB);

            TextDisplay holo = createHologram(instLoc, mobTemplate);
            if (holo != null) {
                updateHologram(holo, mobTemplate, dungeon, instance, is, spawn, initialStatus);
                is.setHologram(holo);
            }
            instance.addInstanceSpawner(is);
        }

        // 2. Initialize boss spawner
        if (dungeon.getBossSpawn() != null) {
            DungeonSpawn bSpawn = dungeon.getBossSpawn();
            Location instLoc = new Location(instance.getWorld(), Math.floor(bSpawn.getX()) + 0.5, Math.floor(bSpawn.getY()), Math.floor(bSpawn.getZ()) + 0.5);
            removeEditorHologramAt(instLoc);
            removeHologramsAt(instLoc);

            DungeonInstance.InstanceSpawner is = new DungeonInstance.InstanceSpawner("boss_spawner", instLoc, DungeonSpawn.SpawnType.BOSS);

            TextDisplay holo = createHologram(instLoc, bossTemplate);
            if (holo != null) {
                String waitWaveStatus = cfg != null ? cfg.getString("spawners.status.boss_waiting_waves", "&8[Limpia las salas previas]") : "&8[Limpia las salas previas]";
                updateHologram(holo, bossTemplate, dungeon, instance, is, bSpawn, waitWaveStatus);
                is.setHologram(holo);
            }
            instance.addInstanceSpawner(is);
        }

        // 3. Initialize reward chest location (hidden until cinematic emergence)
        if (dungeon.getRewardChestSpawn() != null) {
            DungeonSpawn cSpawn = dungeon.getRewardChestSpawn();
            Location instChestLoc = new Location(instance.getWorld(), Math.floor(cSpawn.getX()) + 0.5, Math.floor(cSpawn.getY()), Math.floor(cSpawn.getZ()) + 0.5);
            instance.setRewardChestLocation(instChestLoc);
            removeEditorHologramAt(instChestLoc);
            removeHologramsAt(instChestLoc);

            // Ensure block is AIR in instance world until emerged
            Block chestBlock = instChestLoc.getBlock();
            if (chestBlock.getType() == Material.CHEST) {
                chestBlock.setType(Material.AIR);
            }

            // Create locked chest hologram
            List<String> lockedHolo = cfg != null ? cfg.getStringList("reward-chest.hologram-locked") : Collections.emptyList();
            if (lockedHolo.isEmpty()) {
                lockedHolo = Arrays.asList("&6&l★ COFRE DE RECOMPENSAS ★", "&c&l[BLOQUEADO]", "&7Supera las oleadas para abrirlo");
            }
            TextDisplay lockedHoloDisplay = createHologram(instChestLoc, lockedHolo);
            if (lockedHoloDisplay != null) {
                updateHologram(lockedHoloDisplay, lockedHolo, dungeon, instance, null, "BLOQUEADO");
                instance.setLockedChestHologram(lockedHoloDisplay);
            }
        }

        // 4. Pre-load spawner waves
        double countMult = instance.getDifficulty().getMobCountMultiplier();
        instance.loadSpawnerWaves(dungeon.getWaves(), countMult);
        instance.loadBossHordeWaves(dungeon.getNormalMobs(), countMult, 10);

        // If no mob spawners, arm boss spawner directly
        if (dungeon.getMobSpawns().isEmpty()) {
            instance.armBossSpawner();
        }
    }

    public void triggerChestEmergenceCinematic(DungeonInstance instance, Location chestLoc) {
        if (instance == null || chestLoc == null || chestLoc.getWorld() == null) return;
        if (instance.isRewardChestEmerging() || instance.isRewardChestEmerged()) return;

        instance.setRewardChestEmerging(true);
        World world = chestLoc.getWorld();

        // 1. Zoom effect, ocultar HUD / DisplayTags pasando temporalmente a SPECTATOR (efecto F1 real)
        final java.util.Map<UUID, org.bukkit.GameMode> originalGameModes = new java.util.HashMap<>();
        final java.util.Map<UUID, Float> originalFlySpeeds = new java.util.HashMap<>();
        final java.util.Map<UUID, Location> startPlayerLocs = new java.util.HashMap<>();

        for (UUID uuid : instance.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getWorld().equals(world)) {
                originalGameModes.put(uuid, p.getGameMode());
                originalFlySpeeds.put(uuid, p.getFlySpeed());
                startPlayerLocs.put(uuid, p.getLocation().clone());

                // SPECTATOR oculta hotbar, corazones, nametags y DisplayTags automáticamente como F1
                p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                p.setFlySpeed(0f);
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_DIG, 1.0f, 0.7f);
                p.sendTitle(TextUtil.colorize("&6&l¡ALGO ESTÁ EMERGIENDO!"), TextUtil.colorize("&eEl suelo tiembla bajo tus pies..."), 5, 40, 10);
            }
        }

        // 2. Spawn BlockDisplay underground (-1.6 blocks)
        Location spawnDisplayLoc = chestLoc.clone().add(0.0, -1.6, 0.0);
        BlockDisplay display;
        try {
            display = world.spawn(spawnDisplayLoc, BlockDisplay.class, ent -> {
                ent.setBlock(Bukkit.createBlockData(Material.CHEST));
                ent.setBillboard(Display.Billboard.FIXED);
                ent.setPersistent(false);
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("[OdisseyDungueons] Error al crear BlockDisplay para cofre: " + t.getMessage());
            chestLoc.getBlock().setType(Material.CHEST);
            restorePlayersFromCinematic(instance, originalGameModes, originalFlySpeeds, startPlayerLocs);
            finishChestEmergence(instance, chestLoc, null);
            return;
        }

        instance.setEmergingChestDisplay(display);

        // 3. Smooth rising task over 50 ticks (2.5 seconds)
        final double startY = -1.6;
        final double targetY = 0.0;
        final int totalTicks = 50;

        Bukkit.getScheduler().runTaskTimer(plugin, new Consumer<BukkitTask>() {
            int tickCount = 0;

            @Override
            public void accept(BukkitTask task) {
                if (instance.getState() != DungeonState.IN_USE || display == null || !display.isValid()) {
                    task.cancel();
                    restorePlayersFromCinematic(instance, originalGameModes, originalFlySpeeds, startPlayerLocs);
                    return;
                }

                tickCount++;
                double progress = Math.min(1.0, (double) tickCount / totalTicks);
                // Smooth sine interpolation
                double currentOffset = startY + (targetY - startY) * Math.sin(progress * Math.PI / 2);

                Location currentDisplayLoc = chestLoc.clone().add(0.0, currentOffset, 0.0);
                display.teleport(currentDisplayLoc);

                // Camera tracking: orient players smoothly towards the rising chest and anchor position
                Location focusTarget = chestLoc.clone().add(0.5, currentOffset + 0.5, 0.5);
                for (UUID uuid : instance.getPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && p.getWorld().equals(world)) {
                        Location eye = p.getEyeLocation();
                        Vector dir = focusTarget.toVector().subtract(eye.toVector());
                        if (dir.lengthSquared() > 0.001) {
                            dir.normalize();
                            float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                            float targetPitch = (float) Math.toDegrees(-Math.asin(dir.getY()));
                            Location startP = startPlayerLocs.get(uuid);
                            if (startP != null) {
                                Location cur = startP.clone();
                                cur.setYaw(targetYaw);
                                cur.setPitch(targetPitch);
                                p.teleport(cur);
                            } else {
                                p.setRotation(targetYaw, targetPitch);
                            }
                        }
                    }
                }

                // Earth rumble sounds & debris particles
                if (tickCount % 4 == 0) {
                    world.playSound(chestLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 0.6f);
                    world.playSound(chestLoc, Sound.BLOCK_PISTON_EXTEND, 0.8f, 0.5f);
                    world.spawnParticle(Particle.BLOCK, chestLoc.clone().add(0.5, 0.2, 0.5), 20, 0.4, 0.1, 0.4, Material.STONE.createBlockData());
                    world.spawnParticle(Particle.CLOUD, chestLoc.clone().add(0.5, 0.2, 0.5), 6, 0.3, 0.1, 0.3, 0.03);
                    world.spawnParticle(Particle.FLAME, chestLoc.clone().add(0.5, 0.1, 0.5), 3, 0.25, 0.1, 0.25, 0.02);
                }

                // Completion: full emergence
                if (tickCount >= totalTicks) {
                    task.cancel();
                    restorePlayersFromCinematic(instance, originalGameModes, originalFlySpeeds, startPlayerLocs);
                    finishChestEmergence(instance, chestLoc, display);
                }
            }
        }, 1L, 1L);
    }

    /**
     * Restaura el modo de juego, velocidad y posición de los jugadores al terminar la cinemática.
     */
    private void restorePlayersFromCinematic(DungeonInstance instance,
                                            java.util.Map<UUID, org.bukkit.GameMode> originalGameModes,
                                            java.util.Map<UUID, Float> originalFlySpeeds,
                                            java.util.Map<UUID, Location> startPlayerLocs) {
        for (UUID uuid : instance.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            org.bukkit.GameMode gm = originalGameModes.getOrDefault(uuid, org.bukkit.GameMode.SURVIVAL);
            float fs = originalFlySpeeds.getOrDefault(uuid, 0.1f);
            p.setGameMode(gm);
            p.setFlySpeed(fs);
            Location start = startPlayerLocs.get(uuid);
            if (start != null) p.teleport(start);
        }
    }

    private void finishChestEmergence(DungeonInstance instance, Location chestLoc, BlockDisplay display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
        instance.setEmergingChestDisplay(null);

        // Solidify real chest
        chestLoc.getBlock().setType(Material.CHEST);

        World world = chestLoc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, chestLoc.clone().add(0.5, 0.5, 0.5), 60, 0.6, 0.6, 0.6, 0.15);
            world.spawnParticle(Particle.EXPLOSION, chestLoc.clone().add(0.5, 0.8, 0.5), 3, 0.1, 0.1, 0.1, 0.0);
            world.playSound(chestLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            world.playSound(chestLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }

        instance.setRewardChestEmerging(false);
        instance.setRewardChestEmerged(true);
        instance.setRewardChestUnlocked(true);

        // Remove locked chest hologram before spawning unlocked one
        TextDisplay lockedHolo = instance.getLockedChestHologram();
        if (lockedHolo != null && lockedHolo.isValid()) {
            lockedHolo.remove();
            instance.setLockedChestHologram(null);
        }

        // Spawn Unlocked Hologram
        FileConfiguration cfg = configManager.getMainConfig();
        List<String> unlockedHolo = cfg != null ? cfg.getStringList("reward-chest.hologram-unlocked") : Collections.emptyList();
        if (unlockedHolo.isEmpty()) {
            unlockedHolo = Arrays.asList("&6&l★ COFRE DE RECOMPENSAS ANCESTRAL ★", "&a&l[¡DESBLOQUEADO!]", "&e¡Haz clic derecho para reclamar tu botín!");
        }
        removeHologramsAt(chestLoc);
        TextDisplay holo = createHologram(chestLoc, unlockedHolo);
        if (holo != null) {
            updateHologram(holo, unlockedHolo, instance.getDungeon(), instance, null, "DESBLOQUEADO");
            instance.setRewardChestHologram(holo);
        }

        // Release players
        for (UUID uuid : instance.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.sendTitle(TextUtil.colorize("&6&l¡COFRE ANCESTRAL DESBLOQUEADO!"), TextUtil.colorize("&e¡Haz clic derecho para reclamar tu botín!"), 10, 60, 20);
            }
        }

        instance.broadcast(messageManager.get("reward-chest-unlocked-broadcast"));

        // Trigger dungeon victory
        instanceManager.handleInstanceVictory(instance);
    }

    public void playTestChestCinematic(Player player) {
        if (player == null || !player.isOnline()) return;

        if (activeCinematicPlayers.contains(player.getUniqueId())) {
            player.sendMessage(TextUtil.colorize("&cYa tienes una prueba de cinemática en ejecución."));
            return;
        }
        activeCinematicPlayers.add(player.getUniqueId());

        World world = player.getWorld();
        Location pLoc = player.getLocation();
        Vector dir = pLoc.getDirection().setY(0);
        if (dir.lengthSquared() < 0.001) {
            dir = new Vector(0, 0, 1);
        } else {
            dir.normalize();
        }

        Location chestLoc = pLoc.clone().add(dir.multiply(2.5));
        chestLoc.setY(pLoc.getY());
        chestLoc = new Location(world, chestLoc.getBlockX() + 0.5, chestLoc.getBlockY(), chestLoc.getBlockZ() + 0.5);

        player.sendMessage(TextUtil.colorize("&6&l[ODISSEY DUNGUEONS] &eIniciando prueba de cinemática del cofre ancestral..."));

        final org.bukkit.GameMode originalGameMode = player.getGameMode();
        final float originalFlySpeed = player.getFlySpeed();
        final Location startLoc = player.getLocation().clone();

        // Modo espectador oculta toda la interfaz (HUD, hotbar, corazones) y nametags como F1
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.setFlySpeed(0f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_DIG, 1.0f, 0.7f);
        player.sendTitle(TextUtil.colorize("&6&l¡PRUEBA DE CINEMÁTICA!"), TextUtil.colorize("&eEl suelo tiembla bajo tus pies..."), 5, 40, 10);

        Location spawnDisplayLoc = chestLoc.clone().add(0.0, -1.6, 0.0);
        BlockDisplay display;
        try {
            display = world.spawn(spawnDisplayLoc, BlockDisplay.class, ent -> {
                ent.setBlock(Bukkit.createBlockData(Material.CHEST));
                ent.setBillboard(Display.Billboard.FIXED);
                ent.setPersistent(false);
            });
        } catch (Throwable t) {
            player.sendMessage(TextUtil.colorize("&cError al generar BlockDisplay: " + t.getMessage()));
            player.setGameMode(originalGameMode);
            player.setFlySpeed(originalFlySpeed);
            activeCinematicPlayers.remove(player.getUniqueId());
            return;
        }

        // Ocultar TextDisplays cercanas como seguridad extra
        final List<org.bukkit.entity.Entity> hiddenEntities = new ArrayList<>();
        for (org.bukkit.entity.Entity nearby : world.getNearbyEntities(player.getLocation(), 25, 25, 25)) {
            if (nearby instanceof TextDisplay && nearby.getEntityId() != display.getEntityId()) {
                player.hideEntity(plugin, nearby);
                hiddenEntities.add(nearby);
            }
        }

        final double startY = -1.6;
        final double targetY = 0.0;
        final int totalTicks = 50;

        final Location finalChestLoc = chestLoc;
        Bukkit.getScheduler().runTaskTimer(plugin, new Consumer<BukkitTask>() {
            int tickCount = 0;

            @Override
            public void accept(BukkitTask task) {
                if (!player.isOnline() || display == null || !display.isValid()) {
                    task.cancel();
                    activeCinematicPlayers.remove(player.getUniqueId());
                    if (display != null && display.isValid()) display.remove();
                    if (player.isOnline()) {
                        player.setGameMode(originalGameMode);
                        player.setFlySpeed(originalFlySpeed);
                        player.teleport(startLoc);
                    }
                    // Restaurar entidades ocultas
                    for (org.bukkit.entity.Entity e : hiddenEntities) {
                        if (e.isValid()) player.showEntity(plugin, e);
                    }
                    return;
                }

                tickCount++;
                double progress = Math.min(1.0, (double) tickCount / totalTicks);
                double currentOffset = startY + (targetY - startY) * Math.sin(progress * Math.PI / 2);

                Location currentDisplayLoc = finalChestLoc.clone().add(0.0, currentOffset, 0.0);
                display.teleport(currentDisplayLoc);

                Location focusTarget = finalChestLoc.clone().add(0.5, currentOffset + 0.5, 0.5);
                Location eye = player.getEyeLocation();
                Vector lookDir = focusTarget.toVector().subtract(eye.toVector());
                if (lookDir.lengthSquared() > 0.001) {
                    lookDir.normalize();
                    float targetYaw = (float) Math.toDegrees(Math.atan2(-lookDir.getX(), lookDir.getZ()));
                    float targetPitch = (float) Math.toDegrees(-Math.asin(lookDir.getY()));
                    Location cur = startLoc.clone();
                    cur.setYaw(targetYaw);
                    cur.setPitch(targetPitch);
                    player.teleport(cur);
                }

                if (tickCount % 4 == 0) {
                    world.playSound(finalChestLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 0.6f);
                    world.playSound(finalChestLoc, Sound.BLOCK_PISTON_EXTEND, 0.8f, 0.5f);
                    world.spawnParticle(Particle.BLOCK, finalChestLoc.clone().add(0.5, 0.2, 0.5), 20, 0.4, 0.1, 0.4, Material.STONE.createBlockData());
                    world.spawnParticle(Particle.CLOUD, finalChestLoc.clone().add(0.5, 0.2, 0.5), 6, 0.3, 0.1, 0.3, 0.03);
                    world.spawnParticle(Particle.FLAME, finalChestLoc.clone().add(0.5, 0.1, 0.5), 3, 0.25, 0.1, 0.25, 0.02);
                }

                if (tickCount >= totalTicks) {
                    task.cancel();

                    // Restaurar modo de juego original al jugador
                    player.setGameMode(originalGameMode);
                    player.setFlySpeed(originalFlySpeed);
                    player.teleport(startLoc);

                    // Partículas y sonidos de finalización
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, finalChestLoc.clone().add(0.5, 0.5, 0.5), 60, 0.6, 0.6, 0.6, 0.15);
                    world.spawnParticle(Particle.EXPLOSION, finalChestLoc.clone().add(0.5, 0.8, 0.5), 3, 0.1, 0.1, 0.1, 0.0);
                    world.playSound(finalChestLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    world.playSound(finalChestLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

                    player.sendTitle(TextUtil.colorize("&6&l¡COFRE ANCESTRAL!"), TextUtil.colorize("&a[Demostración — desaparecerá en 5s]"), 10, 80, 20);

                    List<String> previewHolo = Arrays.asList(
                            "&6&l★ COFRE ANCESTRAL (DEMO) ★",
                            "&a&l[¡DESBLOQUEADO!]",
                            "&7Desaparecerá en 5 segundos..."
                    );
                    TextDisplay holo = createHologram(finalChestLoc, previewHolo);

                    // Restaurar entidades ocultas
                    for (org.bukkit.entity.Entity e : hiddenEntities) {
                        if (e.isValid()) player.showEntity(plugin, e);
                    }

                    // Esperar 5 segundos (100 ticks) y ENTONCES eliminar el cofre y el holograma
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        activeCinematicPlayers.remove(player.getUniqueId());
                        if (display != null && display.isValid()) display.remove();
                        if (holo != null && holo.isValid()) holo.remove();
                        if (player.isOnline()) {
                            player.sendTitle(TextUtil.colorize(""), TextUtil.colorize(""), 0, 1, 5);
                            player.sendMessage(TextUtil.colorize("&a&l[ODISSEY DUNGUEONS] &aLa animación de prueba finalizó correctamente."));
                            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.5f);
                        }
                    }, 100L); // 5 segundos
                }
            }
        }, 1L, 1L);
    }
}
