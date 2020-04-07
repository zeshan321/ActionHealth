package com.zeshanaslam.actionhealth.action;
import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.action.data.Action;
import com.zeshanaslam.actionhealth.action.data.Tagged;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

public class ActionStore {

    private final Main main;
    public boolean enabled;
    public int tagLength;
    public int tagAmount;
    public HashMap<ActionType, List<Action>> events;
    public HashMap<UUID, List<Tagged>> tagged = new HashMap<>();
    public boolean isUsingAnyDamageCause = false;

    public ActionStore(Main main) {
        this.main = main;
        enabled = main.getConfig().getBoolean("Action.Enabled");
        tagLength = main.getConfig().getInt("Action.TagLength");
        tagAmount  = main.getConfig().getInt("Action.TagAmount");
        events = new HashMap<>();

        for (String action: main.getConfig().getConfigurationSection("Action.Events").getKeys(false)) {
            for (String type: main.getConfig().getConfigurationSection("Action.Events." + action).getKeys(false)) {
                String output = main.getConfig().getString("Action.Events." + action + "." + type);
                ActionType actionType = ActionType.valueOf(action);

                if (actionType == ActionType.DAMAGE && output != null && output.equalsIgnoreCase("any")) {
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
        if (damager == damaged)
            return;

        if (tagged.containsKey(damager)) {
            // Remove oldest if > tag amount to add new player
            if (tagAmount != -1 && tagged.get(damager).size() >= tagAmount)
                tagged.get(damager).remove(0);

            tagged.get(damager).add(new Tagged(damager, damaged, System.currentTimeMillis()));
        } else {
            List<Tagged> taggedList = new ArrayList<>();
            taggedList.add(new Tagged(damager, damaged, System.currentTimeMillis()));

            tagged.put(damager, taggedList);
        }
    }

    public void remove(UUID remove) {
        tagged.remove(remove);
        tagged.values().forEach(l -> l.removeIf(c -> c.damaged.equals(remove)));
    }

    private void sendMessage(LivingEntity entity, String message, Optional<Double> health) {
        for (List<Tagged> taggedList: tagged.values()) {
            for (Tagged tagged: taggedList) {
                if (tagged.damaged.equals(entity.getUniqueId())) {
                    Player damager = Bukkit.getServer().getPlayer(tagged.damager);

                    String output = main.healthUtil.getOutput(health.orElseGet(entity::getHealth), message, damager, entity);

                    if (output != null)
                        main.healthUtil.sendActionBar(damager, output);
                }
            }
        }
    }

    public void triggerAction(ActionType actionType, LivingEntity entity, String name) {
        triggerAction(actionType, entity, name, Optional.empty());
    }

    public void triggerAction(ActionType actionType, LivingEntity entity, String name, Optional<Double> health) {
        if (main.configStore.actionStore.events.containsKey(actionType)) {
            List<Action> actionList = new ArrayList<>(main.configStore.actionStore.events.get(actionType));
            Optional<Action> actionOptional = actionList.stream()
                    .filter(a -> a.material.equalsIgnoreCase(name)).findAny();

            if (actionOptional.isPresent()) {
                Action action = actionOptional.get();
                main.configStore.actionStore.sendMessage(entity, action.output, health);
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
