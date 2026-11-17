package com.example.platform.operation.batch;

import com.example.platform.shared.digest.CanonicalCommandFingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Deterministic batch identity helpers.
 *
 * <p>{@code batchDigest} covers the target, the exact base, the ordered step
 * identities and the final candidate hash. It is plan/provenance identity and
 * NEVER a revision id. The idempotency fingerprint additionally binds the caller,
 * the expected head and the durable batch id, so reordering steps changes it.
 */
@org.springframework.modulith.NamedInterface("batch")
public final class OperationBatchDigest {

    public static final String DIGEST_VERSION = "OPERATION_BATCH_DIGEST_V1";
    public static final String FINGERPRINT_DOMAIN = "OPERATION_BATCH";

    private OperationBatchDigest() {
    }

    public static String compute(OperationBatchRequest request, List<BatchStepDescriptor> steps,
                                 String finalCandidateHash) {
        MessageDigest digest = sha256();
        update(digest, DIGEST_VERSION);
        update(digest, request.target().aggregateKind());
        update(digest, request.target().tenantId());
        update(digest, request.target().projectId());
        update(digest, request.target().aggregateId());
        update(digest, request.target().refId());
        update(digest, request.baseRevisionId());
        update(digest, request.baseContentHash());
        update(digest, Integer.toString(steps.size()));
        for (BatchStepDescriptor step : steps) {
            update(digest, Integer.toString(step.index()));
            update(digest, step.definitionId());
            update(digest, step.version());
            update(digest, step.parameterDigest());
            update(digest, step.targetIdentity());
            update(digest, step.stepPlanDigest());
            update(digest, step.candidateHash());
        }
        update(digest, finalCandidateHash);
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    public static String fingerprint(OperationBatchPlan<?> plan, OperationBatchContext context) {
        StringBuilder ordered = new StringBuilder();
        for (BatchStepDescriptor step : plan.steps()) {
            if (!ordered.isEmpty()) {
                ordered.append('\n');
            }
            ordered.append(step.definitionId()).append('|').append(step.version()).append('|')
                    .append(step.parameterDigest()).append('|').append(step.targetIdentity());
        }
        return CanonicalCommandFingerprint.builder(FINGERPRINT_DOMAIN)
                .required("tenantId", plan.target().tenantId())
                .required("projectId", plan.target().projectId())
                .required("targetRefId", plan.target().refId())
                .required("expectedHeadRevisionId", plan.baseRevisionId())
                .required("batchId", plan.batchId())
                .required("batchDigest", plan.batchDigest())
                .required("principalRef", context.principalRef())
                .required("orderedSteps", ordered.toString())
                .sha256Hex();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
