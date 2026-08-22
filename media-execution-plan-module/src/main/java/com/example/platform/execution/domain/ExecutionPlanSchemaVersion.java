package com.example.platform.execution.domain;

import java.util.Objects;

/**
 * ROADMAP #21 canonical plan schema version (frozen ledger REUSE_AS_CANONICAL).
 * Frozen version semantics for the plan structure; part of plan identity
 * metadata, excluded from semantic content digest (structural format version
 * of the PLAN is digest participant; this type is the canonical carrier).
 */
public record ExecutionPlanSchemaVersion(int major, int minor) {

    public ExecutionPlanSchemaVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("schema version must be non-negative");
        }
    }

    public String canonical() {
        return major + "." + minor;
    }
}
