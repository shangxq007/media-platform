package com.example.platform.testinfra;

import com.example.platform.shared.test.*;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.render.api.GraphRenderLifecycle.*;
import com.example.platform.render.app.*;
import com.example.platform.render.app.event.*;
import com.example.platform.render.infrastructure.*;
import com.example.platform.artifact.infrastructure.JooqArtifactApplicationQuery;
import com.example.platform.entitlement.app.*;
import com.example.platform.entitlement.infrastructure.QuotaUsageJdbcRepository;
import com.example.platform.outbox.app.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Each case owns a real PG schema and drives Render's production methods and typed outbox codec. */
class GraphRenderLifecycleIT {
    static final String PLAN="a".repeat(64),GRAPH="b".repeat(64),TASK="c".repeat(64);
    @AfterEach void clear(){TenantContext.clear();}
    static class World implements AutoCloseable {
        final TestDatabaseSchema schema;
        final org.jooq.DSLContext db;
        final GraphRenderLifecycleService owner;
        final RenderJobRepository jobs;
        World() throws Exception {
            TenantContext.set("tenant-a");schema=TestDatabaseSchema.open(TestDatabaseConnectionProvider.fromControlledLaunch());
            for(String file:List.of("V1__baseline.sql"))try(var input=getClass().getResourceAsStream("/db/migration/"+file)){schema.executeScript(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            db=schema.dsl();
            db.execute("insert into tenant(id,name,created_at) values('tenant-a','test',current_timestamp),('tenant-b','other',current_timestamp)");
            db.execute("insert into project(id,tenant_id,name,created_at) values('project-a','tenant-a','test',current_timestamp),('project-b','tenant-b','other',current_timestamp)");
            db.execute("insert into render_job(id,project_id,tenant_id,timeline_snapshot_id,profile,status,initiator_type,initiator_id,initiator_tenant_id,created_at) values('job-a','project-a','tenant-a','snapshot','test','QUEUED','USER','user-a','tenant-a',current_timestamp),('job-b','project-b','tenant-b','snapshot','test','QUEUED','USER','user-b','tenant-b',current_timestamp)");
            db.execute("insert into wf_execution_backend_selection(selection_id,task_id,backend,placement_authority_scope,active,selected_at) values('selection',?,'NATIVE_PULL_WORKER','PLATFORM_MANAGED',true,now())",TASK);
            db.execute("insert into wf_execution_ownership_generation(task_id,generation,created_at) values(?,1,now())",TASK);
            db.execute("insert into wf_execution_attempt(attempt_id,task_id,generation,backend,state,backend_selection_id,created_at,updated_at) values('task-attempt-1',?,1,'NATIVE_PULL_WORKER','CREATED','selection',now(),now())",TASK);
            jobs=new RenderJobRepository(db);
            var events=new RenderLifecyclePublisher(new OutboxEventService(db,3,mock(PostgresNotificationService.class),new OutboxEventRouter(List.of(new RenderOutboxEvents()))));
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(schema.connection(),true));
            var quota=new QuotaConsumptionApplicationService(new QuotaPolicyService(),new QuotaUsageAuthority(new QuotaUsageJdbcRepository(jdbc)));
            owner=new GraphRenderLifecycleService(jobs,new GraphRenderExecutionRepository(db),new RenderStateTransitions(jobs,new RenderJobStatusHistoryRepository(db),events),events,new JooqArtifactApplicationQuery(db),quota);
        }
        <T> T tx(java.util.function.Supplier<T> action) throws Exception {
            schema.connection().setAutoCommit(false);
            try{T value=action.get();schema.connection().commit();return value;}catch(RuntimeException e){schema.connection().rollback();throw e;}finally{schema.connection().setAutoCommit(true);}
        }
        Execution begin() throws Exception {return tx(()->owner.begin("tenant-a","job-a","plan-ref",PLAN,GRAPH,TASK));}
        Output output(){return new Output(TASK,"task-attempt-1",1,"artifact-final");}
        void artifact(String id,String tenant,String project,String job,String kind) {
            db.execute("insert into artifact(id,tenant_id,project_id,render_job_id,content_digest,byte_length,media_type,artifact_kind,state,schema_version,created_at) values(?,?,?,?,?,1,'VIDEO',?,'AVAILABLE',1,current_timestamp)",id,tenant,project,job,"a".repeat(64),kind);
        }
        void ready(Execution e) throws Exception {tx(()->{owner.bindOutput(e,output());return null;});artifact("artifact-final","tenant-a","project-a","job-a","RENDER_MASTER");}
        String state(){return db.fetchOne("select status from render_job where id='job-a'").get("status",String.class);}
        int count(String table){return db.fetchCount(org.jooq.impl.DSL.table(table));}
        public void close() throws Exception{schema.close();}
    }
    @Test void exactFinalOutputCompletesOnceWithQuotaAndTypedFacts() throws Exception {
        try(var w=new World()) {
            var e=w.begin();assertEquals("EXECUTING",w.state());assertEquals(e,w.begin());w.ready(e);
            var accepted=w.tx(()->w.owner.complete(e,w.output()));
            assertEquals("artifact-final",accepted.artifactId().value());assertEquals("COMPLETED",w.state());
            assertEquals(1,w.count("quota_usage_operation"));assertEquals(6,w.count("outbox_events"));
            assertEquals(1,w.db.fetchCount(org.jooq.impl.DSL.table("outbox_events"),org.jooq.impl.DSL.field("event_type").eq("render.job.completed")));
            assertEquals(accepted,w.tx(()->w.owner.complete(e,w.output())));assertEquals(1,w.count("quota_usage_operation"));assertEquals(6,w.count("outbox_events"));
            var status=w.owner.find("tenant-a","project-a","job-a").orElseThrow();assertEquals("artifact-final",status.finalArtifactId());assertEquals("COMPLETED",status.status());
        }
    }
    @Test void outboxFailureRollsBackTerminalOutputQuotaAndStateAndCanRetry() throws Exception {
        try(var w=new World()) {
            var e=w.begin();w.ready(e);
            w.db.execute("create function reject_completion() returns trigger language plpgsql as $$ begin if new.event_type='render.job.completed' then raise exception 'synthetic terminal outbox fault'; end if;return new;end $$");
            w.db.execute("create trigger reject_completion before insert on outbox_events for each row execute function reject_completion()");
            assertThrows(org.jooq.exception.DataAccessException.class,()->w.tx(()->w.owner.complete(e,w.output())));
            assertEquals("EXECUTING",w.state());assertNull(w.owner.find("tenant-a","project-a","job-a").orElseThrow().finalArtifactId());assertEquals(0,w.count("quota_usage_operation"));assertEquals(3,w.count("outbox_events"));
            w.db.execute("drop trigger reject_completion on outbox_events");w.tx(()->w.owner.complete(e,w.output()));assertEquals("COMPLETED",w.state());assertEquals(1,w.count("quota_usage_operation"));
        }
    }
    @Test void staleGraphAttemptGenerationAndOutputCannotConsumeQuotaOrPublish() throws Exception {
        try(var w=new World()) {
            var e=w.begin();w.ready(e);
            var stale=new Execution(e.tenantId(),e.projectId(),e.jobId(),e.planRef(),e.planDigest(),e.graphDigest(),"old-attempt",e.generation()+1,e.finalTaskId());
            assertThrows(IllegalArgumentException.class,()->w.tx(()->w.owner.complete(stale,w.output())));
            assertThrows(IllegalArgumentException.class,()->w.tx(()->w.owner.complete(e,new Output(TASK,"old-task-attempt",2,"artifact-final"))));
            assertThrows(IllegalArgumentException.class,()->w.tx(()->{w.owner.bindOutput(e,new Output(TASK,"task-attempt-1",1,"different-artifact"));return null;}));
            assertEquals("EXECUTING",w.state());assertEquals(0,w.count("quota_usage_operation"));assertEquals(3,w.count("outbox_events"));
        }
    }
    @Test void wrongTenantProjectJobAndIntermediateArtifactNeverBecomeFinal() throws Exception {
        try(var w=new World()) {
            var e=w.begin();w.tx(()->{w.owner.bindOutput(e,w.output());return null;});
            w.artifact("artifact-final","tenant-b","project-b","job-b","RENDER_MASTER");
            assertThrows(IllegalArgumentException.class,()->w.tx(()->w.owner.complete(e,w.output())));
            TenantContext.set("tenant-b");assertThrows(RuntimeException.class,()->w.owner.find("tenant-a","project-a","job-a"));TenantContext.set("tenant-a");
            assertTrue(w.owner.find("tenant-a","project-b","job-a").isEmpty());assertEquals(0,w.count("quota_usage_operation"));assertEquals("EXECUTING",w.state());
        }
        try(var w=new World()) {var e=w.begin();w.tx(()->{w.owner.bindOutput(e,w.output());return null;});w.artifact("artifact-final","tenant-a","project-a","job-a","DERIVED_MEDIA");assertThrows(IllegalStateException.class,()->w.tx(()->w.owner.complete(e,w.output())));assertEquals(0,w.count("quota_usage_operation"));}
    }
    @Test void failureAndCancellationAreTerminalAndRejectLateSuccess() throws Exception {
        for(boolean cancelled:List.of(false,true))try(var w=new World()) {
            var e=w.begin();w.ready(e);w.tx(()->{w.owner.fail("tenant-a","job-a","plan-ref",PLAN,GRAPH,cancelled);return null;});
            assertEquals(cancelled?"CANCELLED":"FAILED",w.state());int facts=w.count("outbox_events");
            assertThrows(IllegalArgumentException.class,()->w.tx(()->w.owner.complete(e,w.output())));
            w.tx(()->{w.owner.fail("tenant-a","job-a","plan-ref",PLAN,GRAPH,cancelled);return null;});assertEquals(facts,w.count("outbox_events"));assertEquals(0,w.count("quota_usage_operation"));
        }
    }
    @Test void dispatchOrPreparationFailureLeavesVisibleFailedJobInsteadOfQueued() throws Exception {
        try(var w=new World()) {w.tx(()->{w.owner.fail("tenant-a","job-a","plan-ref",PLAN,GRAPH,false);return null;});assertEquals("FAILED",w.state());assertEquals(0,w.count("quota_usage_operation"));assertEquals(2,w.count("outbox_events"));}
    }
}
