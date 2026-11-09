package com.example.platform.testinfra;

import com.example.platform.config.firstrender.*;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.execution.binding.*;
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.persistence.binding.JooqBoundGraphInputStore;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.render.api.GraphRenderLifecycle.PreparationStage;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.*;
import com.example.platform.render.app.dto.*;
import com.example.platform.render.app.event.*;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.policy.RenderPolicyEngine;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.identity.api.project.ProjectReadQuery;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.*;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.api.revision.*;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.semantics.effect.*;
import com.example.platform.timeline.semantics.temporal.*;
import com.example.platform.timeline.version.*;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.outbox.app.*;
import java.time.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real preparation/planning/binding/save and Render persistence; failures do not invoke fail directly. */
class FirstRenderPreparationPostgresTest {
    static final String TENANT="tenant-a", PROJECT="project-a", REVISION="preparation-revision", SOURCE="source-a";
    static final EffectSemanticSnapshot EFFECT = new EffectSemanticSnapshotAuthority(
            new EffectDefinitionVersionRegistry.InMemory(), new EffectSemanticSnapshotStore.InMemory()).mintEmpty();

    @AfterEach void clear() { TenantContext.clear(); }

    static class Scenario implements AutoCloseable {
        final GraphRenderLifecycleIT.World world = new GraphRenderLifecycleIT.World();
        final GraphRenderLifecycle lifecycle;
        final FirstRenderPlanningAndBindingService planning;
        final FirstRenderSaveAndStartService save;
        final FirstRenderWorkflowStarter starter = mock(FirstRenderWorkflowStarter.class);
        final RenderLifecyclePublisher events;
        Scenario(String fault) throws Exception {
            var db=world.db;
            events=new RenderLifecyclePublisher(new OutboxEventService(db,3,mock(PostgresNotificationService.class),
                    new OutboxEventRouter(List.of(new RenderOutboxEvents()))));
            // Tests emulate Spring's transaction interception on this one schema connection.
            // Every intercepted operation executes the production owner, with commit/rollback.
            lifecycle=(GraphRenderLifecycle)Proxy.newProxyInstance(GraphRenderLifecycle.class.getClassLoader(),
                    new Class<?>[]{GraphRenderLifecycle.class},(proxy,method,args)->world.tx(()-> {
                        try { return method.invoke(world.owner,args); }
                        catch (InvocationTargetException e) {
                            if(e.getCause() instanceof RuntimeException failure)throw failure;
                            throw new IllegalStateException(e.getCause());
                        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
                    }));
            var render=new RenderJobService(world.jobs,mock(RenderPolicyEngine.class),events,
                    new RenderJobStatusHistoryRepository(db),null,mock(ProjectReadQuery.class),
                    mock(CanonicalActorResolver.class),mock(AuthorizationDecisionPort.class),
                    mock(RenderAcceptanceContextService.class)) {
                @Override public RenderJobResponse createForGraphPreparation(String tenant,String project,
                        CreateRenderJobRequest request,RenderInitiator initiator) {
                    try { return world.tx(()->super.createForGraphPreparation(tenant,project,request,initiator)); }
                    catch(RuntimeException e) { throw e; }
                    catch(Exception e) { throw new IllegalStateException(e); }
                }
            };
            var commands=mock(TimelineRevisionCommands.class);
            var queries=mock(TimelineRevisionQueries.class);
            var artifacts=mock(ArtifactQueryService.class);
            var interoperability=mock(ProviderInteroperabilityAuthority.class);
            var effects=new JdbcEffectSemanticSnapshotStore(db);
            if(!"SNAPSHOT".equals(fault)) world.tx(()->{effects.storeTx(db,PROJECT,TENANT,EFFECT);return null;});
            var document=document();String json=TimelineDocumentJsonSerializer.serialize(document);
            String timelineDigest=new TimelineContentDigester().digest(document);
            String revisionDigest=TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(timelineDigest,EFFECT.reference());
            var revision=new TimelineRevision(REVISION,PROJECT,null,TimelineDocument.CURRENT_SCHEMA_VERSION,null,
                    revisionDigest,Instant.EPOCH,"test",new TimelineRevisionSemanticContext(timelineDigest,
                    EFFECT.reference(),revisionDigest,TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
            when(commands.findById(TENANT,REVISION)).thenReturn(revision);
            when(queries.getRevisionSnapshotPayload(PROJECT,TENANT,REVISION)).thenReturn(Optional.of(
                    new TimelineRevisionQueries.RevisionSnapshotPayload("snapshot",json,"timeline-1.0")));
            when(artifacts.getArtifact(TENANT,new ArtifactId(SOURCE))).thenReturn("PLANNING".equals(fault)
                    ?Optional.empty():Optional.of(mock(com.example.platform.artifact.domain.Artifact.class)));
            when(interoperability.declarations()).thenReturn(List.of());
            var catalog=FirstRenderPlanningFixture.catalog();
            if ("BINDING".equals(fault)) when(catalog.contributions()).thenReturn(List.of());
            var inputs=new FirstRenderPlanningApplicationService(render,commands,queries,artifacts,interoperability);
            planning=new FirstRenderPlanningAndBindingService(inputs,commands,queries,effects,catalog,
                    new RenderPlanningEntryService(),new PhysicalPlanningEntryService(),new ProviderBindingEntryService(),lifecycle);
            if("BOUND_GRAPH_SAVE".equals(fault)) {
                db.execute("create function reject_bound_graph_save() returns trigger language plpgsql as $$ begin raise exception 'synthetic save failure'; end $$");
                db.execute("create trigger reject_bound_graph_save before insert on render_binding_inputs for each row execute function reject_bound_graph_save()");
            }
            when(starter.start(any(),any(),any())).thenReturn(true);
            save=new FirstRenderSaveAndStartService(new JooqBoundGraphInputStore(db),lifecycle);
        }
        FirstRenderPlanningAndBindingResult plan() {
            return planning.planAndBind(new FirstRenderPlanningRequest(TENANT,REVISION,
                    new RenderInitiator.System("probe",ActorType.SYSTEM,TENANT),
                    new com.example.platform.render.domain.renderplan.RenderExtent(
                            MediaTime.ofMillis(0),MediaTime.ofMillis(2000),FrameRate.of(30,1)),"preparation-test"));
        }
        org.jooq.Record job() { return world.db.fetchOne("select * from render_job where profile='preparation-test'"); }
        void assertFailed(String stage) {
            var job=job();assertNotNull(job);assertEquals("FAILED",job.get("status"));
            String code="GRAPH_PREPARATION_"+stage+"_FAILED";
            assertEquals(code,job.get("error_message"));assertNull(job.get("graph_preparation_deadline"));
            assertEquals(1,world.db.fetchOne("select count(*) from render_job_status_history where job_id=? and to_status='FAILED' and error_code=?",job.get("id"),code).get(0,Integer.class));
            assertEquals(1,world.db.fetchOne("select count(*) from outbox_events where aggregate_id=? and event_type='render.job.failed'",job.get("id")).get(0,Integer.class));
            assertEquals(0,world.count("quota_usage_operation"));
            verifyNoInteractions(starter);
        }
        public void close() throws Exception { world.close(); }
    }
    @Test void missingPinnedSnapshotFailsCreatedJobThroughRealPreparationChain() throws Exception {
        try(var s=new Scenario("SNAPSHOT")){assertThrows(RuntimeException.class,s::plan);s.assertFailed("SNAPSHOT");}
    }
    @Test void unresolvedSourceFailsAtActualPlanningGateAndProjectsOneFailure() throws Exception {
        try(var s=new Scenario("PLANNING")){assertThrows(RuntimeException.class,s::plan);s.assertFailed("PLANNING");}
    }
    @Test void emptyCatalogFailsRealBindingAndLeavesNoUnexplainedQueuedJob() throws Exception {
        try(var s=new Scenario("BINDING")){assertThrows(RuntimeException.class,s::plan);s.assertFailed("BINDING");}
    }
    @Test void databaseSaveFailureUsesRenderOwnerAndNeverStartsWorkflow() throws Exception {
        try(var s=new Scenario("BOUND_GRAPH_SAVE")){var plan=s.plan();assertThrows(RuntimeException.class,()->s.save.save(plan));s.assertFailed("BOUND_GRAPH_SAVE");assertEquals(0,s.world.count("render_binding_inputs"));}
    }
    @Test void crashedPreparationIsRecoveredAfterDurableDeadlineButNotBeforeIt() throws Exception {
        try(var s=new Scenario("NONE")) {
            var plan=s.plan();String job=plan.renderJobId();
            var recovery=new StaleRenderJobCompensationService(s.world.db,new RenderJobStatusHistoryRepository(s.world.db),s.events);
            var request=StaleRenderJobCompensationService.CompensationRequest.scheduled(Duration.ofMinutes(30));
            assertEquals(0,s.world.tx(()->recovery.compensate(request)).compensated());
            // Fault fixture simulates elapsed time after process loss, never writes a job terminal.
            s.world.db.execute("update render_job set graph_preparation_deadline=clock_timestamp()-interval '1 second' where id=?",job);
            assertEquals(1,s.world.tx(()->recovery.compensate(request)).compensated());
            assertEquals(0,s.world.tx(()->recovery.compensate(request)).compensated());
            assertEquals("FAILED",s.job().get("status"));
            assertEquals("GRAPH_PREPARATION_BINDING_TIMEOUT",s.job().get("error_message"));
            assertThrows(RuntimeException.class,()->s.save.save(plan));
            verifyNoInteractions(s.starter);assertEquals(0,s.world.count("render_graph_execution"));
        }
    }
    @Test void actualBeginDisarmsPreparationRecoveryAndLateFailureCannotOverwriteExecution() throws Exception {
        try(var s=new Scenario("NONE")) {
            var plan=s.plan();s.save.save(plan);
            var reference=new JooqBoundGraphInputStore(s.world.db).load(new JooqBoundGraphInputStore(s.world.db).save(plan.boundGraphInputs(),TENANT,plan.renderJobId()));
            // Use the actual bound final task, not a fabricated Render output association.
            var graph=new ProviderBindingEntryService().bind(reference.physicalPlan(),reference.candidates(),reference.transitionDeclarations()).executableTaskGraph();
            String task=graph.tasks().getLast().id().sha256Hex();
            var bound=new JooqBoundGraphInputStore(s.world.db).save(plan.boundGraphInputs(),TENANT,plan.renderJobId());
            s.lifecycle.begin(TENANT,plan.renderJobId(),bound.planRef(),bound.planDigest(),bound.expectedExecutableTaskGraphDigest(),task);
            s.lifecycle.failPreparation(TENANT,plan.renderJobId(),PreparationStage.DISPATCH);
            assertEquals("EXECUTING",s.job().get("status"));assertNull(s.job().get("graph_preparation_deadline"));
            assertEquals(0,s.world.count("quota_usage_operation"));
        }
    }
    static TimelineDocument document() {
        var clip=new TimelineClip("clip","asset","stream",SOURCE,"a".repeat(64),MediaTime.ofMillis(0),MediaTime.ofMillis(2000),
                MediaTime.ofMillis(0),MediaTime.ofMillis(2000),"MEDIA_STREAM",ConstantRateTemporalMapping.of(1,1,PlaybackDirection.FORWARD));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,List.of(new TimelineTrack("track","video",TrackType.VIDEO,List.of(clip))),TimelineMetadata.empty(),AudioMix.EMPTY,List.of(),List.of());
    }
}
