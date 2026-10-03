package com.zeshanaslam.actionhealth;

import com.zeshanaslam.actionhealth.action.ActionHelper;
import com.zeshanaslam.actionhealth.action.ActionListener;
import com.zeshanaslam.actionhealth.action.ActionTask;
import com.zeshanaslam.actionhealth.commands.HealthCommand;
import com.zeshanaslam.actionhealth.config.ConfigStore;
import com.zeshanaslam.actionhealth.config.ConfigUpdater;
import com.zeshanaslam.actionhealth.config.ToggleStore;
import com.zeshanaslam.actionhealth.events.HealthListeners;
import com.zeshanaslam.actionhealth.utils.HealthUtil;
import com.zeshanaslam.actionhealth.utils.Scheduler;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class Main extends JavaPlugin {

    public ConfigStore configStore;
    public boolean worldGuardEnabled;
    public HealthUtil healthUtil;
    public Scheduler.Task lookTask;
    public boolean mcMMOEnabled;
    public boolean mythicMobsEnabled;
    public boolean langUtilsEnabled;
    public Scheduler.Task actionTask;
    public Metrics metrics;
    public ToggleStore toggles;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfig();

        // Register health util
        this.healthUtil = new HealthUtil(this);
        toggles = new ToggleStore(getDataFolder(), getLogger());

        // Load config settings
        configStore = new ConfigStore(this);
        toggles.load(configStore.rememberToggle);

        // Register listeners
        getServer().getPluginManager().registerEvents(new HealthListeners(this), this);
        getServer().getPluginManager().registerEvents(new ActionListener(this, new ActionHelper(this)), this);

        // Register commands
        PluginCommand command = getCommand("Actionhealth");
        HealthCommand healthCommand = new HealthCommand(this);
        command.setExecutor(healthCommand);
        command.setTabCompleter(healthCommand);

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

        // Tags last whole seconds, so a check every second is enough.
        actionTask = Scheduler.runTimer(this, new ActionTask(this), 20);
    }

    @Override
    public void onDisable() {
        if (lookTask != null) lookTask.cancel();
        if (actionTask != null) actionTask.cancel();
    }

    /**
     * Adds options from new versions to config.yml, then reloads it.
     */
    public void updateConfig() {
        List<String> added = ConfigUpdater.update(this);
        if (!added.isEmpty()) {
            getLogger().info("Added new options to config.yml: " + String.join(", ", added));
            reloadConfig();
        }
    }
}
