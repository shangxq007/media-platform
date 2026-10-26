package com.example.platform.workflow.temporal.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Worker activity contract over stable graph references and Render-owned terminal projection. */
@ActivityInterface
public interface MediaTaskActivities {

    /**
     * Loads the bound inputs, re-derives the executable task graph and verifies the digest.
     *
     * @return stable identity/digest summary of the prepared graph (never the graph itself)
     */
    @ActivityMethod
    PreparedTaskRef prepareTask(BoundGraphReference reference, String tenantId);

    /** Executes the whole graph with current native fencing and committed output identities. */
    @ActivityMethod
    MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId);

    @ActivityMethod
    void completeTask(PreparedTaskRef prepared, MediaTaskWorkflowResult result, String tenantId);

    @ActivityMethod
    void failTask(BoundGraphReference reference, String tenantId, boolean cancelled);
}
