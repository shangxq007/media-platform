package com.example.platform.timeline.revisioncommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * REVISION_COMMAND_MODEL_V1 (RC9/§17): deterministic domain-separated
 * RevisionCommandPlanDigest — SHA-256, distinct from OperationPlanDigest /
 * TimelineContentHash / RevisionId / ApplyCommandId. Excludes principal,
 * AuthorizationDecision, DB SQL, provider. Refs participate as command
 * semantic identity where frozen.
 */
public final class RevisionCommandPlanDigest {

    public static final String FORMAT_VERSION = "revision-command-plan-format-v1";

    private RevisionCommandPlanDigest() {
    }

    public static String createRef(String projectId, String refId, String sourceRevisionId) {
        return digest("create-ref", projectId, refId, sourceRevisionId);
    }

    public static String deleteRef(String projectId, String refId, String expectedHead) {
        return digest("delete-ref", projectId, refId, expectedHead);
    }

    public static String restore(String projectId, String historicalSource, String targetRefId,
                                 String expectedHead, String candidateHash) {
        return digest("restore", projectId, historicalSource, targetRefId, expectedHead, candidateHash);
    }

    public static String merge(String projectId, String sourceRevisionId, String targetRefId,
                               String targetOursRevisionId, String mergeBaseRevisionId,
                               String candidateHash, boolean conflict) {
        return digest("merge", projectId, sourceRevisionId, targetRefId, targetOursRevisionId,
                mergeBaseRevisionId, candidateHash, conflict ? "conflict" : "clean");
    }

    private static String digest(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(FORMAT_VERSION.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            for (String p : parts) {
                md.update(p.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 1);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
