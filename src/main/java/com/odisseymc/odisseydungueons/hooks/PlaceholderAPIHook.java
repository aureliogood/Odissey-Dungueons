package com.odisseymc.odisseydungueons.hooks;

import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.instance.InstanceManager;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.model.DungeonState;
import com.odisseymc.odisseydungueons.util.TextUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Function;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final Function<String, Dungeon> dungeonLookup;
    private final InstanceManager instanceManager;
    private boolean registered = false;

    public PlaceholderAPIHook(JavaPlugin plugin, Function<String, Dungeon> dungeonLookup, InstanceManager instanceManager) {
        this.plugin = plugin;
        this.dungeonLookup = dungeonLookup;
        this.instanceManager = instanceManager;
    }

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.registered = this.register();
        }
    }

    @Override
    public String getIdentifier() {
        return "odisseydungueons";
    }

    @Override
    public String getAuthor() {
        return "OdisseyMC";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        // Params format: <dungeon>_<property>
        // Example: %ndungeons_Prueba_time%
        int lastUnder = params.lastIndexOf('_');
        if (lastUnder == -1) {
            return "";
        }

        String dungeonName = params.substring(0, lastUnder);
        String property = params.substring(lastUnder + 1).toLowerCase();

        Dungeon dungeon = dungeonLookup.apply(dungeonName);
        if (dungeon == null) {
            return "N/A";
        }

        // Active instance if any
        DungeonInstance activeInstance = instanceManager.findInstanceByDungeon(dungeonName);

        switch (property) {
            case "name":
                return dungeon.getName();

            case "display":
                return dungeon.getDisplayName();

            case "time":
                if (dungeon.isOnCooldown()) {
                    return dungeon.getFormattedCooldown();
                } else if (activeInstance != null) {
                    return TextUtil.formatDuration(activeInstance.getRemainingSeconds());
                }
                return "00s";

            case "minutes":
                long remMin = dungeon.getRemainingCooldownSeconds() / 60;
                return String.valueOf(remMin);

            case "seconds":
                long remSec = dungeon.getRemainingCooldownSeconds() % 60;
                return String.valueOf(remSec);

            case "status":
                if (!dungeon.isEnabled()) {
                    return DungeonState.DISABLED.getDisplay();
                }
                if (dungeon.isOnCooldown()) {
                    return DungeonState.COOLDOWN.getDisplay();
                }
                if (activeInstance != null) {
                    return DungeonState.IN_USE.getDisplay();
                }
                return DungeonState.IDLE.getDisplay();

            case "players":
                if (activeInstance != null) {
                    return String.valueOf(activeInstance.getPlayerCount());
                }
                return "0";

            case "max_players":
            case "maxplayers":
                return String.valueOf(dungeon.getMaxPlayers());

            case "boss":
            case "boss_hp":
            case "bosshp":
                if (activeInstance != null && activeInstance.getBossEntity() != null && !activeInstance.getBossEntity().isDead()) {
                    if (activeInstance.getBossEntity() instanceof org.bukkit.entity.LivingEntity le) {
                        return String.format("%.1f", le.getHealth());
                    }
                    return "Vivo";
                }
                return (dungeon.getBossConfig() != null) ? dungeon.getBossConfig().getName() : "Ninguno";

            case "wave":
            case "wave_current":
                if (activeInstance != null) {
                    return String.valueOf(activeInstance.getCurrentWaveIndex());
                }
                return "0";

            case "waves_total":
                return String.valueOf(dungeon.getWaves().size());

            case "enemies":
            case "enemies_remaining":
                if (activeInstance != null) {
                    return String.valueOf(activeInstance.getAliveMobCount());
                }
                return "0";

            case "difficulty":
                if (activeInstance != null) {
                    return activeInstance.getDifficulty().getDisplayName();
                }
                return "NORMAL";

            default:
                return "";
        }
    }
}
