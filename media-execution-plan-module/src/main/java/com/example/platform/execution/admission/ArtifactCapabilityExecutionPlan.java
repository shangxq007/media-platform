package com.example.platform.execution.admission;

import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.planning.ArtifactCapabilityRequest;
import com.example.platform.execution.planning.CanonicalWriter;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Internal, immutable admission facts for an authorized Artifact-sourced request. */
public record ArtifactCapabilityExecutionPlan(String tenantId, String projectId, String workspaceId,
        String actorId, String idempotencyKey, BoundGraphInputs inputs) {
    public ArtifactCapabilityExecutionPlan {
        for(String value:List.of(tenantId,projectId,workspaceId,actorId,idempotencyKey)) {
            if(value.isBlank() || value.length()>256)throw new IllegalArgumentException("execution scope/key required and bounded");
        }
        Objects.requireNonNull(inputs,"inputs");
        if(inputs.physicalPlan().units().size()!=1)throw new IllegalArgumentException("one typed capability unit required");
        var request=inputs.physicalPlan().units().getFirst().artifactCapabilityRequest();
        if(request==null || !request.requestIdentity().equals(requestIdentity(tenantId,projectId,request.capabilityId(),idempotencyKey))) {
            throw new IllegalArgumentException("Artifact capability plan does not bind its scoped request identity");
        }
    }
    public ArtifactCapabilityRequest request() { return inputs.physicalPlan().units().getFirst().artifactCapabilityRequest(); }
    public String executionId() { return "exe_"+request().requestIdentity(); }
    public String scopedIdempotencyKey() { return request().requestIdentity(); }
    public String requestHash() { return hash(request().canonical()); }
    public static String requestIdentity(String tenant, String project, String capability, String key) {
        return hash(new CanonicalWriter().tag("artifact-capability-execution-identity-v1")
                .field("tenant",tenant).field("project",project).field("capability",capability).field("key",key).build());
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
