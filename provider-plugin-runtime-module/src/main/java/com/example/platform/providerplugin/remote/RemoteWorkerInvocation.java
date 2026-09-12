package com.example.platform.providerplugin.remote;

import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionBundle;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.util.List;
import java.util.Objects;

/** Transport envelope of existing runtime mechanics; never a task, assignment or lifecycle model. */
public record RemoteWorkerInvocation(RuntimeExecutionBundle bundle, List<MaterializedExecutionInput> inputs) {
    public RemoteWorkerInvocation {
        Objects.requireNonNull(bundle, "bundle");
        inputs = List.copyOf(inputs);
        if (bundle.commands().isEmpty() || bundle.commands().stream().map(c -> c.sequence()).distinct().count() != bundle.commands().size()) {
            throw new IllegalArgumentException("commands must be nonempty with unique sequences");
        }
        if (inputs.stream().map(MaterializedExecutionInput::inputId).distinct().count() != inputs.size()) {
            throw new IllegalArgumentException("duplicate materialized input identity");
        }
    }
}
