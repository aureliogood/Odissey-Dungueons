package com.odisseymc.odisseydungueons.commands;

import com.odisseymc.odisseydungueons.config.ConfigManager;
import com.odisseymc.odisseydungueons.config.MessageManager;
import com.odisseymc.odisseydungueons.gui.GUIManager;
import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.instance.InstanceManager;
import com.odisseymc.odisseydungueons.model.Difficulty;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.model.DungeonSpawn;
import com.odisseymc.odisseydungueons.setup.SpawnerManager;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Main command handler executing and tab-completing all /dungeons subcommands.
 */
public class DungeonsCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final GUIManager guiManager;
    private final InstanceManager instanceManager;
    private final Function<String, Dungeon> dungeonLookup;
    private final Consumer<Dungeon> dungeonRegistrar;
    private final Consumer<String> dungeonRemover;
    private final Runnable reloadAction;
    private final java.util.function.Supplier<Collection<Dungeon>> allDungeonsSupplier;
    private SpawnerManager spawnerManager;

    public DungeonsCommand(ConfigManager configManager,
                           MessageManager messageManager,
                           GUIManager guiManager,
                           InstanceManager instanceManager,
                           Function<String, Dungeon> dungeonLookup,
                           Consumer<Dungeon> dungeonRegistrar,
                           Consumer<String> dungeonRemover,
                           Runnable reloadAction,
                           java.util.function.Supplier<Collection<Dungeon>> allDungeonsSupplier) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.guiManager = guiManager;
        this.instanceManager = instanceManager;
        this.dungeonLookup = dungeonLookup;
        this.dungeonRegistrar = dungeonRegistrar;
        this.dungeonRemover = dungeonRemover;
        this.reloadAction = reloadAction;
        this.allDungeonsSupplier = allDungeonsSupplier;
    }

    public void setSpawnerManager(SpawnerManager spawnerManager) {
        this.spawnerManager = spawnerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                guiManager.openMainMenu(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "gui":
                if (sender instanceof Player player) {
                    guiManager.openMainMenu(player);
                } else {
                    sender.sendMessage(TextUtil.colorize("&cEste comando solo puede ser ejecutado por un jugador."));
                }
                break;

            case "create":
                handleCreate(sender, args);
                break;

            case "delete":
                handleDelete(sender, args);
                break;

            case "setspawn":
                handleSetSpawn(sender, args);
                break;

            case "spawner":
            case "getspawner":
                handleSpawner(sender, args);
                break;

            case "tp":
            case "teleport":
                handleTeleport(sender, args);
                break;

            case "start":
                handleStart(sender, args, false);
                break;

            case "test":
                handleStart(sender, args, true);
                break;

            case "stop":
                handleStop(sender, args);
                break;

            case "join":
                handleJoin(sender, args);
                break;

            case "leave":
                handleLeave(sender);
                break;

            case "info":
                handleInfo(sender, args);
                break;

            case "reload":
                handleReload(sender);
                break;

            case "help":
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("odisseydungueons.admin")) {
            messageManager.send(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUso: /dungeons create <nombre>"));
            return;
        }

        String name = args[1];
        if (dungeonLookup.apply(name) != null) {
            sender.sendMessage(TextUtil.colorize("&cYa existe una dungeon con ese nombre."));
            return;
        }

        Dungeon dungeon = new Dungeon(name);
        dungeon.initDefaultWaves();
        dungeonRegistrar.accept(dungeon);
        configManager.saveDungeon(dungeon);

        sender.sendMessage(TextUtil.colorize("&a&l[ODISSEY DUNGUEONS] &a¡Mazmorra &f" + name + " &acreada con éxito!"));
        sender.sendMessage(TextUtil.colorize("&e&lREQUISITOS PENDIENTES:"));
        sender.sendMessage(TextUtil.colorize("&7- Ve al mundo de tu mazmorra y usa: &b/dungeons setspawn " + name));
        sender.sendMessage(TextUtil.colorize("&7- O abre &b/dungeons gui &7y selecciona el mundo en la opción de mapas."));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("odisseydungueons.admin")) {
            messageManager.send(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUso: /dungeons delete <nombre>"));
            return;
        }

        String name = args[1];
        Dungeon dungeon = dungeonLookup.apply(name);
        if (dungeon == null) {
            sender.sendMessage(TextUtil.colorize("&cNo existe la dungeon: " + name));
            return;
        }

        dungeonRemover.accept(name);
        configManager.deleteDungeonFile(name);

        Map<String, String> ph = new HashMap<>();
        ph.put("dungeon", name);
        messageManager.send(sender, "dungeon-deleted", ph);
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("odisseydungueons.admin")) {
            messageManager.send(sender, "no-permission");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cEste comando solo puede ser ejecutado por un jugador."));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(TextUtil.colorize("&cUso: /dungeons setspawn <nombre>"));
            return;
        }

        String name = args[1];
        Dungeon dungeon = dungeonLookup.apply(name);
        if (dungeon == null) {
            player.sendMessage(TextUtil.colorize("&cNo existe la dungeon: " + name));
            return;
        }

        Location loc = player.getLocation();
        dungeon.setPlayerSpawn(new DungeonSpawn("player", DungeonSpawn.SpawnType.PLAYER, loc));
        dungeon.setWorldName(loc.getWorld().getName());
        configManager.saveDungeon(dungeon);

        player.sendMessage(TextUtil.colorize("&a&l[ODISSEY DUNGUEONS] &a¡Punto de spawn establecido para &f" + dungeon.getName() + "&a!"));
        player.sendMessage(TextUtil.colorize("&7Mundo: &e" + loc.getWorld().getName() +
                " &7| Coordenadas: &fX: " + String.format("%.2f", loc.getX()) +
                ", Y: " + String.format("%.2f", loc.getY()) +
                ", Z: " + String.format("%.2f", loc.getZ()) +
                " &7(Yaw: " + String.format("%.1f", loc.getYaw()) + ", Pitch: " + String.format("%.1f", loc.getPitch()) + ")"));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    private void handleSpawner(CommandSender sender, String[] args) {
        if (!sender.hasPermission("odisseydungueons.admin")) {
            messageManager.send(sender, "no-permission");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cEste comando solo puede ser ejecutado por un jugador."));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(TextUtil.colorize("&cUso: /dungeons spawner <mob|boss|chest> <dungeon>"));
            return;
        }

        String type = args[1].toLowerCase();
        String dungeonName = args[2];
        Dungeon dungeon = dungeonLookup.apply(dungeonName);
        if (dungeon == null) {
            messageManager.send(player, "dungeon-not-found", Map.of("dungeon", dungeonName));
            return;
        }

        if (spawnerManager == null) {
            player.sendMessage(TextUtil.colorize("&cEl sistema de spawners no está inicializado."));
            return;
        }

        if ("mob".equals(type) || "mobs".equals(type)) {
            player.getInventory().addItem(spawnerManager.createMobSpawner(dungeon.getName()));
            messageManager.send(player, "spawner-mob-received", Map.of("dungeon", dungeon.getName()));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        } else if ("boss".equals(type)) {
            player.getInventory().addItem(spawnerManager.createBossSpawner(dungeon.getName()));
            messageManager.send(player, "spawner-boss-received", Map.of("dungeon", dungeon.getName()));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        } else if ("chest".equals(type) || "rewardchest".equals(type) || "cofre".equals(type)) {
            player.getInventory().addItem(spawnerManager.createRewardChest(dungeon.getName()));
            messageManager.send(player, "reward-chest-received", Map.of("dungeon", dungeon.getName()));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        } else {
            player.sendMessage(TextUtil.colorize("&cTipo inválido. Usa: /dungeons spawner <mob|boss|chest> <dungeon>"));
        }
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("odisseydungueons.admin")) {
            messageManager.send(sender, "no-permission");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cEste comando solo puede ser ejecutado por un jugador."));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(TextUtil.colorize("&cUso: /dungeons tp <nombre>"));
            return;
        }

        String name = args[1];
        Dungeon dungeon = dungeonLookup.apply(name);
        if (dungeon == null) {
            player.sendMessage(TextUtil.colorize("&cNo existe la dungeon: " + name));
            return;
        }

        Location targetLoc = null;
        if (dungeon.getPlayerSpawn() != null) {
            targetLoc = dungeon.getPlayerSpawn().toBukkitLocation();
            if (targetLoc.getWorld() == null) {
                org.bukkit.World w = org.bukkit.Bukkit.getWorld(dungeon.getWorldName());
                if (w == null) {
                    for (org.bukkit.World bw : org.bukkit.Bukkit.getWorlds()) {
                        if (bw.getName().equalsIgnoreCase(dungeon.getWorldName())) {
                            w = bw;
                            break;
                        }
                    }
                }
                if (w == null) {
                    try {
                        w = org.bukkit.Bukkit.createWorld(new org.bukkit.WorldCreator(dungeon.getWorldName()));
                    } catch (Exception ignored) {}
                }
                if (w != null) {
                    targetLoc = new Location(w, dungeon.getPlayerSpawn().getX(), dungeon.getPlayerSpawn().getY(), dungeon.getPlayerSpawn().getZ(), dungeon.getPlayerSpawn().getYaw(), dungeon.getPlayerSpawn().getPitch());
                }
            }
        }

        if (targetLoc == null || targetLoc.getWorld() == null) {
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(dungeon.getWorldName());
            if (w == null) {
                for (org.bukkit.World bw : org.bukkit.Bukkit.getWorlds()) {
                    if (bw.getName().equalsIgnoreCase(dungeon.getWorldName())) {
                        w = bw;
                        break;
                    }
                }
            }
            if (w == null) {
                try {
                    w = org.bukkit.Bukkit.createWorld(new org.bukkit.WorldCreator(dungeon.getWorldName()));
                } catch (Exception ignored) {}
            }
            if (w != null) {
                targetLoc = w.getSpawnLocation();
            }
        }

        if (targetLoc == null || targetLoc.getWorld() == null) {
            player.sendMessage(TextUtil.colorize("&cNo se pudo teletransportar: El mundo &e" + dungeon.getWorldName() + " &cno está cargado o no existe."));
            return;
        }

        if (targetLoc.getWorld() != null) {
            targetLoc.getChunk().load(true);
        }

        player.teleport(targetLoc);
        player.sendMessage(TextUtil.colorize("&a&l[ODISSEY DUNGUEONS] &aTeletransportado a la mazmorra &f" + dungeon.getName() +
                " &7(Mundo: &e" + targetLoc.getWorld().getName() +
                " &7| &fX: " + String.format("%.1f", targetLoc.getX()) +
                ", Y: " + String.format("%.1f", targetLoc.getY()) +
                ", Z: " + String.format("%.1f", targetLoc.getZ()) + "&7)."));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void handleStart(CommandSender sender, String[] args, boolean testMode) {
        if (!sender.hasPermission("odisseydungueons.admin")) {
            messageManager.send(sender, "no-permission");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cDebes ser un jugador para iniciar una mazmorra."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUso: /dungeons " + (testMode ? "test" : "start") + " <nombre>"));
            return;
        }

        String name = args[1];
        Dungeon dungeon = dungeonLookup.apply(name);
        if (dungeon == null) {
            sender.sendMessage(TextUtil.colorize("&cNo existe la dungeon: " + name));
            return;
        }

        if (!testMode && dungeon.isOnCooldown()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("time", dungeon.getFormattedCooldown());
            messageManager.send(player, "dungeon-cooldown", ph);
            return;
        }

        instanceManager.startInstance(dungeon, Difficulty.NORMAL, Collections.singletonList(player), testMode);
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("odisseydungueons.admin")) {
            messageManager.send(sender, "no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUso: /dungeons stop <nombre>"));
            return;
        }

        String name = args[1];
        List<DungeonInstance> matching = instanceManager.getActiveInstances().stream()
                .filter(i -> i.getDungeon().getName().equalsIgnoreCase(name))
                .toList();

        if (!matching.isEmpty()) {
            for (DungeonInstance inst : matching) {
                instanceManager.endInstance(inst);
            }
            sender.sendMessage(TextUtil.colorize("&aSe detuvieron &f" + matching.size() + " &ainstancia(s) de &f" + name + "&a."));
        } else {
            sender.sendMessage(TextUtil.colorize("&cNo hay instancias activas de &f" + name));
        }
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cDebes ser un jugador para unirte."));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(TextUtil.colorize("&cUso: /dungeons join <nombre>"));
            return;
        }

        String name = args[1];
        Dungeon dungeon = dungeonLookup.apply(name);
        if (dungeon == null) {
            player.sendMessage(TextUtil.colorize("&cNo existe la dungeon: " + name));
            return;
        }

        if (!dungeon.isEnabled()) {
            player.sendMessage(TextUtil.colorize("&cEsta dungeon está actualmente desactivada."));
            return;
        }

        if (dungeon.isOnCooldown()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("time", dungeon.getFormattedCooldown());
            messageManager.send(player, "dungeon-cooldown", ph);
            return;
        }

        instanceManager.startInstance(dungeon, Difficulty.NORMAL, Collections.singletonList(player), false);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("&cDebes ser un jugador."));
            return;
        }

        DungeonInstance inst = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (inst == null) {
            player.sendMessage(TextUtil.colorize("&cNo te encuentras dentro de ninguna dungeon activa."));
            return;
        }

        inst.removePlayer(player);
        Location exit = (inst.getDungeon().getLobbySpawn() != null) ?
                inst.getDungeon().getLobbySpawn().toBukkitLocation() :
                player.getWorld().getSpawnLocation();
        player.teleport(exit);
        player.sendMessage(TextUtil.colorize("&eHas abandonado la dungeon."));

        if (inst.getPlayerCount() == 0) {
            instanceManager.endInstance(inst);
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUso: /dungeons info <nombre>"));
            return;
        }

        String name = args[1];
        Dungeon dungeon = dungeonLookup.apply(name);
        if (dungeon == null) {
            sender.sendMessage(TextUtil.colorize("&cNo existe la dungeon: " + name));
            return;
        }

        sender.sendMessage(TextUtil.colorize("&b&l----------------------------------------"));
        sender.sendMessage(TextUtil.colorize("&b&lDUNGEON: &f" + dungeon.getDisplayName()));
        sender.sendMessage(TextUtil.colorize("&7Estado: &f" + (dungeon.isEnabled() ? (dungeon.isOnCooldown() ? "&eEn Cooldown" : "&aDisponible") : "&cDesactivada")));
        sender.sendMessage(TextUtil.colorize("&7Cooldown: &f" + TextUtil.formatDuration(dungeon.getCooldownSeconds())));
        sender.sendMessage(TextUtil.colorize("&7Duración: &f" + TextUtil.formatDuration(dungeon.getDurationSeconds())));
        sender.sendMessage(TextUtil.colorize("&7Jugadores: &f" + dungeon.getMinPlayers() + " - " + dungeon.getMaxPlayers()));
        sender.sendMessage(TextUtil.colorize("&7Spawns de mobs: &f" + dungeon.getMobSpawns().size()));
        sender.sendMessage(TextUtil.colorize("&7Oleadas: &f" + dungeon.getWaves().size()));
        sender.sendMessage(TextUtil.colorize("&7Boss: &f" + (dungeon.getBossConfig() != null ? dungeon.getBossConfig().getName() : "&cNinguno")));
        sender.sendMessage(TextUtil.colorize("&b&l----------------------------------------"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("odisseydungueons.admin")) {
            messageManager.send(sender, "no-permission");
            return;
        }

        reloadAction.run();
        sender.sendMessage(TextUtil.colorize("&a&l¡OdisseyDungueons recargado correctamente!"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&b&l=================[ &fODISSEY DUNGUEONS &b&l]================="));
        sender.sendMessage(TextUtil.colorize("&b/dungeons gui &7- Abre el menú principal"));
        sender.sendMessage(TextUtil.colorize("&b/dungeons join <nombre> &7- Entra a una mazmorra"));
        sender.sendMessage(TextUtil.colorize("&b/dungeons leave &7- Sal de la mazmorra actual"));
        sender.sendMessage(TextUtil.colorize("&b/dungeons info <nombre> &7- Información de la mazmorra"));

        if (sender.hasPermission("odisseydungueons.admin")) {
            sender.sendMessage(TextUtil.colorize("&e--- Comandos Administrativos ---"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons create <nombre> &7- Crea una nueva mazmorra"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons delete <nombre> &7- Elimina una mazmorra"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons spawner <mob|boss|chest> <dungeon> &7- Obtener bloque spawner o cofre"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons setspawn <nombre> &7- Establece punto de spawn"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons tp <nombre> &7- Teletranspórtate a la mazmorra"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons start <nombre> &7- Inicia una mazmorra"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons stop <nombre> &7- Detiene una mazmorra"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons test <nombre> &7- Modo de prueba sin cooldown"));
            sender.sendMessage(TextUtil.colorize("&e/dungeons reload &7- Recarga configuraciones"));
        }
        sender.sendMessage(TextUtil.colorize("&b&l=================================================="));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("gui", "join", "leave", "info", "help"));
            if (sender.hasPermission("odisseydungueons.admin")) {
                subs.addAll(Arrays.asList("create", "delete", "spawner", "setspawn", "tp", "teleport", "start", "stop", "test", "reload"));
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("spawner".equals(sub)) {
                return Arrays.asList("mob", "boss", "chest").stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (Arrays.asList("delete", "setspawn", "tp", "teleport", "start", "stop", "test", "join", "info").contains(sub)) {
                return allDungeonsSupplier.get().stream()
                        .map(Dungeon::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ("spawner".equals(sub)) {
                return allDungeonsSupplier.get().stream()
                        .map(Dungeon::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
