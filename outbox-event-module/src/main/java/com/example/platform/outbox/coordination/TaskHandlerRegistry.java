package com.example.platform.outbox.coordination;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registry for task handlers — auto-discovered via Spring.
 *
 * <p>All {@link TaskHandler} beans are injected and registered by {@link TaskCapability}.
 * The dispatcher resolves handlers by capability.
 *
 * <p>Single-authority invariant (ONE_TASK_CAPABILITY_ONE_HANDLER_AUTHORITY_V1):
 * duplicate registration for the same {@link TaskCapability} is a startup
 * configuration failure — never resolved by bean ordering, injection-list
 * order, @Order, last-write-wins, or first-write-wins. A capability must have
 * exactly one active handler authority.
 */
@Component
public class TaskHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(TaskHandlerRegistry.class);
    private final Map<TaskCapability, TaskHandler> handlers = new HashMap<>();
    private final List<TaskHandler> allHandlers;

    public TaskHandlerRegistry(List<TaskHandler> allHandlers) {
        this.allHandlers = allHandlers;
    }

    @PostConstruct
    public void init() {
        for (TaskHandler handler : allHandlers) {
            TaskCapability capability = handler.capability();
            TaskHandler existing = handlers.putIfAbsent(capability, handler);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate task handler for capability " + capability
                                + ": existing=" + existing.getClass().getName()
                                + " duplicate=" + handler.getClass().getName()
                                + ". One TaskCapability must have exactly one active TaskHandler authority.");
            }
            log.info("Registered task handler: {} → {}", capability, handler.getClass().getSimpleName());
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
