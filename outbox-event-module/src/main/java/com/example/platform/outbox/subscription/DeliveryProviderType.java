package com.example.platform.outbox.subscription;

public enum DeliveryProviderType {
    SPRING_EVENT,
    WEBHOOK,
    EVENTMESH,
    CAMEL,
    SEARCH,
    NOTIFICATION,
    AUDIT,
    CONNECTOR
}
