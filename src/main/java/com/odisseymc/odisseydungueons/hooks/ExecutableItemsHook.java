package com.odisseymc.odisseydungueons.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;

public class ExecutableItemsHook {

    private boolean enabled = false;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("ExecutableItems") != null) {
            enabled = true;
            Bukkit.getLogger().info("[OdisseyDungueons] ExecutableItems hook detected.");
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
            Class<?> managerClass = Class.forName("com.ssomar.score.api.executableitems.ExecutableItemsAPI");
            Method getManagerMethod = managerClass.getMethod("getExecutableItemsManager");
            Object manager = getManagerMethod.invoke(null);

            Method getExecutableItemMethod = manager.getClass().getMethod("getExecutableItem", String.class);
            Object optionalItem = getExecutableItemMethod.invoke(manager, itemId);

            if (optionalItem instanceof Optional<?> opt && opt.isPresent()) {
                Object execItem = opt.get();
                Method buildItemMethod = execItem.getClass().getMethod("buildItem", int.class, Optional.class);
                return (ItemStack) buildItemMethod.invoke(execItem, 1, Optional.empty());
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "[OdisseyDungueons] Could not fetch ExecutableItem " + itemId + ": " + t.getMessage());
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
