package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public class WorldGuardHook {

    private boolean enabled = false;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            enabled = true;
            Bukkit.getLogger().info("[OdisseyDungueons] WorldGuard hook enabled.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
