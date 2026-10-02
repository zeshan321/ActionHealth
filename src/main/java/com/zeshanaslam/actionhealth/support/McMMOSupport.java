package com.zeshanaslam.actionhealth.support;

import org.bukkit.metadata.MetadataValue;

public class McMMOSupport {

    public String getName(MetadataValue metadataValue) {
        // Use the interface method. mcMMO versions store the old name in different MetadataValue types.
        return metadataValue.asString();
    }
}
