package com.odisseymc.odisseydungueons.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Utility class for location serialization, deserialization, and world mapping.
 */
public final class LocationUtil {

    private LocationUtil() {}

    /**
     * Serializes a Location to a string: "world;x;y;z;yaw;pitch"
     */
    public static String serialize(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "";
        }
        return String.format("%s;%.3f;%.3f;%.3f;%.2f;%.2f",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch());
    }

    /**
     * Deserializes a string formatted as "world;x;y;z;yaw;pitch" or "world;x;y;z" to a Location.
     */
    public static Location deserialize(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }

        String[] parts = str.split(";");
        if (parts.length < 4) {
            return null;
        }

        World world = Bukkit.getWorld(parts[0]);
        if (world == null && parts[0] != null) {
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().equalsIgnoreCase(parts[0])) {
                    world = w;
                    break;
                }
            }
        }
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
        float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Clones a location mapped to a different target world.
     */
    public static Location toWorld(Location loc, World targetWorld) {
        if (loc == null || targetWorld == null) {
            return null;
        }
        return new Location(targetWorld, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }
}
