package com.zeshanaslam.actionhealth.config;

import com.zeshanaslam.actionhealth.LookThread;
import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.action.ActionStore;
import com.zeshanaslam.actionhealth.utils.Scheduler;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ConfigStore {

    public String healthMessage;
    public String healthMessageOther;
    public boolean usePerms;
    public boolean showMobs;
    public boolean showPlayers;
    public boolean delay;
    public int delayTick;
    public boolean checkPvP;
    public boolean stripName;
    public boolean rememberToggle;
    public boolean canSee;
    public boolean invisiblePotion;
    public boolean spectatorMode;
    public boolean useClientLanguage;
    public String filledHeartIcon;
    public String halfHeartIcon;
    public String emptyHeartIcon;
    public String absorptionIcon;
    public int displayTime;
    public List<String> worlds = new ArrayList<>();
    public HashMap<String, String> translate = new HashMap<>();
    public List<String> regions = new ArrayList<>();
    public boolean showOnLook;
    public double lookDistance;
    public List<String> blacklist = new ArrayList<>();
    public String toggleMessage;
    public String enableMessage;
    public String disableMessage;
    public String noPermissionMessage;
    public boolean hasMVdWPlaceholderAPI;
    public boolean hasPlaceholderAPI;
    public int limitHealth;
    public boolean showNPC;
    public boolean showMiniaturePets;
    public double lookDot;
    public double lookTolerance;
    public long checkTicks;
    public ActionStore actionStore;
    public List<String> whitelist = new ArrayList<>();
    public String upperLimit;
    public int upperLimitStart;
    public int upperLimitLength;
    public boolean allowMetrics;
    public int healthDecimals;
    public TreeMap<Integer, String> healthColors = new TreeMap<>();
    public boolean enabledByDefault;
    public boolean updateCheck;
    public String reloadMessage;
    public List<String> helpMessage;
    public String updateMessage;

    public ConfigStore(Main plugin) {
        Logger logger = plugin.getLogger();

        // Clear settings for reloads
        worlds.clear();
        regions.clear();
        blacklist.clear();
        translate.clear();
        whitelist.clear();

        // Check if using MVdWPlaceholderAPI
        hasMVdWPlaceholderAPI = Bukkit.getPluginManager().isPluginEnabled("MVdWPlaceholderAPI");

        // Check if using placeholderAPI
        hasPlaceholderAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        // Get settings from config
        healthMessage = plugin.getConfig().getString("Health Message");

        healthMessageOther = "";
        if (plugin.getConfig().contains("Non Player Message")) {
            healthMessageOther = plugin.getConfig().getString("Non Player Message");
        }

        usePerms = plugin.getConfig().getBoolean("Use Permissions");
        showMobs = plugin.getConfig().getBoolean("Show Mob");
        showPlayers = plugin.getConfig().getBoolean("Show Player");
        showNPC = plugin.getConfig().getBoolean("Show NPC");
        delay = plugin.getConfig().getBoolean("Delay Message");
        if (plugin.getConfig().contains("Delay Tick")) {
            delayTick = plugin.getConfig().getInt("Delay Tick");
        } else {
            delayTick = 1;
        }

        checkPvP = plugin.getConfig().getBoolean("Region PvP");
        stripName = plugin.getConfig().getBoolean("Strip Name");
        filledHeartIcon = plugin.getConfig().getString("Full Health Icon");
        halfHeartIcon = plugin.getConfig().getString("Half Health Icon");
        emptyHeartIcon = plugin.getConfig().getString("Empty Health Icon");
        // Missing in configs from before 3.7.0, which keeps their look unchanged.
        absorptionIcon = plugin.getConfig().getString("Absorption Icon", "");
        displayTime = plugin.getConfig().getInt("Display Time", -1);
        if (plugin.getConfig().getBoolean("Name Change")) {
            for (String s : plugin.getConfig().getStringList("Name")) {
                String[] split = s.split(" = ", 2);
                if (split.length != 2) {
                    logger.warning("Name: '" + s + "' was skipped. Use the format 'Snow Golem = New name'.");
                    continue;
                }
                translate.put(split[0], split[1]);
            }
        }
        useClientLanguage = plugin.getConfig().getBoolean("Use Client Language");

        // Load disabled regions
        regions = plugin.getConfig().getStringList("Disabled regions");

        worlds = plugin.getConfig().getStringList("Disabled worlds");

        if (plugin.getConfig().contains("Remember Toggle")) {
            rememberToggle = plugin.getConfig().getBoolean("Remember Toggle");
        } else {
            rememberToggle = false;
        }

        // New options
        if (plugin.getConfig().contains("Blacklist")) {
            blacklist.addAll(plugin.getConfig().getStringList("Blacklist").stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).collect(Collectors.toList()));
        }

        if (plugin.getConfig().contains("Whitelist")) {
            whitelist.addAll(plugin.getConfig().getStringList("Whitelist").stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).collect(Collectors.toList()));
        }

        if (plugin.getConfig().contains("Toggle Message")) {
            toggleMessage = plugin.getConfig().getString("Toggle Message");
        }

        if (plugin.getConfig().contains("On Enable")) {
            enableMessage = plugin.getConfig().getString("On Enable");
        } else {
            enableMessage = "&7ActionHealth has been &cenabled&7.";
        }

        if (plugin.getConfig().contains("On Disable")) {
            disableMessage = plugin.getConfig().getString("On Disable");
        } else {
            disableMessage = "&7ActionHealth has been &cdisabled&7.";
        }

        noPermissionMessage = plugin.getConfig().getString("No Permission", "&cYou do not have permission to do that.");
        reloadMessage = plugin.getConfig().getString("Reload Message", "&cActionHealth &7has been reloaded!");
        helpMessage = plugin.getConfig().contains("Help Message") ? plugin.getConfig().getStringList("Help Message")
                : Arrays.asList("&cActionHealth Commands:", "&7/{label} reload", "&7/{label} toggle");
        updateMessage = plugin.getConfig().getString("Update Message", "");

        healthDecimals = Math.max(0, Math.min(3, plugin.getConfig().getInt("Health Decimals", 0)));
        ConfigurationSection colors = plugin.getConfig().getConfigurationSection("Health Colors");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                try {
                    healthColors.put(Integer.parseInt(key.trim()), colors.getString(key));
                } catch (NumberFormatException e) {
                    logger.warning("Health Colors: '" + key + "' was skipped. Use a percentage from 0 to 100, for example 50: '&e'.");
                }
            }
        }
        // Missing in configs from before 3.9.0, which keeps their behavior unchanged.
        enabledByDefault = plugin.getConfig().getBoolean("Enabled By Default", true);
        // On by default, also for configs from before 3.9.0. ConfigUpdater adds the option with its comment.
        updateCheck = plugin.getConfig().getBoolean("Update Check", true);

        if (plugin.getConfig().contains("Can See")) {
            canSee = plugin.getConfig().getBoolean("Can See");
        } else {
            canSee = true;
        }

        if (plugin.getConfig().contains("Invisible Potion")) {
            invisiblePotion = plugin.getConfig().getBoolean("Invisible Potion");
        } else {
            invisiblePotion = true;
        }

        if (plugin.getConfig().contains("Spectator Mode")) {
            spectatorMode = plugin.getConfig().getBoolean("Spectator Mode");
        } else {
            spectatorMode = true;
        }

        if (plugin.getConfig().contains("Limit Health")) {
            if (plugin.getConfig().isBoolean("Limit Health")) {
                limitHealth = 10;
            } else {
                limitHealth = plugin.getConfig().getInt("Limit Health");
            }
        }
        showMiniaturePets = plugin.getConfig().getBoolean("ShowMiniaturePets");
        actionStore = new ActionStore(plugin);

        if (plugin.getConfig().contains("LookValues")) {
            lookDot = plugin.getConfig().getDouble("LookValues.Dot");
            lookTolerance = plugin.getConfig().getDouble("LookValues.Tolerance");
        } else {
            lookDot = 0;
            lookTolerance = 4;
        }

        if (plugin.getConfig().contains("LookValues.CheckTicks")) {
            checkTicks = plugin.getConfig().getLong("LookValues.CheckTicks");
        } else {
            checkTicks = 2;
        }

        if (plugin.lookTask != null) {
            plugin.lookTask.cancel();
            plugin.lookTask = null;
        }

        showOnLook = plugin.getConfig().getBoolean("Show On Look");
        lookDistance = plugin.getConfig().getDouble("Look Distance");
        if (showOnLook) {
            plugin.lookTask = Scheduler.runTimer(plugin, new LookThread(plugin), checkTicks);
        }

        // Empty disables the upper limit, as the config comment says.
        upperLimit = null;
        String limit = plugin.getConfig().getString("Upper Limit Health");
        if (limit != null && !limit.trim().isEmpty()) {
            String[] limits = limit.split("->");
            try {
                upperLimitStart = Integer.parseInt(limits[0].trim());
                upperLimitLength = Integer.parseInt(limits[1].trim());
                upperLimit = limit;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                logger.warning("Upper Limit Health: '" + limit + "' was skipped. Use the format '40 -> 10', or leave it empty.");
            }
        }

        if (plugin.getConfig().contains("Allow Metrics")) {
            allowMetrics = plugin.getConfig().getBoolean("Allow Metrics");
        } else {
            allowMetrics = true;
        }

        if (allowMetrics && plugin.metrics == null) {
            plugin.metrics = new Metrics(plugin, 11639);
        }
    }

    public boolean isUsingWhiteList() {
        return !whitelist.isEmpty();
    }
}
