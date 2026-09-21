package com.example.platform.social;

import com.example.platform.PlatformApplication;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.social.app.*;
import com.example.platform.social.domain.*;
import com.example.platform.social.infrastructure.persistence.SocialPostRepository;
import com.example.platform.social.infrastructure.platform.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real application, Identity authorization, migrations, PostgreSQL and transaction proxies. Only IO/timing is controlled. */
@SpringBootTest(classes=PlatformApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"app.social-publish.enabled=true", "app.social-publish.scheduler.enabled=true", "render.providers.natron.enabled=false"})
@ActiveProfiles({"dev","test","preview"})
class SocialPublicationOwnershipPostgresTest extends PostgresTestContainerSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired SocialPublishService service;
    @Autowired SocialPostRepository posts;
    @Autowired PostSchedulerService scheduler;
    @Autowired PlatformTransactionManager manager;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PlatformAdapter provider;
    @MockitoBean(name="taskScheduler") TaskScheduler clock;
    static final String SCHEMA=isolatedSchemaName();
    @org.springframework.test.context.DynamicPropertySource
    static void schema(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
    }
    String tenant, actor, project, account, id, workspace;

    @BeforeEach void fixture() {
        jdbc.update("DELETE FROM social_post");
        String key=UUID.randomUUID().toString().substring(0,24);
        tenant="t"+key; actor="u"+key; project="p"+key; account="a"+key; id="s"+key; workspace="w"+key;
        TenantContext.set(tenant);
        jdbc.update("INSERT INTO tenant(id,name,status,created_at) VALUES (?,?,'ACTIVE',now())",tenant,tenant);
        jdbc.update("INSERT INTO account(id,issuer,subject,status,created_at) VALUES (?,'test',?,'ACTIVE',now())",actor,actor);
        jdbc.update("INSERT INTO \"user\"(id,tenant_id,username,email,status,account_id,created_at) VALUES (?,?,?,?,'ACTIVE',?,now())",actor,tenant,actor,actor,actor);
        jdbc.update("INSERT INTO workspace(id,tenant_id,name,created_at,updated_at) VALUES (?,?,?,now(),now())",workspace,tenant,workspace);
        jdbc.update("INSERT INTO workspace_member(id,workspace_id,user_id,role,joined_at,updated_at) VALUES (?,?,?,'OWNER',now(),now())",actor,workspace,actor);
        jdbc.update("INSERT INTO project(id,tenant_id,workspace_id,name,status,created_at) VALUES (?,?,?,?,'ACTIVE',now())",project,tenant,workspace,project);
        jdbc.update("INSERT INTO role(id,role_key,name,scope,created_at) VALUES (?,?,?,'WORKSPACE',now())",actor,actor,actor);
        jdbc.update("INSERT INTO permission(id,permission_key,name,created_at) VALUES ('social-correction-publish','social.publish','publish',now()) ON CONFLICT(permission_key) DO NOTHING");
        String permission=jdbc.queryForObject("SELECT id FROM permission WHERE permission_key='social.publish'",String.class);
        jdbc.update("INSERT INTO role_permission(id,role_id,permission_id,created_at) VALUES (?,?,?,now())",actor,actor,permission);
        jdbc.update("INSERT INTO user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) VALUES (?,?,?,?,?,now())",actor,tenant,workspace,actor,actor);
        jdbc.update("INSERT INTO social_connected_platform(id,tenant_id,user_id,platform_type,status,binding_version) VALUES (?,?,?,'TWITTER','ACTIVE',2)",account,tenant,actor);
        jdbc.update("INSERT INTO social_post(id,tenant_id,user_id,project_id,connected_platform_id,connected_platform_binding_version,platform_type,status,scheduled_at) VALUES (?,?,?,?,?,2,'TWITTER','SCHEDULED',?)",id,tenant,actor,project,account,LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(provider.platform()).thenReturn(PlatformType.TWITTER);
        when(provider.validateCredentials(any())).thenReturn(true);
        when(provider.publish(any(),any())).thenAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(state().get("dispatch_started_at")).isNotNull();
            return success();
        });
    }
    @AfterEach void clear() { TenantContext.clear(); }
    PublishResult success() { return new PublishResult(true,"external","https://example.test/post",null,null); }
    Map<String,Object> state() {return jdbc.queryForMap("SELECT * FROM social_post WHERE id=?",id);}
    void publish() { service.publishNow(tenant,actor,id); }
    void scheduled() { TenantContext.set(tenant); try { service.publishScheduled(tenant,actor,id); } finally {TenantContext.clear();} }
    void await(CountDownLatch latch) throws InterruptedException { assertThat(latch.await(10,TimeUnit.SECONDS)).isTrue(); }
    SocialPostRepository.Attempt attempt() {return new SocialPostRepository.Attempt(posts.findById(id).orElseThrow(), (String)state().get("publication_attempt_id"));}

    @Test void ordinaryPublicationRepeatedProcessingAndExactBoundAccount() {
        jdbc.update("INSERT INTO social_connected_platform(id,tenant_id,user_id,platform_type,status) VALUES (?,?,?,'TWITTER','ACTIVE')","other"+account,tenant,actor);
        publish(); var before=state();
        assertThat(before).containsEntry("status","PUBLISHED").containsEntry("retry_count",0).containsEntry("attempt_account_id",account).containsEntry("attempt_binding_version",2L);
        scheduler.processScheduledPosts();
        assertThatThrownBy(this::publish).isInstanceOf(IllegalArgumentException.class);
        assertThat(state()).isEqualTo(before);
        var captured=org.mockito.ArgumentCaptor.forClass(ConnectedPlatform.class);
        verify(provider).publish(any(),captured.capture());
        assertThat(captured.getValue().id()).isEqualTo(account);
        assertThat(captured.getValue().bindingVersion()).isEqualTo(2);
    }
    @Test void overlappingSchedulersCannotAcquireSecondAttempt() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(provider.publish(any(),any())).thenAnswer(inv->{entered.countDown();await(release);return success();});
        try(var pool=Executors.newSingleThreadExecutor()) {
            var first=pool.submit(scheduler::processScheduledPosts);
            try {await(entered);var owned=state();scheduler.processScheduledPosts();assertThat(state()).isEqualTo(owned);
                assertThatThrownBy(this::publish).isInstanceOf(IllegalArgumentException.class);
                verify(provider).publish(any(),any());
            } finally {release.countDown();}
            first.get(15,TimeUnit.SECONDS);
        }
        assertThat(state()).containsEntry("status","PUBLISHED");
    }
    @Test void simultaneousCompareAndSetHasExactlyOneWinner() throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> claim=()->{TenantContext.set(tenant);try {barrier.await(10,TimeUnit.SECONDS);return new TransactionTemplate(manager).execute(tx->posts.claim(tenant,actor,id,UUID.randomUUID().toString(),PostStatus.SCHEDULED,Instant.now()).isPresent());} finally{TenantContext.clear();}};
            var a=pool.submit(claim);var b=pool.submit(claim);
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        var before=state();scheduler.processScheduledPosts();assertThat(state()).isEqualTo(before);
        assertThatThrownBy(()->service.retryPost(tenant,actor,id)).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo(before);verify(provider,never()).publish(any(),any());
    }
    @Test void obsoleteFailureCannotOverwriteCommittedSuccessAndStaleWritesPreserveEveryField() {
        publish();var before=state();var current=attempt();
        assertThat(posts.failBeforeDispatch(current,Instant.now())).isFalse();
        assertThat(posts.unresolved(current,Instant.now())).isFalse();
        assertThat(posts.complete(new SocialPostRepository.Attempt(current.post(),UUID.randomUUID().toString()),"wrong","wrong",Instant.now())).isFalse();
        assertThat(state()).isEqualTo(before);
    }
    @Test void predispatchCredentialFailureAllowsExplicitRetry() {
        when(provider.validateCredentials(any())).thenReturn(false);
        assertThatThrownBy(this::publish).hasMessageContaining("credentials");
        var first=state();assertThat(first).containsEntry("status","FAILED").containsEntry("retry_count",1).containsEntry("dispatch_started_at",null);
        verify(provider,never()).publish(any(),any());
        when(provider.validateCredentials(any())).thenReturn(true);service.retryPost(tenant,actor,id);
        assertThat(state()).containsEntry("status","PUBLISHED").containsEntry("retry_count",1);
        assertThat(state().get("publication_attempt_id")).isNotEqualTo(first.get("publication_attempt_id"));
    }
    @Test void uncertainOutcomeNeverAutomaticallyRetries() {
        when(provider.publish(any(),any())).thenThrow(new IllegalStateException("network outcome unknown"));
        assertThatThrownBy(this::publish).hasMessageContaining("unknown");
        var before=state();assertThat(before).containsEntry("status","UNRESOLVED").containsEntry("retry_count",0);
        scheduler.processScheduledPosts();
        assertThatThrownBy(()->service.retryPost(tenant,actor,id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->service.schedulePost(tenant,actor,id,new com.example.platform.social.api.dto.SchedulePostRequest(Instant.now().toString()))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->service.deletePost(tenant,actor,id)).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo(before);verify(provider).publish(any(),any());
    }
    @Test void enclosingTransactionRejectedBeforeAnyEffects() {
        var before=state();
        assertThatThrownBy(()->new TransactionTemplate(manager).executeWithoutResult(tx->publish())).hasMessageContaining("enclosing transaction");
        assertThat(state()).isEqualTo(before);verify(provider,never()).publish(any(),any());publish();
        assertThat(state()).containsEntry("status","PUBLISHED");
    }
    @Test void cancellationFencesLateSuccessAndFailure() throws Exception {
        for(boolean failed:List.of(false,true)) {
            if(failed) {fixture();clearInvocations(provider);}
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            when(provider.publish(any(),any())).thenAnswer(inv->{entered.countDown();await(release);if(failed)throw new IllegalStateException("late failure");return success();});
            try(var pool=Executors.newSingleThreadExecutor()) {
                var job=pool.submit(()->assertThatThrownBy(this::scheduled).isInstanceOf(IllegalStateException.class));
                Map<String,Object> cancelled;
                try {await(entered);service.cancelScheduled(tenant,actor,id);cancelled=state();assertThat(cancelled).containsEntry("status","UNRESOLVED").containsEntry("error_code","CANCELLED_AFTER_DISPATCH");}
                finally {release.countDown();}
                job.get(15,TimeUnit.SECONDS);assertThat(state()).isEqualTo(cancelled);
            }
        }
    }
    @Test void cancellationBeforeDispatchAllowsNewAttemptButFencesOldWorker() {
        var old=new TransactionTemplate(manager).execute(tx->posts.claim(tenant,actor,id,UUID.randomUUID().toString(),PostStatus.SCHEDULED,Instant.now()).orElseThrow());
        service.cancelScheduled(tenant,actor,id);
        service.schedulePost(tenant,actor,id,new com.example.platform.social.api.dto.SchedulePostRequest(Instant.now().minusSeconds(1).toString()));
        publish();var before=state();
        assertThat(posts.markDispatched(old,Instant.now())).isFalse();assertThat(posts.failBeforeDispatch(old,Instant.now())).isFalse();assertThat(posts.complete(old,"late","late",Instant.now())).isFalse();assertThat(state()).isEqualTo(before);
    }
    @Test void invalidBindingsAndCanonicalPermissionFailClosed() {
        for(String change:List.of(
                "UPDATE social_post SET project_id=NULL,connected_platform_id=NULL,connected_platform_binding_version=NULL WHERE id=?",
                "UPDATE social_connected_platform SET status='INACTIVE' WHERE id=?",
                "UPDATE workspace_member SET status='REMOVED' WHERE id=?",
                "DELETE FROM user_role_assignment WHERE id=?",
                "UPDATE project SET status='ARCHIVED' WHERE id=?")) {
            jdbc.update(change,change.contains("social_post")?id:change.contains("social_connected")?account:change.contains("project SET")?project:actor);
            assertThatThrownBy(this::publish).isInstanceOf(RuntimeException.class);
            assertThat(state()).containsEntry("status","FAILED").containsEntry("dispatch_started_at",null).containsEntry("platform_post_id",null).containsEntry("retry_count",1);
            verify(provider,never()).publish(any(),any());fixture();
        }
        var before=state();assertThatThrownBy(()->service.publishNow(tenant,"foreign",id)).isInstanceOf(IllegalArgumentException.class);assertThat(state()).isEqualTo(before);
        TenantContext.set("foreign");assertThatThrownBy(this::publish).isInstanceOf(RuntimeException.class);TenantContext.set(tenant);assertThat(state()).isEqualTo(before);
        // Invalid revision cannot exist under the composite FK; this is separate schema evidence.
        assertThatThrownBy(()->jdbc.update("UPDATE social_post SET connected_platform_binding_version=99 WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(state()).isEqualTo(before);
    }
    @Test void accountChangeBeforeDispatchRejectsButAfterDispatchCannotRecallRequest() {
        when(provider.validateCredentials(any())).thenAnswer(inv->{jdbc.update("UPDATE social_connected_platform SET status='INACTIVE' WHERE id=?",account);return true;});
        assertThatThrownBy(this::publish).hasMessageContaining("binding");verify(provider,never()).publish(any(),any());
        jdbc.update("UPDATE social_connected_platform SET status='ACTIVE' WHERE id=?",account);
        when(provider.validateCredentials(any())).thenReturn(true);
        when(provider.publish(any(),any())).thenAnswer(inv->{jdbc.update("UPDATE social_connected_platform SET status='INACTIVE' WHERE id=?",account);return success();});
        service.retryPost(tenant,actor,id);assertThat(state()).containsEntry("status","PUBLISHED").containsEntry("attempt_account_id",account);
    }
    @Test void completionDatabaseFailureRetainsCommittedDispatchAndCannotRepublish() {
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_social_completion() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='PUBLISHED' THEN RAISE EXCEPTION 'controlled completion failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_social_completion BEFORE UPDATE ON social_post FOR EACH ROW EXECUTE FUNCTION reject_social_completion()");
        try {assertThatThrownBy(this::publish).isInstanceOf(org.springframework.dao.DataAccessException.class);}
        finally {jdbc.execute("DROP TRIGGER reject_social_completion ON social_post");jdbc.execute("DROP FUNCTION reject_social_completion()");}
        var before=state();assertThat(before).containsEntry("status","UNRESOLVED");assertThat(before.get("dispatch_started_at")).isNotNull();
        scheduler.processScheduledPosts();assertThatThrownBy(()->service.retryPost(tenant,actor,id)).isInstanceOf(IllegalStateException.class);assertThat(state()).isEqualTo(before);verify(provider).publish(any(),any());
    }
}
