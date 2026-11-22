package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class MythicMobsHook {

    private boolean enabled = false;
    private Object mythicInstance = null;
    private Method spawnMethod = null;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            try {
                // Try MythicMobs v5
                Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Method instMethod = mythicBukkitClass.getMethod("get");
                mythicInstance = instMethod.invoke(null);

                Method apiHelperMethod = mythicBukkitClass.getMethod("getAPIHelper");
                Object apiHelper = apiHelperMethod.invoke(mythicInstance);

                spawnMethod = apiHelper.getClass().getMethod("spawnMythicMob", String.class, Location.class, int.class);
                mythicInstance = apiHelper;
                enabled = true;
                Bukkit.getLogger().info("[OdisseyDungueons] MythicMobs v5 API hooked successfully.");
                return;
            } catch (Throwable ignored) {
            }

            try {
                // Try MythicMobs v4
                Class<?> mythicClass = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs");
                Method instMethod = mythicClass.getMethod("inst");
                Object inst = instMethod.invoke(null);

                Method apiHelperMethod = mythicClass.getMethod("getAPIHelper");
                Object apiHelper = apiHelperMethod.invoke(inst);

                spawnMethod = apiHelper.getClass().getMethod("spawnMythicMob", String.class, Location.class, int.class);
                mythicInstance = apiHelper;
                enabled = true;
                Bukkit.getLogger().info("[OdisseyDungueons] MythicMobs v4 API hooked successfully.");
            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.WARNING, "[OdisseyDungueons] MythicMobs plugin detected, but failed to link API: " + t.getMessage());
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Entity spawnMythicMob(String mobId, Location location) {
        if (!enabled || mythicInstance == null || spawnMethod == null) {
            return null;
        }

        try {
            Object result = spawnMethod.invoke(mythicInstance, mobId, location, 1);
            if (result instanceof Entity ent) {
                return ent;
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[OdisseyDungueons] Error spawning MythicMob " + mobId + ": " + t.getMessage());
        }
        return null;
    }
}
