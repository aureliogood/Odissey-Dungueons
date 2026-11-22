package com.odisseymc.odisseydungueons.model;

import com.odisseymc.odisseydungueons.util.CuboidRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a room/chamber inside a dungeon with independent bounds, triggers, doors, and spawns.
 */
public class DungeonRoom {

    private final String id;
    private final String name;
    private final int order;
    private final CuboidRegion bounds;
    private final List<DungeonSpawn> spawns;
    private final List<DoorBlock> doors;
    private final List<DungeonTrigger> triggers;
    private boolean locked;

    public DungeonRoom(String id, String name, int order, CuboidRegion bounds,
                       List<DungeonSpawn> spawns, List<DoorBlock> doors, List<DungeonTrigger> triggers) {
        this.id = id;
        this.name = name != null ? name : id;
        this.order = order;
        this.bounds = bounds;
        this.spawns = spawns != null ? spawns : new ArrayList<>();
        this.doors = doors != null ? doors : new ArrayList<>();
        this.triggers = triggers != null ? triggers : new ArrayList<>();
        this.locked = order > 1; // 1st room unlocked by default
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getOrder() {
        return order;
    }

    public CuboidRegion getBounds() {
        return bounds;
    }

    public List<DungeonSpawn> getSpawns() {
        return spawns;
    }

    public List<DoorBlock> getDoors() {
        return doors;
    }

    public List<DungeonTrigger> getTriggers() {
        return triggers;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
