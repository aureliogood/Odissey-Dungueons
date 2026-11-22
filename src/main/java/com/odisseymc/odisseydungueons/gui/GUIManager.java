package com.odisseymc.odisseydungueons.gui;

import com.odisseymc.odisseydungueons.config.ConfigManager;
import com.odisseymc.odisseydungueons.config.MessageManager;
import com.odisseymc.odisseydungueons.instance.InstanceManager;
import com.odisseymc.odisseydungueons.model.Difficulty;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.model.DungeonState;
import com.odisseymc.odisseydungueons.setup.SpawnerManager;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Manages dynamic and configurable YAML-driven GUI menus, click events, and administrative tool distribution.
 */
public class GUIManager implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Supplier<Collection<Dungeon>> dungeonSupplier;
    private SpawnerManager spawnerManager;
    private InstanceManager instanceManager;

    public GUIManager(JavaPlugin plugin, ConfigManager configManager, MessageManager messageManager, Supplier<Collection<Dungeon>> dungeonSupplier) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.dungeonSupplier = dungeonSupplier;
    }

    public GUIManager(JavaPlugin plugin, ConfigManager configManager, Supplier<Collection<Dungeon>> dungeonSupplier) {
        this(plugin, configManager, null, dungeonSupplier);
    }

    public void setSpawnerManager(SpawnerManager spawnerManager) {
        this.spawnerManager = spawnerManager;
    }

    public void setInstanceManager(InstanceManager instanceManager) {
        this.instanceManager = instanceManager;
    }

    private FileConfiguration loadGuiConfig(String name) {
        File file = new File(configManager.getGuisFolder(), name);
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        return new YamlConfiguration();
    }

    public void openMainMenu(Player player) {
        FileConfiguration config = loadGuiConfig("main-menu.yml");
        String title = TextUtil.colorize(config.getString("title", "&b&lODISSEY DUNGUEONS"));
        int rows = config.getInt("rows", 4);
        int size = Math.max(9, Math.min(54, rows * 9));

        CustomHolder holder = new CustomHolder("MAIN", null);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // Fill background if configured
        String bgMatName = config.getString("background.material", "BLACK_STAINED_GLASS_PANE");
        Material bgMat = Material.matchMaterial(bgMatName);
        if (bgMat != null) {
            ItemStack bg = createSimpleItem(bgMat, " ", Collections.emptyList());
            for (int i = 0; i < size; i++) {
                inv.setItem(i, bg);
            }
        }

        Collection<Dungeon> dungeons = dungeonSupplier.get();
        if (dungeons.isEmpty()) {
            ItemStack emptyItem = createSimpleItem(Material.BARRIER, "&c&lNo hay mazmorras creadas",
                    Arrays.asList("&7Usa &f/dungeons create <nombre>", "&7para crear la primera mazmorra."));
            inv.setItem(size / 2, emptyItem);
            player.openInventory(inv);
            return;
        }

        int slot = 10;
        for (Dungeon dungeon : dungeons) {
            if (slot >= size - 9) break;
            if ((slot % 9) == 8) slot += 2; // skip borders

            Material iconMat = dungeon.isEnabled() ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
            String status = dungeon.isEnabled() ?
                    (dungeon.isOnCooldown() ? "&eEn Cooldown (&f" + dungeon.getFormattedCooldown() + "&e)" : "&aDisponible") :
                    "&8Desactivada";

            List<String> lore = new ArrayList<>();
            lore.add("&7Estado: " + status);
            lore.add("&7Cooldown base: &f" + TextUtil.formatDuration(dungeon.getCooldownSeconds()));
            lore.add("&7Duración: &f" + TextUtil.formatDuration(dungeon.getDurationSeconds()));
            lore.add("&7Jugadores: &f" + dungeon.getMinPlayers() + " - " + dungeon.getMaxPlayers());
            lore.add("");
            for (String desc : dungeon.getDescription()) {
                lore.add(TextUtil.colorize(desc));
            }
            lore.add("");
            if (player.hasPermission("odisseydungueons.admin")) {
                lore.add("&eClic Izquierdo para configurar");
                lore.add("&aClic Derecho para jugar");
            } else {
                lore.add("&aClic para entrar");
            }

            ItemStack dItem = createSimpleItem(iconMat, dungeon.getDisplayName(), lore);
            inv.setItem(slot, dItem);
            holder.setAction(slot, "DUNGEON_" + dungeon.getName());
            slot++;
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    public List<String> getAvailableWorldNames() {
        Set<String> set = new LinkedHashSet<>();
        for (World w : Bukkit.getWorlds()) {
            String name = w.getName();
            if (!name.startsWith("instances/") && !name.contains("inst_")) {
                set.add(name);
            }
        }
        File worldContainer = Bukkit.getWorldContainer();
        if (worldContainer != null && worldContainer.isDirectory()) {
            File[] subdirs = worldContainer.listFiles(File::isDirectory);
            if (subdirs != null) {
                for (File d : subdirs) {
                    String name = d.getName();
                    if (!name.equalsIgnoreCase("instances") && !name.startsWith("inst_") && !set.contains(name)) {
                        if (new File(d, "level.dat").exists()) {
                            set.add(name);
                        }
                    }
                }
            }
        }
        if (set.isEmpty()) {
            set.add("world");
        }
        return new ArrayList<>(set);
    }

    public void openDungeonConfigMenu(Player player, Dungeon dungeon) {
        openDungeonConfigMenu(player, dungeon, -1);
    }

    public void openDungeonConfigMenu(Player player, Dungeon dungeon, int worldBrowsingIndex) {
        if (dungeon == null) return;

        FileConfiguration config = loadGuiConfig("dungeon-menu.yml");
        String title = TextUtil.colorize(config.getString("title", "&8Config: " + dungeon.getName()));
        int size = 36;

        CustomHolder holder = new CustomHolder("CONFIG", dungeon);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // Fill decorative frame
        ItemStack gray = createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < size; i++) {
            inv.setItem(i, gray);
        }

        // 1. Cooldown
        inv.setItem(10, createSimpleItem(Material.CLOCK, "&e&lTiempo de Cooldown", Arrays.asList(
                "&7Actual: &f" + TextUtil.formatDuration(dungeon.getCooldownSeconds()),
                "",
                "&aClic Izq: &f+15 minutos",
                "&cClic Der: &f-15 minutos"
        )));
        holder.setAction(10, "TOGGLE_COOLDOWN");

        // 2. Duration
        inv.setItem(11, createSimpleItem(Material.COMPASS, "&6&lTiempo de Duración", Arrays.asList(
                "&7Actual: &f" + TextUtil.formatDuration(dungeon.getDurationSeconds()),
                "",
                "&aClic Izq: &f+1 minuto",
                "&cClic Der: &f-1 minuto"
        )));
        holder.setAction(11, "TOGGLE_DURATION");

        // 3. Difficulty
        inv.setItem(12, createSimpleItem(Material.NETHER_STAR, "&d&lConfigurar Dificultad", Arrays.asList(
                "&7Ajusta la dificultad y multiplicadores",
                "&eClic para abrir menú de dificultad"
        )));
        holder.setAction(12, "OPEN_DIFFICULTY");

        // 4. Max Players
        inv.setItem(13, createSimpleItem(Material.PLAYER_HEAD, "&b&lCantidad de Jugadores", Arrays.asList(
                "&7Máximo actual: &f" + dungeon.getMaxPlayers(),
                "&7Mínimo actual: &f" + dungeon.getMinPlayers(),
                "",
                "&aClic Izq: &f+1 jugador máx",
                "&cClic Der: &f-1 jugador máx"
        )));
        holder.setAction(13, "TOGGLE_PLAYERS");

        // 5. Normal Mobs
        inv.setItem(14, createSimpleItem(Material.ZOMBIE_HEAD, "&2&lMobs Normales", Arrays.asList(
                "&7Total configurados: &f" + dungeon.getNormalMobs().size(),
                "&eClic para ver detalles"
        )));
        holder.setAction(14, "OPEN_MOBS");

        // 6. Boss
        inv.setItem(15, createSimpleItem(Material.WITHER_SKELETON_SKULL, "&c&lJefe Final", Arrays.asList(
                "&7Nombre: &f" + (dungeon.getBossConfig() != null ? dungeon.getBossConfig().getName() : "&cNinguno"),
                "&7Vida: &f" + (dungeon.getBossConfig() != null ? dungeon.getBossConfig().getHealth() : 0) + " HP",
                "&eClic para ver detalles"
        )));
        holder.setAction(15, "OPEN_BOSS");

        // 7. Rewards & Reward Chest
        boolean chestPlaced = dungeon.getRewardChestSpawn() != null;
        inv.setItem(16, createSimpleItem(Material.CHEST, "&6&lCofre de Recompensas", Arrays.asList(
                "&7Estado: " + (chestPlaced ? "&a✔ Colocado en el mapa" : "&c✘ No colocado"),
                "&7Recompensas configuradas: &f" + dungeon.getRewards().size(),
                "",
                "&aClic Izquierdo: &fVer y editar recompensas",
                "&eClic Derecho: &fObtener cofre para colocar"
        )));
        holder.setAction(16, "REWARD_CHEST_ACTION");

        // 8. Mob Spawner
        inv.setItem(19, createSimpleItem(Material.SPAWNER, "&b&lSpawner de Mobs Normales", Arrays.asList(
                "&7Obtener bloque de spawner para colocar en salas.",
                "&7Invocará monstruos cuando los jugadores se acerquen.",
                "",
                "&eClic para recibir el spawner"
        )));
        holder.setAction(19, "GIVE_MOB_SPAWNER");

        // 9. Boss Spawner
        inv.setItem(20, createSimpleItem(Material.SPAWNER, "&c&lSpawner de Jefe Final", Arrays.asList(
                "&7Obtener bloque de spawner para el Boss final.",
                "&7El Boss NO aparecerá hasta que lleguen a la sala.",
                "",
                "&eClic para recibir el spawner"
        )));
        holder.setAction(20, "GIVE_BOSS_SPAWNER");

        // 10. Enable/Disable toggle
        Material toggleMat = dungeon.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE;
        String toggleName = dungeon.isEnabled() ? "&a&lDungeon Activada" : "&c&lDungeon Desactivada";
        inv.setItem(21, createSimpleItem(toggleMat, toggleName, Arrays.asList("&eClic para alternar estado")));
        holder.setAction(21, "TOGGLE_ENABLED");

        // 11. World Selector
        List<String> worlds = getAvailableWorldNames();
        int activeBrowsing = worldBrowsingIndex;
        if (activeBrowsing < 0 || activeBrowsing >= worlds.size()) {
            activeBrowsing = 0;
            if (dungeon.getWorldName() != null) {
                for (int i = 0; i < worlds.size(); i++) {
                    if (worlds.get(i).equalsIgnoreCase(dungeon.getWorldName())) {
                        activeBrowsing = i;
                        break;
                    }
                }
            }
        }
        holder.setWorldBrowsingIndex(activeBrowsing);

        String currentAssignedWorld = dungeon.getWorldName() != null ? dungeon.getWorldName() : "&c&nSIN ASIGNAR (Requerido)";
        String browsingWorld = worlds.get(activeBrowsing);

        List<String> worldLore = new ArrayList<>();
        worldLore.add("&7Mundo asignado actual: &a&l" + currentAssignedWorld);
        worldLore.add("&7Mundo en selección: &e&l" + browsingWorld);
        worldLore.add("&8&m----------------------------------------");
        worldLore.add("&6&lMundos disponibles en el servidor (" + worlds.size() + "):");

        for (int i = 0; i < worlds.size(); i++) {
            String w = worlds.get(i);
            boolean isCurrent = dungeon.getWorldName() != null && w.equalsIgnoreCase(dungeon.getWorldName());
            boolean isBrowsing = (i == activeBrowsing);

            if (isBrowsing && isCurrent) {
                worldLore.add("&a  ▶ &f" + w + " &a✔ &8[&fACTUAL & SELECCIONADO&8]");
            } else if (isBrowsing) {
                worldLore.add("&e  ▶ &e" + w + " &6◀ &8[&eSELECCIONANDO...&8]");
            } else if (isCurrent) {
                worldLore.add("&2    ✔ &7" + w + " &8[&aACTUAL&8]");
            } else {
                worldLore.add("&8    • &7" + w);
            }
        }

        worldLore.add("&8&m----------------------------------------");
        worldLore.add("&a&lClic Izquierdo: &fCambiar al siguiente mundo");
        worldLore.add("&e&lClic Derecho: &aConfirmar y asignar &f" + browsingWorld);

        inv.setItem(22, createSimpleItem(Material.FILLED_MAP, "&6&lMundo de Mazmorra: &e" + browsingWorld, worldLore));
        holder.setAction(22, "CYCLE_WORLD");

        // 12. Test Mode
        inv.setItem(23, createSimpleItem(Material.EMERALD_BLOCK, "&a&lIniciar Modo de Prueba", Arrays.asList(
                "&7Entra a una instancia de prueba",
                "&7No altera cooldowns ni recompensas",
                "&eClic para iniciar"
        )));
        holder.setAction(23, "START_TEST");

        // 13. Preview Chest Cinematic
        inv.setItem(24, createSimpleItem(Material.CHEST_MINECART, "&6&l★ Probar Cinemática del Cofre ★", Arrays.asList(
                "&7Ejecuta una demostración de la cinemática",
                "&7ancestral en tu posición actual.",
                "&7El cofre emergerá de la tierra y al terminar",
                "&7todo volverá a la normalidad sin romper bloques.",
                "",
                "&eClic para probar animación"
        )));
        holder.setAction(24, "TEST_CHEST_CINEMATIC");

        // Back button
        inv.setItem(31, createSimpleItem(Material.ARROW, "&c&lVolver al Menú Principal", Collections.emptyList()));
        holder.setAction(31, "BACK_MAIN");

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
    }

    public void openDifficultyMenu(Player player, Dungeon dungeon) {
        if (dungeon == null) return;
        CustomHolder holder = new CustomHolder("DIFFICULTY", dungeon);
        Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.colorize("&8Dificultad: " + dungeon.getName()));
        holder.setInventory(inv);

        // NORMAL
        inv.setItem(11, createSimpleItem(Material.LIME_CONCRETE, Difficulty.NORMAL.getDisplayName(), Arrays.asList(
                "&7Multiplicador de vida mobs: &a1.0x",
                "&7Multiplicador de daño mobs: &a1.0x",
                "&7Multiplicador de boss: &a1.0x",
                "",
                "&eClic para iniciar en esta dificultad"
        )));
        holder.setAction(11, "START_NORMAL");

        // HARD
        inv.setItem(13, createSimpleItem(Material.YELLOW_CONCRETE, Difficulty.DIFICIL.getDisplayName(), Arrays.asList(
                "&7Multiplicador de vida mobs: &e1.5x",
                "&7Multiplicador de daño mobs: &e1.3x",
                "&7Multiplicador de boss: &e1.5x",
                "",
                "&eClic para iniciar en esta dificultad"
        )));
        holder.setAction(13, "START_HARD");

        // NIGHTMARE
        inv.setItem(15, createSimpleItem(Material.RED_CONCRETE, Difficulty.PESADILLA.getDisplayName(), Arrays.asList(
                "&7Multiplicador de vida mobs: &c2.2x",
                "&7Multiplicador de daño mobs: &c1.8x",
                "&7Multiplicador de boss: &c2.5x",
                "",
                "&eClic para iniciar en esta dificultad"
        )));
        holder.setAction(15, "START_NIGHTMARE");

        inv.setItem(22, createSimpleItem(Material.ARROW, "&c&lVolver", Collections.emptyList()));
        holder.setAction(22, "BACK_CONFIG");

        player.openInventory(inv);
    }

    public void openRewardsMenu(Player player, Dungeon dungeon) {
        if (dungeon == null) return;
        CustomHolder holder = new CustomHolder("REWARDS", dungeon);
        Inventory inv = Bukkit.createInventory(holder, 36, TextUtil.colorize("&8Recompensas: " + dungeon.getName()));
        holder.setInventory(inv);

        // Fill background
        ItemStack gray = createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, gray);
        }

        List<com.odisseymc.odisseydungueons.model.RewardItem> rewards = dungeon.getRewards();
        if (rewards.isEmpty()) {
            inv.setItem(13, createSimpleItem(Material.BARRIER, "&c&lSin Recompensas Globales", Arrays.asList(
                    "&7Esta mazmorra no tiene recompensas configuradas.",
                    "&7Puedes añadirlas en el archivo &f" + dungeon.getName().toLowerCase() + ".yml"
            )));
        } else {
            int slot = 10;
            for (com.odisseymc.odisseydungueons.model.RewardItem r : rewards) {
                if (slot > 25) break;
                if ((slot % 9) == 8) slot += 2;

                Material mat = r.getMaterial() != null ? r.getMaterial() : Material.GOLD_INGOT;
                String name = !r.getDisplayName().isEmpty() ? r.getDisplayName() : "&e" + r.getId();

                List<String> lore = new ArrayList<>();
                lore.add("&7Tipo: &b" + r.getType().name());
                lore.add("&7Valor: &f" + r.getValue());
                lore.add("&7Cantidad: &f" + r.getAmount());
                lore.add("&7Probabilidad: &a" + r.getChancePercentage() + "%");
                if (r.getLore() != null && !r.getLore().isEmpty()) {
                    lore.add("&8&m-----------------");
                    for (String l : r.getLore()) {
                        lore.add(TextUtil.colorize(l));
                    }
                }

                inv.setItem(slot, createSimpleItem(mat, name, lore));
                slot++;
            }
        }

        // Back button
        inv.setItem(31, createSimpleItem(Material.ARROW, "&c&lVolver a Configuración", Collections.emptyList()));
        holder.setAction(31, "BACK_CONFIG");

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CustomHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) return;

        String action = holder.getAction(rawSlot);
        if (action == null) return;

        Dungeon dungeon = holder.getDungeon();

        if (action.startsWith("DUNGEON_")) {
            String dName = action.replace("DUNGEON_", "");
            Dungeon clickedDungeon = findDungeon(dName);
            if (clickedDungeon != null) {
                if (event.getClick() == ClickType.RIGHT || !player.hasPermission("odisseydungueons.admin")) {
                    openDifficultyMenu(player, clickedDungeon);
                } else {
                    openDungeonConfigMenu(player, clickedDungeon);
                }
            }
            return;
        }

        if (dungeon == null) {
            if ("BACK_MAIN".equalsIgnoreCase(action)) {
                openMainMenu(player);
            }
            return;
        }

        switch (action) {
            case "TOGGLE_COOLDOWN":
                if (event.getClick() == ClickType.LEFT) {
                    dungeon.setCooldownSeconds(dungeon.getCooldownSeconds() + 900); // +15 min
                } else if (event.getClick() == ClickType.RIGHT) {
                    dungeon.setCooldownSeconds(Math.max(0, dungeon.getCooldownSeconds() - 900));
                }
                configManager.saveDungeon(dungeon);
                openDungeonConfigMenu(player, dungeon);
                break;

            case "TOGGLE_DURATION":
                if (event.getClick() == ClickType.LEFT) {
                    dungeon.setDurationSeconds(dungeon.getDurationSeconds() + 60); // +1 min
                } else if (event.getClick() == ClickType.RIGHT) {
                    dungeon.setDurationSeconds(Math.max(60, dungeon.getDurationSeconds() - 60));
                }
                configManager.saveDungeon(dungeon);
                openDungeonConfigMenu(player, dungeon);
                break;

            case "TOGGLE_PLAYERS":
                if (event.getClick() == ClickType.LEFT) {
                    dungeon.setMaxPlayers(dungeon.getMaxPlayers() + 1);
                } else if (event.getClick() == ClickType.RIGHT) {
                    dungeon.setMaxPlayers(Math.max(1, dungeon.getMaxPlayers() - 1));
                }
                configManager.saveDungeon(dungeon);
                openDungeonConfigMenu(player, dungeon);
                break;

            case "TOGGLE_ENABLED":
                dungeon.setEnabled(!dungeon.isEnabled());
                configManager.saveDungeon(dungeon);
                openDungeonConfigMenu(player, dungeon);
                break;

            case "CYCLE_WORLD":
                List<String> worldList = getAvailableWorldNames();
                if (worldList.isEmpty()) return;

                int currentIdx = holder.getWorldBrowsingIndex();
                if (currentIdx < 0 || currentIdx >= worldList.size()) currentIdx = 0;

                if (event.getClick() == ClickType.LEFT) {
                    int nextIdx = (currentIdx + 1) % worldList.size();
                    holder.setWorldBrowsingIndex(nextIdx);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
                    openDungeonConfigMenu(player, dungeon, nextIdx);
                } else if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    String chosenWorld = worldList.get(currentIdx);
                    dungeon.setWorldName(chosenWorld);
                    if (dungeon.getPlayerSpawn() != null) {
                        dungeon.getPlayerSpawn().setWorldName(chosenWorld);
                    }
                    configManager.saveDungeon(dungeon);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    player.sendMessage(TextUtil.colorize("&a&l[ODISSEY DUNGUEONS] &aMundo de la mazmorra &f" + dungeon.getName() + " &aestablecido a: &e" + chosenWorld));
                    openDungeonConfigMenu(player, dungeon, currentIdx);
                }
                break;

            case "OPEN_DIFFICULTY":
                openDifficultyMenu(player, dungeon);
                break;

            case "REWARD_CHEST_ACTION":
            case "OPEN_REWARDS":
                if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    if (spawnerManager != null) {
                        player.getInventory().addItem(spawnerManager.createRewardChest(dungeon.getName()));
                        if (messageManager != null) {
                            messageManager.send(player, "reward-chest-received", Map.of("dungeon", dungeon.getName()));
                        } else {
                            player.sendMessage(TextUtil.colorize("&6&l[ODISSEY DUNGUEONS] &6Has recibido el Cofre de Recompensas para: &f" + dungeon.getName()));
                        }
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                    }
                } else {
                    openRewardsMenu(player, dungeon);
                }
                break;

            case "GIVE_MOB_SPAWNER":
            case "GIVE_MOB_WAND":
                if (spawnerManager != null) {
                    player.getInventory().addItem(spawnerManager.createMobSpawner(dungeon.getName()));
                    player.sendMessage(TextUtil.colorize("&a&l[ODISSEY DUNGUEONS] &aHas recibido el Spawner de Mobs para: &f" + dungeon.getName()));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                }
                break;

            case "GIVE_BOSS_SPAWNER":
            case "GIVE_BOSS_WAND":
                if (spawnerManager != null) {
                    player.getInventory().addItem(spawnerManager.createBossSpawner(dungeon.getName()));
                    player.sendMessage(TextUtil.colorize("&c&l[ODISSEY DUNGUEONS] &cHas recibido el Spawner del Jefe Final para: &f" + dungeon.getName()));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                }
                break;

            case "START_TEST":
                player.closeInventory();
                if (instanceManager != null) {
                    instanceManager.startInstance(dungeon, Difficulty.NORMAL, Collections.singletonList(player), true);
                }
                break;

            case "TEST_CHEST_CINEMATIC":
                player.closeInventory();
                if (spawnerManager != null) {
                    spawnerManager.playTestChestCinematic(player);
                }
                break;

            case "START_NORMAL":
                player.closeInventory();
                if (instanceManager != null) {
                    instanceManager.startInstance(dungeon, Difficulty.NORMAL, Collections.singletonList(player), false);
                }
                break;

            case "START_HARD":
                player.closeInventory();
                if (instanceManager != null) {
                    instanceManager.startInstance(dungeon, Difficulty.DIFICIL, Collections.singletonList(player), false);
                }
                break;

            case "START_NIGHTMARE":
                player.closeInventory();
                if (instanceManager != null) {
                    instanceManager.startInstance(dungeon, Difficulty.PESADILLA, Collections.singletonList(player), false);
                }
                break;

            case "BACK_MAIN":
                openMainMenu(player);
                break;

            case "BACK_CONFIG":
                openDungeonConfigMenu(player, dungeon);
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CustomHolder) {
            event.setCancelled(true);
        }
    }

    private Dungeon findDungeon(String name) {
        for (Dungeon d : dungeonSupplier.get()) {
            if (d.getName().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    private ItemStack createSimpleItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.colorize(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(TextUtil::colorize).collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
