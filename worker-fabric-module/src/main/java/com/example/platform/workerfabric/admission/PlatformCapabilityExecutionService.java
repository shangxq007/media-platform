package com.example.platform.workerfabric.admission;

import com.example.platform.execution.admission.*;
import com.example.platform.execution.admission.PlatformExecutionAdmissionPort.CapabilityExecution;
import com.example.platform.execution.binding.*;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.shared.commercial.*;
import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.workerfabric.domain.CompletedTaskOutputQuery;
import com.example.platform.workerfabric.reuse.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Execution-owner adapter over the existing platform admission/result lifecycle and native completion. */
@Service
public class PlatformCapabilityExecutionService implements PlatformExecutionAdmissionPort.ArtifactCapabilities, com.example.platform.workerfabric.domain.CapabilityExecutionCompletionPort {
    private static final String SOURCE = "artifact-capability";
    private final DSLContext db;
    private final ObjectMapper json = new ObjectMapper();
    private final OutboxEventService outbox;
    private final QuotaConsumptionPort quota;
    private final CompletedTaskOutputQuery completed;
    private final ArtifactApplicationQuery artifacts;

    public PlatformCapabilityExecutionService(DSLContext db,OutboxEventService outbox,
            QuotaConsumptionPort quota,CompletedTaskOutputQuery completed,ArtifactApplicationQuery artifacts) {
        this.db=db;this.outbox=outbox;this.quota=quota;this.completed=completed;this.artifacts=artifacts;
    }
    @Override @Transactional
    public CapabilityExecution admit(ArtifactCapabilityExecutionPlan plan) {
        TenantGuard.assertSameTenant(plan.tenantId());
        // Canonical decode re-runs #22 and checks the graph digest before any durable admission.
        byte[] encoded=BoundGraphInputsCanonicalCodec.encode(plan.inputs());
        BoundGraphInputsCanonicalCodec.decode(encoded);
        var facts=json.createObjectNode().put("format","artifact-capability-v1")
                .put("graph",Base64.getEncoder().encodeToString(encoded)).put("userKey",plan.idempotencyKey());
        int inserted=db.execute("""
                insert into execution.platform_execution_admission(execution_id,tenant_id,workspace_id,actor_id,project_id,
                    source_domain,source_id,source_revision,plan_id,idempotency_key,request_hash,plan_fingerprint,
                    plan_facts,ownership_generation,state,quota_units,quota_unit,created_at,updated_at)
                values(?,?,?,?,?,?,?,1,?,?,?,?,cast(? as jsonb),1,'ADMITTED',?,'operation',clock_timestamp(),clock_timestamp())
                on conflict do nothing
                """,plan.executionId(),plan.tenantId(),plan.workspaceId(),plan.actorId(),plan.projectId(),SOURCE,
                plan.request().source().artifactId().value(),plan.inputs().physicalPlan().planId().value(),
                plan.scopedIdempotencyKey(),plan.requestHash(),plan.inputs().physicalPlan().digest().sha256Hex(),facts.toString(),
                "media.thumbnail".equals(plan.request().capabilityId())?BigDecimal.ONE:BigDecimal.ZERO);
        var row=locked(plan.tenantId(),plan.projectId(),plan.executionId());
        if(!plan.requestHash().equals(row.get("request_hash",String.class)))throw new com.example.platform.shared.web.PlatformException(com.example.platform.shared.web.CommonErrorCode.CONFLICT,"idempotency key conflicts with accepted capability parameters");
        if(inserted==1)enqueue(row);
        return view(row,inserted==1);
    }
    @Override
    public Optional<CapabilityExecution> find(String tenant,String project,String execution) {
        TenantGuard.assertSameTenant(tenant);
        return db.fetchOptional("select * from execution.platform_execution_admission where tenant_id=? and project_id=? and execution_id=? and source_domain=?",
                tenant,project,execution,SOURCE).map(row->view(row,false));
    }
    @Override @Transactional
    public CapabilityExecution begin(String tenant,String project,String execution,long generation) {
        var row=locked(tenant,project,execution);requireGeneration(row,generation);
        var state=state(row);
        if(state==PlatformExecutionState.ADMITTED || state==PlatformExecutionState.RETRYING)transition(row,PlatformExecutionState.RUNNING);
        else if(state!=PlatformExecutionState.RUNNING && state!=PlatformExecutionState.COMPLETED)throw new IllegalStateException("execution is not runnable");
        return view(locked(tenant,project,execution),false);
    }
    @Override @Transactional
    public CapabilityExecution cancel(String tenant,String project,String execution,long generation) {
        var row=locked(tenant,project,execution);requireGeneration(row,generation);
        if(state(row).canTransitionTo(PlatformExecutionState.CANCELLED))transition(row,PlatformExecutionState.CANCELLED);
        return view(locked(tenant,project,execution),false);
    }
    @Override @Transactional
    public CapabilityExecution retry(String tenant,String project,String execution,long generation) {
        var row=locked(tenant,project,execution);requireGeneration(row,generation);
        if(row.get("retry_blocked",Boolean.class))throw new com.example.platform.shared.web.PlatformException(com.example.platform.shared.web.CommonErrorCode.CONFLICT,"execution requires physical reconciliation before retry");
        if(state(row)!=PlatformExecutionState.FAILED)throw new com.example.platform.shared.web.PlatformException(com.example.platform.shared.web.CommonErrorCode.CONFLICT,"only a failed execution can be retried");
        transition(row,PlatformExecutionState.RETRYING);
        db.execute("update execution.platform_execution_admission set ownership_generation=ownership_generation+1,failure_code=null where execution_id=?",execution);
        row=locked(tenant,project,execution);enqueue(row);
        return view(row,false);
    }
    @Transactional
    public void fail(String tenant,String project,String execution,long generation,String code,boolean uncertain) {
        if(code==null||!code.matches("[A-Z0-9_]{1,64}"))throw new IllegalArgumentException("typed execution failure required");
        var row=locked(tenant,project,execution);
        if(row.get("ownership_generation",Long.class)!=generation)return;
        if(state(row)==PlatformExecutionState.RUNNING || state(row)==PlatformExecutionState.ADMITTED || state(row)==PlatformExecutionState.RETRYING) {
            transition(row,PlatformExecutionState.FAILED);
            db.execute("update execution.platform_execution_admission set failure_code=?,retry_blocked=? where execution_id=?",code,uncertain,execution);
        }
    }
    /** A native output is committed only while holding the logical execution's cancellation fence. */
    @Transactional(rollbackFor=Exception.class)
    public RuntimeClosedLoopTaskResult publish(String tenant,String project,String execution,long generation,
            ExecutableTaskId task,TaskRuntimeExecution nativeExecution,RuntimePublicationTransaction.CommitAction action) throws IOException {
        var row=locked(tenant,project,execution);requireGeneration(row,generation);
        if(state(row)==PlatformExecutionState.COMPLETED)throw new AlreadyCompleted();
        if(state(row)!=PlatformExecutionState.RUNNING)throw new java.util.concurrent.CancellationException("execution no longer owns publication");
        var accepted=view(row,false);
        var graph=new ProviderBindingEntryService().bind(accepted.plan().inputs().physicalPlan(),accepted.plan().inputs().candidates(),
                accepted.plan().inputs().transitionDeclarations()).executableTaskGraph();
        if(graph.tasks().size()!=1 || !graph.tasks().getFirst().id().equals(task)
                || !tenant.equals(nativeExecution.artifactCommitMetadata().tenantId())
                || !project.equals(nativeExecution.artifactCommitMetadata().projectId())
                || nativeExecution.artifactCommitMetadata().renderJobId()!=null) {
            throw new IllegalArgumentException("publication scope or task identity mismatch");
        }
        RuntimeClosedLoopTaskResult result=action.commit();
        var commit=result.durableArtifactCommit().artifactCommitResult();
        var artifact=commit.artifact();
        var proof=completed.find(task.sha256Hex()).orElseThrow(()->new IllegalStateException("native fenced completion is absent"));
        if(!proof.artifactId().equals(artifact.artifactId().value())
                || !proof.attemptId().equals(nativeExecution.runtimeContext().platformExecutionAttemptId().value())
                || proof.generation()!=nativeExecution.runtimeContext().platformOwnershipGeneration().value()
                || artifact.state()!=ArtifactState.AVAILABLE || artifact.mediaType()!=ArtifactMediaType.IMAGE
                || artifact.artifactKind()!=ArtifactKind.DERIVED_MEDIA
                || artifacts.findProjectArtifact(new ArtifactProjectScope(tenant,project),artifact.artifactId()).isEmpty()) {
            throw new IllegalStateException("committed Artifact differs from current native execution evidence");
        }
        var units=row.get("quota_units",BigDecimal.class);
        if(units.signum()>0) {
            Instant now=Instant.now();var month=YearMonth.from(row.get("created_at",OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC));
            var decision=quota.consume(new QuotaConsumptionRequest(PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),
                    accepted.plan().request().capabilityId(),units,month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                    month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                    "execution:"+execution+":completion","execution:"+execution,"capability output accepted",now));
            if(decision==null||!decision.allowed())throw new IllegalStateException("execution quota settlement rejected");
        }
        db.execute("""
                insert into execution.platform_execution_result(execution_id,attempt_id,ownership_generation,native_task_id,native_generation,
                    result_id,storage_receipt,artifact_id,output_digest,output_length,materialization_state,reconciliation_state,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?,?,'ARTIFACT_COMMITTED','NONE',clock_timestamp(),clock_timestamp())
                """,execution,proof.attemptId(),generation,task.sha256Hex(),proof.generation(),
                "execution-result:"+execution+":"+generation,result.durableArtifactCommit().storagePublication().objectId().value(),
                artifact.artifactId().value(),artifact.contentDigest().canonicalValue(),artifact.byteLength());
        transition(row,PlatformExecutionState.COMPLETED);
        db.execute("update execution.platform_execution_admission set quota_charged=true,quota_claimed=true,quota_charged_units=quota_units where execution_id=?",execution);
        return result;
    }
    /** Expected control-flow for a duplicate activity; it never executes the publication callback. */


    private Record locked(String tenant,String project,String execution) {
        TenantGuard.assertSameTenant(tenant);
        return db.fetchOptional("select * from execution.platform_execution_admission where tenant_id=? and project_id=? and execution_id=? and source_domain=? for update",
                tenant,project,execution,SOURCE).orElseThrow(()->new IllegalArgumentException("execution not found"));
    }
    private static PlatformExecutionState state(Record row){return PlatformExecutionState.valueOf(row.get("state",String.class));}
    private static void requireGeneration(Record row,long generation){if(row.get("ownership_generation",Long.class)!=generation)throw new com.example.platform.shared.web.PlatformException(com.example.platform.shared.web.CommonErrorCode.CONFLICT,"stale execution generation");}
    private void transition(Record row,PlatformExecutionState next) {
        if(!state(row).canTransitionTo(next))throw new IllegalStateException("invalid platform execution transition");
        if(db.execute("update execution.platform_execution_admission set state=?,updated_at=clock_timestamp() where execution_id=? and ownership_generation=? and state=?",
                next.name(),row.get("execution_id"),row.get("ownership_generation"),row.get("state"))!=1)throw new IllegalStateException("execution changed during transition");
    }
    private void enqueue(Record row) {
        var event=new CapabilityExecutionRequested(row.get("execution_id",String.class),row.get("tenant_id",String.class),
                row.get("project_id",String.class),row.get("ownership_generation",Long.class));
        outbox.append(ExecutionOutboxEvents.CAPABILITY_REQUESTED.append(event.tenantId(),event,"execution:"+event.executionId()+":generation:"+event.generation()));
    }
    private CapabilityExecution view(Record row,boolean created) {
        try {
            var facts=json.readTree(row.get("plan_facts",String.class));
            if(!"artifact-capability-v1".equals(facts.path("format").asText()))throw new IllegalStateException("unknown execution input format");
            var inputs=BoundGraphInputsCanonicalCodec.decode(Base64.getDecoder().decode(facts.path("graph").asText()));
            var plan=new ArtifactCapabilityExecutionPlan(row.get("tenant_id",String.class),row.get("project_id",String.class),
                    row.get("workspace_id",String.class),row.get("actor_id",String.class),facts.path("userKey").asText(),inputs);
            if(!plan.executionId().equals(row.get("execution_id",String.class)) || !plan.requestHash().equals(row.get("request_hash",String.class))) {
                throw new IllegalStateException("persisted execution input identity mismatch");
            }
            String artifact=null;
            if(state(row)==PlatformExecutionState.COMPLETED) {
                var result=db.fetchOne("select artifact_id from execution.platform_execution_result where execution_id=? and ownership_generation=? and materialization_state='ARTIFACT_COMMITTED'",
                        plan.executionId(),row.get("ownership_generation"));
                if(result==null)throw new IllegalStateException("completed execution lacks committed output");
                artifact=result.get("artifact_id",String.class);
            }
            return new CapabilityExecution(plan.executionId(),plan.tenantId(),plan.projectId(),row.get("ownership_generation",Long.class),
                    state(row),artifact,row.get("failure_code",String.class),row.get("retry_blocked",Boolean.class),created,plan);
        } catch(com.fasterxml.jackson.core.JsonProcessingException malformed) {throw new IllegalStateException("invalid persisted execution inputs",malformed);}
    }
}
