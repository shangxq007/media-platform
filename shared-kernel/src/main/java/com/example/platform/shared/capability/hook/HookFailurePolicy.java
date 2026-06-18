package com.example.platform.shared.capability.hook;

/**
 * Policy for handling hook failures.
 */
public enum HookFailurePolicy {
    FAIL_CLOSED,
    FAIL_OPEN,
    RETRY_THEN_FAIL_CLOSED,
    RETRY_THEN_FAIL_OPEN,
    IGNORE
}
