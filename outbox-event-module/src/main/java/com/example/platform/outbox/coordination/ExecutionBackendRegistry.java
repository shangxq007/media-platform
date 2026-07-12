package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.TaskCapability;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for execution backends — resolves backends by TaskCapability.
 */
@Component
public class ExecutionBackendRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExecutionBackendRegistry.class);
    private final Map<TaskCapability, ExecutionBackend> backends = new ConcurrentHashMap<>();

    public ExecutionBackendRegistry(List<ExecutionBackend> allBackends) {
        for (ExecutionBackend backend : allBackends) {
            for (TaskCapability cap : TaskCapability.values()) {
                if (backend.supports(cap)) {
                    backends.put(cap, backend);
                    log.info("Registered execution backend: {} supports {}", backend.backendId(), cap);
                }
            }
        }
    }

    public Optional<ExecutionBackend> resolve(TaskCapability capability) {
        return Optional.ofNullable(backends.get(capability));
    }

    public int size() {
        return backends.size();
    }
}
