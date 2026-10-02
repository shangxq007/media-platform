package com.example.platform.runtime.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.workflow.temporal.mediatask.MediaTaskActivities;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowResult;
import com.example.platform.workflow.temporal.mediatask.PreparedTaskRef;
import io.temporal.spring.boot.ActivityImpl;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * P2-5b-2b-2a: Temporal-facing activity adapter for the media task workflow.
 *
 * <p>The workflow's payload rule is "stable references only", so this adapter is the only place the
 * in-process {@code PreparedTask} is reduced to a {@link PreparedTaskRef} (identities + digests +
 * executable task ids). It delegates every side effect to {@link MediaTaskActivity} and adds no
 * logic of its own — the activity keeps its own public API unchanged.
 */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
@ActivityImpl(taskQueues = "media-platform-tasks")
public class MediaTaskActivitiesAdapter implements MediaTaskActivities {

    private final MediaTaskActivity activity;

    public MediaTaskActivitiesAdapter(MediaTaskActivity activity) {
        this.activity = Objects.requireNonNull(activity, "activity");
    }

    @Override
    public PreparedTaskRef prepareTask(BoundGraphReference reference, String tenantId) {
        PreparedTask prepared = activity.prepareTask(reference, tenantId);
        return new PreparedTaskRef(
                reference.tenantId(),
                reference.renderJobId(),
                reference.planRef(),
                reference.planDigest(),
                reference.expectedExecutableTaskGraphDigest(),
                prepared.executableTaskGraph().tasks().stream()
                        .map(ExecutableTask::id)
                        .map(taskId -> taskId.sha256Hex())
                        .toList());
    }

    @Override
    public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId) {
        return activity.executeTask(prepared, tenantId);
    }
}
