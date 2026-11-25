package com.example.platform.runtime.mediatask;

import com.example.platform.execution.admission.*;
import com.example.platform.execution.binding.*;
import com.example.platform.providerplugin.*;
import com.example.platform.workerfabric.domain.*;
import com.example.platform.workerfabric.domain.providernative.*;
import com.example.platform.workerfabric.reuse.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.workflow.temporal.mediatask.CapabilityExecutionActivities;
import io.temporal.activity.Activity;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import java.time.Instant;
import java.util.*;

/** Worker adapter to the existing #22/native runtime; all durable state writes use owner ports. */
@Component
@ConditionalOnExpression("'${platform.runtime.role:}' == 'WORKER' or '${platform.runtime.role:}' == 'COMBINED'")
@ActivityImpl(taskQueues="media-platform-tasks")
public class CapabilityExecutionActivity implements CapabilityExecutionActivities {
    private final PlatformExecutionAdmissionPort.ArtifactCapabilities executions;
    private final CapabilityExecutionCompletionPort completion;
    private final AtomicAssignmentGrantBoundary grants;
    private final MediaTaskGrantMinter minter;
    private final ProviderPluginCatalog catalog;
    private final RuntimeClosedLoopOrchestrator runtime;
    private final MediaTaskPublicationSettings publication;
    private final ExecutionLifecycleBoundary lifecycle;
    public CapabilityExecutionActivity(PlatformExecutionAdmissionPort.ArtifactCapabilities executions,
            CapabilityExecutionCompletionPort completion,AtomicAssignmentGrantBoundary grants,MediaTaskGrantMinter minter,
            ProviderPluginCatalog catalog,RuntimeClosedLoopOrchestrator runtime,MediaTaskPublicationSettings publication,
            ExecutionLifecycleBoundary lifecycle) {
        this.executions=executions;this.completion=completion;this.grants=grants;this.minter=minter;this.catalog=catalog;
        this.runtime=runtime;this.publication=publication;this.lifecycle=lifecycle;
    }
    @Override public void executeCapability(String tenant,String project,String execution,long generation) {
        inTenant(tenant,()->execute(tenant,project,execution,generation));
    }
    private void execute(String tenant,String project,String execution,long generation) {
        var accepted=executions.begin(tenant,project,execution,generation);
        if(accepted.state()==PlatformExecutionState.COMPLETED)return;
        var inputs=accepted.plan().inputs();
        var graph=new ProviderBindingEntryService().bind(inputs.physicalPlan(),inputs.candidates(),inputs.transitionDeclarations()).executableTaskGraph();
        if(!graph.digest().equals(inputs.expectedExecutableTaskGraphDigest()) || graph.tasks().size()!=1) {
            throw new IllegalArgumentException("execution graph identity or cardinality mismatch");
        }
        var task=graph.tasks().getFirst();
        var contribution=catalog.find(task.providerBindingPin()).orElseThrow(()->new IllegalArgumentException("bound provider unavailable"));
        var pinned=inputs.candidates().stream().filter(c->c.bindingPin().equals(task.providerBindingPin())).findFirst().orElseThrow();
        ProviderCandidateProjection.requireMatching(contribution, pinned);
        var grant=grants.findCurrentGrant(task.id()).orElseGet(()-> {
            // Each actual Activity delivery is a distinct RequestWork, not a reused terminal resolution.
            String dispatch=execution+":epoch:"+generation+":delivery:"+Activity.getExecutionContext().getInfo().getAttempt();
            minter.mint(dispatch,task.id(),graph,contribution);
            return grants.findCurrentGrant(task.id()).orElseThrow(()->new IllegalStateException("native admission did not grant execution"));
        });
        minter.requireLocalGrant(grant);
        var context=TaskOutputPublicationContext.forExecution(tenant,project,execution,
                new StorageProviderId(publication.storageProvider()),publication.region(),ArtifactMediaType.IMAGE,
                ArtifactKind.DERIVED_MEDIA,publication.clock().instant());
        var target=TaskOutputPublicationPlanner.plan(task,context,grant.attempt().id().value(),grant.attempt().ownershipGeneration().value());
        var base=TaskRuntimeExecutionFactory.build(task,grant,target.durableOutputTarget(),target.artifactCommitMetadata());
        boolean cover="media.cover-image".equals(accepted.plan().request().capabilityId());
        var intent=new ArtifactDerivation(accepted.plan().request().source().artifactId(),
                cover?ProvenanceRelationType.COVER_OF:ProvenanceRelationType.THUMBNAIL_OF,
                cover?com.example.platform.contract.media.CoverImageContracts.OPERATION_ID:com.example.platform.contract.media.ThumbnailContracts.OPERATION_ID,1,ContentDigest.sha256(accepted.plan().requestHash()));
        var nativeExecution=new TaskRuntimeExecution(base.runtimeContext(),base.completionEvidence(),base.durableOutputTarget(),base.artifactCommitMetadata(),List.of(intent));
        var physicallyFinished=new java.util.concurrent.atomic.AtomicBoolean();
        var cancellation=new com.example.platform.sandbox.SandboxCancellation() {
            long nextCheck;boolean stopped;
            @Override public boolean isCancellationRequested() {
                if(stopped || Thread.currentThread().isInterrupted())return true;
                long now=System.nanoTime();if(now<nextCheck)return false;nextCheck=now+100_000_000L;
                try {
                    Activity.getExecutionContext().heartbeat(Map.of("executionId",execution,"generation",generation));
                    var current=executions.find(tenant,project,execution);
                    stopped=current.isEmpty() || current.orElseThrow().generation()!=generation
                            || current.orElseThrow().state()!=PlatformExecutionState.RUNNING;
                } catch(io.temporal.client.ActivityCompletionException cancelled) {stopped=true;}
                return stopped;
            }
        };
        try {
            runtime.execute(new RuntimeClosedLoopRequest(tenant,graph,Set.of(task.id()),
                    Map.of(task.id(),com.example.platform.execution.taskgraph.Cacheability.NOT_CACHEABLE),Map.of(task.id(),nativeExecution)),
                    (id,details,action)-> {
                        physicallyFinished.set(true);
                        return completion.publish(tenant,project,execution,generation,id,details,action);
                    },cancellation);
            physicallyFinished.set(true);
            release(grant,true);
        } catch(CapabilityExecutionCompletionPort.AlreadyCompleted duplicate) {
            release(grant,true);
        } catch(Exception failure) {
            boolean typedUnknown=failure instanceof ProviderNativeExecutionFailure nativeFailure
                    && (nativeFailure.code()==ProviderNativeFailureCode.RUNTIME_EXECUTION_UNKNOWN
                        || nativeFailure.code()==ProviderNativeFailureCode.PROCESS_CLEANUP_FAILED);
            boolean knownExit=physicallyFinished.get() || failure instanceof ProviderNativeExecutionFailure && !typedUnknown;
            boolean uncertain=!knownExit;
            release(grant,knownExit);
            if(uncertain)completion.fail(tenant,project,execution,generation,"EXECUTION_RECONCILIATION_REQUIRED",true);
            if(failure instanceof RuntimeException runtimeFailure)throw runtimeFailure;
            throw new IllegalStateException("capability execution failed",failure);
        }
    }
    private void release(AssignmentGrant grant,boolean physicalExitConfirmed) {
        var fence=ExecutionLifecycleBoundary.LeaseOwnershipFence.from(grant.lease());
        lifecycle.cancelAttempt(new ExecutionLifecycleBoundary.AttemptCancellation(fence,Instant.now()));
        if(physicalExitConfirmed)lifecycle.confirmPhysicalRelease(new ExecutionLifecycleBoundary.PhysicalReleaseConfirmation(
                "capability-release:"+grant.attempt().id().value(),fence,grant.assignment().reservationIds(),Instant.now()));
    }
    @Override public void failCapability(String tenant,String project,String execution,long generation,boolean cancelled,String code) {
        inTenant(tenant,()-> {
            var current=executions.find(tenant,project,execution);
            if(current.isEmpty() || current.orElseThrow().generation()!=generation)return;
            if(cancelled)executions.cancel(tenant,project,execution,generation);
            else completion.fail(tenant,project,execution,generation,code,false);
        });
    }
    private static void inTenant(String tenant,Runnable operation) {
        String previous=TenantContext.get();
        if(previous!=null&&!previous.equals(tenant))throw new IllegalArgumentException("worker tenant conflict");
        TenantContext.set(tenant);
        try {operation.run();}finally{if(previous==null)TenantContext.clear();else TenantContext.set(previous);}
    }
}
