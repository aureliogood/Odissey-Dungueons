package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class SlimeWorldManagerHook {

    private boolean enabled = false;
    private Object swmPlugin = null;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("SlimeWorldManager") != null ||
                Bukkit.getPluginManager().getPlugin("AdvancedSlimePaper") != null) {
            try {
                Class<?> apiClass = Class.forName("com.infernalsuite.aswm.api.SlimePlugin");
                swmPlugin = Bukkit.getPluginManager().getPlugin("SlimeWorldManager");
                if (swmPlugin == null) {
                    swmPlugin = Bukkit.getPluginManager().getPlugin("AdvancedSlimePaper");
                }
                enabled = (swmPlugin != null);
                if (enabled) {
                    Bukkit.getLogger().info("[OdisseyDungueons] SlimeWorldManager hook detected.");
                }
            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.FINE, "[OdisseyDungueons] SlimeWorldManager API not loaded: " + t.getMessage());
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
