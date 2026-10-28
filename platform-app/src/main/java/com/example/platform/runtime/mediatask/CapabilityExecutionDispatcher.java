package com.example.platform.runtime.mediatask;

import com.example.platform.workerfabric.admission.CapabilityExecutionRequested;
import com.example.platform.execution.admission.PlatformExecutionAdmissionPort;
import com.example.platform.execution.admission.PlatformExecutionState;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import com.example.platform.workflow.temporal.mediatask.CapabilityExecutionWorkflow;
import io.temporal.client.*;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Registered in both API and Worker so an outbox command always has its real dispatch consumer. */
@Component
public class CapabilityExecutionDispatcher {
    private final ObjectProvider<WorkflowClient> clients;
    private final PlatformExecutionAdmissionPort.ArtifactCapabilities executions;
    public CapabilityExecutionDispatcher(ObjectProvider<WorkflowClient> clients,PlatformExecutionAdmissionPort.ArtifactCapabilities executions) {
        this.clients=clients;this.executions=executions;
    }
    public static String workflowId(String tenant,String execution,long generation) {
        return "capability:"+tenant+":"+execution+":"+generation;
    }
    @EventListener public void dispatch(CapabilityExecutionRequested command) {
        var current=executions.find(command.tenantId(),command.projectId(),command.executionId())
                .orElseThrow(()->new IllegalStateException("dispatch admission not found"));
        if(current.generation()!=command.generation() || current.state()==PlatformExecutionState.CANCELLED
                || current.state()==PlatformExecutionState.COMPLETED || current.state()==PlatformExecutionState.FAILED)return;
        var client=clients.getIfAvailable();
        if(client==null)throw new IllegalStateException("Temporal client unavailable for accepted execution dispatch");
        var workflow=client.newWorkflowStub(CapabilityExecutionWorkflow.class,WorkflowOptions.newBuilder()
                .setTaskQueue(RenderTaskQueue.NAME).setWorkflowId(workflowId(command.tenantId(),command.executionId(),command.generation()))
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE).build());
        try { WorkflowClient.start(workflow::run,command.tenantId(),command.projectId(),command.executionId(),command.generation()); }
        catch(WorkflowExecutionAlreadyStarted duplicate) { /* One workflow owns this exact generation. */ }
    }
}
