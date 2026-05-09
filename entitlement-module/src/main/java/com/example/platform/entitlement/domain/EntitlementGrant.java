package com.example.platform.entitlement.domain;

import java.time.Instant;

public record EntitlementGrant(String subjectId, String featureBundleCode, String quotaProfileCode, Instant effectiveUntil) {}
