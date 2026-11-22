package com.odisseymc.odisseydungueons.api;

import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DungeonWaveClearEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonInstance instance;
    private final int waveNumber;

    public DungeonWaveClearEvent(DungeonInstance instance, int waveNumber) {
        this.instance = instance;
        this.waveNumber = waveNumber;
    }

    public DungeonInstance getInstance() {
        return instance;
    }

    public int getWaveNumber() {
        return waveNumber;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
