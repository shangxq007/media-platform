package com.example.platform.thumbnail;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import io.temporal.failure.CanceledFailure;

@WorkflowImpl(taskQueues = "media-platform-tasks")
public class ThumbnailWorkflowImpl implements ThumbnailWorkflow {
    private final ThumbnailActivities activities = Workflow.newActivityStub(ThumbnailActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(5)).setHeartbeatTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2).setDoNotRetry(IllegalArgumentException.class.getName()).build()).build());
    private String state="RUNNING"; private boolean cancelled;
    @Override public String run(String taskId,String tenantId,String projectId){
        Workflow.getVersion("MEDIA-THUMBNAIL-WF-V1", Workflow.DEFAULT_VERSION, 1);
        if(cancelled){state="CANCELLED";throw new CanceledFailure("thumbnail cancelled");}
        try { String artifact=activities.extractAndCommit(taskId,tenantId,projectId); if(cancelled || artifact == null){state="CANCELLED";throw new CanceledFailure("thumbnail cancelled");} state="COMPLETED"; return artifact; }
        catch(CanceledFailure e){state="CANCELLED";throw e;} catch(RuntimeException e){state="FAILED";throw e;}
    }
    @Override public void cancel(String reason){cancelled=true;state="CANCELLED";}
    @Override public String status(){return state;}
}
