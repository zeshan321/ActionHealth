package com.zeshanaslam.actionhealth.commands;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.config.ConfigStore;
import com.zeshanaslam.actionhealth.utils.Colors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HealthCommand implements TabExecutor {

    private static final String RELOAD_PERMISSION = "ActionHealth.Reload";
    private static final String TOGGLE_PERMISSION = "ActionHealth.Toggle";

    private final Main plugin;

    public HealthCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        if (subcommand.equals("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sendNoPermission(sender);
                return true;
            }

            plugin.reloadConfig();
            plugin.updateConfig();
            plugin.configStore = new ConfigStore(plugin);
            plugin.toggles.load(plugin.configStore.rememberToggle);
            plugin.applySettings();
            send(sender, plugin.configStore.reloadMessage);
            return true;
        }

        if (subcommand.equals("toggle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("ActionHealth toggle can only run in-game.");
                return true;
            }

            if (!sender.hasPermission(TOGGLE_PERMISSION)) {
                sendNoPermission(sender);
                return true;
            }

            Player player = (Player) sender;
            boolean off = !plugin.toggles.isToggled(player.getUniqueId());
            plugin.toggles.setToggled(player.getUniqueId(), off, plugin.configStore.rememberToggle);

            String message = off ? plugin.configStore.disableMessage : plugin.configStore.enableMessage;
            send(player, plugin.healthUtil.replacePlaceholders(message, "name", player.getName()));
            return true;
        }

        for (String line : plugin.configStore.helpMessage) {
            send(sender, plugin.healthUtil.replacePlaceholders(line, "label", label));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();

        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if ("reload".startsWith(typed) && sender.hasPermission(RELOAD_PERMISSION)) options.add("reload");
        if ("toggle".startsWith(typed) && sender instanceof Player && sender.hasPermission(TOGGLE_PERMISSION)) options.add("toggle");
        return options;
    }

    private void sendNoPermission(CommandSender sender) {
        send(sender, plugin.configStore.noPermissionMessage);
    }

    /**
     * Sends a message from the config, with its colors. An empty message is not sent.
     */
    private static void send(CommandSender sender, String message) {
        if (message != null && !message.isEmpty()) {
            sender.sendMessage(Colors.translate(message));
        }
    }
}
