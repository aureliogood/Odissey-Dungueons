package com.odisseymc.odisseydungueons;

import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.model.Difficulty;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.model.DungeonState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class InstanceLogicTest {

    @Test
    @DisplayName("Prueba 13 & 14: Aislamiento e instancias privadas independientes")
    void testInstanceIsolation() {
        Dungeon dungeon = new Dungeon("Laberinto");

        // Instancia para Grupo 1
        DungeonInstance inst1 = new DungeonInstance("inst_laberinto_1", dungeon, Difficulty.NORMAL, null, false);
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        inst1.getPlayers().add(player1);
        inst1.getPlayers().add(player2);

        // Instancia para Grupo 2
        DungeonInstance inst2 = new DungeonInstance("inst_laberinto_2", dungeon, Difficulty.PESADILLA, null, false);
        UUID player3 = UUID.randomUUID();
        inst2.getPlayers().add(player3);

        // Aislamiento verificado
        assertNotEquals(inst1.getInstanceId(), inst2.getInstanceId());
        assertEquals(2, inst1.getPlayerCount());
        assertEquals(1, inst2.getPlayerCount());
        assertTrue(inst1.containsPlayer(player1));
        assertFalse(inst1.containsPlayer(player3));
        assertTrue(inst2.containsPlayer(player3));
        assertFalse(inst2.containsPlayer(player1));

        assertEquals(Difficulty.NORMAL, inst1.getDifficulty());
        assertEquals(Difficulty.PESADILLA, inst2.getDifficulty());
    }

    @Test
    @DisplayName("Prueba 10: Control de Mobs y Oleadas en Instancia")
    void testInstanceMobTracking() {
        Dungeon dungeon = new Dungeon("Torre");
        DungeonInstance inst = new DungeonInstance("inst_torre_1", dungeon, Difficulty.NORMAL, null, false);

        UUID mob1 = UUID.randomUUID();
        UUID mob2 = UUID.randomUUID();

        inst.getAliveMobUuids().add(mob1);
        inst.getAliveMobUuids().add(mob2);
        assertEquals(2, inst.getAliveMobCount());

        // Muerte de mob 1
        inst.getAliveMobUuids().remove(mob1);
        assertEquals(1, inst.getAliveMobCount());

        // Muerte de mob 2 -> 0 enemigos restantes
        inst.getAliveMobUuids().remove(mob2);
        assertEquals(0, inst.getAliveMobCount());
    }

    @Test
    @DisplayName("Prueba de triggers de un solo uso (OneTimeOnly)")
    void testTriggerStateTracking() {
        Dungeon dungeon = new Dungeon("Cripta");
        DungeonInstance inst = new DungeonInstance("inst_cripta_1", dungeon, Difficulty.NORMAL, null, false);

        assertFalse(inst.hasTriggered("door_trigger_1"));
        inst.markTriggered("door_trigger_1");
        assertTrue(inst.hasTriggered("door_trigger_1"));
        assertFalse(inst.hasTriggered("door_trigger_2"));
    }
}
