package com.example.platform.entitlement.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record EntitlementGrantView(
        String grantId,
        PrincipalRef principal,
        String bundleCode,
        String quotaProfileCode,
        String sourceType,
        String sourceRef,
        String status,
        Instant effectiveAt,
        Instant expiresAt,
        long version,
        boolean workspaceGrant) {}
