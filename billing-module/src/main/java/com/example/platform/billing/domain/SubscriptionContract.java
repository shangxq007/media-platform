package com.example.platform.billing.domain;

import java.time.Instant;

public record SubscriptionContract(String contractId, String subjectId, String canonicalProductCode, String lifecycleState, Instant periodStartAt, Instant periodEndAt) {}
