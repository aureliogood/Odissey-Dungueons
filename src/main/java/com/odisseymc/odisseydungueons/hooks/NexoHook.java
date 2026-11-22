package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class NexoHook {

    private boolean enabled = false;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            enabled = true;
            Bukkit.getLogger().info("[OdisseyDungueons] Nexo hook detected.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ItemStack getItemStack(String itemId) {
        if (!enabled || itemId == null || itemId.isEmpty()) {
            return null;
        }

        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
            Object itemBuilder = itemFromIdMethod.invoke(null, itemId);

            if (itemBuilder != null) {
                Method buildMethod = itemBuilder.getClass().getMethod("build");
                return (ItemStack) buildMethod.invoke(itemBuilder);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[OdisseyDungueons] Could not fetch Nexo item " + itemId + ": " + t.getMessage());
        }
        return null;
    }

    public void giveItem(Player player, String itemId, int amount) {
        ItemStack item = getItemStack(itemId);
        if (item != null && player != null) {
            item.setAmount(amount);
            player.getInventory().addItem(item);
        }
    }
}
