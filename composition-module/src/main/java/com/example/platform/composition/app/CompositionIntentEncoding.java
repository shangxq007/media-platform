package com.example.platform.composition.app;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Typed, length-delimited intent encoding. Map keys sort lexically; array order is semantic. */
final class CompositionIntentEncoding {
    private CompositionIntentEncoding() {}

    static byte[] encode(Object value) {
        try {
            var bytes = new ByteArrayOutputStream();
            write(new DataOutputStream(bytes), value);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    static String text(Object value) {
        return Base64.getEncoder().encodeToString(encode(value));
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void write(DataOutputStream out, Object value) throws IOException {
        if (value == null) { out.writeByte(0); return; }
        if (value instanceof String s) { out.writeByte(1); string(out, s); return; }
        if (value instanceof Boolean b) { out.writeByte(2); out.writeBoolean(b); return; }
        if (value instanceof BigDecimal || value instanceof BigInteger || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long) {
            out.writeByte(3);
            string(out, new BigDecimal(value.toString()).stripTrailingZeros().toPlainString());
            return;
        }
        if (value instanceof Float || value instanceof Double)
            throw new IllegalArgumentException("INEXACT_PARAMETER_NUMBER: use exact decimal JSON values");
        if (value instanceof Map<?, ?> map) {
            out.writeByte(4); out.writeInt(map.size());
            var sorted = new TreeMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("NON_STRING_PARAMETER_KEY");
                sorted.put(key, entry.getValue());
            }
            for (var entry : sorted.entrySet()) { string(out, entry.getKey()); write(out, entry.getValue()); }
            return;
        }
        if (value instanceof List<?> list) {
            out.writeByte(5); out.writeInt(list.size());
            for (Object item : list) write(out, item);
            return;
        }
        throw new IllegalArgumentException("UNSUPPORTED_PARAMETER_TYPE: " + value.getClass().getName());
    }
}
