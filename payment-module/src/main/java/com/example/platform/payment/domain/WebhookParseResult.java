package com.example.platform.payment.domain;

public record WebhookParseResult(String eventType, int eventVersion, String externalReference, boolean validSignature) {}
