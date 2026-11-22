package com.odisseymc.odisseydungueons.trigger;

import com.odisseymc.odisseydungueons.boss.BossManager;
import com.odisseymc.odisseydungueons.instance.DungeonInstance;
import com.odisseymc.odisseydungueons.instance.InstanceManager;
import com.odisseymc.odisseydungueons.model.*;
import com.odisseymc.odisseydungueons.util.TextUtil;
import com.odisseymc.odisseydungueons.wave.WaveManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Evaluates in-dungeon triggers (region entrance, block click, button press, mob clears)
 * and executes configured actions (open doors, spawn waves, activate traps, send alerts).
 */
public class TriggerManager implements Listener {

    private final JavaPlugin plugin;
    private final InstanceManager instanceManager;
    private final WaveManager waveManager;
    private final BossManager bossManager;

    public TriggerManager(JavaPlugin plugin, InstanceManager instanceManager, WaveManager waveManager, BossManager bossManager) {
        this.plugin = plugin;
        this.instanceManager = instanceManager;
        this.waveManager = waveManager;
        this.bossManager = bossManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        // Optimization: only check if player changed block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        DungeonInstance instance = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (instance == null) return;

        Location to = event.getTo();
        for (DungeonRoom room : instance.getDungeon().getRooms()) {
            for (DungeonTrigger trigger : room.getTriggers()) {
                if (trigger.getType() == DungeonTrigger.TriggerType.ENTER_REGION ||
                        trigger.getType() == DungeonTrigger.TriggerType.STEP_LOCATION) {

                    if (trigger.isOneTimeOnly() && instance.hasTriggered(trigger.getId())) {
                        continue;
                    }

                    if (trigger.getRegion() != null && trigger.getRegion().contains(to)) {
                        instance.markTriggered(trigger.getId());
                        executeTriggerActions(instance, room, trigger.getActions());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        DungeonInstance instance = instanceManager.findInstanceByPlayer(player.getUniqueId());
        if (instance == null) return;

        Block clicked = event.getClickedBlock();
        String blockCoord = clicked.getX() + ";" + clicked.getY() + ";" + clicked.getZ();

        for (DungeonRoom room : instance.getDungeon().getRooms()) {
            for (DungeonTrigger trigger : room.getTriggers()) {
                if (trigger.getType() == DungeonTrigger.TriggerType.INTERACT_BLOCK ||
                        trigger.getType() == DungeonTrigger.TriggerType.BUTTON_PRESS) {

                    if (trigger.isOneTimeOnly() && instance.hasTriggered(trigger.getId())) {
                        continue;
                    }

                    if (trigger.getTargetBlockLoc().equalsIgnoreCase(blockCoord)) {
                        instance.markTriggered(trigger.getId());
                        executeTriggerActions(instance, room, trigger.getActions());
                    }
                }
            }
        }
    }

    public void handleMobClear(DungeonInstance instance) {
        if (instance == null) return;
        for (DungeonRoom room : instance.getDungeon().getRooms()) {
            for (DungeonTrigger trigger : room.getTriggers()) {
                if (trigger.getType() == DungeonTrigger.TriggerType.MOB_CLEAR) {
                    if (trigger.isOneTimeOnly() && instance.hasTriggered(trigger.getId())) {
                        continue;
                    }
                    instance.markTriggered(trigger.getId());
                    executeTriggerActions(instance, room, trigger.getActions());
                }
            }
        }
    }

    public void executeTriggerActions(DungeonInstance instance, DungeonRoom room, List<TriggerAction> actions) {
        World world = instance.getWorld();
        if (world == null || actions == null) return;

        for (TriggerAction action : actions) {
            switch (action.getType()) {
                case OPEN_DOOR:
                    for (DoorBlock door : room.getDoors()) {
                        door.setOpen(world, true);
                    }
                    break;

                case CLOSE_DOOR:
                    for (DoorBlock door : room.getDoors()) {
                        door.setOpen(world, false);
                    }
                    break;

                case SEND_MESSAGE:
                    String msg = action.getParam("message", "");
                    instance.broadcast(msg);
                    break;

                case START_WAVE:
                    int waveNum = Integer.parseInt(action.getParam("wave", "1"));
                    waveManager.startWave(instance, waveNum);
                    break;

                case ACTIVATE_BOSS:
                    bossManager.spawnBoss(instance);
                    break;

                case UNLOCK_ROOM:
                    room.setLocked(false);
                    instance.broadcast("&a&l¡Has desbloqueado la siguiente sala: " + room.getName() + "!");
                    break;

                case ACTIVATE_TRAP:
                    // Simple harmless visual trap or player effect
                    instance.broadcast("&c&l¡CUIDADO! ¡Una trampa ha sido activada!");
                    break;

                case SPAWN_MOBS:
                    // Extra mobs spawn
                    break;
            }
        }
    }
}
