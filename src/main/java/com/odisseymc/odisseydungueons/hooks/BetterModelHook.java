package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class BetterModelHook {

    private boolean enabled = false;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("BetterModel") != null) {
            enabled = true;
            Bukkit.getLogger().info("[OdisseyDungueons] BetterModel hook detected.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void applyModel(Entity entity, String modelId) {
        if (!enabled || entity == null || modelId == null || modelId.isEmpty()) {
            return;
        }

        try {
            Class<?> apiClass = Class.forName("com.bettermodel.api.BetterModelAPI");
            Method setModel = apiClass.getMethod("setModel", Entity.class, String.class);
            setModel.invoke(null, entity, modelId);
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[OdisseyDungueons] Could not apply BetterModel model " + modelId + ": " + t.getMessage());
        }
    }
}
