package com.example.platform.studio.digest;

import com.example.platform.shared.digest.ContentDigest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class StudioDigest {
    private StudioDigest() {}

    public static ContentDigest sha256(byte[] bytes) {
        try {
            return ContentDigest.sha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static void verify(ContentDigest expected, byte[] bytes) {
        if (expected == null || !expected.matches(sha256(bytes))) {
            throw new IllegalArgumentException("semantic digest mismatch");
        }
    }
}
