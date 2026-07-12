package com.example.platform.ingest.preflight.policy;

public enum PreflightPolicyDecision {
    ACCEPT, ACCEPT_WITH_WARNINGS, REJECT_CANDIDATE, REJECT, SKIP, ERROR_FAIL_OPEN
}
