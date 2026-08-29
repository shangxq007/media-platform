package com.example.platform.shared.usage;

/** Runtime outcome retained as observation provenance, never as a billability decision. */
public enum RuntimeOutcome {
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
