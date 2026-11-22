package com.odisseymc.odisseydungueons.model;

/**
 * Dungeon difficulty levels.
 */
public enum Difficulty {
    NORMAL("&a&lNORMAL", 1.0, 1.0, 1.0, 1.0, 1.0),
    DIFICIL("&e&lDIFÍCIL", 1.5, 1.3, 1.25, 1.5, 1.35),
    PESADILLA("&c&lPESADILLA", 2.2, 1.8, 1.5, 2.5, 1.8);

    private final String displayName;
    private final double mobHpMultiplier;
    private final double mobDamageMultiplier;
    private final double mobCountMultiplier;
    private final double bossHpMultiplier;
    private final double bossDamageMultiplier;

    Difficulty(String displayName, double mobHpMultiplier, double mobDamageMultiplier,
               double mobCountMultiplier, double bossHpMultiplier, double bossDamageMultiplier) {
        this.displayName = displayName;
        this.mobHpMultiplier = mobHpMultiplier;
        this.mobDamageMultiplier = mobDamageMultiplier;
        this.mobCountMultiplier = mobCountMultiplier;
        this.bossHpMultiplier = bossHpMultiplier;
        this.bossDamageMultiplier = bossDamageMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMobHpMultiplier() {
        return mobHpMultiplier;
    }

    public double getMobDamageMultiplier() {
        return mobDamageMultiplier;
    }

    public double getMobCountMultiplier() {
        return mobCountMultiplier;
    }

    public double getBossHpMultiplier() {
        return bossHpMultiplier;
    }

    public double getBossDamageMultiplier() {
        return bossDamageMultiplier;
    }

    public static Difficulty fromString(String str) {
        if (str == null) return NORMAL;
        for (Difficulty d : values()) {
            if (d.name().equalsIgnoreCase(str) || d.name().replace("I", "Í").equalsIgnoreCase(str)) {
                return d;
            }
        }
        return NORMAL;
    }
}
