package com.odisseymc.odisseydungueons.config;

import com.odisseymc.odisseydungueons.util.TextUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized message manager ensuring all messages respect the configured prefix and color formats.
 */
public class MessageManager {

    private final JavaPlugin plugin;
    private final File messageFile;
    private FileConfiguration config;
    private String prefix = "&b&lODISSEY DUNGUEONS &8»";
    private final Map<String, String> cachedMessages = new HashMap<>();

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messageFile = new File(plugin.getDataFolder(), "messages.yml");
        load();
    }

    public void load() {
        if (!messageFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(messageFile);

        // Copy defaults from jar if missing and save to disk
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            boolean modified = false;
            for (String key : defConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defConfig.get(key));
                    modified = true;
                }
            }
            if (modified) {
                try {
                    config.save(messageFile);
                    plugin.getLogger().info("[OdisseyDungueons] messages.yml actualizado en disco con nuevas claves.");
                } catch (Exception ignored) {}
            }
        }

        prefix = config.getString("prefix", "&b&lODISSEY DUNGUEONS &8»");
        cachedMessages.clear();

        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                cachedMessages.put(key, config.getString(key));
            }
        }
    }

    public String getRaw(String key) {
        return cachedMessages.getOrDefault(key, key);
    }

    public String get(String key, Map<String, String> placeholders) {
        String raw = getRaw(key);
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        // Apply prefix
        raw = raw.replace("%prefix%", prefix);

        // Apply placeholders
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        return TextUtil.colorize(raw);
    }

    public String get(String key) {
        return get(key, null);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String msg = get(key, placeholders);
        if (msg != null && !msg.isEmpty()) {
            sender.sendMessage(msg);
        }
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, null);
    }

    public void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(TextUtil.colorize(message)));
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;
        player.sendTitle(TextUtil.colorize(title), TextUtil.colorize(subtitle), fadeIn, stay, fadeOut);
    }

    public void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public String getPrefix() {
        return prefix;
    }
}
