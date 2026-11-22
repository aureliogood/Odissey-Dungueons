package com.odisseymc.odisseydungueons.cooldown;

import com.odisseymc.odisseydungueons.config.MessageManager;
import com.odisseymc.odisseydungueons.model.Dungeon;
import com.odisseymc.odisseydungueons.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Manages persistent cooldown tracking and scheduled global broadcast announcements
 * (e.g. 10m, 5m, 1m, and available) without message duplication.
 */
public class CooldownManager {

    private final JavaPlugin plugin;
    private final MessageManager messageManager;
    private final Supplier<Collection<Dungeon>> dungeonSupplier;

    // Track announced intervals per dungeon to avoid duplication
    private final Map<String, Set<Integer>> announcedIntervals = new ConcurrentHashMap<>();
    private final Map<String, Boolean> announcedAvailable = new ConcurrentHashMap<>();
    private BukkitTask announcementTask;

    public CooldownManager(JavaPlugin plugin, MessageManager messageManager, Supplier<Collection<Dungeon>> dungeonSupplier) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.dungeonSupplier = dungeonSupplier;
    }

    public void start() {
        announcementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAndBroadcast, 20L * 5, 20L * 5); // every 5 seconds
    }

    public void stop() {
        if (announcementTask != null) {
            announcementTask.cancel();
            announcementTask = null;
        }
    }

    private void checkAndBroadcast() {
        Collection<Dungeon> dungeons = dungeonSupplier.get();
        if (dungeons == null) return;

        for (Dungeon dungeon : dungeons) {
            if (!dungeon.isEnabled()) continue;

            String name = dungeon.getName();
            long remSeconds = dungeon.getRemainingCooldownSeconds();

            if (dungeon.isOnCooldown()) {
                announcedAvailable.put(name, false);
                Set<Integer> announced = announcedIntervals.computeIfAbsent(name, k -> new HashSet<>());

                int minutes = (int) (remSeconds / 60);

                // Check 10 minutes warning
                if (minutes == 10 && !announced.contains(10)) {
                    broadcastWarning(dungeon, "10 minutos");
                    announced.add(10);
                }
                // Check 5 minutes warning
                else if (minutes == 5 && !announced.contains(5)) {
                    broadcastWarning(dungeon, "5 minutos");
                    announced.add(5);
                }
                // Check 1 minute warning
                else if (minutes == 1 && !announced.contains(1)) {
                    broadcastWarning(dungeon, "1 minuto");
                    announced.add(1);
                }
            } else {
                // Cooldown has just expired
                if (Boolean.FALSE.equals(announcedAvailable.getOrDefault(name, true))) {
                    announcedAvailable.put(name, true);
                    announcedIntervals.remove(name);
                    broadcastAvailable(dungeon);
                }
            }
        }
    }

    private void broadcastWarning(Dungeon dungeon, String timeFormatted) {
        Map<String, String> ph = new HashMap<>();
        ph.put("dungeon", dungeon.getDisplayName());
        ph.put("time", timeFormatted);
        String msg = messageManager.get("dungeon-cooldown-announcement", ph);
        if (msg != null && !msg.isEmpty()) {
            Bukkit.broadcastMessage(msg);
        }
    }

    private void broadcastAvailable(Dungeon dungeon) {
        Map<String, String> ph = new HashMap<>();
        ph.put("dungeon", dungeon.getDisplayName());
        String msg = messageManager.get("dungeon-available-announcement", ph);
        if (msg != null && !msg.isEmpty()) {
            Bukkit.broadcastMessage(msg);
        }
    }
}
