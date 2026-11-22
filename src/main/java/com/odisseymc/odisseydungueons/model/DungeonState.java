package com.odisseymc.odisseydungueons.model;

/**
 * Lifecycle states of a dungeon template or active instance.
 */
public enum DungeonState {
    IDLE("&aDisponible"),
    IN_USE("&cEn curso"),
    COOLDOWN("&eEn Cooldown"),
    RESETTING("&6Reiniciando"),
    DISABLED("&8Desactivada");

    private final String display;

    DungeonState(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
