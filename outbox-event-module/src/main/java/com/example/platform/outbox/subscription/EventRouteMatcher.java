package com.example.platform.outbox.subscription;

import java.util.List;

/**
 * Matches events against subscriptions.
 */
public interface EventRouteMatcher {
    boolean matches(EventSubscription subscription, EventDescriptor event);
    List<EventRouteMatch> match(EventDescriptor event, List<EventSubscription> subscriptions);
}
