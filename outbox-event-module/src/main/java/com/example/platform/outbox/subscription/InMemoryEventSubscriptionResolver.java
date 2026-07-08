package com.example.platform.outbox.subscription;

import java.util.List;

/**
 * In-memory subscription resolver for tests and POC.
 * NOT a production config source.
 */
public class InMemoryEventSubscriptionResolver implements EventSubscriptionResolver {

    private final List<EventSubscription> subscriptions;
    private final EventRouteMatcher matcher;

    public InMemoryEventSubscriptionResolver(List<EventSubscription> subscriptions) {
        this(subscriptions, new DefaultEventRouteMatcher());
    }

    public InMemoryEventSubscriptionResolver(List<EventSubscription> subscriptions, EventRouteMatcher matcher) {
        this.subscriptions = List.copyOf(subscriptions);
        this.matcher = matcher;
    }

    @Override
    public List<EventRouteMatch> resolveSubscriptions(EventDescriptor event) {
        return matcher.match(event, subscriptions);
    }
}
