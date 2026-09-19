package com.example.platform.render.app.asset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.platform.outbox.coordination.*;
import com.example.platform.outbox.api.event.OutboxDeliveryContext;
import com.example.platform.shared.events.AssetPublishedEvent;
import com.example.platform.sandbox.execution.TaskCapability;
import org.junit.jupiter.api.Test;
class AssetSearchConsumerTest {
    @Test void publicationIntentUsesDurableIdentityAndAuthoritativeEnvelopeScope() {
        var coordinator=mock(PlatformCoordinationService.class);var consumer=new AssetSearchConsumer(coordinator);
        var event=new AssetPublishedEvent("asset","v1","MEDIA","project","PUBLISHED");
        assertThrows(IllegalStateException.class,()->consumer.onAssetPublished(event));verifyNoInteractions(coordinator);
        OutboxDeliveryContext.run(new OutboxDeliveryContext.Delivery("event","tenant"),()->consumer.onAssetPublished(event));
        verify(coordinator).createJobWithTaskOnce(eq("asset-publication:event"),eq(JobType.SEARCH_REINDEX),eq("ASSET"),eq("asset"),eq("tenant"),eq("project"),contains("\"tenantId\":\"tenant\""),eq("REINDEX"),eq(TaskCapability.REINDEX));
    }
}
