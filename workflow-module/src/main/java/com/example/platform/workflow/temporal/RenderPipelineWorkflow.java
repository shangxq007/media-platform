package com.example.platform.workflow.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.SignalMethod;

public class RenderPipelineWorkflow {

    @WorkflowInterface
    public interface RenderPipeline {
        @WorkflowMethod
        String execute(String jobId, String timelineJson, String profile);

        @SignalMethod
        void cancel(String reason);

        @SignalMethod
        void retry(String nodeId);
    }
}
