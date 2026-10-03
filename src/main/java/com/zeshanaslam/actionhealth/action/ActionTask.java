package com.zeshanaslam.actionhealth.action;

import com.zeshanaslam.actionhealth.Main;

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
        main.configStore.actionStore.expire(System.currentTimeMillis());
    }
}
