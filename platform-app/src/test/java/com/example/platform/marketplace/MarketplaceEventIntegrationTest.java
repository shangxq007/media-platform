package com.example.platform.marketplace;

import static org.assertj.core.api.Assertions.*;
import com.example.platform.outbox.app.*;
import com.example.platform.outbox.coordination.*;
import com.example.platform.notification.domain.*;
import com.example.platform.render.app.asset.SearchReindexTaskHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;

@Import(MarketplaceEventIntegrationTest.ControlledNotifications.class)
@TestPropertySource(properties="app.notification.novu.enabled=false")
class MarketplaceEventIntegrationTest extends MarketplaceTestSupport {
    @Autowired DatabaseNotificationProvider controlled;
    @TestConfiguration
    static class ControlledNotifications {
        @Bean DatabaseNotificationProvider marketplaceDatabaseNotificationProvider(JdbcTemplate jdbc){return new DatabaseNotificationProvider(jdbc);}
    }
    /** Participating database effect; deliberately not proof of external provider execution. */
    static class DatabaseNotificationProvider implements NotificationProvider {
        private final JdbcTemplate jdbc;
        final AtomicBoolean failOnce=new AtomicBoolean();
        DatabaseNotificationProvider(JdbcTemplate jdbc){this.jdbc=jdbc;}
        public String channel(){return "MARKETPLACE_TEST";}
        public String providerCode(){return "marketplace-test-database";}
        public DeliveryResult send(DeliveryCommand c) {
            jdbc.update("insert into ep29c_notification_effect(event_id,attempts) values (?,1) on conflict(event_id) do update set attempts=ep29c_notification_effect.attempts+1",c.eventId());
            if(failOnce.compareAndSet(true,false))throw new IllegalStateException("Injected consumer failure after participating DB effect");
            return new DeliveryResult("SENT","{}");
        }
    }
    @BeforeEach void effects(){jdbc.execute("create table if not exists ep29c_notification_effect(event_id text primary key,attempts integer not null)");controlled.failOnce.set(false);}
    OutboxEventDispatcher dispatcher(){return new OutboxEventDispatcher(context.getBean(OutboxEventService.class),context,context.getBean(OutboxEventRouter.class),3,context.getBean(io.micrometer.core.instrument.MeterRegistry.class));}
    List<Map<String,Object>> facts(){return jdbc.queryForList("select * from outbox_events where event_type like 'marketplace.%' and payload like ? order by created_at,id","%"+tenant+"%");}
    Map<String,Object> fact(String type){return facts().stream().filter(r->type.equals(r.get("event_type"))).findFirst().orElseThrow();}
    String notificationId(Map<String,Object> row)throws Exception {
        var ref=JSON.readTree((String)row.get("payload")).path("payload").path("reference");
        return "nev_"+UUID.nameUUIDFromBytes(("marketplace:"+tenant+":"+ref.path("factId").asText()).getBytes(StandardCharsets.UTF_8));
    }
    String duplicate(Map<String,Object> row){String id="obx_duplicate_"+UUID.randomUUID();jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values (?,?,?,?,?,?,'PENDING',0,3,now())",id,row.get("aggregate_type"),row.get("aggregate_id"),row.get("event_type"),row.get("event_version"),row.get("payload"));return id;}

    @Test void productionCommandsEmitCoherentTypedFactsAndActualConsumersDeduplicate() throws Exception {
        String asset=asset();var published=publish(approve(submit(create(asset))));String listing=published.path("id").asText();var rows=facts();
        assertThat(rows.stream().map(r->r.get("event_type"))).containsExactly("marketplace.listing.created","marketplace.review.created","marketplace.review.approved","marketplace.listing.published");
        Set<String> ids=new HashSet<>();long version=1;
        String account=jdbc.queryForObject("select account_id from \"user\" where id=?",String.class,user);
        for(var row:rows) {
            assertThat(row.get("event_version")).isEqualTo(1);
            var decoded=context.getBean(OutboxEventRouter.class).decode((String)row.get("event_type"),1,(String)row.get("aggregate_type"),(String)row.get("aggregate_id"),(String)row.get("payload"));
            assertThat(decoded.payload().getClass().getPackageName()).isEqualTo("com.example.platform.marketplace.api.event");
            var payload=JSON.readTree((String)row.get("payload")).path("payload");var ref=payload.path("reference");
            assertThat(ref.path("listingId").asText()).isEqualTo(listing);assertThat(ref.path("listingVersion").asLong()).isEqualTo(version++);
            assertThat(ref.path("scope").path("tenantId").asText()).isEqualTo(tenant);assertThat(ref.path("scope").path("workspaceId").asText()).isEqualTo(workspace).isNotEqualTo(project);
            assertThat(ref.path("scope").path("projectId").asText()).isEqualTo(project);
            assertThat(ref.path("actorId").asText()).isEqualTo(user);assertThat(ref.path("accountId").asText()).isEqualTo(account);assertThat(ref.path("actorType").asText()).isEqualTo("USER");
            assertThat(ref.path("subject").path("kind").asText()).isEqualTo("MEDIA_ASSET");assertThat(ref.path("subject").path("assetId").path("value").asText()).isEqualTo(asset);assertThat(ref.path("subject").path("version").asText()).isEqualTo("v1");
            assertThat(ids.add(ref.path("factId").asText())).isTrue();assertThat(payload.toString()).doesNotContain("Private review details");
            try(var d=dispatcher()){assertThat(d.processOnce((String)row.get("id"))).isTrue();}
        }
        var publication=fact("marketplace.listing.published");String notification=notificationId(publication);
        assertThat(jdbc.queryForObject("select attempts from ep29c_notification_effect where event_id=?",Integer.class,notification)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_records where action='MARKETPLACE_LISTING_PUBLISHED' and resource_id=? and actor_type='USER' and actor_id=?",Integer.class,listing,user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_job where job_type='SEARCH_REINDEX' and tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        String replay=duplicate(publication);try(var d=dispatcher()){assertThat(d.processOnce(replay)).isTrue();}
        assertThat(jdbc.queryForObject("select attempts from ep29c_notification_effect where event_id=?",Integer.class,notification)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_records where action='MARKETPLACE_LISTING_PUBLISHED' and resource_id=?",Integer.class,listing)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_job where job_type='SEARCH_REINDEX' and tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select aggregate_version from marketplace_listing where id=?",Long.class,listing)).isEqualTo(4L);
    }

    @Test void consumerFailureRollsBackItsDatabaseEffectsAndAReplacementDispatcherRecovers() throws Exception {
        var published=publish(approve(submit(create(asset()))));String listing=published.path("id").asText();var row=fact("marketplace.listing.published");String event=(String)row.get("id"),notification=notificationId(row);
        controlled.failOnce.set(true);try(var d=dispatcher()){assertThat(d.processOnce(event)).isFalse();}
        assertThat(jdbc.queryForObject("select status from marketplace_listing where id=?",String.class,listing)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,event)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from notification_event where id=?",Integer.class,notification)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ep29c_notification_effect where event_id=?",Integer.class,notification)).isZero();
        try(var restarted=dispatcher()){org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(12)).until(()->restarted.processOnce(event));}
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,event)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("select retry_count from outbox_events where id=?",Integer.class,event)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select attempts from ep29c_notification_effect where event_id=?",Integer.class,notification)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_records where action='MARKETPLACE_LISTING_PUBLISHED' and resource_id=?",Integer.class,listing)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_job where job_type='SEARCH_REINDEX' and tenant_id=?",Integer.class,tenant)).isEqualTo(1);
    }

    @Test void delayedPublicationCannotResurrectArchivedListingOrSearchProjection() throws Exception {
        String asset=asset();var publication=publish(approve(submit(create(asset))));String listing=publication.path("id").asText();
        response(http(user,"POST",root()+"/listings/"+listing+"/transitions",Map.of("commandId","archive","expectedVersion",publication.path("version").asLong(),"transition","ARCHIVE")),200);
        for(String type:List.of("marketplace.listing.archived","marketplace.listing.published"))try(var d=dispatcher()){assertThat(d.processOnce((String)fact(type).get("id"))).isTrue();}
        var jobs=context.getBean(PlatformJobRepository.class);var tasks=context.getBean(PlatformTaskRepository.class);
        for(String id:jdbc.queryForList("select id from platform_job where tenant_id=? order by created_at desc",String.class,tenant)) {
            var job=jobs.findById(id).orElseThrow();
            as(user,()->context.getBean(SearchReindexTaskHandler.class).execute(new TaskExecutionContext(job.id(),"controlled",com.example.platform.sandbox.execution.TaskCapability.REINDEX,job,null,job.payloadJson())));
        }
        assertThat(jdbc.queryForObject("select publish_status from search_projection where asset_id=?",String.class,asset)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("select status from marketplace_listing where id=?",String.class,listing)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("select aggregate_version from marketplace_listing where id=?",Long.class,listing)).isEqualTo(5L);
        assertThat(jdbc.queryForObject("select count(*) from platform_job where tenant_id=?",Integer.class,tenant)).isEqualTo(2);
        try(var d=dispatcher()){assertThat(d.processOnce(duplicate(fact("marketplace.listing.published")))).isTrue();}
        assertThat(jdbc.queryForObject("select count(*) from platform_job where tenant_id=?",Integer.class,tenant)).isEqualTo(2);
    }

    @Test void obsoleteEnvelopesAndScopeTamperingQuarantineWithoutConsumerEffects() throws Exception {
        var published=publish(approve(submit(create(asset()))));String listing=published.path("id").asText();var row=fact("marketplace.listing.published");
        var envelope=JSON.readTree((String)row.get("payload"));((com.fasterxml.jackson.databind.node.ObjectNode)envelope).put("tenantId","foreign");
        String malformed="bad_"+UUID.randomUUID();jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values (?,?,?,?,1,?,'PENDING',0,3,now())",malformed,row.get("aggregate_type"),row.get("aggregate_id"),row.get("event_type"),envelope.toString());
        try(var d=dispatcher()){assertThat(d.processOnce(malformed)).isFalse();}
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,malformed)).isEqualTo("DEAD_LETTER");
        String legacy="legacy_"+UUID.randomUUID();jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values (?,'ASSET','asset','asset.published',1,'{}','PENDING',0,3,now())",legacy);
        try(var d=dispatcher()){assertThat(d.processOnce(legacy)).isFalse();}
        assertThat(jdbc.queryForObject("select status from outbox_events where id=?",String.class,legacy)).isEqualTo("DEAD_LETTER");
        assertThat(jdbc.queryForObject("select count(*) from audit_records where action='MARKETPLACE_LISTING_PUBLISHED' and resource_id=?",Integer.class,listing)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_job where tenant_id=?",Integer.class,tenant)).isZero();
        try(var d=dispatcher()){assertThat(d.processOnce((String)row.get("id"))).isTrue();}
        assertThat(jdbc.queryForObject("select count(*) from platform_job where tenant_id=?",Integer.class,tenant)).isEqualTo(1);
        for(String type:List.of("AssetPublishedEvent","AssetApprovedEvent","AssetArchivedEvent","AssetSubmittedForReviewEvent"))
            assertThatThrownBy(()->Class.forName("com.example.platform.shared.events."+type)).isInstanceOf(ClassNotFoundException.class);
    }

    @Test void databaseFencesUnversionedLegacyWritersWithoutBlockingOwnerCommands() throws Exception {
        var published=publish(approve(submit(create(asset()))));String listing=published.path("id").asText();var before=state();
        assertThatThrownBy(()->jdbc.update("update marketplace_listing set status='DRAFT' where id=?",listing)).isInstanceOf(RuntimeException.class).hasMessageContaining("must advance its version");
        assertThatThrownBy(()->jdbc.update("update marketplace_listing set workspace_id=?,aggregate_version=aggregate_version+1 where id=?",project,listing)).isInstanceOf(RuntimeException.class).hasMessageContaining("immutable");
        assertThat(state()).isEqualTo(before);
        response(http(user,"POST",root()+"/listings/"+listing+"/transitions",Map.of("commandId","valid-archive","expectedVersion",published.path("version").asLong(),"transition","ARCHIVE")),200);
        assertThat(jdbc.queryForObject("select status from marketplace_listing where id=?",String.class,listing)).isEqualTo("ARCHIVED");
    }

    @Test void mediaSnapshotSerializesAnOldProjectionWriteBeforeConcurrentArchive() throws Exception {
        String asset=asset();var publication=publish(approve(submit(create(asset))));String listing=publication.path("id").asText();
        try(var d=dispatcher()){assertThat(d.processOnce((String)fact("marketplace.listing.published").get("id"))).isTrue();}
        String jobId=jdbc.queryForObject("select id from platform_job where tenant_id=?",String.class,tenant);
        var job=context.getBean(PlatformJobRepository.class).findById(jobId).orElseThrow();
        var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var oldProjection=pool.submit(()-> {
                com.example.platform.shared.web.TenantContext.set(job.tenantId());
                try {
                    new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                            .executeWithoutResult(tx->{
                                var snapshot=context.getBean(com.example.platform.media.api.MediaAssets.class).publicationSnapshot(tenant,project,asset);
                                assertThat(snapshot.publishStatus()).isEqualTo("PUBLISHED");locked.countDown();
                                try {if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Projection gate timed out");}
                                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                                context.getBean(SearchReindexTaskHandler.class).execute(new TaskExecutionContext(job.id(),"controlled",com.example.platform.sandbox.execution.TaskCapability.REINDEX,job,null,job.payloadJson()));
                            });
                } finally {com.example.platform.shared.web.TenantContext.clear();}
            });
            assertThat(locked.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var archive=pool.submit(()->http(user,"POST",root()+"/listings/"+listing+"/transitions",Map.of("commandId","concurrent-archive","expectedVersion",publication.path("version").asLong(),"transition","ARCHIVE")));
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%media_asset%'",Integer.class)>=1);
                assertThat(archive.isDone()).isFalse();
            } finally {release.countDown();}
            oldProjection.get(15,java.util.concurrent.TimeUnit.SECONDS);response(archive.get(15,java.util.concurrent.TimeUnit.SECONDS),200);
        } finally {release.countDown();}
        try(var d=dispatcher()){assertThat(d.processOnce((String)fact("marketplace.listing.archived").get("id"))).isTrue();}
        String newest=jdbc.queryForObject("select id from platform_job where tenant_id=? and id<>?",String.class,tenant,jobId);
        var latest=context.getBean(PlatformJobRepository.class).findById(newest).orElseThrow();
        as(user,()->context.getBean(SearchReindexTaskHandler.class).execute(new TaskExecutionContext(latest.id(),"controlled",com.example.platform.sandbox.execution.TaskCapability.REINDEX,latest,null,latest.payloadJson())));
        assertThat(jdbc.queryForObject("select publish_status from search_projection where asset_id=?",String.class,asset)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("select aggregate_version from marketplace_listing where id=?",Long.class,listing)).isEqualTo(5L);
    }
}
