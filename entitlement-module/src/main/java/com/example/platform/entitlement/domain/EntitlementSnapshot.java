package com.example.platform.entitlement.domain;

import java.time.Instant;
import java.util.List;

public record EntitlementSnapshot(String subjectId, List<String> featureCodes, String quotaProfileCode, Instant expiresAt) {}
