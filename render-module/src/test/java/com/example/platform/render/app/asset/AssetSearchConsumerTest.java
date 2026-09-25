package com.example.platform.render.app.asset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.platform.outbox.coordination.*;
import com.example.platform.outbox.api.event.OutboxDeliveryContext;
import com.example.platform.marketplace.api.event.*;
import com.example.platform.marketplace.api.MarketplacePublicationSubjectRef.ArtifactSubject;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.identity.api.project.ProjectScope;
import com.example.platform.shared.authorization.ActorType;
import java.time.Instant;
import com.example.platform.sandbox.execution.TaskCapability;
import org.junit.jupiter.api.Test;
class AssetSearchConsumerTest {
    @Test void publicationIntentUsesDurableIdentityAndAuthoritativeEnvelopeScope() {
        var coordinator=mock(PlatformCoordinationService.class);var consumer=new AssetSearchConsumer(coordinator);
        var event=new MarketplaceListingPublishedEvent(new MarketplaceEventReference("fact","listing",new ArtifactSubject(new ArtifactId("asset"),"a".repeat(64)),new ProjectScope("tenant","workspace","project"),4,"actor","account",ActorType.USER,Instant.EPOCH),"review");
        assertThrows(IllegalStateException.class,()->consumer.onMarketplaceListingPublished(event));verifyNoInteractions(coordinator);
        OutboxDeliveryContext.run(new OutboxDeliveryContext.Delivery("event","tenant"),()->consumer.onMarketplaceListingPublished(event));
        verify(coordinator).createJobWithTaskOnce(eq("marketplace:tenant:fact"),eq(JobType.SEARCH_REINDEX),eq("ASSET"),eq("asset"),eq("tenant"),eq("project"),contains("\"tenantId\":\"tenant\""),eq("REINDEX"),eq(TaskCapability.REINDEX));
    }
}
