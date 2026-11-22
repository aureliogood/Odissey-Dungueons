package com.odisseymc.odisseydungueons.api;

import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DungeonBossKillEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonInstance instance;
    private final Entity bossEntity;
    private final Player killer;

    public DungeonBossKillEvent(DungeonInstance instance, Entity bossEntity, Player killer) {
        this.instance = instance;
        this.bossEntity = bossEntity;
        this.killer = killer;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public Entity getBossEntity() {
        return bossEntity;
    }

    public Player getKiller() {
        return killer;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
