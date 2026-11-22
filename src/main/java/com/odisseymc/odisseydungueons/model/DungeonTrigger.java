package com.odisseymc.odisseydungueons.model;

import com.odisseymc.odisseydungueons.util.CuboidRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * Triggers events based on player movement, block clicks, or mob deaths.
 */
public class DungeonTrigger {

    public enum TriggerType {
        ENTER_REGION,
        STEP_LOCATION,
        MOB_CLEAR,
        INTERACT_BLOCK,
        BUTTON_PRESS,
        ITEM_COLLECT,
        OBJECTIVE_COMPLETE
    }

    private final String id;
    private final TriggerType type;
    private final CuboidRegion region;
    private final String targetBlockLoc; // "x;y;z"
    private final List<TriggerAction> actions;
    private boolean oneTimeOnly;

    public DungeonTrigger(String id, TriggerType type, CuboidRegion region,
                          String targetBlockLoc, List<TriggerAction> actions, boolean oneTimeOnly) {
        this.id = id;
        this.type = type != null ? type : TriggerType.ENTER_REGION;
        this.region = region;
        this.targetBlockLoc = targetBlockLoc != null ? targetBlockLoc : "";
        this.actions = actions != null ? actions : new ArrayList<>();
        this.oneTimeOnly = oneTimeOnly;
    }

    public String getId() {
        return id;
    }

    public TriggerType getType() {
        return type;
    }

    public CuboidRegion getRegion() {
        return region;
    }

    public String getTargetBlockLoc() {
        return targetBlockLoc;
    }

    public List<TriggerAction> getActions() {
        return actions;
    }

    public boolean isOneTimeOnly() {
        return oneTimeOnly;
    }

    public void setOneTimeOnly(boolean oneTimeOnly) {
        this.oneTimeOnly = oneTimeOnly;
    }
}
