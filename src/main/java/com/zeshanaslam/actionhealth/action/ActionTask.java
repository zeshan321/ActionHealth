package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.Main;

import java.util.UUID;

/**
 * Removes combat tags that are older than "Action.TagLength". Uses no entities, so it can run
 * on the global region thread on Folia.
 */
public class ActionTask implements Runnable {

    private final Main main;

    public ActionTask(Main main) {
        this.main = main;
    }

    @Override
    public void run() {
        ActionStore actionStore = main.configStore.actionStore;
        long now = System.currentTimeMillis() / 1000;
        for (UUID damager : actionStore.tagged.keySet()) {
            // Atomic for each player, so a tag that is added at the same time is not lost.
            actionStore.tagged.computeIfPresent(damager, (key, list) -> {
                list.removeIf(tagged -> (tagged.timestamp / 1000) + actionStore.tagLength - now <= 0);
                return list.isEmpty() ? null : list;
            });
        }
    }
}
