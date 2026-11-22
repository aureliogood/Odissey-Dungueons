package com.odisseymc.odisseydungueons.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single reward entry granted to players.
 */
public class RewardItem {

    public enum RewardType {
        VAULT_MONEY,
        EXPERIENCE,
        COMMAND,
        VANILLA_ITEM,
        ITEMSADDER,
        NEXO,
        EXECUTABLEITEMS
    }

    private final String id;
    private final RewardType type;
    private final String value; // Command string, item ID, or amount
    private final int amount;
    private final double chancePercentage; // 0.0 - 100.0%
    private final String displayName;
    private final Material material; // For vanilla items or GUI display
    private final List<String> lore;

    public RewardItem(String id, RewardType type, String value, int amount,
                      double chancePercentage, String displayName, Material material, List<String> lore) {
        this.id = id;
        this.type = type != null ? type : RewardType.VAULT_MONEY;
        this.value = value != null ? value : "0";
        this.amount = Math.max(1, amount);
        this.chancePercentage = Math.min(100.0, Math.max(0.01, chancePercentage));
        this.displayName = displayName != null ? displayName : "";
        this.material = material != null ? material : Material.CHEST;
        this.lore = lore != null ? lore : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public RewardType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getAmount() {
        return amount;
    }

    public double getChancePercentage() {
        return chancePercentage;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public List<String> getLore() {
        return lore;
    }
}
