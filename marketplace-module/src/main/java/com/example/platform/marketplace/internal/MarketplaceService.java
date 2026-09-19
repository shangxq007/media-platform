package com.example.platform.marketplace.internal;

import com.example.platform.marketplace.api.*;
import com.example.platform.marketplace.api.MarketplacePublicationSubjectRef.MediaAssetSubject;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.identity.api.project.*;
import com.example.platform.media.api.*;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.web.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Supplier;

@Service
@Transactional
public class MarketplaceService implements MarketplaceApi {
    private final MarketplaceStore store;
    private final MarketplaceEvents events;
    private final MediaAssets media;
    private final MediaAssetQueries mediaFacts;
    private final CanonicalActorResolver actors;
    private final ProjectScopeQueries scopes;
    private final AuthorizationDecisionPort authorization;
    public MarketplaceService(MarketplaceStore store,MarketplaceEvents events,MediaAssets media,
            MediaAssetQueries mediaFacts,CanonicalActorResolver actors,ProjectScopeQueries scopes,AuthorizationDecisionPort authorization) {
        this.store=store;this.events=events;this.media=media;this.mediaFacts=mediaFacts;this.actors=actors;this.scopes=scopes;this.authorization=authorization;
    }
    private record Access(CanonicalActor actor,ProjectScope scope) {}
    private Access access(String project,String permission) {
        var actor=actors.resolveCurrentActor().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        TenantGuard.assertSameTenant(actor.tenantId());
        if(actor.actorType()==ActorType.USER && (actor.accountId()==null||actor.accountId().isBlank())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var scope=scopes.resolveForAcceptance(actor.tenantId(),project);
        require(actor,scope,"READ");if(!permission.equals("READ"))require(actor,scope,permission);
        return new Access(actor,scope);
    }
    private void require(CanonicalActor actor,ProjectScope scope,String key) {
        authorization.requireAuthorized(new AuthorizationRequest(actor,new AuthorizationAction(key,AuthorizationResourceType.PROJECT,"Marketplace"),
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT,scope.projectId(),scope.tenantId(),scope.projectId(),null),
                new AuthorizationContext("marketplace",scope.workspaceId(),Map.of())));
    }
    private <T> T command(Access access,String id,String action,String target,Object input,Class<T> type,Supplier<T> change) {
        text(id,128,"commandId");
        var actor=access.actor();var scope=access.scope();
        String digest=MarketplaceJson.digest(List.of(actor.actorId(),actor.actorType(),Objects.toString(actor.accountId(),""),scope,action,target,input));
        store.lockCommand(scope.tenantId(),id);
        var prior=store.command(scope.tenantId(),id);
        if(prior.isPresent()) {
            if(!digest.equals(prior.get().get("request_digest")))throw conflict("Command identity reused with different inputs or actor");
            return MarketplaceJson.read((String)prior.get().get("result_json"),type);
        }
        T result=change.get();store.recordCommand(scope.tenantId(),id,digest,MarketplaceJson.write(actor),result);return result;
    }
    private Listing requireListing(Access access,String id,boolean lock) {
        var scope=access.scope();var row=store.listing(scope.tenantId(),scope.projectId(),id,lock).orElseThrow(()->missing("Listing not found"));
        if(!scope.workspaceId().equals(row.workspaceId()))throw conflict("Listing Workspace relationship changed; owner reconciliation required");
        return row;
    }
    private MediaAssetSubject subject(MarketplacePublicationSubjectRef ref) {
        if(!(ref instanceof MediaAssetSubject media))throw new IllegalArgumentException("Only the evidenced MediaAsset publication subject is supported");
        return media;
    }
    private Asset validateSubject(Access access,MarketplacePublicationSubjectRef ref) {
        var s=subject(ref);var scope=access.scope();
        media.requireReadScope(scope.tenantId(),scope.projectId());
        var asset=media.publicationSnapshot(scope.tenantId(),scope.projectId(),s.assetId().value());
        if(!scope.tenantId().equals(asset.tenantId())||!scope.projectId().equals(asset.projectId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(!s.version().equals(asset.assetVersion()))throw conflict("Media subject version changed");
        if(!Set.of("VIDEO","AUDIO","IMAGE","SUBTITLE").contains(asset.mediaType()))throw new IllegalArgumentException("Unsupported Media subject type");
        if(!eligible(asset.classification(),asset.securityLevel(),asset.containsPii()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Restricted Media subject");
        return asset;
    }
    /** Marketplace release policy consumes current Media-owned governance facts; no license/rights grant is invented. */
    private boolean eligible(String classification,String security,boolean pii) {
        return !pii && (classification==null||classification.isBlank()||classification.equalsIgnoreCase("public"))
                && (security==null||security.isBlank()||security.equalsIgnoreCase("L1"));
    }
    private void version(Listing row,long expected) {if(expected<1||row.version()!=expected)throw conflict("Stale listing version");}
    private Listing updated(Access a,Listing row){return requireListing(a,row.id(),false);}
    @Override public Listing create(String project,String expectedWorkspace,Create c) {
        var a=access(project,"marketplace.manage");
        if(expectedWorkspace!=null&&!expectedWorkspace.equals(a.scope().workspaceId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Marketplace Workspace mismatch");
        return command(a,c.commandId(),"create",project,c,Listing.class,()->{
            validateSubject(a,c.subject());metadata(c.title(),c.summary(),c.description());var s=subject(c.subject());
            String id=store.create(a.scope().tenantId(),a.scope().workspaceId(),project,s.assetId().value(),s.version(),a.actor().actorId(),c.title(),normal(c.summary()),c.description());
            var row=requireListing(a,id,false);events.listingCreated(row,a.actor());return row;
        });
    }
    @Override public Listing edit(String project,String listing,Edit c) {
        var a=access(project,"marketplace.manage");
        return command(a,c.commandId(),"edit",listing,c,Listing.class,()->{
            var row=requireListing(a,listing,true);version(row,c.expectedVersion());
            if(row.status()!=Status.DRAFT)throw conflict("Only a draft is editable");
            validateSubject(a,row.subject());metadata(c.title(),c.summary(),c.description());
            store.edit(row,c.title(),normal(c.summary()),c.description(),a.actor().actorId());var changed=updated(a,row);events.listingUpdated(changed,a.actor());return changed;
        });
    }
    @Override public Review submit(String project,String listing,Submit c) {
        var a=access(project,"marketplace.manage");
        return command(a,c.commandId(),"submit",listing,c,Review.class,()->{
            var row=requireListing(a,listing,true);version(row,c.expectedVersion());
            if(row.status()!=Status.DRAFT||row.reviewId()!=null)throw conflict("Submit requires an unreviewed draft; edit explicitly before resubmission");
            validateSubject(a,row.subject());text(c.title(),256,"review title");nullable(c.description(),4000,"review description");
            String id="mrev_"+UUID.randomUUID().toString().replace("-","");store.insertReview(id,row,a.actor().actorId(),c.title(),c.description());store.change(row,Status.DRAFT,id,a.actor().actorId());
            events.reviewCreated(updated(a,row),id,a.actor());return reviewValue(a,id);
        });
    }
    private Listing currentReviewListing(Access a,String review,long expected) {
        var r=store.reviewRow(a.scope().tenantId(),a.scope().projectId(),review);
        var row=requireListing(a,(String)r.get("listing_id"),true);version(row,expected);
        if(!review.equals(row.reviewId())||row.status()==Status.PUBLISHED||row.status()==Status.ARCHIVED)throw conflict("Review no longer accepts commands");
        if(ReviewStatus.REJECTED.name().equals(r.get("status")))throw conflict("Review rejected; edit and resubmit a new review");
        validateSubject(a,row.subject());return row;
    }
    @Override public Review decide(String project,String review,Decide c) {
        var a=access(project,"marketplace.review");
        return command(a,c.commandId(),"decide",review,c,Review.class,()->{
            if(c.decision()==null)throw new IllegalArgumentException("Decision required");var row=currentReviewListing(a,review,c.expectedVersion());
            var status=switch(c.decision()){case APPROVE->ReviewStatus.APPROVED;case REQUEST_CHANGES->ReviewStatus.CHANGES_REQUESTED;case REJECT->ReviewStatus.REJECTED;};
            if(status.name().equals(store.reviewRow(a.scope().tenantId(),project,review).get("status"))) {
                var previous=store.latestDecision(review,a.actor().actorId());
                if(previous.filter(c.decision().name()::equals).isPresent())
                    throw conflict("Decision already applied for this actor; retry with original command identity");
            }
            if(c.decision()==Decision.APPROVE&&store.unresolved(review))throw conflict("Unresolved review threads");
            store.reviewStatus(review,status,row.version()+1);store.change(row,c.decision()==Decision.APPROVE?Status.READY:Status.DRAFT,review,a.actor().actorId());
            String decisionId="mdec_"+UUID.randomUUID();
            store.insertDecision(decisionId,review,MarketplaceJson.write(a.actor()),c.decision().name(),row.version()+1);
            events.decided(updated(a,row),review,decisionId,c.decision().name(),a.actor());return reviewValue(a,review);
        });
    }
    @Override public Listing transition(String project,String listing,Change c) {
        var a=access(project,"marketplace.publish");
        return command(a,c.commandId(),"transition",listing,c,Listing.class,()->{
            if(c.transition()==null)throw new IllegalArgumentException("Transition required");var row=requireListing(a,listing,true);version(row,c.expectedVersion());
            if(row.status()==Status.ARCHIVED)throw conflict("Archived listing is terminal");
            if(c.transition()==Transition.PUBLISH) {
                if(row.status()!=Status.READY||row.reviewId()==null)throw conflict("An approved review is required");
                var review=store.reviewRow(row.tenantId(),project,row.reviewId());
                if(!ReviewStatus.APPROVED.name().equals(review.get("status"))||!subject(row.subject()).version().equals(review.get("subject_version"))||store.unresolved(row.reviewId()))throw conflict("Review does not approve this exact subject");
                var asset=validateSubject(a,row.subject());media.updatePublishStatus(row.tenantId(),project,asset.id(),asset.publishStatus(),"PUBLISHED");
                store.change(row,Status.PUBLISHED,row.reviewId(),a.actor().actorId());var result=updated(a,row);events.published(result,a.actor());return result;
            }
            // Withdrawal belongs to the listing even if its subject is now stale/restricted.
            // Never mutate a foreign or changed Media version while withdrawing old metadata.
            var s=subject(row.subject());
            if (!media.archivePublicationIfCurrent(row.tenantId(),project,s.assetId().value(),s.version())) {
                // An explicit conditional miss permits listing-only withdrawal. Exceptions
                // still roll back the entire command; they are never translated into a miss.
                org.slf4j.LoggerFactory.getLogger(MarketplaceService.class)
                    .debug("Withdrawing listing {} without changing its stale Media subject", listing);
            }
            store.change(row,Status.ARCHIVED,row.reviewId(),a.actor().actorId());var result=updated(a,row);events.archived(result,a.actor());return result;
        });
    }
    @Override public Review comment(String project,String review,Comment c) {
        var a=access(project,"marketplace.review");
        return command(a,c.commandId(),"comment",review,c,Review.class,()->{
            var row=currentReviewListing(a,review,c.expectedVersion());text(c.content(),4000,"comment");
            var added=store.addComment(review,c.threadId(),a.actor().actorId(),c.content());
            store.reviewStatus(review,ReviewStatus.OPEN,row.version()+1);store.change(row,Status.DRAFT,review,a.actor().actorId());events.commentAdded(updated(a,row),review,added.thread(),added.id(),a.actor());return reviewValue(a,review);
        });
    }
    @Override public Review resolve(String project,String review,Resolve c) {
        var a=access(project,"marketplace.review");
        return command(a,c.commandId(),"resolve",review,c,Review.class,()->{
            var row=currentReviewListing(a,review,c.expectedVersion());
            store.resolveThread(review,c.threadId(),a.actor().actorId());
            store.reviewStatus(review,ReviewStatus.OPEN,row.version()+1);store.change(row,Status.DRAFT,review,a.actor().actorId());events.threadResolved(updated(a,row),review,c.threadId(),a.actor());return reviewValue(a,review);
        });
    }
    @Override public boolean canPublish(String project,String id) {
        var read=access(project,"READ");var row=requireListing(read,id,false);
        if(row.status()!=Status.READY||row.reviewId()==null||store.unresolved(row.reviewId()))return false;
        try {
            var a=access(project,"marketplace.publish");
            media.requireRegistrationScope(a.scope().tenantId(),project);
            validateSubject(a,row.subject());
            var r=store.reviewRow(row.tenantId(),project,row.reviewId());
            return ReviewStatus.APPROVED.name().equals(r.get("status"))&&subject(row.subject()).version().equals(r.get("subject_version"));
        } catch(org.springframework.web.server.ResponseStatusException | com.example.platform.shared.web.PlatformException | com.example.platform.identity.api.authorization.AuthorizationDeniedException | IllegalArgumentException rejected) {return false;}
    }
    @Override public Listing managedListing(String project,String id){return requireListing(access(project,"READ"),id,false);}
    @Override public Optional<Listing> managedByAsset(String asset) {
        var actor=actors.resolveCurrentActor().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var value=media.findById(actor.tenantId(),asset).orElseThrow(()->missing("Media subject not found"));var a=access(value.projectId(),"READ");
        return store.admittedAsset(a.scope().tenantId(),asset).map(row->requireListing(a,row.id(),false));
    }
    @Override public ProjectSummary summary(String project) {
        var a=access(project,"READ");
        return store.summary(a.scope().tenantId(),project,a.scope().workspaceId());
    }
    @Override public List<Listing> managedByProject(String project,int limit){var a=access(project,"READ");return store.project(a.scope().tenantId(),project,bound(limit)).stream().filter(row->a.scope().workspaceId().equals(row.workspaceId())).toList();}
    @Override public Review review(String project,String id){return reviewValue(access(project,"READ"),id);}
    private Review reviewValue(Access a,String id) {
        var r=store.reviewRow(a.scope().tenantId(),a.scope().projectId(),id);var listing=requireListing(a,(String)r.get("listing_id"),false);
        return new Review(id,listing.id(),new MediaAssetSubject(subject(listing.subject()).assetId(),(String)r.get("subject_version")),ReviewStatus.valueOf((String)r.get("status")),(String)r.get("author_id"),(String)r.get("title"),(String)r.get("description"),((Number)r.get("aggregate_version")).longValue(),store.comments(id));
    }
    private boolean visible(Listing row) {
        if(row.status()!=Status.PUBLISHED)return false;
        var s=subject(row.subject());var asset=mediaFacts.findById(s.assetId());
        if(asset.isEmpty())return false;var a=asset.get();
        if(!row.tenantId().equals(a.tenantId())||!row.projectId().equals(a.projectId())||!s.version().equals(a.mediaVersion())||!"PUBLISHED".equals(a.publishStatus())||!eligible(a.classification(),a.securityLevel(),a.containsPii()))return false;
        try {return row.workspaceId().equals(scopes.resolveForAcceptance(row.tenantId(),row.projectId()).workspaceId());}catch(RuntimeException unavailable){return false;}
    }
    private PublicListing publicValue(Listing row){return new PublicListing(row.id(),row.subject(),row.title(),row.summary(),row.description(),"MEDIA",row.version(),row.updatedAt());}
    @Override public Optional<PublicListing> publicListing(String id){return store.publicListing(id).filter(this::visible).map(this::publicValue);}
    @Override public SearchResult discover(String q,String workspace,int offset,int limit) {
        if(offset<0||offset>10000)throw new IllegalArgumentException("Invalid offset");limit=bound(limit);nullable(q,256,"query");
        var visible=store.published(q==null||q.isBlank()?null:q,workspace).stream().filter(this::visible).map(this::publicValue).toList();
        return new SearchResult(visible.size(),offset,limit,visible.stream().skip(offset).limit(limit).toList());
    }
    @Override public Optional<PublicationFact> publicationFact(String tenant,String project,String asset) {
        TenantGuard.assertSameTenant(tenant);
        return store.admittedAsset(tenant,asset).filter(x->project.equals(x.projectId())).map(x->new PublicationFact(x.id(),x.version(),x.status(),x.subject()));
    }
    private static int bound(int n){if(n<1||n>100)throw new IllegalArgumentException("Limit must be 1..100");return n;}
    private static String normal(String s){return s==null?"":s;}
    private static void metadata(String title,String summary,String description){text(title,256,"title");nullable(summary,2000,"summary");nullable(description,4000,"description");}
    private static void text(String s,int n,String label){if(s==null||s.isBlank()||s.length()>n)throw new IllegalArgumentException("Invalid "+label);}
    private static void nullable(String s,int n,String label){if(s!=null&&s.length()>n)throw new IllegalArgumentException("Invalid "+label);}
    static ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
    static ResponseStatusException missing(String text){return new ResponseStatusException(HttpStatus.NOT_FOUND,text);}
}
