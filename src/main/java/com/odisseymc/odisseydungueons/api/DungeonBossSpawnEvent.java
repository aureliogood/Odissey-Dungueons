package com.odisseymc.odisseydungueons.api;

import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DungeonBossSpawnEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonInstance instance;
    private final Entity bossEntity;

    public DungeonBossSpawnEvent(DungeonInstance instance, Entity bossEntity) {
        this.instance = instance;
        this.bossEntity = bossEntity;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public Entity getBossEntity() {
        return bossEntity;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
