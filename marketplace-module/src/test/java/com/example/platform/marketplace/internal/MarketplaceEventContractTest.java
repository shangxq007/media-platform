package com.example.platform.marketplace.internal;

import com.example.platform.marketplace.api.event.*;
import com.example.platform.marketplace.api.MarketplacePublicationSubjectRef.MediaAssetSubject;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.identity.api.project.ProjectScope;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.outbox.app.OutboxEventRouter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;

class MarketplaceEventContractTest {
    MarketplaceEventReference ref(){return new MarketplaceEventReference("fact","listing",new MediaAssetSubject(new MediaAssetId("asset"),"v1"),new ProjectScope("tenant","workspace","project"),4,"actor","account",ActorType.USER,Instant.EPOCH);}
    @Test void typedReferencesPreserveOwnerIdentityAndRequiredScope() {
        var ref=ref();assertThat(ref.subject()).isInstanceOf(MediaAssetSubject.class);assertThat(ref.factKey()).isEqualTo("marketplace:tenant:fact");
        assertThatThrownBy(()->new MarketplaceEventReference("fact","listing",ref.subject(),ref.scope(),0,"actor","account",ActorType.USER,Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MarketplaceEventReference("fact","listing",ref.subject(),new ProjectScope("tenant","","project"),1,"actor","account",ActorType.USER,Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MarketplaceEventReference("fact","listing",ref.subject(),ref.scope(),1,"actor",null,ActorType.USER,Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void schemaNamesAndVersionsHaveOneOwnerWithoutGenericAssetAliases() {
        var types=new MarketplaceOutboxEvents().types();assertThat(types).hasSize(10);
        assertThat(types).allMatch(t->t.name().startsWith("marketplace.")&&t.version()==1);
        assertThat(types.stream().map(t->t.name()).distinct()).hasSize(10);
        assertThat(types.stream().map(t->t.payloadType()).distinct()).hasSize(10);
    }
    @Test void recordCodecRoundTripRetainsTypedSubjectAndPinnedFacts() {
        var event=new MarketplaceListingPublishedEvent(ref(),"review");
        var encoded=MarketplaceJson.write(event);assertThat(encoded).contains("MEDIA_ASSET","workspace","account");
        assertThat(MarketplaceJson.read(encoded,MarketplaceListingPublishedEvent.class)).isEqualTo(event);
        assertThatThrownBy(()->MarketplaceJson.read(encoded.replace("MEDIA_ASSET","PLUGIN"),MarketplaceListingPublishedEvent.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MarketplaceJson.read(encoded.replace("\"reviewId\":\"review\"","\"targetType\":\"ASSET\",\"reviewId\":\"review\""),MarketplaceListingPublishedEvent.class)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reviewFactsRequireActualDecisionCommentAndThreadIdentities() {
        assertThatThrownBy(()->new MarketplaceReviewApprovedEvent(ref(),"review","")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MarketplaceReviewCommentAddedEvent(ref(),"review",null,"comment")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MarketplaceReviewThreadResolvedEvent(ref(),"review","")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new MarketplaceReviewApprovedEvent(ref(),"review","decision").decisionId()).isEqualTo("decision");
    }

    @Test @SuppressWarnings({"rawtypes","unchecked"})
    void actualOutboxCodecRoundTripsEveryOwnerFactAndRejectsForeignScopeAndVersions() {
        var catalog=new MarketplaceOutboxEvents();var router=new OutboxEventRouter(List.of(catalog));
        List<Record> facts=List.of(new MarketplaceListingCreatedEvent(ref()),new MarketplaceListingUpdatedEvent(ref()),
                new MarketplaceReviewCreatedEvent(ref(),"review"),new MarketplaceReviewApprovedEvent(ref(),"review","decision"),
                new MarketplaceReviewRejectedEvent(ref(),"review","decision"),new MarketplaceReviewChangesRequestedEvent(ref(),"review","decision"),
                new MarketplaceReviewCommentAddedEvent(ref(),"review","thread","comment"),new MarketplaceReviewThreadResolvedEvent(ref(),"review","thread"),
                new MarketplaceListingPublishedEvent(ref(),"review"),new MarketplaceListingArchivedEvent(ref()));
        for(Record fact:facts) {
            com.example.platform.outbox.api.event.OutboxEventType type=catalog.types().stream().filter(t->t.payloadType().equals(fact.getClass())).findFirst().orElseThrow();
            String encoded=router.encode(type.append("tenant",fact,"command"));String aggregate=(String)type.aggregateId().apply(fact);
            assertThat(router.decode(type.name(),1,type.aggregateType(),aggregate,encoded).payload()).isEqualTo(fact);
            assertThatThrownBy(()->router.decode(type.name(),2,type.aggregateType(),aggregate,encoded)).isInstanceOf(OutboxEventRouter.InvalidEvent.class);
            assertThatThrownBy(()->type.append("foreign",fact,"command")).isInstanceOf(IllegalArgumentException.class);
        }
        for(String retired:List.of("asset.published","asset.archived","asset.approved","asset.submitted.review"))
            assertThatThrownBy(()->router.decode(retired,1,"ASSET","asset","{}")).isInstanceOf(OutboxEventRouter.InvalidEvent.class);
    }
}
