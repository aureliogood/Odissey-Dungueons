package com.odisseymc.odisseydungueons.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Tracks a door/gate block in a dungeon room to open/close or restore.
 */
public class DoorBlock {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final Material closedMaterial;
    private final Material openMaterial;

    public DoorBlock(String worldName, int x, int y, int z, Material closedMaterial, Material openMaterial) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.closedMaterial = closedMaterial != null ? closedMaterial : Material.IRON_BARS;
        this.openMaterial = openMaterial != null ? openMaterial : Material.AIR;
    }

    public DoorBlock(Location loc, Material closedMaterial, Material openMaterial) {
        this(loc.getWorld() != null ? loc.getWorld().getName() : "world",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                closedMaterial, openMaterial);
    }

    public void setOpen(World instanceWorld, boolean open) {
        if (instanceWorld == null) return;
        Block block = instanceWorld.getBlockAt(x, y, z);
        block.setType(open ? openMaterial : closedMaterial);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Material getClosedMaterial() {
        return closedMaterial;
    }

    public Material getOpenMaterial() {
        return openMaterial;
    }
}
