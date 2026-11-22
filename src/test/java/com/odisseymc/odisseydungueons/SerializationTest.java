package com.odisseymc.odisseydungueons;

import com.odisseymc.odisseydungueons.model.DungeonSpawn;
import com.odisseymc.odisseydungueons.util.CuboidRegion;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SerializationTest {

    @Test
    @DisplayName("Prueba 2: Serialización y deserialización de Spawn")
    void testSpawnSerialization() {
        DungeonSpawn original = new DungeonSpawn("spawn_test", DungeonSpawn.SpawnType.MOB, "dungeon_world", 123.456, 78.9, -45.67, 180.0f, -45.0f);
        String serialized = original.serialize();

        assertNotNull(serialized);
        assertTrue(serialized.startsWith("MOB;dungeon_world;123.456"));

        DungeonSpawn deserialized = DungeonSpawn.deserialize("spawn_test", serialized);
        assertNotNull(deserialized);
        assertEquals(original.getId(), deserialized.getId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getWorldName(), deserialized.getWorldName());
        assertEquals(original.getX(), deserialized.getX(), 0.001);
        assertEquals(original.getY(), deserialized.getY(), 0.001);
        assertEquals(original.getZ(), deserialized.getZ(), 0.001);
        assertEquals(original.getYaw(), deserialized.getYaw(), 0.01f);
        assertEquals(original.getPitch(), deserialized.getPitch(), 0.01f);
    }

    @Test
    @DisplayName("Prueba 2: Serialización y deserialización de Región Cuboide")
    void testCuboidRegionSerialization() {
        CuboidRegion region = new CuboidRegion("world", 10, 50, 10, 30, 80, 40);
        String serialized = region.serialize();

        assertEquals("world;10;50;10;30;80;40", serialized);

        CuboidRegion deserialized = CuboidRegion.deserialize(serialized);
        assertNotNull(deserialized);
        assertEquals(region.getWorldName(), deserialized.getWorldName());
        assertEquals(10, deserialized.getMinX());
        assertEquals(30, deserialized.getMaxX());
        assertEquals(50, deserialized.getMinY());
        assertEquals(80, deserialized.getMaxY());
        assertEquals(10, deserialized.getMinZ());
        assertEquals(40, deserialized.getMaxZ());
    }

    @Test
    @DisplayName("Prueba de formato de tiempo (TextUtil.formatDuration)")
    void testFormatDuration() {
        assertEquals("00s", TextUtil.formatDuration(0));
        assertEquals("00s", TextUtil.formatDuration(-10));
        assertEquals("35s", TextUtil.formatDuration(35));
        assertEquals("02m 15s", TextUtil.formatDuration(135));
        assertEquals("01h 00m 00s", TextUtil.formatDuration(3600));
        assertEquals("01h 25m 30s", TextUtil.formatDuration(3600 + (25 * 60) + 30));
    }
}
