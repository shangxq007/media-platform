package com.example.platform.render.domain.execution;

import java.util.List;
import java.util.Map;

/**
 * Common interface for all backend execution specifications.
 */
public interface BackendExecutionSpec {
    String executionSpecId();
    String backendId();
    String backendType();
    String producerId();
    List<String> inputProductIds();
    List<ExecutionInput> materializedInputs();
    List<ExecutionOutput> expectedOutputs();
    Map<String, String> executionHints();
}
