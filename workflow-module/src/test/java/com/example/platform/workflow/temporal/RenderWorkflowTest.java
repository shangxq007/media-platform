package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class RenderWorkflowTest {

    @Test
    void workflowInterfaceHasWorkflowInterfaceAnnotation() {
        assertNotNull(RenderWorkflow.class.getAnnotation(WorkflowInterface.class),
                "RenderWorkflow must be annotated with @WorkflowInterface");
    }

    @Test
    void workflowMethodHasWorkflowMethodAnnotation() {
        Method method = assertDoesNotThrow(() -> RenderWorkflow.class.getMethod("run", String.class, String.class));
        assertNotNull(method.getAnnotation(WorkflowMethod.class),
                "run() must be annotated with @WorkflowMethod");
    }

    @Test
    void workflowMethodSignatureAcceptsRenderJobIdAndTenantId() {
        assertDoesNotThrow(() -> RenderWorkflow.class.getMethod("run", String.class, String.class));
    }

    @Test
    void workflowMethodReturnsString() {
        Method method = assertDoesNotThrow(() -> RenderWorkflow.class.getMethod("run", String.class, String.class));
        assertTrue(String.class.equals(method.getReturnType()),
                "run() must return String");
    }

    @Test
    void activitiesInterfaceHasActivityInterfaceAnnotation() {
        assertNotNull(RenderActivities.class.getAnnotation(ActivityInterface.class),
                "RenderActivities must be annotated with @ActivityInterface");
    }

    @Test
    void decideRenderPipelineHasActivityMethodAnnotation() {
        Method method = assertDoesNotThrow(() -> RenderActivities.class.getMethod("decideRenderPipeline", String.class, String.class));
        assertNotNull(method.getAnnotation(ActivityMethod.class),
                "decideRenderPipeline() must be annotated with @ActivityMethod");
    }

    @Test
    void taskQueueNameIsNotEmpty() {
        assertNotNull(RenderTaskQueue.NAME);
        assertTrue(RenderTaskQueue.NAME.length() > 0);
    }

    @Test
    void taskQueueNameContainsExpectedPrefix() {
        assertTrue(RenderTaskQueue.NAME.contains("media-platform"),
                "Task queue name should reference media-platform");
    }
}
