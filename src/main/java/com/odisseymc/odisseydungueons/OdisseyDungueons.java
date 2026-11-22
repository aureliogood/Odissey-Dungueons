package com.odisseymc.odisseydungueons;

import com.odisseymc.odisseydungueons.boss.BossManager;
import com.odisseymc.odisseydungueons.combat.DungeonCombatListener;
import com.odisseymc.odisseydungueons.commands.DungeonsCommand;
import com.odisseymc.odisseydungueons.config.ConfigManager;
import com.odisseymc.odisseydungueons.config.MessageManager;
import com.odisseymc.odisseydungueons.cooldown.CooldownManager;
import com.odisseymc.odisseydungueons.gui.GUIManager;
import com.odisseymc.odisseydungueons.hooks.HookManager;
import com.odisseymc.odisseydungueons.instance.InstanceManager;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.rewards.RewardManager;
import com.odisseymc.odisseydungueons.setup.SpawnerManager;
import com.odisseymc.odisseydungueons.trigger.TriggerManager;
import com.odisseymc.odisseydungueons.util.TextUtil;
import com.odisseymc.odisseydungueons.wave.WaveManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OdisseyDungueons main plugin class.
 */
public class OdisseyDungueons extends JavaPlugin {

    private static OdisseyDungueons instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private InstanceManager instanceManager;
    private WaveManager waveManager;
    private BossManager bossManager;
    private RewardManager rewardManager;
    private TriggerManager triggerManager;
    private SpawnerManager spawnerManager;
    private GUIManager guiManager;
    private CooldownManager cooldownManager;
    private HookManager hookManager;
    private DungeonCombatListener combatListener;

    private final Map<String, Dungeon> dungeonRegistry = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        long startTime = System.currentTimeMillis();
        getLogger().info(TextUtil.colorize("&b&lODISSEY DUNGUEONS &7- Inicializando sistema..."));

        // 1. Configs & Messages
        this.configManager = new ConfigManager(this);
        this.configManager.initialize();
        this.messageManager = new MessageManager(this);

        // 2. Load Dungeons into memory
        for (Dungeon d : configManager.loadAllDungeons()) {
            dungeonRegistry.put(d.getName().toLowerCase(), d);
        }
        getLogger().info("[OdisseyDungueons] Cargadas " + dungeonRegistry.size() + " mazmorras.");

        // 3. Core Engine Managers
        this.instanceManager = new InstanceManager(this, configManager, messageManager);
        this.instanceManager.cleanupOrphanedInstances();

        // 4. Integrations / Hooks
        this.hookManager = new HookManager(this, this::getDungeon, instanceManager);
        this.hookManager.initialize();

        // 5. Combat & Wave Subsystems
        this.waveManager = new WaveManager(this, hookManager);
        this.bossManager = new BossManager(this, hookManager);
        this.rewardManager = new RewardManager(this, hookManager);
        this.triggerManager = new TriggerManager(this, instanceManager, waveManager, bossManager);

        this.instanceManager.setDependencies(rewardManager, waveManager, bossManager);

        this.combatListener = new DungeonCombatListener(instanceManager, messageManager);
        this.combatListener.setTriggerManager(triggerManager);

        // 6. Setup & GUI Subsystems (Spawners & Holograms)
        this.spawnerManager = new SpawnerManager(this, configManager, messageManager, this::getDungeon, this::getAllDungeons);
        this.spawnerManager.setDependencies(instanceManager, waveManager, bossManager);
        this.spawnerManager.setRewardManager(rewardManager);
        this.instanceManager.setSpawnerManager(spawnerManager);

        this.guiManager = new GUIManager(this, configManager, messageManager, this::getAllDungeons);
        this.guiManager.setSpawnerManager(spawnerManager);
        this.guiManager.setInstanceManager(instanceManager);

        this.cooldownManager = new CooldownManager(this, messageManager, this::getAllDungeons);

        // 7. Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(combatListener, this);
        pm.registerEvents(guiManager, this);
        pm.registerEvents(spawnerManager, this);
        pm.registerEvents(triggerManager, this);

        // 8. Commands
        DungeonsCommand cmd = new DungeonsCommand(
                configManager,
                messageManager,
                guiManager,
                instanceManager,
                this::getDungeon,
                this::registerDungeon,
                this::unregisterDungeon,
                this::reloadAll,
                this::getAllDungeons
        );
        cmd.setSpawnerManager(spawnerManager);

        if (getCommand("dungeons") != null) {
            getCommand("dungeons").setExecutor(cmd);
            getCommand("dungeons").setTabCompleter(cmd);
        }

        // 9. Start Tasks
        this.spawnerManager.start();
        this.cooldownManager.start();

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info(TextUtil.colorize("&b&lODISSEY DUNGUEONS &aactivado con éxito en &f" + loadTime + "ms&a."));
    }

    @Override
    public void onDisable() {
        getLogger().info("[OdisseyDungueons] Desactivando plugin y limpiando instancias...");

        if (spawnerManager != null) {
            spawnerManager.stop();
        }

        if (cooldownManager != null) {
            cooldownManager.stop();
        }

        if (instanceManager != null) {
            instanceManager.shutdownAll();
        }

        // Save all dungeons state (cooldowns, etc.)
        if (configManager != null) {
            for (Dungeon d : dungeonRegistry.values()) {
                configManager.saveDungeon(d);
            }
        }

        instance = null;
        getLogger().info("[OdisseyDungueons] Plugin desactivado correctamente.");
    }

    public void reloadAll() {
        configManager.loadConfigs();
        messageManager.load();
        dungeonRegistry.clear();
        for (Dungeon d : configManager.loadAllDungeons()) {
            dungeonRegistry.put(d.getName().toLowerCase(), d);
        }
        if (spawnerManager != null) {
            spawnerManager.refreshAllEditorHolograms();
        }
        getLogger().info("[OdisseyDungueons] Recargado. Total mazmorras: " + dungeonRegistry.size());
    }

    public Dungeon getDungeon(String name) {
        if (name == null) return null;
        return dungeonRegistry.get(name.toLowerCase());
    }

    public void registerDungeon(Dungeon dungeon) {
        if (dungeon != null) {
            dungeonRegistry.put(dungeon.getName().toLowerCase(), dungeon);
        }
    }

    public void unregisterDungeon(String name) {
        if (name != null) {
            dungeonRegistry.remove(name.toLowerCase());
        }
    }

    public Collection<Dungeon> getAllDungeons() {
        return dungeonRegistry.values();
    }

    public static OdisseyDungueons getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public InstanceManager getInstanceManager() {
        return instanceManager;
    }

    public WaveManager getWaveManager() {
        return waveManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }
}
