package com.example.platform.outbox.subscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Default route matcher supporting exact and wildcard suffix matching.
 */
public class DefaultEventRouteMatcher implements EventRouteMatcher {

    @Override
    public boolean matches(EventSubscription subscription, EventDescriptor event) {
        if (!subscription.enabled()) return false;
        if (!matchesEventType(subscription.eventTypePattern(), event.eventType())) return false;
        if (subscription.filter() != null) {
            EventFilter f = subscription.filter();
            if (f.tenantId() != null && !f.tenantId().equals(event.tenantId())) return false;
            if (f.projectId() != null && !f.projectId().equals(event.projectId())) return false;
            if (f.aggregateType() != null && !f.aggregateType().equals(event.aggregateType())) return false;
            if (f.aggregateId() != null && !f.aggregateId().equals(event.aggregateId())) return false;
        }
        return true;
    }

    @Override
    public List<EventRouteMatch> match(EventDescriptor event, List<EventSubscription> subscriptions) {
        List<EventRouteMatch> matches = new ArrayList<>();
        for (EventSubscription sub : subscriptions) {
            if (matches(sub, event)) {
                matches.add(new EventRouteMatch(
                    sub, sub.deliveryProviderType(), sub.destinationRef(),
                    sub.retryPolicyRef(), sub.priority(), "type:" + sub.eventTypePattern()
                ));
            }
        }
        matches.sort(Comparator.comparingInt(EventRouteMatch::priority));
        return matches;
    }

    private boolean matchesEventType(String pattern, String eventType) {
        if (pattern.equals(eventType)) return true;
        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return eventType.startsWith(prefix + ".");
        }
        return false;
    }
}
