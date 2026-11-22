package com.odisseymc.odisseydungueons.gui;

import com.odisseymc.odisseydungueons.model.Dungeon;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom InventoryHolder to cleanly distinguish plugin GUIs and store context securely.
 */
public class CustomHolder implements InventoryHolder {

    private final String menuType;
    private final Dungeon dungeon;
    private final Map<Integer, String> slotActions = new HashMap<>();
    private Inventory inventory;
    private int worldBrowsingIndex = 0;

    public CustomHolder(String menuType, Dungeon dungeon) {
        this.menuType = menuType;
        this.dungeon = dungeon;
    }

    public int getWorldBrowsingIndex() {
        return worldBrowsingIndex;
    }

    public void setWorldBrowsingIndex(int index) {
        this.worldBrowsingIndex = index;
    }

    public String getMenuType() {
        return menuType;
    }

    public Dungeon getDungeon() {
        return dungeon;
    }

    public void setAction(int slot, String action) {
        slotActions.put(slot, action);
    }

    public String getAction(int slot) {
        return slotActions.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
