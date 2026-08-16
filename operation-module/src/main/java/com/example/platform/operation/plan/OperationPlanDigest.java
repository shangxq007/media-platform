package com.example.platform.operation.plan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (PT13/PT14/§17): deterministic
 * domain-separated OperationPlanDigest. Includes plan-format-version, base
 * revision/hash, definition/version, resolved target identities, parameter
 * digest, planned primary changes, secondary consequences, candidate hash.
 * EXCLUDES targetRef, expectedHead, principal, authorization, invocation
 * metadata. Distinct from ParameterDigest/TimelineContentHash/RevisionId.
 */
public final class OperationPlanDigest {

    private OperationPlanDigest() {
    }

    public static String compute(
            String baseRevisionId,
            String baseContentHash,
            String definitionId,
            String contractVersion,
            String parameterDigest,
            List<String> resolvedTargetIdentities,
            List<String> plannedChangeKeys,
            String candidateContentHash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(OperationPlan.FORMAT_VERSION.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(baseRevisionId.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(baseContentHash.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(definitionId.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(contractVersion.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(parameterDigest.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            for (String t : resolvedTargetIdentities) {
                md.update(t.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 1);
            }
            md.update((byte) 0);
            for (String c : plannedChangeKeys) {
                md.update(c.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 2);
            }
            md.update((byte) 0);
            md.update(candidateContentHash.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Deterministic key for one planned change (sorted by construction order — planner emits deterministic order). */
    public static String changeKey(PlannedChange c) {
        return switch (c) {
            case PlannedChange.ClipRemoved r -> "remove(" + r.clipId().value() + ")";
            case PlannedChange.ClipReplaced r -> "replace(" + r.clipId().value() + "," + r.newClip().getEndTime() + ")";
            case PlannedChange.RelationshipRemoved r -> "rel-remove(" + r.relationshipIdentity() + ")";
            case PlannedChange.RelationshipAdded r -> "rel-add(" + r.relationship().kind() + ")";
            case PlannedChange.GroupMembershipUpdated g -> "group(" + g.groupId().value() + ","
                    + g.remainingMembers().stream().map(m -> m.value()).sorted().toList() + ")";
            case PlannedChange.AudioMixReplaced a -> "audio(" + a.summary() + ")";
            case PlannedChange.TextElementAdded r -> "text-add(" + r.textElementId().value() + ")";
            case PlannedChange.TextElementRemoved r -> "text-remove(" + r.textElementId().value() + ")";
            case PlannedChange.TextElementReplaced r -> "text-replace(" + r.textElementId().value() + ")";
        };
    }

    public static List<String> changeKeys(List<PlannedChange> changes) {
        return changes.stream().map(OperationPlanDigest::changeKey).toList();
    }
}
