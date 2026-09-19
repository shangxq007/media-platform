package com.example.platform.marketplace;

import static org.assertj.core.api.Assertions.*;
import com.example.platform.marketplace.api.MarketplaceApi;
import com.example.platform.identity.api.dto.CreateProjectRequest;
import com.example.platform.identity.api.workspace.AddWorkspaceMemberRequest;
import com.example.platform.identity.app.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.*;

class MarketplaceFoundationIntegrationTest extends MarketplaceTestSupport {
    @Test void authenticatedOwnerLifecycleAndAnonymousDiscoveryDoNotLeakReviewOrStorage() throws Exception {
        String asset=asset();var identity=jdbc.queryForMap("select id,media_version,storage_key,checksum from media_asset where id=?",asset);
        var listing=create(asset);assertThat(listing.path("workspaceId").asText()).isEqualTo(workspace).isNotEqualTo(project).isNotEqualTo(tenant);
        assertThat(listing.path("createdBy").asText()).isEqualTo(user);String id=listing.path("id").asText();
        assertThat(http(null,"GET","/api/marketplace/listings/"+id,null).statusCode()).isEqualTo(404);
        assertThat(http(null,"GET",root()+"/listings/"+id,null).statusCode()).isIn(401,403);
        var review=submit(listing);String rid=review.path("id").asText();
        var before=state();assertThat(http(outsider,"GET",root()+"/reviews/"+rid,null).statusCode()).isIn(403,404);assertThat(state()).isEqualTo(before);
        var approved=approve(review);var published=publish(approved);
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        var publicView=http(null,"GET","/api/marketplace/listings/"+id,null);
        assertThat(publicView.statusCode()).isEqualTo(200);assertThat(publicView.body()).contains("Public title").doesNotContain("Private review details", "reviewId", "tenantId", "workspaceId", "createdBy", "storageKey", "marketplace/");
        assertThat(http(null,"GET","/api/marketplace/search?q=Public",null).body()).contains(id);
        assertThat(jdbc.queryForObject("select publish_status from media_asset where id=?",String.class,asset)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForMap("select id,media_version,storage_key,checksum from media_asset where id=?",asset)).isEqualTo(identity);
        var archived=response(http(user,"POST",root()+"/listings/"+id+"/transitions",Map.of("commandId",UUID.randomUUID().toString(),"expectedVersion",published.path("version").asLong(),"transition","ARCHIVE")),200);
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");assertThat(http(null,"GET","/api/marketplace/listings/"+id,null).statusCode()).isEqualTo(404);
        before=state();assertThat(http(user,"POST",root()+"/listings/"+id+"/transitions",Map.of("commandId",UUID.randomUUID().toString(),"expectedVersion",archived.path("version").asLong(),"transition","PUBLISH")).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(before);
    }

    @Test void identityScopeAndSubjectRejectionsLeaveNoAcceptedState() throws Exception {
        String asset=asset();String path=root()+"/listings";
        var before=state();
        for(String actor:new String[]{null,outsider})assertThat(http(actor,"POST",path,createBody(asset,UUID.randomUUID().toString())).statusCode()).isIn(401,403);
        var forged=new HashMap<String,Object>(createBody(asset,"forged"));forged.put("actorId",outsider);
        assertThat(http(user,"POST",path,forged).statusCode()).isEqualTo(400);
        forged.remove("actorId");forged.put("workspaceId",project);assertThat(http(user,"POST",path,forged).statusCode()).isEqualTo(400);
        var unsupported=new HashMap<String,Object>(createBody(asset,"unsupported"));unsupported.put("subject",Map.of("kind","PLUGIN","id",asset));assertThat(http(user,"POST",path,unsupported).statusCode()).isEqualTo(400);
        assertThat(http(user,"POST",path,createBody("missing","missing")).statusCode()).isIn(400,404);
        jdbc.update("update media_asset set media_version='v2' where id=?",asset);assertThat(http(user,"POST",path,createBody(asset,"stale")).statusCode()).isEqualTo(409);
        jdbc.update("update media_asset set media_version='v1',classification='restricted' where id=?",asset);assertThat(http(user,"POST",path,createBody(asset,"restricted")).statusCode()).isEqualTo(403);
        jdbc.update("update media_asset set classification=null,contains_pii=true where id=?",asset);assertThat(http(user,"POST",path,createBody(asset,"pii")).statusCode()).isEqualTo(403);
        jdbc.update("update media_asset set contains_pii=false where id=?",asset);
        String[] otherProject=new String[1];as(user,()->otherProject[0]=context.getBean(TenantProjectService.class).createProject(tenant,new CreateProjectRequest("other",null,workspace)).id());
        assertThat(http(user,"POST","/api/projects/"+otherProject[0]+"/marketplace/listings",createBody(asset,"mismatch")).statusCode()).isIn(400,403,404);
        assertThat(state()).isEqualTo(before);
        assertThat(create(asset).path("status").asText()).isEqualTo("DRAFT");
    }

    @Test void anotherTenantAndWorkspaceCannotSupplySubjectsOrReadPrivateListings() throws Exception {
        String foreignTenant=tenant,foreignProject=project,foreignAsset=asset();var foreign=create(foreignAsset);String foreignListing=foreign.path("id").asText();
        fixture();var before=state();
        assertThat(http(user,"POST",root()+"/listings",createBody(foreignAsset,"cross-tenant")).statusCode()).isIn(400,403,404);
        assertThat(http(user,"GET","/api/projects/"+foreignProject+"/marketplace/listings/"+foreignListing,null).statusCode()).isIn(400,403,404);
        assertThat(state()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from marketplace_listing where tenant_id=?",Integer.class,foreignTenant)).isEqualTo(1);
        String ownAsset=asset();String[] otherWorkspace=new String[1];as(outsider,()->otherWorkspace[0]=context.getBean(WorkspaceService.class).createWorkspace(tenant,new com.example.platform.identity.api.workspace.CreateWorkspaceRequest("other",null,null)).id());
        assertThat(http(user,"POST","/api/product/marketplace/"+otherWorkspace[0]+"/items",Map.of("projectId",project,"listing",createBody(ownAsset,"cross-workspace"))).statusCode()).isEqualTo(403);
        assertThat(state()).isEqualTo(before);
        response(http(user,"POST","/api/product/marketplace/"+workspace+"/items",Map.of("projectId",project,"listing",createBody(ownAsset,"valid-product"))),201);
    }

    @Test void readerCannotMutateAndDuplicateConcurrentCreateHasOneLogicalListing() throws Exception {
        as(user,()->context.getBean(WorkspaceService.class).addMember(workspace,new AddWorkspaceMemberRequest(outsider,"VIEWER")));
        grant(outsider,"READ");
        String asset=asset();var before=state();assertThat(http(outsider,"POST",root()+"/listings",createBody(asset,"reader")).statusCode()).isEqualTo(403);assertThat(state()).isEqualTo(before);
        var body=createBody(asset,"concurrent-create");var responses=race(()->http(user,"POST",root()+"/listings",body),()->http(user,"POST",root()+"/listings",body));
        var a=response(responses.get(0),201);var b=response(responses.get(1),201);assertThat(a).isEqualTo(b);assertThat(listings()).isEqualTo(1);assertThat(commands()).isEqualTo(1);
        before=state();var different=new HashMap<String,Object>(body);different.put("title","Conflicting title");assertThat(http(user,"POST",root()+"/listings",different).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(before);
        String id=a.path("id").asText();assertThat(http(outsider,"GET",root()+"/listings/"+id,null).statusCode()).isEqualTo(200);
        var review=submit(a);before=state();assertThat(http(outsider,"POST",root()+"/reviews/"+review.path("id").asText()+"/decisions",Map.of("commandId","reader-decision","expectedVersion",review.path("version").asLong(),"decision","APPROVE")).statusCode()).isEqualTo(403);assertThat(state()).isEqualTo(before);
    }

    @Test void concurrentDecisionsAreVersionFencedAndDuplicateCommandsDoNotRepeatFacts() throws Exception {
        var review=submit(create(asset()));String id=review.path("id").asText();long version=review.path("version").asLong();long before=events();
        var approve=Map.of("commandId","approve","expectedVersion",version,"decision","APPROVE");var reject=Map.of("commandId","reject","expectedVersion",version,"decision","REJECT");
        var results=race(()->http(user,"POST",root()+"/reviews/"+id+"/decisions",approve),()->http(user,"POST",root()+"/reviews/"+id+"/decisions",reject));
        assertThat(results.stream().map(java.net.http.HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("select count(*) from marketplace_review_decision where review_id=?",Integer.class,id)).isEqualTo(1);
        var accepted=results.get(0).statusCode()==200?approve:reject;var snapshot=state();response(http(user,"POST",root()+"/reviews/"+id+"/decisions",accepted),200);assertThat(state()).isEqualTo(snapshot);
        assertThat(events()-before).isEqualTo(1);
    }

    @Test void publicationAndOutboxFailureRollBackMediaListingAndReceiptTogether() throws Exception {
        String asset=asset();var ready=approve(submit(create(asset)));String id=ready.path("listingId").asText();var before=state();
        var command=Map.of("commandId","publish-retry","expectedVersion",ready.path("version").asLong(),"transition","PUBLISH");
        jdbc.execute("create function ep15_outbox_fault() returns trigger language plpgsql as $$ begin if NEW.event_type like '%.published' then raise exception 'ep15 injected outbox persistence failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger ep15_outbox_fault before insert on outbox_events for each row execute function ep15_outbox_fault()");
        try {assertThat(http(user,"POST",root()+"/listings/"+id+"/transitions",command).statusCode()).isGreaterThanOrEqualTo(400);}
        finally {jdbc.execute("drop trigger ep15_outbox_fault on outbox_events");jdbc.execute("drop function ep15_outbox_fault()");}
        assertThat(state()).isEqualTo(before);assertThat(jdbc.queryForObject("select status from marketplace_listing where id=?",String.class,id)).isEqualTo("READY");assertThat(jdbc.queryForObject("select aggregate_version from marketplace_listing where id=?",Long.class,id)).isEqualTo(ready.path("version").asLong());assertThat(jdbc.queryForObject("select publish_status from media_asset where id=?",String.class,asset)).isEqualTo("DRAFT");
        response(http(user,"POST",root()+"/listings/"+id+"/transitions",command),200);assertThat(events()).isEqualTo(before.get(3)+1);assertThat(commands()).isEqualTo(before.get(2)+1);var after=state();response(http(user,"POST",root()+"/listings/"+id+"/transitions",command),200);assertThat(state()).isEqualTo(after);
    }

    @Test void exactSubjectPinAndPrivateReadFilteringSurviveLaterSourceChanges() throws Exception {
        String asset=asset();var ready=approve(submit(create(asset)));String id=ready.path("listingId").asText();var before=state();
        jdbc.update("update media_asset set media_version='v2' where id=?",asset);
        assertThat(http(user,"POST",root()+"/listings/"+id+"/transitions",Map.of("commandId","stale-publish","expectedVersion",ready.path("version").asLong(),"transition","PUBLISH")).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(before);
        jdbc.update("update media_asset set media_version='v1' where id=?",asset);var published=publish(ready);
        jdbc.update("update media_asset set classification='confidential' where id=?",asset);
        assertThat(http(null,"GET","/api/marketplace/listings/"+id,null).statusCode()).isEqualTo(404);assertThat(http(null,"GET","/api/marketplace/search",null).body()).doesNotContain(id);
        assertThat(http(null,"GET","/api/marketplace/search?status=DRAFT",null).statusCode()).isEqualTo(400);
        assertThat(http(null,"GET","/api/marketplace/assets/"+asset+"/listing",null).statusCode()).isIn(401,403);
        response(http(user,"POST",root()+"/listings/"+id+"/transitions",Map.of("commandId","withdraw-restricted","expectedVersion",published.path("version").asLong(),"transition","ARCHIVE")),200);
    }

    @Test void reviewCommentsAndThreadCorrelationFenceApprovalAndRetiredReviews() throws Exception {
        var listing=create(asset());var review=submit(listing);String rid=review.path("id").asText();String id=listing.path("id").asText();
        var commented=response(http(user,"POST",root()+"/reviews/"+rid+"/comments",Map.of("commandId","comment","expectedVersion",review.path("version").asLong(),"content","Private comment")),200);
        String thread=commented.path("comments").get(0).path("threadId").asText();var before=state();
        assertThat(http(user,"POST",root()+"/reviews/"+rid+"/decisions",Map.of("commandId","unresolved","expectedVersion",commented.path("version").asLong(),"decision","APPROVE")).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(before);
        assertThat(http(user,"POST",root()+"/reviews/"+rid+"/resolve",Map.of("commandId","foreign-thread","expectedVersion",commented.path("version").asLong(),"threadId","missing")).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(before);
        var resolved=response(http(user,"POST",root()+"/reviews/"+rid+"/resolve",Map.of("commandId","resolve","expectedVersion",commented.path("version").asLong(),"threadId",thread)),200);
        assertThat(resolved.path("comments").get(0).path("resolved").asBoolean()).isTrue();publish(approve(resolved));
        before=state();assertThat(http(user,"POST",root()+"/reviews/"+rid+"/comments",Map.of("commandId","late-comment","expectedVersion",resolved.path("version").asLong(),"content","Late")).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from timeline_review where tenant_id=?",Integer.class,tenant)).isZero();
    }

    @Test void distinctReviewersRetainTheirOwnDecisionsWithoutPublicationPermission() throws Exception {
        as(user,()->context.getBean(WorkspaceService.class).addMember(workspace,new AddWorkspaceMemberRequest(outsider,"VIEWER")));
        grant(outsider,"READ","marketplace.review");
        String asset=asset();var first=approve(submit(create(asset)));String review=first.path("id").asText();
        var second=response(http(outsider,"POST",root()+"/reviews/"+review+"/decisions",Map.of("commandId","second-approval","expectedVersion",first.path("version").asLong(),"decision","APPROVE")),200);
        assertThat(jdbc.queryForList("select actor_json::jsonb->>'actorId' from marketplace_review_decision where review_id=? order by aggregate_version",String.class,review)).containsExactly(user,outsider);
        var status=response(http(outsider,"GET","/api/projects/"+project+"/assets/"+asset+"/publish-status",null),200);
        assertThat(status.path("canPublish").asBoolean()).isFalse();
        var before=state();assertThat(http(outsider,"POST",root()+"/listings/"+first.path("listingId").asText()+"/transitions",Map.of("commandId","reviewer-publish","expectedVersion",second.path("version").asLong(),"transition","PUBLISH")).statusCode()).isEqualTo(403);assertThat(state()).isEqualTo(before);
        publish(second);
    }
    @Test void unresolvedWorkspaceAndRetiredStatusShortcutCannotMutateState() throws Exception {
        String asset=asset();var before=state();
        assertThatThrownBy(()->jdbc.update("update project set workspace_id=null where id=?",project))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Established Project scope is immutable");
        String legacy="legacy-project-"+UUID.randomUUID();
        jdbc.update("insert into project(id,tenant_id,name,status,workspace_id,created_at) values (?,?,?,'ACTIVE',null,now())",legacy,tenant,"Unresolved historical scope");
        assertThat(http(user,"POST","/api/projects/"+legacy+"/marketplace/listings",createBody(asset,"unresolved")).statusCode()).isIn(400,403,409);
        assertThat(http(user,"POST","/api/product/marketplace/"+project+"/items",Map.of("projectId",project,"listing",createBody(asset,"project-as-workspace"))).statusCode()).isEqualTo(403);
        assertThat(state()).isEqualTo(before);
        var listing=create(asset);before=state();
        assertThat(http(user,"PATCH","/api/marketplace/listings/"+listing.path("id").asText()+"/status",Map.of("status","PUBLISHED")).statusCode()).isIn(401,403,404,405);
        assertThat(state()).isEqualTo(before);assertThat(jdbc.queryForObject("select status from marketplace_listing where id=?",String.class,listing.path("id").asText())).isEqualTo("DRAFT");
    }

    @Test void productionAssemblyHasOneMarketplaceOwnerAndNoRetiredRuntimeClasses() {
        assertThat(context.getBeansOfType(MarketplaceApi.class)).hasSize(1);
        assertThat(context.getBean(MarketplaceConfiguration.class)).isNotNull();
        for(String name:List.of(
                "com.example.platform.render.app.asset.AssetReviewService",
                "com.example.platform.render.app.asset.MarketplaceConsumer",
                "com.example.platform.render.app.asset.MarketplaceValidateTaskHandler",
                "com.example.platform.render.app.asset.MarketplacePackageTaskHandler",
                "com.example.platform.render.infrastructure.asset.MarketplaceListingRepository",
                "com.example.platform.render.infrastructure.productization.marketplace.MarketplaceService"))
            assertThatThrownBy(()->Class.forName(name)).isInstanceOf(ClassNotFoundException.class);
    }

    @Test void explicitLegacyReadmissionPreservesEvidenceAndNeverReusesOldApproval() throws Exception {
        String asset=asset(),id="legacy-listing-"+UUID.randomUUID();
        jdbc.update("insert into marketplace_listing(id,asset_id,tenant_id,project_id,listing_type,title,status,version,review_id,legacy_snapshot,created_at,updated_at) values (?,?,'foreign','wrong','MEDIA','Historical title','PUBLISHED','1.0','old-review','{\"status\":\"PUBLISHED\",\"reviewId\":\"old-review\"}'::jsonb,now(),now())",id,asset);
        String snapshot=jdbc.queryForObject("select legacy_snapshot::text from marketplace_listing where id=?",String.class,id);
        assertThat(http(null,"GET","/api/marketplace/listings/"+id,null).statusCode()).isEqualTo(404);
        var before=state();assertThat(http(user,"POST",root()+"/listings",createBody(asset,"foreign-legacy")).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select title from marketplace_listing where id=?",String.class,id)).isEqualTo("Historical title");
        // Test fixture reconciles the historical scope; the application must still require
        // explicit current admission and a new review, never reinterpret old approval.
        jdbc.update("update marketplace_listing set tenant_id=?,project_id=? where id=?",tenant,project,id);
        var admitted=create(asset);assertThat(admitted.path("id").asText()).isEqualTo(id);assertThat(admitted.path("status").asText()).isEqualTo("DRAFT");assertThat(admitted.path("reviewId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select legacy_snapshot::text from marketplace_listing where id=?",String.class,id)).isEqualTo(snapshot);
        before=state();assertThat(http(user,"POST",root()+"/listings/"+id+"/transitions",Map.of("commandId","old-approval","expectedVersion",1,"transition","PUBLISH")).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(before);
        publish(approve(submit(admitted)));assertThat(jdbc.queryForObject("select legacy_snapshot::text from marketplace_listing where id=?",String.class,id)).isEqualTo(snapshot);
    }
    @Test void migratedAssetTransportUsesOwnerScopeAndRejectsOldAttributionFields() throws Exception {
        String asset=asset();var listing=create(asset);String base="/api/projects/"+project+"/assets/"+asset;
        var before=state();assertThat(http(user,"POST",base+"/submit-review",Map.of("authorUserId",outsider,"title","forged")).statusCode()).isEqualTo(400);assertThat(state()).isEqualTo(before);
        var review=response(http(user,"POST",base+"/submit-review",Map.of("commandId","asset-submit","expectedVersion",listing.path("version").asLong(),"title","Review")),201);
        var decision=Map.of("commandId","asset-approve","expectedVersion",review.path("version").asLong(),"decision","APPROVE");before=state();
        assertThat(http(user,"POST",base+"/approve-review?reviewerUserId="+outsider,decision).statusCode()).isEqualTo(400);assertThat(state()).isEqualTo(before);
        var approved=response(http(user,"POST",base+"/approve-review",decision),200);
        var published=response(http(user,"POST",base+"/publish",Map.of("commandId","asset-publish","expectedVersion",approved.path("version").asLong(),"transition","PUBLISH")),200);
        assertThat(published.path("id").asText()).isEqualTo(listing.path("id").asText());assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(response(http(user,"GET",base+"/review",null),200).path("authorId").asText()).isEqualTo(user);
    }
}
