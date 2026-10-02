package com.example.platform.runtime.mediatask;

import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.Objects;

/**
 * In-process result of {@link MediaTaskActivity#prepareTask}.
 *
 * <p>Carries the re-derived executable task graph, the exact bound inputs it was derived from, and
 * the verified graph digest. It is intentionally <b>not</b> a Temporal payload type: only
 * {@code PreparedTaskRef} (identities + digests) crosses the workflow boundary.
 */
public record PreparedTask(
        ProviderBoundExecutableTaskGraph executableTaskGraph,
        BoundGraphInputs boundGraphInputs,
        ExecutableTaskGraphDigest executableTaskGraphDigest) {

    public PreparedTask {
        Objects.requireNonNull(executableTaskGraph, "executableTaskGraph");
        Objects.requireNonNull(boundGraphInputs, "boundGraphInputs");
        Objects.requireNonNull(executableTaskGraphDigest, "executableTaskGraphDigest");
    }
}
