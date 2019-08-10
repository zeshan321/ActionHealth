package com.zeshanaslam.actionhealth.action.data;

import java.util.UUID;

public class Tagged {

    public UUID damager;
    public UUID damaged;
    public long timestamp;

    public Tagged(UUID damager, UUID damaged, long timestamp) {
        this.damager = damager;
        this.damaged = damaged;
        this.timestamp = timestamp;
    }
}
