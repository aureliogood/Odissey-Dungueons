package com.odisseymc.odisseydungueons;

import com.odisseymc.odisseydungueons.model.*;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class DungeonModelTest {

    @Test
    @DisplayName("Prueba 1: Creación de dungeon con valores por defecto")
    void testDungeonCreation() {
        Dungeon dungeon = new Dungeon("Catacumbas");
        assertEquals("Catacumbas", dungeon.getName());
        assertEquals("&b&lCatacumbas", dungeon.getDisplayName());
        assertTrue(dungeon.isEnabled());
        assertEquals(3600, dungeon.getCooldownSeconds());
        assertEquals(900, dungeon.getDurationSeconds());
        assertEquals(1, dungeon.getMinPlayers());
        assertEquals(4, dungeon.getMaxPlayers());
        assertFalse(dungeon.isOnCooldown());
    }

    @Test
    @DisplayName("Prueba 3 & 4: Cooldown y persistencia de tiempo")
    void testCooldownMechanisms() {
        Dungeon dungeon = new Dungeon("Minas");
        dungeon.setCooldownSeconds(60); // 60 segundos

        assertFalse(dungeon.isOnCooldown());
        assertEquals(0, dungeon.getRemainingCooldownSeconds());

        // Disparar cooldown
        dungeon.triggerCooldown();
        assertTrue(dungeon.isOnCooldown());
        assertTrue(dungeon.getRemainingCooldownSeconds() > 0 && dungeon.getRemainingCooldownSeconds() <= 60);

        // Formato de tiempo
        String formatted = dungeon.getFormattedCooldown();
        assertNotNull(formatted);
        assertFalse(formatted.isEmpty());

        // Simular persistencia tras reinicio (guardar timestamp en disco y restaurar)
        long savedTimestamp = dungeon.getCooldownExpireTimestamp();
        Dungeon restoredDungeon = new Dungeon("Minas");
        restoredDungeon.setCooldownExpireTimestamp(savedTimestamp);

        assertTrue(restoredDungeon.isOnCooldown());
        assertEquals(dungeon.getRemainingCooldownSeconds(), restoredDungeon.getRemainingCooldownSeconds());

        // Reset manual
        dungeon.resetCooldown();
        assertFalse(dungeon.isOnCooldown());
        assertEquals(0, dungeon.getRemainingCooldownSeconds());
    }

    @Test
    @DisplayName("Prueba 5: Gestión y registro de Spawns")
    void testSpawnManagement() {
        Dungeon dungeon = new Dungeon("Templo");
        DungeonSpawn s1 = new DungeonSpawn("mob_1", DungeonSpawn.SpawnType.MOB, "world", 100.5, 64.0, 200.5, 90f, 0f);
        DungeonSpawn s2 = new DungeonSpawn("mob_2", DungeonSpawn.SpawnType.MOB, "world", 110.5, 64.0, 205.5, 0f, 0f);

        dungeon.addMobSpawn(s1);
        dungeon.addMobSpawn(s2);
        assertEquals(2, dungeon.getMobSpawns().size());

        // Modificar spawn existente con mismo id
        DungeonSpawn s1Updated = new DungeonSpawn("mob_1", DungeonSpawn.SpawnType.MOB, "world", 102.0, 64.0, 202.0, 90f, 0f);
        dungeon.addMobSpawn(s1Updated);
        assertEquals(2, dungeon.getMobSpawns().size());

        // Eliminar spawn
        boolean removed = dungeon.removeMobSpawn("mob_2");
        assertTrue(removed);
        assertEquals(1, dungeon.getMobSpawns().size());
        assertFalse(dungeon.removeMobSpawn("non_existent"));
    }

    @Test
    @DisplayName("Prueba 10: Configuración y cálculo de Oleadas")
    void testWaves() {
        Dungeon dungeon = new Dungeon("Fortaleza");
        MobConfig mob1 = new MobConfig("z1", MobType.VANILLA, null, "", 5, 20.0, 3.0, 0.25, "&cZombie", 100.0, 1, "");
        MobConfig mob2 = new MobConfig("s1", MobType.VANILLA, null, "", 3, 20.0, 4.0, 0.25, "&7Esqueleto", 100.0, 1, "");

        WaveConfig wave1 = new WaveConfig(1, Arrays.asList(mob1, mob2), 5, "&6Oleada 1");
        dungeon.getWaves().add(wave1);

        assertEquals(1, dungeon.getWaves().size());
        assertEquals(8, wave1.getTotalExpectedMobCount());
        assertEquals(5, wave1.getDelaySeconds());
    }

    @Test
    @DisplayName("Prueba 11: Configuración de Boss y Recompensas")
    void testBossConfiguration() {
        Dungeon dungeon = new Dungeon("Infierno");
        BossConfig boss = new BossConfig(
                "&4Rey Demonio",
                MobType.VANILLA,
                null,
                "",
                500.0,
                15.0,
                "demon_model",
                "¡El rey despierta!",
                "¡El rey ha caído!",
                Collections.emptyList()
        );
        dungeon.setBossConfig(boss);

        assertNotNull(dungeon.getBossConfig());
        assertEquals("&4Rey Demonio", dungeon.getBossConfig().getName());
        assertEquals(500.0, dungeon.getBossConfig().getHealth());
        assertEquals("demon_model", dungeon.getBossConfig().getModelId());
    }

    @Test
    @DisplayName("Prueba 15: Modificadores de Dificultades")
    void testDifficultyMultipliers() {
        Difficulty normal = Difficulty.NORMAL;
        assertEquals(1.0, normal.getMobHpMultiplier());
        assertEquals(1.0, normal.getBossHpMultiplier());

        Difficulty hard = Difficulty.DIFICIL;
        assertTrue(hard.getMobHpMultiplier() > 1.0);
        assertTrue(hard.getBossHpMultiplier() > 1.0);
        assertTrue(hard.getMobDamageMultiplier() > 1.0);

        Difficulty nightmare = Difficulty.PESADILLA;
        assertTrue(nightmare.getMobHpMultiplier() > hard.getMobHpMultiplier());
        assertTrue(nightmare.getBossHpMultiplier() > hard.getBossHpMultiplier());
        assertTrue(nightmare.getMobDamageMultiplier() > hard.getMobDamageMultiplier());
    }
}
