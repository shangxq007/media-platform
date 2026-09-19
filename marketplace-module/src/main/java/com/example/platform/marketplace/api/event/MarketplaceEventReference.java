package com.example.platform.marketplace.api.event;
import com.example.platform.marketplace.api.MarketplacePublicationSubjectRef;
import com.example.platform.identity.api.project.ProjectScope;
import com.example.platform.shared.authorization.ActorType;
import java.time.Instant;
import java.util.Objects;
/** Accepted Marketplace fact context; this is not an authorization grant or a new subject identity. */
public record MarketplaceEventReference(String factId,String listingId,MarketplacePublicationSubjectRef subject,
        ProjectScope scope,long listingVersion,String actorId,String accountId,ActorType actorType,Instant occurredAt) {
    public MarketplaceEventReference {
        require(factId);require(listingId);Objects.requireNonNull(subject);Objects.requireNonNull(scope);
        require(scope.tenantId());require(scope.workspaceId());require(scope.projectId());
        if(listingVersion<1)throw new IllegalArgumentException("Positive Marketplace version required");
        require(actorId);Objects.requireNonNull(actorType);Objects.requireNonNull(occurredAt);
        if(actorType==ActorType.USER)require(accountId);
    }
    public String factKey(){return "marketplace:"+scope.tenantId()+":"+factId;}
    public static void require(String value){if(value==null||value.isBlank()||value.length()>128)throw new IllegalArgumentException("Bounded fact identity required");}
}
