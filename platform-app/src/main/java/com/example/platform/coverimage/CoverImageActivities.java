package com.example.platform.coverimage;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Worker-side cover-image activity contract; executed only on media-platform-tasks. */
@ActivityInterface
public interface CoverImageActivities {

    @ActivityMethod
    String renderAndCommit(String taskId, String tenantId, String projectId);
}
