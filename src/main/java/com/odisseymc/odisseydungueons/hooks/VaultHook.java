package com.odisseymc.odisseydungueons.hooks;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private boolean enabled = false;
    private Economy economy = null;

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                enabled = (economy != null);
            }
        }
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public void giveMoney(Player player, double amount) {
        if (isEnabled() && player != null && amount > 0) {
            economy.depositPlayer(player, amount);
        }
    }
}
