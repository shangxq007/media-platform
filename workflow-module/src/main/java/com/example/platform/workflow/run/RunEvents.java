package com.example.platform.workflow.run;

import com.example.platform.outbox.api.event.*;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RunEvents implements OutboxEventCatalog {
    public record Start(String tenantId, String runId, String workflowPlanDigest) {}

    public record Control(
            String tenantId,
            String runId,
            String stepId,
            String releaseId,
            boolean approved,
            boolean cancel) {}

    public static final OutboxEventType<Start> START =
            new OutboxEventType<>(
                    "workflow.run.start",
                    1,
                    "WorkflowRun",
                    Start.class,
                    Start::runId,
                    Start::tenantId);
    public static final OutboxEventType<Control> CONTROL =
            new OutboxEventType<>(
                    "workflow.run.control",
                    1,
                    "WorkflowRun",
                    Control.class,
                    Control::runId,
                    Control::tenantId);

    @Override
    public List<OutboxEventType<?>> types() {
        return List.of(START, CONTROL);
    }
}
