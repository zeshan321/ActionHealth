package com.zeshanaslam.actionhealth;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class SettingsManager {

    public String healthMessage;
    public boolean usePerms;
    public boolean showMobs;
    public boolean showPlayers;
    public boolean delay;
    public boolean checkPvP;
    public boolean limitHealth;
    public boolean stripName;
    public boolean rememberToggle;
    public String filledHeartIcon;
    public String halfHeartIcon;
    public String emptyHeartIcon;
    public List<String> worlds = new ArrayList<>();
    public HashMap<String, String> translate = new HashMap<>();
    public List<String> regions = new ArrayList<>();

    public String mcVersion;
    public boolean useOldMethods;

    public SettingsManager(Main plugin) {
        healthMessage = plugin.getConfig().getString("Health Message");
        usePerms = plugin.getConfig().getBoolean("Use Permissions");
        showMobs = plugin.getConfig().getBoolean("Show Mob");
        showPlayers = plugin.getConfig().getBoolean("Show Player");
        delay = plugin.getConfig().getBoolean("Delay Message");
        checkPvP = plugin.getConfig().getBoolean("Region PvP");
        limitHealth = plugin.getConfig().getBoolean("Limit Health");
        stripName = plugin.getConfig().getBoolean("Strip Name");
        filledHeartIcon = plugin.getConfig().getString("Full Health Icon");
        halfHeartIcon = plugin.getConfig().getString("Half Health Icon");
        emptyHeartIcon = plugin.getConfig().getString("Empty Health Icon");
        if (plugin.getConfig().getBoolean("Name Change")) {
            for (String s : plugin.getConfig().getStringList("Name")) {
                String[] split = s.split(" = ");
                translate.put(split[0], split[1]);
            }
        }

        // Load disabled regions
        regions = plugin.getConfig().getStringList("Disabled regions");

        worlds = plugin.getConfig().getStringList("Disabled worlds");

        // Check if using protocol build
        mcVersion = Bukkit.getServer().getClass().getPackage().getName();
        mcVersion = mcVersion.substring(mcVersion.lastIndexOf(".") + 1);

        useOldMethods = mcVersion.equalsIgnoreCase("v1_8_R1") || mcVersion.equalsIgnoreCase("v1_7_");

        if (plugin.getConfig().contains("Remember Toggle")) {
            rememberToggle = plugin.getConfig().getBoolean("Remember Toggle");
        } else {
            rememberToggle = false;
        }
    }
}
