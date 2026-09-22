package com.example.platform.thumbnail;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
@ActivityInterface
public interface ThumbnailActivities {
    @ActivityMethod String extractAndCommit(String taskId, String tenantId, String projectId);
}
