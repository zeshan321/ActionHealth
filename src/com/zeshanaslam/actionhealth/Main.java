package com.zeshanaslam.actionhealth;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    public SettingsManager settingsManager;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();

        // Load config settings
        settingsManager = new SettingsManager(this);


    }

    @Override
    public void onDisable() {

    }
}
