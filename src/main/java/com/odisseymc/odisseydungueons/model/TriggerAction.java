package com.odisseymc.odisseydungueons.model;

import java.util.HashMap;
import java.util.Map;

/**
 * An action executed when a dungeon trigger fires.
 */
public class TriggerAction {

    public enum ActionType {
        OPEN_DOOR,
        CLOSE_DOOR,
        SPAWN_MOBS,
        START_WAVE,
        ACTIVATE_TRAP,
        SEND_MESSAGE,
        ACTIVATE_BOSS,
        UNLOCK_ROOM
    }

    private final ActionType type;
    private final Map<String, String> parameters;

    public TriggerAction(ActionType type, Map<String, String> parameters) {
        this.type = type;
        this.parameters = parameters != null ? parameters : new HashMap<>();
    }

    public ActionType getType() {
        return type;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getParam(String key, String defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }
}
