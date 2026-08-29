package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** SHA-256 operational evidence for exact dependency bundle content and its runtime binding. */
public record RuntimeDependencyFingerprint(String value) implements Serializable {

    private static final String SHA_256_PREFIX = "sha256:";
    private static final String SHA_256_HEX = "[0-9a-f]{64}";

    public RuntimeDependencyFingerprint {
        if (value == null || !value.matches(SHA_256_HEX)) {
            throw new IllegalArgumentException("runtime dependency fingerprint must be lowercase SHA-256");
        }
    }

    public static RuntimeDependencyFingerprint from(RuntimeDependencyObservation observation) {
        Objects.requireNonNull(observation, "observation");
        MessageDigest digest = sha256();
        frame(digest, "contract", "runtime-dependency-fingerprint-v1");
        frame(digest, "providerImplementationId", observation.providerImplementationId().value());
        frame(digest, "workerRuntimeId", observation.workerRuntimeId().value());
        frame(digest, "device.present", observation.deviceId().isPresent() ? "1" : "0");
        observation.deviceId().ifPresent(device -> frame(digest, "device.value", device.value()));
        frame(digest, "probeSchemaVersion", Integer.toString(observation.probeSchemaVersion().value()));
        frame(digest, "dependency.count", Integer.toString(observation.dependencies().size()));
        for (RuntimeDependencyObservedDependency dependency : observation.dependencies()) {
            frame(digest, "dependency.coordinate", dependency.coordinate().value());
            frame(digest, "dependency.version", dependency.version().value());
            frame(digest, "dependency.abi.present", dependency.abi().isPresent() ? "1" : "0");
            dependency.abi().ifPresent(abi -> frame(digest, "dependency.abi.value", abi.value()));
            frame(digest, "dependency.feature.count", Integer.toString(dependency.enabledFeatures().size()));
            dependency.enabledFeatures().forEach(feature ->
                    frame(digest, "dependency.feature", feature));
            frame(
                    digest,
                    "dependency.buildRuntimeFlag.count",
                    Integer.toString(dependency.enabledBuildRuntimeFlags().size()));
            dependency.enabledBuildRuntimeFlags().forEach(flag ->
                    frame(digest, "dependency.buildRuntimeFlag", flag));
        }
        return new RuntimeDependencyFingerprint(HexFormat.of().formatHex(digest.digest()));
    }

    public static RuntimeDependencyFingerprint parseSha256(String canonicalDigest) {
        if (canonicalDigest == null
                || !canonicalDigest.matches(SHA_256_PREFIX + SHA_256_HEX)) {
            throw new IllegalArgumentException(
                    "canonical runtime dependency fingerprint must be lowercase sha256:<64-hex>");
        }
        return new RuntimeDependencyFingerprint(
                canonicalDigest.substring(SHA_256_PREFIX.length()));
    }

    public String canonicalSha256() {
        return SHA_256_PREFIX + value;
    }

    @Override
    public String toString() {
        return value;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    /** Length-prefixes both field name and value bytes so the stream is injectively framed. */
    private static void frame(MessageDigest digest, String field, String value) {
        byte[] fieldBytes = field.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(fieldBytes.length).array());
        digest.update(fieldBytes);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(valueBytes.length).array());
        digest.update(valueBytes);
    }
}
