package com.example.platform.billing.domain;

import java.time.Instant;

public record BillingState(String subjectId, String contractState, Instant periodEndAt, String canonicalProductCode) {}
