package com.example.platform.outbox.subscription;

public enum SubscriberType {
    INTERNAL_LISTENER,
    WEBHOOK,
    EVENT_BUS,
    SEARCH_INDEX,
    NOTIFICATION,
    AUDIT_LOG,
    CONNECTOR,
    AGENT_CALLBACK,
    CUSTOM_INTEGRATION
}
