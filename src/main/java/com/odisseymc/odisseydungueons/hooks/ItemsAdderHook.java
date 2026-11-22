package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class ItemsAdderHook {

    private boolean enabled = false;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            enabled = true;
            Bukkit.getLogger().info("[OdisseyDungueons] ItemsAdder hook detected.");
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
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstanceMethod = customStackClass.getMethod("getInstance", String.class);
            Object customStack = getInstanceMethod.invoke(null, itemId);

            if (customStack != null) {
                Method getItemStackMethod = customStackClass.getMethod("getItemStack");
                return (ItemStack) getItemStackMethod.invoke(customStack);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[OdisseyDungueons] Could not fetch ItemsAdder item " + itemId + ": " + t.getMessage());
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
