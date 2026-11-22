package com.odisseymc.odisseydungueons.model;

import com.odisseymc.odisseydungueons.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Represents a saved spawn location in a dungeon.
 */
public class DungeonSpawn {

    public enum SpawnType {
        MOB,
        BOSS,
        PLAYER,
        LOBBY,
        REWARD_CHEST
    }

    private final String id;
    private final SpawnType type;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public DungeonSpawn(String id, SpawnType type, String worldName, double x, double y, double z, float yaw, float pitch) {
        this.id = id;
        this.type = type;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public DungeonSpawn(String id, SpawnType type, Location loc) {
        this(id, type,
                loc.getWorld() != null ? loc.getWorld().getName() : "world",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }

    public String getId() {
        return id;
    }

    public SpawnType getType() {
        return type;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public Location toBukkitLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null && worldName != null) {
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().equalsIgnoreCase(worldName)) {
                    world = w;
                    break;
                }
            }
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public Location toInstanceLocation(World instanceWorld) {
        if (instanceWorld == null) {
            return toBukkitLocation();
        }
        return new Location(instanceWorld, x, y, z, yaw, pitch);
    }

    public String serialize() {
        return type.name() + ";" + worldName + ";" + x + ";" + y + ";" + z + ";" + yaw + ";" + pitch;
    }

    public static DungeonSpawn deserialize(String id, String serialized) {
        String[] parts = serialized.split(";");
        SpawnType type = SpawnType.valueOf(parts[0]);
        String worldName = parts[1];
        double x = Double.parseDouble(parts[2]);
        double y = Double.parseDouble(parts[3]);
        double z = Double.parseDouble(parts[4]);
        float yaw = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
        float pitch = parts.length > 6 ? Float.parseFloat(parts[6]) : 0f;
        return new DungeonSpawn(id, type, worldName, x, y, z, yaw, pitch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DungeonSpawn that)) return false;
        return Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0 &&
                Double.compare(that.z, z) == 0 && Objects.equals(id, that.id) &&
                type == that.type && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, worldName, x, y, z);
    }
}
