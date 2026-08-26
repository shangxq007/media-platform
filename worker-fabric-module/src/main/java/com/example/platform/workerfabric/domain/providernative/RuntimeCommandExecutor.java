package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.io.IOException;
import java.util.List;

/** Executes already-adapted runtime commands without acquiring lifecycle or output authority. */
@FunctionalInterface
public interface RuntimeCommandExecutor {
    ProviderExecutionOutput execute(
            RuntimeExecutionBundle executionBundle,
            List<MaterializedExecutionInput> runtimeLocalInputs) throws IOException;
}
