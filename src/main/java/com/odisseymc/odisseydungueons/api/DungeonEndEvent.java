package com.odisseymc.odisseydungueons.api;

import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DungeonEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonInstance instance;
    private final boolean completed; // true if won, false if failed/stopped

    public DungeonEndEvent(DungeonInstance instance, boolean completed) {
        this.instance = instance;
        this.completed = completed;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public boolean isCompleted() {
        return completed;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
