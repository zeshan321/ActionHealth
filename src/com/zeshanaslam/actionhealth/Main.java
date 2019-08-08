package com.zeshanaslam.actionhealth;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.zeshanaslam.actionhealth.commands.HealthCommand;
import com.zeshanaslam.actionhealth.config.ConfigStore;
import com.zeshanaslam.actionhealth.events.HealthListeners;
import com.zeshanaslam.actionhealth.support.WorldGuardAPI;
import com.zeshanaslam.actionhealth.utils.HealthUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main extends JavaPlugin {

    public ConfigStore configStore;
    public WorldGuardPlugin worldGuardPlugin;
    public WorldGuardAPI worldGuardAPI;
    public HealthUtil healthUtil;
    public int taskID = -1;
    public boolean mcMMOEnabled;
    public boolean mythicMobsEnabled;
    public boolean langUtilsEnabled;

    public List<UUID> toggle = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Register health util
        this.healthUtil = new HealthUtil(this);

        // Load config settings
        configStore = new ConfigStore(this);

        // Create player folder
        File file = new File("plugins/ActionHealth/players/");
        file.mkdirs();

        // Register listeners
        getServer().getPluginManager().registerEvents(new HealthListeners(this), this);

        // Register commands
        getCommand("Actionhealth").setExecutor(new HealthCommand(this));

        if (Bukkit.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            this.worldGuardPlugin = ((WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard"));
            this.worldGuardAPI = new WorldGuardAPI(this);
        }

        if (Bukkit.getServer().getPluginManager().isPluginEnabled("mcMMO")) {
            mcMMOEnabled = true;
        }

        if (Bukkit.getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            mythicMobsEnabled = true;
        }

        if (Bukkit.getServer().getPluginManager().isPluginEnabled("LangUtils")) {
            langUtilsEnabled = true;
        }
    }

    @Override
    public void onDisable() {

    }
}
