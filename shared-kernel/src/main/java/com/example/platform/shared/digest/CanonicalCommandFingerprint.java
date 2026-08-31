package com.example.platform.shared.digest;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versioned, injective binary framing for durable command identity. */
public final class CanonicalCommandFingerprint {

    public static final String DOMAIN_TAG = "CANONICAL_COMMAND_FINGERPRINT_V1";
    public static final int ENCODING_VERSION = 1;

    private CanonicalCommandFingerprint() {
    }

    public static Builder builder(String commandDomain) {
        return new Builder(commandDomain);
    }

    public static final class Builder {
        private final String commandDomain;
        private final List<Field> fields = new ArrayList<>();
        private final Set<String> tags = new HashSet<>();

        private Builder(String commandDomain) {
            this.commandDomain = requireText(commandDomain, "commandDomain");
        }

        public Builder required(String tag, String value) {
            fields.add(new Field(register(tag), requireText(value, tag), false));
            return this;
        }

        public Builder nullable(String tag, String value) {
            fields.add(new Field(register(tag), value, true));
            return this;
        }

        public byte[] framedBytes() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeNonNull(out, DOMAIN_TAG);
            writeInt(out, ENCODING_VERSION);
            writeNonNull(out, commandDomain);
            writeInt(out, fields.size());
            for (Field field : fields) {
                writeNonNull(out, field.tag());
                if (field.value() == null) {
                    out.write(0);
                    writeInt(out, 0);
                } else {
                    out.write(1);
                    byte[] bytes = field.value().getBytes(StandardCharsets.UTF_8);
                    writeInt(out, bytes.length);
                    out.writeBytes(bytes);
                }
            }
            return out.toByteArray();
        }

        public String sha256Hex() {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(framedBytes());
                return java.util.HexFormat.of().formatHex(digest);
            } catch (java.security.NoSuchAlgorithmException unavailable) {
                throw new IllegalStateException("SHA-256 unavailable", unavailable);
            }
        }

        private String register(String tag) {
            String exact = requireText(tag, "field tag");
            if (!tags.add(exact)) {
                throw new IllegalArgumentException("duplicate canonical field tag: " + exact);
            }
            return exact;
        }
    }

    private record Field(String tag, String value, boolean nullable) {
        private Field {
            if (!nullable && value == null) {
                throw new IllegalArgumentException("required canonical value missing: " + tag);
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
        return value;
    }

    private static void writeNonNull(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}
