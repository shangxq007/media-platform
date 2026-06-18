package com.example.platform.shared.capability.hook;

/**
 * Decision returned by a hook handler.
 */
public enum HookDecision {
    ALLOW,
    DENY,
    NOOP,
    DEFER
}
