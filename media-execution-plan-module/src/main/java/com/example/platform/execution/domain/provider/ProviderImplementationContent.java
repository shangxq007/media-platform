package com.example.platform.execution.domain.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Content-address the actual packaged provider classes and its admitted native distribution. */
public record ProviderImplementationContent(String implementationDigest, String distributionIdentity) {
    public ProviderImplementationContent {
        if (!implementationDigest.matches("[0-9a-f]{64}") || distributionIdentity.isBlank()) {
            throw new IllegalArgumentException("Invalid native provider identity");
        }
    }

    public static ProviderImplementationContent packaged(Class<?> anchor, String module, String distributionIdentity) {
        return new ProviderImplementationContent(indexedClasses(anchor, module), distributionIdentity);
    }

    public ProviderCapabilityProfileVersionOrDigest profile(String declaration) {
        MessageDigest digest = sha256();
        append(digest, "provider-implementation-content-v2");
        append(digest, implementationDigest);
        append(digest, distributionIdentity);
        append(digest, declaration);
        return ProviderCapabilityProfileVersionOrDigest.digest(
                ProviderCapabilityProfileDigest.sha256(HexFormat.of().formatHex(digest.digest())));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String indexedClasses(Class<?> anchor, String module) {
        MessageDigest digest = sha256();
        try {
            try (var reader = new BufferedReader(new InputStreamReader(
                    resource(anchor, "/META-INF/provider-content/" + module), StandardCharsets.UTF_8))) {
                var classes = reader.lines().toList();
                if (classes.isEmpty()) throw new IllegalStateException("empty provider class index");
                for (String name : classes) {
                    append(digest, name);
                    try (var bytes = resource(anchor, "/" + name)) {
                        digest.update(sha256().digest(bytes.readAllBytes()));
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException failure) {
            throw new IllegalStateException("provider implementation content is unavailable", failure);
        }
    }

    private static InputStream resource(Class<?> anchor, String path) {
        InputStream stream = anchor.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("missing packaged provider content " + path);
        return stream;
    }

    private static void append(MessageDigest digest, String field) {
        digest.update(field.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
