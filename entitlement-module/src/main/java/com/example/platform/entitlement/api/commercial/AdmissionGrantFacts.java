package com.example.platform.entitlement.api.commercial;

import java.time.Instant;
import com.example.platform.shared.commercial.PrincipalRef;

/** Immutable facts selected by Entitlement during the locked final admission decision. */
public record AdmissionGrantFacts(String grantId, PrincipalRef principal, String bundleCode,
        long version, Instant effectiveAt, Instant expiresAt) {}
