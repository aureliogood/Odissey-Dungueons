package com.odisseymc.odisseydungueons.instance;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Stores a block's original state before being modified in a dungeon instance.
 */
public class BlockStateRecord {

    private final int x;
    private final int y;
    private final int z;
    private final Material originalMaterial;
    private final BlockData originalData;

    public BlockStateRecord(Block block) {
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        this.originalMaterial = block.getType();
        this.originalData = block.getBlockData().clone();
    }

    public void restore(Block block) {
        block.setType(originalMaterial, false);
        block.setBlockData(originalData, false);
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
}
