package com.example.platform.outbox.app;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes outbox event types to Java event classes for deserialization.
 * Replaces the hardcoded switch in OutboxEventDispatcher.
 *
 * <p>New event types are registered here instead of modifying the dispatcher.
 * Unknown event types return empty, triggering proper error handling.</p>
 */
@Component
public class OutboxEventRouter {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRouter.class);
    private final Map<String, Class<?>> routes = new ConcurrentHashMap<>();

    public void register(String eventType, Class<?> eventClass) {
        if (routes.containsKey(eventType)) {
            log.warn("Re-registering event type '{}' from {} to {}",
                    eventType, routes.get(eventType).getSimpleName(), eventClass.getSimpleName());
        }
        routes.put(eventType, eventClass);
        log.debug("Registered outbox event route: {} → {}", eventType, eventClass.getSimpleName());
    }

    public boolean isKnown(String eventType) {
        return routes.containsKey(eventType);
    }

    public Class<?> resolve(String eventType) {
        return routes.get(eventType);
    }

    public int size() {
        return routes.size();
    }
}
