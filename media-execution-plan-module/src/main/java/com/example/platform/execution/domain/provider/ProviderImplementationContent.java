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
public final class ProviderImplementationContent {
    private ProviderImplementationContent() {}

    public static ProviderCapabilityProfileVersionOrDigest profile(Class<?> anchor, String module, String nativeIdentity) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, "provider-implementation-content-v1");
            append(digest, nativeIdentity);
            try (var reader = new BufferedReader(new InputStreamReader(
                    resource(anchor, "/META-INF/provider-content/" + module), StandardCharsets.UTF_8))) {
                var classes = reader.lines().toList();
                if (classes.isEmpty()) throw new IllegalStateException("empty provider class index");
                for (String name : classes) {
                    append(digest, name);
                    try (var bytes = resource(anchor, "/" + name)) {
                        digest.update(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
                    }
                }
            }
            return ProviderCapabilityProfileVersionOrDigest.digest(
                    ProviderCapabilityProfileDigest.sha256(HexFormat.of().formatHex(digest.digest())));
        } catch (IOException | NoSuchAlgorithmException failure) {
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
