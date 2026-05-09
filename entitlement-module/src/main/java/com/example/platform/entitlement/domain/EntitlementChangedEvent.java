package com.example.platform.entitlement.domain;

public record EntitlementChangedEvent(String subjectId, String reason, String sourceEventType) {}
