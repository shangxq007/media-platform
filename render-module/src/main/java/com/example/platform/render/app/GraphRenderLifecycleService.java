package com.example.platform.render.app;

import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.render.api.event.*;
import com.example.platform.render.app.event.RenderLifecyclePublisher;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.*;
import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.shared.commercial.*;
import java.time.*;
import java.util.*;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional owner of graph fencing, terminal projection, accepted output, quota and facts. */
@Service
public class GraphRenderLifecycleService implements GraphRenderLifecycle {
    private final RenderJobRepository jobs;
    private final GraphRenderExecutionRepository executions;
    private final RenderStateTransitions lifecycle;
    private final RenderLifecyclePublisher events;
    private final ArtifactApplicationQuery artifacts;
    private final QuotaConsumptionPort quota;
    public GraphRenderLifecycleService(RenderJobRepository jobs, GraphRenderExecutionRepository executions,
            RenderStateTransitions lifecycle, RenderLifecyclePublisher events,
            ArtifactApplicationQuery artifacts, QuotaConsumptionPort quota) {
        this.jobs=jobs;this.executions=executions;this.lifecycle=lifecycle;this.events=events;this.artifacts=artifacts;this.quota=quota;
    }
    @Override @Transactional
    public void preparationStage(String tenant, String jobId, PreparationStage stage) {
        TenantGuard.assertSameTenant(tenant);
        var job = jobs.requireScopedJobForUpdate(tenant, jobId);
        String current = job.get("graph_preparation_stage", String.class);
        if (!"QUEUED".equals(job.get("status", String.class)) || current == null) {
            throw new IllegalStateException("graph preparation no longer current");
        }
        if (stage.ordinal() < PreparationStage.valueOf(current).ordinal()) {
            throw new IllegalArgumentException("graph preparation cannot move backwards");
        }
        jobs.graphPreparationStage(tenant, jobId, stage.name());
    }
    @Override @Transactional
    public void failPreparation(String tenant, String jobId, PreparationStage stage) {
        TenantGuard.assertSameTenant(tenant);
        var job = jobs.requireScopedJobForUpdate(tenant, jobId);
        if (!"QUEUED".equals(job.get("status", String.class))) return;
        String current = job.get("graph_preparation_stage", String.class);
        if (current == null || !current.equals(stage.name())) return;
        String project = job.get("project_id", String.class);
        if (executions.find(tenant, project, jobId).isPresent()) {
            throw new IllegalStateException("started graph must use its fenced execution lifecycle");
        }
        String code = "GRAPH_PREPARATION_" + stage.name() + "_FAILED";
        lifecycle.transition(jobId, project, RenderJobStatus.QUEUED, RenderJobStatus.FAILED, code);
        jobs.closeGraphPreparation(tenant, jobId, code);
        events.publishEvent(new RenderJobFailedEvent(jobId, project, RenderFailureReason.EXECUTION_FAILED,
                Instant.now(), RenderJobRepository.initiatorFrom(job), RenderJobStatus.FAILED));
    }
    @Override @Transactional
    public Execution begin(String tenant,String jobId,String planRef,String plan,String graph,String finalTask) {
        TenantGuard.assertSameTenant(tenant);
        for(String value:List.of(planRef,plan,graph,finalTask)) if(value.isBlank()) throw new IllegalArgumentException("execution pin required");
        Record job=jobs.requireScopedJobForUpdate(tenant,jobId);String project=job.get("project_id",String.class);
        var deadline=job.get("graph_preparation_deadline",OffsetDateTime.class);
        if (deadline != null && !deadline.toInstant().isAfter(Instant.now())) {
            throw new IllegalStateException("graph preparation window expired");
        }
        var old=executions.find(tenant,project,jobId);
        Execution e=old.map(GraphRenderExecutionRepository::execution).orElseGet(()->new Execution(tenant,project,jobId,planRef,plan,graph,UUID.randomUUID().toString(),1,finalTask));
        if(!e.planRef().equals(planRef)||!e.planDigest().equals(plan)||!e.graphDigest().equals(graph)||!e.finalTaskId().equals(finalTask)) throw new IllegalArgumentException("bound execution identity conflict");
        if(old.isEmpty())executions.insert(e);
        var state=RenderJobStatus.valueOf(job.get("status",String.class));
        if(state==RenderJobStatus.QUEUED) {
            lifecycle.transition(jobId,project,state,RenderJobStatus.SELECTING_PROVIDER,null);
            lifecycle.transition(jobId,project,RenderJobStatus.SELECTING_PROVIDER,RenderJobStatus.PROVIDER_SELECTED,null);
            lifecycle.transition(jobId,project,RenderJobStatus.PROVIDER_SELECTED,RenderJobStatus.EXECUTING,null);
            jobs.closeGraphPreparation(tenant,jobId,null);
        } else if(state!=RenderJobStatus.EXECUTING&&state!=RenderJobStatus.COMPLETING&&state!=RenderJobStatus.COMPLETED) {
            throw new IllegalStateException("graph execution cannot begin in terminal state");
        }
        return e;
    }
    private Record locked(Execution e) {
        TenantGuard.assertSameTenant(e.tenantId());
        var job=jobs.requireScopedJobForUpdate(e.tenantId(),e.jobId());
        if(!e.projectId().equals(job.get("project_id",String.class))) throw new IllegalArgumentException("execution project conflict");
        Record row=executions.find(e.tenantId(),e.projectId(),e.jobId()).orElseThrow(()->new IllegalStateException("execution absent"));
        if(!e.equals(GraphRenderExecutionRepository.execution(row)))throw new IllegalArgumentException("stale graph attempt or generation");
        return row;
    }
    private void requireOutput(Execution e,Output o,Record row) {
        if(o==null||!e.finalTaskId().equals(o.taskId())||o.taskAttemptId()==null||o.taskAttemptId().isBlank()||o.taskGeneration()<1)throw new IllegalArgumentException("final task assignment mismatch");
        if(row.get("expected_artifact_id")!=null && (!o.artifactId().equals(row.get("expected_artifact_id",String.class))
                ||!o.taskAttemptId().equals(row.get("task_attempt_id",String.class))||o.taskGeneration()!=row.get("task_generation",Long.class)))throw new IllegalArgumentException("stale or conflicting final publication");
    }
    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireExecuting(Execution e) {
        locked(e);
        if (!"EXECUTING".equals(jobs.requireScopedJobForUpdate(e.tenantId(), e.jobId()).get("status", String.class)))
            throw new IllegalStateException("Render execution no longer accepts publication");
    }
    @Override @Transactional
    public void bindOutput(Execution e,Output o) {
        Record row=locked(e);requireOutput(e,o,row);
        String status=jobs.requireScopedJobForUpdate(e.tenantId(),e.jobId()).get("status",String.class);
        if(!Set.of("EXECUTING","COMPLETING","COMPLETED").contains(status))throw new IllegalStateException("execution no longer accepts output");
        if(row.get("expected_artifact_id")==null)executions.bind(e,o);
    }
    @Override @Transactional
    public ArtifactOutputReference complete(Execution e,Output o) {
        Record row=locked(e);requireOutput(e,o,row);
        if(row.get("expected_artifact_id")==null)throw new IllegalStateException("output was not bound before execution");
        var job=jobs.requireScopedJobForUpdate(e.tenantId(),e.jobId());
        var scope=new ArtifactScope(e.tenantId(),e.projectId(),e.jobId());
        var reference=new ArtifactOutputReference(scope,new ArtifactId(o.artifactId()));
        Artifact a=artifacts.findArtifact(scope,reference.artifactId()).orElseThrow(()->new IllegalArgumentException("final Artifact scope mismatch"));
        if(a.state()!=ArtifactState.AVAILABLE||a.artifactKind()!=ArtifactKind.RENDER_MASTER)throw new IllegalStateException("final Artifact is not committed");
        var state=RenderJobStatus.valueOf(job.get("status",String.class));
        if(state==RenderJobStatus.COMPLETED) {
            if(!o.artifactId().equals(row.get("final_artifact_id",String.class)))throw new IllegalArgumentException("completed output conflict");
            return reference;
        }
        if(state!=RenderJobStatus.EXECUTING&&state!=RenderJobStatus.COMPLETING)throw new IllegalArgumentException("terminal execution cannot succeed");
        if(state==RenderJobStatus.EXECUTING)lifecycle.transition(e.jobId(),e.projectId(),state,RenderJobStatus.COMPLETING,null);
        Instant now=Instant.now();YearMonth month=YearMonth.from(now.atZone(ZoneOffset.UTC));
        var quotaDecision=quota.consume(new QuotaConsumptionRequest(PrincipalRef.tenantScoped(e.tenantId(),PrincipalType.ORGANIZATION,e.tenantId()),"render.job.create",1,
                month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                "render-job:"+e.jobId()+":completion","render-job:"+e.jobId(),"render completion "+e.jobId(),now));
        if(quotaDecision==null||!quotaDecision.allowed())throw new IllegalArgumentException("Render completion quota settlement rejected");
        executions.accept(e,o.artifactId());
        lifecycle.transition(e.jobId(),e.projectId(),RenderJobStatus.COMPLETING,RenderJobStatus.COMPLETED,null);
        events.publishEvent(new RenderJobCompletedEvent(reference,now,RenderJobRepository.initiatorFrom(job)));
        return reference;
    }
    @Override @Transactional
    public void fail(String tenant,String jobId,String ref,String plan,String graph,boolean cancelled) {
        TenantGuard.assertSameTenant(tenant);
        var job=jobs.requireScopedJobForUpdate(tenant,jobId);String project=job.get("project_id",String.class);
        executions.find(tenant,project,jobId).ifPresent(row->{var e=GraphRenderExecutionRepository.execution(row);
            if(!e.planRef().equals(ref)||!e.planDigest().equals(plan)||!e.graphDigest().equals(graph))throw new IllegalArgumentException("failure execution mismatch");});
        var state=RenderJobStatus.valueOf(job.get("status",String.class));
        if(state.isTerminal())return; // late failure/cancellation never overwrites an accepted success
        var outcome=cancelled&&state!=RenderJobStatus.COMPLETING?RenderJobStatus.CANCELLED:RenderJobStatus.FAILED;
        lifecycle.transition(jobId,project,state,outcome,cancelled?"GRAPH_CANCELLED":"GRAPH_EXECUTION_FAILED");
        executions.failure(jobId,cancelled?"GRAPH_CANCELLED":"GRAPH_EXECUTION_FAILED");
        if(outcome==RenderJobStatus.FAILED)events.publishEvent(new RenderJobFailedEvent(jobId,project,RenderFailureReason.EXECUTION_FAILED,Instant.now(),RenderJobRepository.initiatorFrom(job),outcome));
    }
    @Override public Optional<Status> find(String tenant,String project,String job) {
        TenantGuard.assertSameTenant(tenant);
        return executions.find(tenant,project,job).map(row->new Status(GraphRenderExecutionRepository.execution(row),row.get("status",String.class),row.get("final_artifact_id",String.class),row.get("failure_code",String.class),row.get("expected_artifact_id")==null?null:new Output(row.get("final_task_id",String.class),row.get("task_attempt_id",String.class),row.get("task_generation",Long.class),row.get("expected_artifact_id",String.class))));
    }
}
