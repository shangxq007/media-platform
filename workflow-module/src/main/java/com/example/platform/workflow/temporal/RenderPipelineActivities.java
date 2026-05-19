package com.example.platform.workflow.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

public class RenderPipelineActivities {

    @ActivityInterface
    public interface RenderActivity {
        @ActivityMethod
        String renderJob(String jobId, String timelineJson, String profile);
    }

    @ActivityInterface
    public interface StorageActivity {
        @ActivityMethod
        String storeArtifact(String jobId, String outputPath, String format);
    }

    @ActivityInterface
    public interface NotificationActivity {
        @ActivityMethod
        void notifyCompletion(String jobId, String status, String artifactUrl);
    }

    @ActivityInterface
    public interface AuditActivity {
        @ActivityMethod
        void recordAudit(String jobId, String action, String status, String details);
    }
}
