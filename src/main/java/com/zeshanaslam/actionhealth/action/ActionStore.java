package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.action.data.Action;
import com.zeshanaslam.actionhealth.action.data.Tagged;
import com.zeshanaslam.actionhealth.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class ActionStore {

    private final Main main;
    public boolean enabled;
    public int tagLength;
    public int tagAmount;
    public HashMap<ActionType, List<Action>> events;
    // Thread safe, because Folia runs events on many threads.
    public Map<UUID, List<Tagged>> tagged = new ConcurrentHashMap<>();
    public boolean isUsingAnyDamageCause = false;

    public ActionStore(Main main) {
        this(main, main.getConfig(), main.getLogger());
    }

    ActionStore(Main main, ConfigurationSection config, Logger logger) {
        this.main = main;
        enabled = config.getBoolean("Action.Enabled");
        tagLength = config.getInt("Action.TagLength");
        tagAmount = config.getInt("Action.TagAmount");
        events = new HashMap<>();

        ConfigurationSection eventSection = config.getConfigurationSection("Action.Events");
        if (eventSection == null) return;

        for (String action : eventSection.getKeys(false)) {
            ActionType actionType;
            try {
                actionType = ActionType.valueOf(action.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warning("Action.Events: '" + action + "' was skipped. Use CONSUME, RIGHTCLICK, LEFTCLICK, SWAP or DAMAGE.");
                continue;
            }

            ConfigurationSection typeSection = eventSection.getConfigurationSection(action);
            if (typeSection == null) {
                logger.warning("Action.Events: '" + action + "' was skipped. It needs a list of items or damage causes with a message.");
                continue;
            }

            for (String type : typeSection.getKeys(false)) {
                String output = typeSection.getString(type);

                // ANY is the damage cause, not the message: 'ANY: <message>'.
                if (actionType == ActionType.DAMAGE && type.equalsIgnoreCase("any")) {
                    isUsingAnyDamageCause = true;
                }

                if (events.containsKey(actionType)) {
                    events.get(actionType).add(new Action(type, output));
                } else {
                    List<Action> actions = new ArrayList<>();
                    actions.add(new Action(type, output));

                    events.put(actionType, actions);
                }
            }
        }
    }

    public void addTag(UUID damager, UUID damaged) {
        if (damager.equals(damaged))
            return;

        tagged.compute(damager, (key, taggedList) -> {
            if (taggedList == null) taggedList = new CopyOnWriteArrayList<>();
            // One tag for each target. A new hit on the same target only starts its time again.
            taggedList.removeIf(tag -> tag.damaged.equals(damaged));
            // Remove the oldest tags to make room for the new target.
            while (tagAmount != -1 && !taggedList.isEmpty() && taggedList.size() >= tagAmount)
                taggedList.remove(0);

            taggedList.add(new Tagged(damager, damaged, System.currentTimeMillis()));
            return taggedList;
        });
    }

    /**
     * Removes the tags that are older than "Action.TagLength" seconds.
     */
    public void expire(long nowMillis) {
        long now = nowMillis / 1000;
        for (UUID damager : tagged.keySet()) {
            // Atomic for each player, so a tag that is added at the same time is not lost.
            tagged.computeIfPresent(damager, (key, list) -> {
                list.removeIf(tag -> (tag.timestamp / 1000) + tagLength - now <= 0);
                return list.isEmpty() ? null : list;
            });
        }
    }

    public void remove(UUID remove) {
        tagged.remove(remove);
        tagged.values().forEach(l -> l.removeIf(c -> c.damaged.equals(remove)));
    }

    private void sendMessage(LivingEntity entity, String message, Optional<Double> health) {
        // Read on the thread of the entity, before the message moves to the thread of each player.
        double value = health.orElseGet(entity::getHealth);
        for (List<Tagged> taggedList : tagged.values()) {
            for (Tagged tagged : taggedList) {
                if (tagged.damaged.equals(entity.getUniqueId())) {
                    Player damager = Bukkit.getServer().getPlayer(tagged.damager);
                    if (damager == null)
                        continue;

                    // On Folia, the player can be in a region that another thread owns.
                    Scheduler.runFor(main, damager, () -> {
                        // The same rules as the health message: worlds, regions, permissions and the toggle.
                        if (!main.healthUtil.matchesFilters(damager, entity) || main.toggles.isToggled(damager.getUniqueId()))
                            return;

                        String output = main.healthUtil.getOutput(value, message, damager, entity);

                        if (output != null)
                            main.healthUtil.sendActionBar(damager, output);
                    });
                }
            }
        }
    }

    public void triggerAction(ActionType actionType, LivingEntity entity, String name) {
        triggerAction(actionType, entity, name, Optional.empty());
    }

    public void triggerAction(ActionType actionType, LivingEntity entity, String name, Optional<Double> health) {
        if (events.containsKey(actionType)) {
            List<Action> actionList = new ArrayList<>(events.get(actionType));
            Optional<Action> actionOptional = actionList.stream()
                    .filter(a -> a.material.equalsIgnoreCase(name)).findAny();

            if (actionOptional.isPresent()) {
                Action action = actionOptional.get();
                sendMessage(entity, action.output, health);
            }
        }
    }

    public enum ActionType {
        CONSUME,
        SWAP,
        RIGHTCLICK,
        LEFTCLICK,
        DAMAGE
    }
}
