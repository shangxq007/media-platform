package com.example.platform.runtime.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.workflow.temporal.mediatask.MediaTaskActivities;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowResult;
import com.example.platform.workflow.temporal.mediatask.PreparedTaskRef;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Temporal adapter coordinating public Render and Execution owner contracts; no domain SQL or Artifact repository access. */
@Component
@ConditionalOnExpression(
        "'${platform.runtime.role:}' == 'WORKER' or '${platform.runtime.role:}' == 'COMBINED'")
@ActivityImpl(taskQueues = "media-platform-tasks")
public class MediaTaskActivitiesAdapter implements MediaTaskActivities {

    private final MediaTaskActivity activity;
    private final com.example.platform.render.api.GraphRenderLifecycle lifecycle;
    private final com.example.platform.workerfabric.domain.CompletedTaskOutputQuery completed;
    private final com.example.platform.artifact.app.ArtifactApplicationQuery artifacts;

    public MediaTaskActivitiesAdapter(MediaTaskActivity activity,
            com.example.platform.render.api.GraphRenderLifecycle lifecycle,
            com.example.platform.workerfabric.domain.CompletedTaskOutputQuery completed,
            com.example.platform.artifact.app.ArtifactApplicationQuery artifacts) {
        this.activity = Objects.requireNonNull(activity, "activity");
        this.lifecycle=Objects.requireNonNull(lifecycle);this.completed=Objects.requireNonNull(completed);this.artifacts=Objects.requireNonNull(artifacts);
    }

    @Override
    public PreparedTaskRef prepareTask(BoundGraphReference reference, String tenantId) {
        PreparedTask prepared = activity.prepareTask(reference, tenantId);
        var finalTask=com.example.platform.execution.taskgraph.GraphFinalOutput.requireRenderMaster(prepared.executableTaskGraph());
        var execution=inTenant(tenantId,()->lifecycle.begin(tenantId,reference.renderJobId(),reference.planRef(),reference.planDigest(),reference.expectedExecutableTaskGraphDigest(),finalTask.id().sha256Hex()));
        return new PreparedTaskRef(
                reference.tenantId(),
                reference.renderJobId(),
                reference.planRef(),
                reference.planDigest(),
                reference.expectedExecutableTaskGraphDigest(),
                prepared.executableTaskGraph().tasks().stream()
                        .map(ExecutableTask::id)
                        .map(taskId -> taskId.sha256Hex())
                        .toList(),execution);
    }

    @Override
    public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId) {
        requirePrepared(prepared,tenantId);
        return inTenant(tenantId,()-> {
            var e=prepared.execution();
            var accepted=lifecycle.find(tenantId,e.projectId(),e.jobId()).filter(s->"COMPLETED".equals(s.status()) && e.equals(s.execution()));
            if(accepted.isPresent())return new MediaTaskWorkflowResult(tenantId,e.jobId(),e.planDigest(),e.graphDigest(),prepared.executableTaskIds(),MediaTaskWorkflowResult.STATUS_EXECUTED,Objects.requireNonNull(accepted.orElseThrow().boundOutput()));
            // Recover a lost Activity response only from every current-generation fenced success.
            var recovered=prepared.executableTaskIds().stream().map(completed::find).toList();
            var scope=new com.example.platform.artifact.app.ArtifactScope(tenantId,e.projectId(),e.jobId());
            boolean sameExecution=!recovered.isEmpty() && recovered.stream().allMatch(item->item.isPresent()
                    && artifacts.findArtifact(scope,new com.example.platform.shared.identity.ArtifactId(item.orElseThrow().artifactId())).isPresent());
            if(sameExecution) {
                var finalOutput=completed.find(e.finalTaskId()).orElseThrow();
                var o=new com.example.platform.render.api.GraphRenderLifecycle.Output(finalOutput.taskId(),finalOutput.attemptId(),finalOutput.generation(),finalOutput.artifactId());
                lifecycle.bindOutput(e,o);
                return new MediaTaskWorkflowResult(tenantId,e.jobId(),e.planDigest(),e.graphDigest(),prepared.executableTaskIds(),MediaTaskWorkflowResult.STATUS_EXECUTED,o);
            }
            return activity.executeTask(prepared,tenantId,o->lifecycle.bindOutput(e,o));
        });
    }

    @Override @org.springframework.transaction.annotation.Transactional
    public void completeTask(PreparedTaskRef prepared,MediaTaskWorkflowResult result,String tenantId) {
        requirePrepared(prepared,tenantId);
        if(result==null || !tenantId.equals(result.tenantId()) || !prepared.renderJobId().equals(result.renderJobId())
                || !prepared.executableTaskIds().equals(result.executableTaskIds()) || !prepared.planDigest().equals(result.planDigest()) || !prepared.expectedExecutableTaskGraphDigest().equals(result.expectedExecutableTaskGraphDigest())
                || !MediaTaskWorkflowResult.STATUS_EXECUTED.equals(result.status()))throw new IllegalArgumentException("graph result identity mismatch");
        var o=Objects.requireNonNull(result.finalOutput(),"committed final output required");
        var prior=inTenant(tenantId,()->lifecycle.find(tenantId,prepared.execution().projectId(),prepared.renderJobId()));
        if(prior.filter(s->"COMPLETED".equals(s.status())).isPresent()) {
            // The durable accepted fact remains idempotent after another job advances the native generation.
            inTenant(tenantId,()->lifecycle.complete(prepared.execution(),o));return;
        }
        var proof=completed.find(o.taskId()).orElseThrow(()->new IllegalStateException("fenced task success absent"));
        if(!proof.attemptId().equals(o.taskAttemptId())||proof.generation()!=o.taskGeneration()||!proof.artifactId().equals(o.artifactId()))throw new IllegalArgumentException("final output is not the current fenced success");
        inTenant(tenantId,()->lifecycle.complete(prepared.execution(),o));
    }
    @Override public void failTask(BoundGraphReference reference,String tenantId,boolean cancelled) {
        if(!tenantId.equals(reference.tenantId()))throw new IllegalArgumentException("failure tenant mismatch");
        inTenant(tenantId,()->{lifecycle.fail(tenantId,reference.renderJobId(),reference.planRef(),reference.planDigest(),reference.expectedExecutableTaskGraphDigest(),cancelled);return null;});
    }
    private static void requirePrepared(PreparedTaskRef p,String tenant) {
        if(p==null||p.execution()==null||!tenant.equals(p.tenantId())||!tenant.equals(p.execution().tenantId())||!p.renderJobId().equals(p.execution().jobId())||!p.planRef().equals(p.execution().planRef())||!p.executableTaskIds().contains(p.execution().finalTaskId())||!p.planDigest().equals(p.execution().planDigest())||!p.expectedExecutableTaskGraphDigest().equals(p.execution().graphDigest()))throw new IllegalArgumentException("bound Render execution required");
    }
    private static <T> T inTenant(String tenant,java.util.function.Supplier<T> action) {
        String old=com.example.platform.shared.web.TenantContext.get();
        if(old!=null&&!old.equals(tenant))throw new IllegalArgumentException("worker tenant context conflict");
        com.example.platform.shared.web.TenantContext.set(tenant);
        try{return action.get();}finally{if(old==null)com.example.platform.shared.web.TenantContext.clear();else com.example.platform.shared.web.TenantContext.set(old);}
    }
}
