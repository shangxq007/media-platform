package com.example.platform.entitlement.domain;

public enum EntitlementDecisionReason {
    TIER,
    TENANT_OVERRIDE,
    WORKSPACE_OVERRIDE,
    WORKSPACE_POOL,
    WORKSPACE_MEMBER_GRANT,
    USER_GRANT,
    GROUP_GRANT,
    QUOTA_POLICY,
    EXPIRED,
    REVOKED,
    ABAC_RULE,
    DEFAULT_DENY
}
