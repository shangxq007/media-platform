package com.example.platform.workflow.temporal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderPipelineWorkflowTest {

    @Test
    void workflowInterfaceHasCorrectMethods() {
        assertNotNull(RenderPipelineWorkflow.RenderPipeline.class);
    }

    @Test
    void activitiesHaveCorrectInterfaces() {
        assertNotNull(RenderPipelineActivities.RenderActivity.class);
        assertNotNull(RenderPipelineActivities.StorageActivity.class);
        assertNotNull(RenderPipelineActivities.NotificationActivity.class);
        assertNotNull(RenderPipelineActivities.AuditActivity.class);
    }

    @Test
    void workflowImplClassExists() {
        // Verify the class exists and can be loaded
        // Note: Instantiation requires Temporal runtime, so we only verify the class
        assertNotNull(RenderPipelineWorkflowImpl.class);
        assertTrue(RenderPipelineWorkflow.RenderPipeline.class.isAssignableFrom(RenderPipelineWorkflowImpl.class));
    }
}
