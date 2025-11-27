package com.stmproject.wHit;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WHit extends JavaPlugin implements Listener, CommandExecutor {

    // Data Storage
    private final Map<UUID, Integer> comboStreaks = new HashMap<>();
    private final Map<UUID, Long> lastHitTimes = new HashMap<>();

    // Config Variables
    private Sound comboSound;
    private float comboPitch;
    private Sound critSound;
    private float critPitch;

    // Messages File Management
    private File messagesFile;
    private FileConfiguration messagesConfig;

    // Hex Pattern
    private final Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");

    @Override
    public void onEnable() {
        // Load configurations
        saveDefaultConfig();
        createMessagesFile();
        loadValues();

        // Register events and commands
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("whit").setExecutor(this);

        // Console Startup Message
        printConsoleBox(
                "&a&lwHit &7- &ePvP Combo System",
                "&7Version: &f" + getDescription().getVersion(),
                "&7Mode: &eStart after 2nd hit",
                "&7Status: &a&lACTIVE",
                "&7Developer: &b@wrusie"
        );
    }

    @Override
    public void onDisable() {
        comboStreaks.clear();
        lastHitTimes.clear();
        printConsoleBox(
                "&c&lwHit &7- &ePvP Combo System",
                "&7Status: &c&lDISABLED"
        );
    }

    // --- COMMAND HANDLING ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("whit.admin")) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }

            // Reload logic
            reloadConfig();
            createMessagesFile(); // Reload messages.yml
            loadValues();

            sender.sendMessage(getMessage("reload-success"));
            if (sender instanceof Player) getLogger().info("Config reloaded by " + sender.getName());
            return true;
        }

        sender.sendMessage(getMessage("command-usage"));
        return true;
    }

    // --- CONFIG & MESSAGE MANAGEMENT ---

    private void createMessagesFile() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void loadValues() {
        try {
            // Load sounds from config.yml
            comboSound = Sound.valueOf(getConfig().getString("sounds.combo-hit.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
            comboPitch = (float) getConfig().getDouble("sounds.combo-hit.pitch", 1.5);
            critSound = Sound.valueOf(getConfig().getString("sounds.crit-hit.sound", "ENTITY_ARROW_HIT_PLAYER"));
            critPitch = (float) getConfig().getDouble("sounds.crit-hit.pitch", 0.5);
        } catch (IllegalArgumentException e) {
            getLogger().severe("Configuration Error! Please check sound names in config.yml.");
        }
    }

    // Helper to get formatted message from messages.yml
    private String getMessage(String path) {
        String msg = messagesConfig.getString(path);
        if (msg == null) return "Message not found: " + path;
        // If a prefix is used in messages.yml, you can append it here, but I'll keep it simple.
        return color(msg);
    }

    // --- UTILITIES ---

    private String color(String message) {
        if (message == null) return "";
        Matcher matcher = hexPattern.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            try {
                matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString());
            } catch (Exception e) {}
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private void printConsoleBox(String... messages) {
        Bukkit.getConsoleSender().sendMessage(color("&8&m----------------------------------------"));
        for (String msg : messages) {
            Bukkit.getConsoleSender().sendMessage(color("   " + msg));
        }
        Bukkit.getConsoleSender().sendMessage(color("&8&m----------------------------------------"));
    }

    // --- PVP LOGIC ---

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // VICTIM LOGIC: Reset combo
        if (comboStreaks.containsKey(victim.getUniqueId())) {
            comboStreaks.remove(victim.getUniqueId());
            lastHitTimes.remove(victim.getUniqueId());
        }

        // ATTACKER LOGIC
        long lastHit = lastHitTimes.getOrDefault(damager.getUniqueId(), 0L);
        int currentStreak = comboStreaks.getOrDefault(damager.getUniqueId(), 0);

        // 1 second (1000ms) timeout check
        if (currentTime - lastHit > 1000) {
            currentStreak = 1; // Reset streak if time elapsed
        } else {
            currentStreak++;
        }

        // Update data
        comboStreaks.put(damager.getUniqueId(), currentStreak);
        lastHitTimes.put(damager.getUniqueId(), currentTime);

        // Trigger effects ONLY after the 2nd hit
        if (currentStreak > 1) {
            boolean isCritical = !damager.isOnGround() && damager.getVelocity().getY() < 0 && !damager.isClimbing() && !damager.isInWater();

            if (isCritical) {
                damager.playSound(damager.getLocation(), critSound, 1.0f, critPitch);
            } else {
                damager.playSound(damager.getLocation(), comboSound, 1.0f, comboPitch);
            }

            // Get Action Bar format from messages.yml
            String actionMsg = getMessage("actionbar-format").replace("%combo%", String.valueOf(currentStreak));
            sendActionBar(damager, actionMsg);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        comboStreaks.remove(uuid);
        lastHitTimes.remove(uuid);
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
}