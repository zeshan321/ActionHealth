package com.zeshanaslam.actionhealth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

public class HealthCommand implements CommandExecutor {

    private final Main plugin;

    public HealthCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 0) {
            sender.sendMessage(ChatColor.RED + "ActionHealth Commands:");
            sender.sendMessage(ChatColor.GRAY + "/ActionHealth reload");
            sender.sendMessage(ChatColor.GRAY + "/ActionHealth toggle");
        } else {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("ActionHealth.Reload")) {
                    return false;
                }

                plugin.reloadConfig();
                plugin.settingsManager = new SettingsManager(plugin);
                sender.sendMessage(ChatColor.RED + "ActionHealth " + ChatColor.GRAY + "has been reloaded!");
                return true;
            }

            if (args[0].equalsIgnoreCase("toggle")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;

                    if (plugin.toggle.contains(player.getUniqueId())) {
                        plugin.toggle.remove(player.getUniqueId());

                        player.sendMessage(ChatColor.GRAY + "ActionHealth has been " + ChatColor.RED + "enabled" + ChatColor.GRAY + ".");
                    } else {
                        plugin.toggle.add(player.getUniqueId());

                        player.sendMessage(ChatColor.GRAY + "ActionHealth has been " + ChatColor.RED + "disabled" + ChatColor.GRAY + ".");
                    }

                    if (plugin.settingsManager.rememberToggle) {
                        FileHandler fileHandler = new FileHandler("plugins/ActionHealth/players/" + player.getUniqueId() + ".yml");
                        fileHandler.set("toggle", plugin.toggle.contains(player.getUniqueId()));

                        fileHandler.save();
                    }
                } else {
                    sender.sendMessage("ActionHealth toggle can only run in-game.");
                }
                return true;
            }

            sender.sendMessage(ChatColor.RED + "ActionHealth Commands:");
            sender.sendMessage(ChatColor.GRAY + "/ActionHealth reload");
            sender.sendMessage(ChatColor.GRAY + "/ActionHealth toggle");
        }

        return true;
    }
}
