package com.example.platform.workflow.plan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Bounded opaque correlation identity; nested positions cannot overflow PostgreSQL index keys. */
public final class WorkflowStepIdentity {
    private WorkflowStepIdentity() {}

    public static String at(String nodeId, String position) {
        try {
            return nodeId
                    + ":"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(position.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String root(String nodeId) {
        return at(nodeId, "root/" + nodeId);
    }

    public static String child(String parent, String nodeId) {
        return at(nodeId, parent + "/" + nodeId);
    }
}
