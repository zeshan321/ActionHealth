package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.Main;
import com.zeshanaslam.actionhealth.action.data.Tagged;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.UUID;

public class ActionTask extends BukkitRunnable {

    private final Main main;

    public ActionTask(Main main) {
        this.main = main;
    }

    @Override
    public void run() {
        for (UUID key : main.configStore.actionStore.tagged.keySet()) {
            Iterator<Tagged> taggedIterator = main.configStore.actionStore.tagged.get(key).iterator();
            while (taggedIterator.hasNext()) {
                Tagged tagged = taggedIterator.next();
                long secondsLeft = ((tagged.timestamp / 1000) + main.configStore.actionStore.tagLength)
                        - (System.currentTimeMillis() / 1000);

                if (secondsLeft <= 0) {
                    taggedIterator.remove();
                }
            }
        }
    }
}
