package com.example.platform.billing.domain;

public record BillingEvent(String eventType, int eventVersion, String subjectId, String canonicalProductCode, String state) {}
