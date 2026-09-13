package com.example.platform.artifact.api.event;
import com.example.platform.outbox.api.event.*;
import java.util.List;
@org.springframework.stereotype.Component
public final class ArtifactOutboxEvents implements OutboxEventCatalog {
    public static final OutboxEventType<ArtifactCreatedEvent> ARTIFACTCREATEDEVENT = new OutboxEventType<>("artifact.created", 2, "artifact", ArtifactCreatedEvent.class, ArtifactCreatedEvent::artifactId, ArtifactCreatedEvent::tenantId);
    public static final OutboxEventType<AssetRegisteredEvent> ASSETREGISTEREDEVENT = new OutboxEventType<>("asset.registered", 1, "ASSET", AssetRegisteredEvent.class, AssetRegisteredEvent::assetId, AssetRegisteredEvent::tenantId);
    public static final OutboxEventType<AssetMetadataUpdatedEvent> ASSETMETADATAUPDATEDEVENT = new OutboxEventType<>("asset.metadata.updated", 1, "ASSET", AssetMetadataUpdatedEvent.class, AssetMetadataUpdatedEvent::assetId, event -> null);
    public static final OutboxEventType<AssetEnrichedEvent> ASSETENRICHEDEVENT = new OutboxEventType<>("asset.enriched", 2, "ASSET", AssetEnrichedEvent.class, AssetEnrichedEvent::assetId, AssetEnrichedEvent::tenantId);
    @Override public List<OutboxEventType<?>> types() { return List.of(ARTIFACTCREATEDEVENT, ASSETREGISTEREDEVENT, ASSETMETADATAUPDATEDEVENT, ASSETENRICHEDEVENT); }
}
