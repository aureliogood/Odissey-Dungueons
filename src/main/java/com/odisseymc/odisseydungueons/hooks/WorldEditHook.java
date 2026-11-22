package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;

public class WorldEditHook {

    private boolean enabled = false;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null) {
            enabled = true;
            Bukkit.getLogger().info("[OdisseyDungueons] WorldEdit hook enabled.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
