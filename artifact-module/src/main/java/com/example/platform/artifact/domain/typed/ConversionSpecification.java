package com.example.platform.artifact.domain.typed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.platform.shared.digest.ContentDigest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public record ConversionSpecification(String specificationId, String tenantId, String workspaceId, String actorId,
                                      List<String> sourceArtifactIds, JsonNode normalizedParameters, String requestedContractId,
                                      String contractVersion, String authorityScope, String entitlementSnapshotReference,
                                      String quotaSnapshotReference, String deterministicFingerprint, List<String> parentLineage,
                                      String createdBy, Instant createdAt) {
    public ConversionSpecification {
        for (String v : List.of(specificationId, tenantId, workspaceId, actorId, requestedContractId, contractVersion, authorityScope, createdBy)) if (v == null || v.isBlank()) throw new IllegalArgumentException("missing specification provenance");
        if (sourceArtifactIds == null || sourceArtifactIds.isEmpty() || sourceArtifactIds.stream().anyMatch(v -> v == null || v.isBlank())) throw new IllegalArgumentException("source artifacts are required");
        Objects.requireNonNull(normalizedParameters); Objects.requireNonNull(createdAt); sourceArtifactIds=List.copyOf(sourceArtifactIds); parentLineage=parentLineage==null?List.of():List.copyOf(parentLineage);
        if (deterministicFingerprint == null || deterministicFingerprint.isBlank()) throw new IllegalArgumentException("fingerprint is required");
    }
    public static String fingerprint(String contractId, String version, List<String> sourceIds, JsonNode parameters) {
        try { String canonical=contractId+"\n"+version+"\n"+String.join("\n", sourceIds.stream().sorted().toList())+"\n"+new ObjectMapper().writeValueAsString(canonicalize(parameters)); byte[] d=MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)); return HexFormat.of().formatHex(d); }
        catch(Exception e){throw new IllegalStateException("cannot fingerprint specification",e);}
    }
    private static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isValueNode()) return node;
        if (node.isArray()) { var out = new com.fasterxml.jackson.databind.node.ArrayNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance); node.forEach(v -> out.add(canonicalize(v))); return out; }
        var out = new com.fasterxml.jackson.databind.node.ObjectNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
        var names = new java.util.ArrayList<String>(); node.fieldNames().forEachRemaining(names::add); java.util.Collections.sort(names);
        for (String name : names) out.set(name, canonicalize(node.get(name)));
        return out;
    }
}
