package com.odisseymc.odisseydungueons.rewards;

import com.odisseymc.odisseydungueons.hooks.HookManager;
import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.model.Difficulty;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.model.RewardItem;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Evaluates and delivers individual, isolated dungeon rewards to participating players.
 */
public class RewardManager {

    private final JavaPlugin plugin;
    private final HookManager hookManager;

    public RewardManager(JavaPlugin plugin, HookManager hookManager) {
        this.plugin = plugin;
        this.hookManager = hookManager;
    }

    public void distributeRewards(DungeonInstance instance) {
        if (instance == null || instance.isTestMode()) {
            return;
        }

        Dungeon dungeon = instance.getDungeon();
        Difficulty difficulty = instance.getDifficulty();
        List<RewardItem> rewards = dungeon.getRewardsForDifficulty(difficulty);

        if (rewards.isEmpty()) {
            return;
        }

        for (UUID uuid : instance.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                givePlayerRewards(player, dungeon, difficulty, rewards);
            }
        }
    }

    public void givePlayerRewards(Player player, Dungeon dungeon, Difficulty difficulty, List<RewardItem> rewards) {
        List<String> rewardedNames = new ArrayList<>();

        for (RewardItem reward : rewards) {
            double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
            if (roll <= reward.getChancePercentage()) {
                grantSingleReward(player, dungeon, difficulty, reward);
                String display = reward.getDisplayName().isEmpty() ? reward.getId() : reward.getDisplayName();
                rewardedNames.add(TextUtil.colorize(display));
            }
        }

        if (!rewardedNames.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&a&l¡RECOMPENSAS RECIBIDAS!"));
            for (String name : rewardedNames) {
                player.sendMessage(TextUtil.colorize(" &8» &f" + name));
            }
        }
    }

    private void grantSingleReward(Player player, Dungeon dungeon, Difficulty difficulty, RewardItem reward) {
        switch (reward.getType()) {
            case VAULT_MONEY:
                try {
                    double amount = Double.parseDouble(reward.getValue()) * reward.getAmount();
                    hookManager.getVaultHook().giveMoney(player, amount);
                } catch (NumberFormatException ignored) {}
                break;

            case EXPERIENCE:
                try {
                    int xp = Integer.parseInt(reward.getValue()) * reward.getAmount();
                    player.giveExp(xp);
                } catch (NumberFormatException ignored) {}
                break;

            case COMMAND:
                String cmd = reward.getValue()
                        .replace("%player%", player.getName())
                        .replace("%dungeon%", dungeon.getName())
                        .replace("%difficulty%", difficulty.name());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                break;

            case VANILLA_ITEM:
                Material mat = reward.getMaterial();
                if (mat != null && mat != Material.AIR) {
                    ItemStack item = new ItemStack(mat, reward.getAmount());
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (!reward.getDisplayName().isEmpty()) {
                            meta.setDisplayName(TextUtil.colorize(reward.getDisplayName()));
                        }
                        if (!reward.getLore().isEmpty()) {
                            meta.setLore(reward.getLore().stream().map(TextUtil::colorize).collect(Collectors.toList()));
                        }
                        item.setItemMeta(meta);
                    }
                    giveItemSafely(player, item);
                }
                break;

            case ITEMSADDER:
                hookManager.getItemsAdderHook().giveItem(player, reward.getValue(), reward.getAmount());
                break;

            case NEXO:
                hookManager.getNexoHook().giveItem(player, reward.getValue(), reward.getAmount());
                break;

            case EXECUTABLEITEMS:
                hookManager.getExecutableItemsHook().giveItem(player, reward.getValue(), reward.getAmount());
                break;
        }
    }

    private void giveItemSafely(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
    }
}
