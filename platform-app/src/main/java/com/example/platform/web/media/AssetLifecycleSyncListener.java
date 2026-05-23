package com.example.platform.web.media;

import com.example.platform.render.app.timeline.TimelineAssetGcService;
import com.example.platform.shared.events.ArtifactTombstonedEvent;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Links catalog artifact tombstone to timeline {@code assetRegistry} entries with the same storage URI.
 */
@Component
public class AssetLifecycleSyncListener {

    private static final Logger log = LoggerFactory.getLogger(AssetLifecycleSyncListener.class);

    private final TimelineAssetGcService timelineAssetGcService;

    public AssetLifecycleSyncListener(TimelineAssetGcService timelineAssetGcService) {
        this.timelineAssetGcService = timelineAssetGcService;
    }

    @EventListener
    public void onArtifactTombstoned(ArtifactTombstonedEvent event) {
        int synced = timelineAssetGcService.tombstoneRegistryByStorageUri(
                event.projectId(), event.storageUri(), TenantContext.get());
        if (synced > 0) {
            log.info("Synced {} timeline registry entries for artifact tombstone id={}",
                    synced, event.artifactId());
        }
    }
}
