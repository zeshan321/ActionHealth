package com.zeshanaslam.actionhealth.support;

import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

public class McMMOSupport {

    public String getName(MetadataValue metadataValue) {
        /*if (metadataValue instanceof OldName) {
            OldName oldName = (OldName) metadataValue;
            return oldName.asString();
        }*/

        FixedMetadataValue fixedMetadataValue = (FixedMetadataValue) metadataValue;
        return fixedMetadataValue.asString();
    }
}
