package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class ModelEngineHook {

    private boolean enabled = false;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
            enabled = true;
            Bukkit.getLogger().info("[OdisseyDungueons] ModelEngine hook detected.");
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
            // ModelEngine API reflection call
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Method getModeledEntity = apiClass.getMethod("getOrCreateModeledEntity", Entity.class);
            Object modeledEntity = getModeledEntity.invoke(null, entity);

            Method createActiveModel = apiClass.getMethod("createActiveModel", String.class);
            Object activeModel = createActiveModel.invoke(null, modelId);

            if (modeledEntity != null && activeModel != null) {
                Method addModel = modeledEntity.getClass().getMethod("addModel", activeModel.getClass(), boolean.class);
                addModel.invoke(modeledEntity, activeModel, true);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[OdisseyDungueons] Could not apply ModelEngine model " + modelId + ": " + t.getMessage());
        }
    }
}
