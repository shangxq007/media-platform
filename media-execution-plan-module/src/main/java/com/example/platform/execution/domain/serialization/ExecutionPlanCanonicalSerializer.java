package com.example.platform.execution.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for deterministic canonical serialization.
 *
 * <p>Produces stable byte representations independent of HashMap iteration, Locale,
 * Timezone, database row order, runtime state, or machine architecture.
 */
public final class ExecutionPlanCanonicalSerializer {

    private ExecutionPlanCanonicalSerializer() {
    }

    /**
     * Computes SHA-256 hex digest of a string.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes a deterministic digest for an execution plan.
     */
    public static String digestPlan(MediaExecutionPlan plan) {
        return sha256Hex(plan.canonicalForm());
    }

    /**
     * Computes a deterministic digest for a single step.
     */
    public static String digestStep(MediaExecutionStep step) {
        return sha256Hex(step.canonicalForm());
    }

    /**
     * Computes a deterministic digest for an input binding.
     */
    public static String digestInput(ExecutionInputBinding input) {
        return sha256Hex(input.canonicalForm());
    }

    /**
     * Computes a deterministic digest for an output declaration.
     */
    public static String digestOutput(ExecutionOutputDeclaration output) {
        return sha256Hex(output.canonicalForm());
    }
}
