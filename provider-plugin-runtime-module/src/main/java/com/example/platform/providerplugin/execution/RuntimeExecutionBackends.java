package com.example.platform.providerplugin.execution;

import com.example.platform.sandbox.execution.*;
import com.example.platform.bmf.BmfExecutionBackend;

import com.example.platform.sandbox.execution.TaskCapability;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Registry for execution backends — resolves backends by TaskCapability.
 */
@org.springframework.modulith.NamedInterface("Execution")
public final class RuntimeExecutionBackends implements ExecutionBackendRegistry {

    private static final Logger log = LoggerFactory.getLogger(RuntimeExecutionBackends.class);
    private final Map<TaskCapability, ExecutionBackend> backends = new ConcurrentHashMap<>();

    public RuntimeExecutionBackends(List<ExecutionBackend> allBackends) {
        for (ExecutionBackend backend : allBackends) {
            for (TaskCapability cap : TaskCapability.values()) {
                if (backend.supports(cap)) {
                    if (backends.putIfAbsent(cap, backend) != null) {
                        throw new IllegalArgumentException("Duplicate execution binding for " + cap);
                    }
                    log.info("Registered execution backend: {} supports {}", backend.backendId(), cap);
                }
            }
        }
    }

    public static ExecutionBackendRegistry create() {
        return new RuntimeExecutionBackends(List.of(new LocalProcessExecutionBackend(), new BmfExecutionBackend()));
    }

    public Optional<ExecutionBackend> resolve(TaskCapability capability) {
        return Optional.ofNullable(backends.get(capability));
    }

    public int size() {
        return backends.size();
    }
}
