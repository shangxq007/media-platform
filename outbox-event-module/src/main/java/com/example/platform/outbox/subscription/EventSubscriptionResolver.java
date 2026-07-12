package com.example.platform.outbox.subscription;

import java.util.List;

/**
 * Resolves matching subscriptions for a given event.
 */
public interface EventSubscriptionResolver {
    List<EventRouteMatch> resolveSubscriptions(EventDescriptor event);
}
