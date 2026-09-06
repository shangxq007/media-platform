package com.example.platform.entitlement.api;

import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.EntitlementDecision;

/**
 * Published query boundary for an entitlement decision.
 *
 * <p>This contract reports entitlement only. Policy permission, quota, trust,
 * runtime availability, and authorization remain separate concerns.</p>
 */
public interface EntitlementDecisionQuery {

    EntitlementDecision evaluate(AccessCheckRequest request);
}
