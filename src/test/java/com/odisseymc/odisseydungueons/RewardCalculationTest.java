package com.odisseymc.odisseydungueons;

import com.odisseymc.odisseydungueons.model.Difficulty;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.model.RewardItem;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RewardCalculationTest {

    @Test
    @DisplayName("Prueba 12: Recompensas individuales y por dificultad")
    void testRewardPerDifficultyFallback() {
        Dungeon dungeon = new Dungeon("Tesoro");

        RewardItem globalReward = new RewardItem("global_coin", RewardItem.RewardType.VAULT_MONEY, "100", 1, 100.0, "100 Monedas", Material.GOLD_NUGGET, Collections.emptyList());
        dungeon.getRewards().add(globalReward);

        // Sin recompensas específicas para DIFICIL, debe devolver las globales
        List<RewardItem> hardRewards = dungeon.getRewardsForDifficulty(Difficulty.DIFICIL);
        assertEquals(1, hardRewards.size());
        assertEquals("global_coin", hardRewards.get(0).getId());

        // Con recompensas específicas para PESADILLA
        RewardItem nightmareReward = new RewardItem("nightmare_star", RewardItem.RewardType.VANILLA_ITEM, "", 1, 5.0, "&cEstrella del Vacío", Material.NETHER_STAR, Collections.emptyList());
        List<RewardItem> nightmareList = new ArrayList<>();
        nightmareList.add(nightmareReward);
        dungeon.getDifficultyRewards().put(Difficulty.PESADILLA, nightmareList);

        List<RewardItem> evaluatedNightmare = dungeon.getRewardsForDifficulty(Difficulty.PESADILLA);
        assertEquals(1, evaluatedNightmare.size());
        assertEquals("nightmare_star", evaluatedNightmare.get(0).getId());
        assertEquals(5.0, evaluatedNightmare.get(0).getChancePercentage());
    }

    @Test
    @DisplayName("Prueba 12: Probabilidad de recompensas")
    void testRewardChances() {
        RewardItem guaranteed = new RewardItem("g1", RewardItem.RewardType.EXPERIENCE, "50", 1, 100.0, "50 XP", Material.EXPERIENCE_BOTTLE, Collections.emptyList());
        assertEquals(100.0, guaranteed.getChancePercentage());

        RewardItem rare = new RewardItem("r1", RewardItem.RewardType.COMMAND, "give %player% diamond 1", 1, 20.0, "Diamante", Material.DIAMOND, Collections.emptyList());
        assertEquals(20.0, rare.getChancePercentage());

        RewardItem legendary = new RewardItem("l1", RewardItem.RewardType.COMMAND, "crate key %player% 1", 1, 5.0, "Llave Legendaria", Material.TRIPWIRE_HOOK, Collections.emptyList());
        assertEquals(5.0, legendary.getChancePercentage());
    }
}
