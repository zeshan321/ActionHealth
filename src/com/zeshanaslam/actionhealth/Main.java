package com.zeshanaslam.actionhealth;

import com.zeshanaslam.actionhealth.action.ActionHelper;
import com.zeshanaslam.actionhealth.action.ActionListener;
import com.zeshanaslam.actionhealth.action.ActionTask;
import com.zeshanaslam.actionhealth.commands.HealthCommand;
import com.zeshanaslam.actionhealth.config.ConfigStore;
import com.zeshanaslam.actionhealth.events.HealthListeners;
import com.zeshanaslam.actionhealth.utils.HealthUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main extends JavaPlugin {

    public ConfigStore configStore;
    public boolean worldGuardEnabled;
    public HealthUtil healthUtil;
    public int taskID = -1;
    public boolean mcMMOEnabled;
    public boolean mythicMobsEnabled;
    public boolean langUtilsEnabled;
    public BukkitTask actionTask;

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
        getServer().getPluginManager().registerEvents(new ActionListener(this, new ActionHelper(this)), this);

        // Register commands
        getCommand("Actionhealth").setExecutor(new HealthCommand(this));

        worldGuardEnabled = Bukkit.getServer().getPluginManager().isPluginEnabled("WorldGuard");

        if (Bukkit.getServer().getPluginManager().isPluginEnabled("mcMMO")) {
            mcMMOEnabled = true;
        }

        if (Bukkit.getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
            mythicMobsEnabled = true;
        }

        if (Bukkit.getServer().getPluginManager().isPluginEnabled("LangUtils")) {
            langUtilsEnabled = true;
        }

        actionTask = new ActionTask(this).runTaskTimer(this, 0, configStore.checkTicks);
    }

    @Override
    public void onDisable() {

    }
}
