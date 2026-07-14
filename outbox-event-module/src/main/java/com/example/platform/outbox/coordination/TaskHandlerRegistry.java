package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.TaskCapability;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for task handlers — auto-discovered via Spring.
 *
 * <p>All {@link TaskHandler} beans are injected and registered by {@link TaskCapability}.
 * The dispatcher resolves handlers by capability.</p>
 */
@Component
public class TaskHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(TaskHandlerRegistry.class);
    private final Map<TaskCapability, TaskHandler> handlers = new ConcurrentHashMap<>();
    private final List<TaskHandler> allHandlers;

    public TaskHandlerRegistry(List<TaskHandler> allHandlers) {
        this.allHandlers = allHandlers;
    }

    @PostConstruct
    public void init() {
        for (TaskHandler handler : allHandlers) {
            handlers.put(handler.capability(), handler);
            log.info("Registered task handler: {} → {}", handler.capability(), handler.getClass().getSimpleName());
        }
    }

    public TaskHandler resolve(TaskCapability capability) {
        TaskHandler handler = handlers.get(capability);
        if (handler == null) {
            log.warn("No task handler registered for capability: {}", capability);
        }
        return handler;
    }

    public int size() {
        return handlers.size();
    }
}
