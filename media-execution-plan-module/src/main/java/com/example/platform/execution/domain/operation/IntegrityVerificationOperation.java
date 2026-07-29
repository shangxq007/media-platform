package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * Operation to verify integrity, quality, or conformance of output.
 *
 * <p>Corresponds to {@link ExecutionStepKind#VERIFY}.
 */
public record IntegrityVerificationOperation(
        String verificationType,
        Set<String> checks,
        String expectedDigestAlgorithm,
        String conformanceStandard
) implements Serializable, MediaOperation {

    public IntegrityVerificationOperation {
        Objects.requireNonNull(verificationType, "verificationType");
        if (verificationType.isBlank()) throw new IllegalArgumentException("verificationType must not be blank");
        Objects.requireNonNull(checks, "checks");
        checks = Set.copyOf(checks);
    }

    /**
     * Creates a digest verification operation.
     */
    public static IntegrityVerificationOperation digestCheck(String algorithm) {
        return new IntegrityVerificationOperation("digest", Set.of("hash"), algorithm, null);
    }

    /**
     * Creates a quality conformance verification.
     */
    public static IntegrityVerificationOperation conformance(String standard) {
        return new IntegrityVerificationOperation("conformance", Set.of("quality", "format"), null, standard);
    }

    /**
     * Creates a checksum verification.
     */
    public static IntegrityVerificationOperation checksum() {
        return new IntegrityVerificationOperation("checksum", Set.of("integrity"), "sha256", null);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.VERIFY;
    }

    @Override
    public String operationType() {
        return "VERIFY";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "verify{" +
                "type=" + verificationType +
                ",checks=" + checks.stream().sorted().toList() +
                ",algo=" + (expectedDigestAlgorithm != null ? expectedDigestAlgorithm : "") +
                ",standard=" + (conformanceStandard != null ? conformanceStandard : "") +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
