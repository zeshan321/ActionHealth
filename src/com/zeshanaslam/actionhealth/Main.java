package com.zeshanaslam.actionhealth;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main extends JavaPlugin {

    public SettingsManager settingsManager;
    public WorldGuardPlugin worldGuardPlugin;
    public HealthUtil healthUtil;

    public List<UUID> toggle = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Register health util
        this.healthUtil = new HealthUtil(this);

        // Load config settings
        settingsManager = new SettingsManager(this);

        // Create player folder
        File file = new File("plugins/ActionHealth/players/");
        file.mkdirs();

        // Register listeners
        getServer().getPluginManager().registerEvents(new HealthListeners(this), this);


        // Register command
        getCommand("Actionhealth").setExecutor(new HealthCommand(this));

        if (Bukkit.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            this.worldGuardPlugin = ((WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard"));
        }
    }

    @Override
    public void onDisable() {

    }
}
