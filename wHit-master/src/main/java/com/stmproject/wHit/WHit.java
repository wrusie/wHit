package com.stmproject.wHit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WHit extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- SPIGOT RESOURCE ID ---
    // IMPORTANT: Replace '00000' with your actual Spigot Resource ID!
    private static final int RESOURCE_ID = 130416;

    // Data Storage (Optimized for fast access)
    private final Map<UUID, Integer> comboStreaks = new HashMap<>(64);
    private final Map<UUID, Long> lastHitTimes = new HashMap<>(64);
    private final Set<UUID> disabledPlayers = new HashSet<>(64);

    // Config Variables
    private Sound comboSound;
    private float comboPitch;
    private Sound critSound;
    private float critPitch;

    // File Management
    private File messagesFile;
    private FileConfiguration messagesConfig;

    // OPTIMIZATION: Cache Strings
    // Store pre-parsed Components to avoid parsing on every hit.
    private String rawActionBar;
    private final Map<String, Component> messageCache = new HashMap<>();

    // Update Checker Cache
    private String latestVersion = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createMessagesFile();
        loadValues();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("whit").setExecutor(this);
        getCommand("whit").setTabCompleter(this);

        // Async Update Checker
        if (getConfig().getBoolean("update-checker", true)) {
            new UpdateChecker(this, RESOURCE_ID).getVersion(version -> {
                if (!getDescription().getVersion().equalsIgnoreCase(version)) {
                    latestVersion = version;
                    getLogger().warning("New version available: v" + version);
                }
            });
        }

        printConsoleBox(
                "&a&lwHit &7- &ePvP Combo System",
                "&7Version: &f" + getDescription().getVersion(),
                "&7Status: &a&lACTIVE"
        );
    }

    @Override
    public void onDisable() {
        comboStreaks.clear();
        lastHitTimes.clear();
        disabledPlayers.clear();
        messageCache.clear();
    }

    // --- COMMAND HANDLING ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(getCachedMessage("command-usage"));
            return true;
        }

        // Command: Reload
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("whit.admin")) {
                sender.sendMessage(getCachedMessage("no-permission"));
                return true;
            }
            reloadConfig();
            createMessagesFile();
            loadValues();
            messageCache.clear(); // Clear cache on reload
            sender.sendMessage(getCachedMessage("reload-success"));
            return true;
        }

        // Command: Toggle
        if (args[0].equalsIgnoreCase("toggle")) {
            // Self Toggle
            if (args.length == 1) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cOnly players can use this command."));
                    return true;
                }
                if (disabledPlayers.contains(player.getUniqueId())) {
                    disabledPlayers.remove(player.getUniqueId());
                    player.sendMessage(getCachedMessage("toggle-on"));
                } else {
                    disabledPlayers.add(player.getUniqueId());
                    player.sendMessage(getCachedMessage("toggle-off"));
                }
                return true;
            }

            // Admin Toggle (Manage other players)
            if (args.length == 2) {
                if (!sender.hasPermission("whit.admin")) {
                    sender.sendMessage(getCachedMessage("no-permission"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(getCachedMessage("player-not-found"));
                    return true;
                }

                // Cannot use cache here due to dynamic placeholders (%player%)
                if (disabledPlayers.contains(target.getUniqueId())) {
                    disabledPlayers.remove(target.getUniqueId());
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            messagesConfig.getString("toggle-other-on", "").replace("%player%", target.getName())));
                    target.sendMessage(getCachedMessage("toggle-by-admin"));
                } else {
                    disabledPlayers.add(target.getUniqueId());
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            messagesConfig.getString("toggle-other-off", "").replace("%player%", target.getName())));
                    target.sendMessage(getCachedMessage("toggle-by-admin"));
                }
                return true;
            }
        }
        sender.sendMessage(getCachedMessage("command-usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = Arrays.asList("reload", "toggle");
            StringUtil.copyPartialMatches(args[0], options, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            if (sender.hasPermission("whit.admin")) return null; // Default player list
        }
        Collections.sort(completions);
        return completions;
    }

    // --- CONFIG & FILE LOADING ---
    private void createMessagesFile() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) saveResource("messages.yml", false);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void loadValues() {
        try {
            comboSound = Sound.valueOf(getConfig().getString("sounds.combo-hit.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
            comboPitch = (float) getConfig().getDouble("sounds.combo-hit.pitch", 1.5);
            critSound = Sound.valueOf(getConfig().getString("sounds.crit-hit.sound", "ENTITY_ARROW_HIT_PLAYER"));
            critPitch = (float) getConfig().getDouble("sounds.crit-hit.pitch", 0.5);

            // Raw string loader
            rawActionBar = messagesConfig.getString("actionbar-format", "&#FFA500&lCOMBO: &e&l%combo%");

        } catch (IllegalArgumentException e) {
            getLogger().severe("Config Error! Check sound names.");
        }
    }

    // --- ADVENTURE API HELPERS ---

    // Ultra-fast method to retrieve Components from cache.
    private Component getCachedMessage(String path) {
        if (messageCache.containsKey(path)) return messageCache.get(path);

        String msg = messagesConfig.getString(path);
        if (msg == null) return Component.text("Msg not found: " + path);

        // Parse hex colors and deserialize
        Component comp = LegacyComponentSerializer.legacyAmpersand().deserialize(formatHex(msg));
        messageCache.put(path, comp);
        return comp;
    }

    private String formatHex(String message) {
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "&x");
            char[] chars = matcher.group(1).toCharArray();
            for (char c : chars) {
                buffer.append("&").append(c);
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace('&', '§');
    }

    private void printConsoleBox(String... messages) {
        Bukkit.getConsoleSender().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&8&m----------------------------------------"));
        for (String msg : messages) {
            Bukkit.getConsoleSender().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("   " + msg));
        }
        Bukkit.getConsoleSender().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&8&m----------------------------------------"));
    }

    // --- MAIN EVENT (OPTIMIZED) ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) return;

        // Fast return if disabled (HashSet is O(1))
        if (disabledPlayers.contains(damager.getUniqueId())) return;

        long currentTime = System.currentTimeMillis();
        UUID damagerId = damager.getUniqueId();
        UUID victimId = victim.getUniqueId();

        // Victim Cleanup
        if (comboStreaks.containsKey(victimId)) {
            comboStreaks.remove(victimId);
            lastHitTimes.remove(victimId);
        }

        // Attacker Logic
        Long lastHit = lastHitTimes.get(damagerId);
        int currentStreak;

        // 1 Second Timeout Rule
        if (lastHit != null && currentTime - lastHit <= 1000) {
            Integer streak = comboStreaks.get(damagerId);
            currentStreak = (streak == null ? 0 : streak) + 1;
        } else {
            currentStreak = 1;
        }

        comboStreaks.put(damagerId, currentStreak);
        lastHitTimes.put(damagerId, currentTime);

        // Action Trigger (Only after 2nd hit)
        if (currentStreak > 1) {
            // Velocity check is expensive, strictly done only when needed.
            boolean isCritical = !damager.isOnGround() && damager.getVelocity().getY() < 0 && !damager.isClimbing() && !damager.isInWater();

            if (isCritical) {
                damager.playSound(damager.getLocation(), critSound, 1.0f, critPitch);
            } else {
                damager.playSound(damager.getLocation(), comboSound, 1.0f, comboPitch);
            }

            // --- ACTION BAR OPTIMIZATION ---
            // Adventure API Implementation
            String finalMsg = formatHex(rawActionBar).replace("%combo%", String.valueOf(currentStreak));
            damager.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(finalMsg));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        comboStreaks.remove(uuid);
        lastHitTimes.remove(uuid);
        disabledPlayers.remove(uuid);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("update-checker", true) && latestVersion != null && event.getPlayer().hasPermission("whit.admin.notify")) {
            List<String> msgs = messagesConfig.getStringList("update-available");
            for (String msg : msgs) {
                event.getPlayer().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(formatHex(msg)
                        .replace("%current%", getDescription().getVersion())
                        .replace("%new%", latestVersion)));
            }
        }
    }
}