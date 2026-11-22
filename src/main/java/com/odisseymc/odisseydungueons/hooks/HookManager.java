package com.odisseymc.odisseydungueons.hooks;

import com.odisseymc.odisseydungueons.instance.InstanceManager;
import com.odisseymc.odisseydungueons.model.Dungeon;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Function;

/**
 * Manages all optional third-party plugin integrations.
 */
public class HookManager {

    private final JavaPlugin plugin;
    private final VaultHook vaultHook;
    private final PlaceholderAPIHook placeholderAPIHook;
    private final MythicMobsHook mythicMobsHook;
    private final ModelEngineHook modelEngineHook;
    private final BetterModelHook betterModelHook;
    private final ItemsAdderHook itemsAdderHook;
    private final NexoHook nexoHook;
    private final ExecutableItemsHook executableItemsHook;
    private final WorldGuardHook worldGuardHook;
    private final WorldEditHook worldEditHook;
    private final SlimeWorldManagerHook slimeWorldManagerHook;

    public HookManager(JavaPlugin plugin, Function<String, Dungeon> dungeonLookup, InstanceManager instanceManager) {
        this.plugin = plugin;
        this.vaultHook = new VaultHook();
        this.placeholderAPIHook = new PlaceholderAPIHook(plugin, dungeonLookup, instanceManager);
        this.mythicMobsHook = new MythicMobsHook();
        this.modelEngineHook = new ModelEngineHook();
        this.betterModelHook = new BetterModelHook();
        this.itemsAdderHook = new ItemsAdderHook();
        this.nexoHook = new NexoHook();
        this.executableItemsHook = new ExecutableItemsHook();
        this.worldGuardHook = new WorldGuardHook();
        this.worldEditHook = new WorldEditHook();
        this.slimeWorldManagerHook = new SlimeWorldManagerHook();
    }

    public void initialize() {
        vaultHook.init();
        placeholderAPIHook.init();
        mythicMobsHook.init();
        modelEngineHook.init();
        betterModelHook.init();
        itemsAdderHook.init();
        nexoHook.init();
        executableItemsHook.init();
        worldGuardHook.init();
        worldEditHook.init();
        slimeWorldManagerHook.init();
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public PlaceholderAPIHook getPlaceholderAPIHook() {
        return placeholderAPIHook;
    }

    public MythicMobsHook getMythicMobsHook() {
        return mythicMobsHook;
    }

    public ModelEngineHook getModelEngineHook() {
        return modelEngineHook;
    }

    public BetterModelHook getBetterModelHook() {
        return betterModelHook;
    }

    public ItemsAdderHook getItemsAdderHook() {
        return itemsAdderHook;
    }

    public NexoHook getNexoHook() {
        return nexoHook;
    }

    public ExecutableItemsHook getExecutableItemsHook() {
        return executableItemsHook;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public WorldEditHook getWorldEditHook() {
        return worldEditHook;
    }

    public SlimeWorldManagerHook getSlimeWorldManagerHook() {
        return slimeWorldManagerHook;
    }
}
