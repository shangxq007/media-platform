package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class PaymentFingerprints {
    private PaymentFingerprints() {}
    static String sha256(String... values) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\u001f", values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static String principal(PrincipalRef value) {
        return String.join(":", value.tenantId(), value.principalType().name(), value.principalId(),
                optional(value.workspaceId()), optional(value.organizationId()));
    }
    static String money(Money value) { return value.amountMinor() + ":" + value.currency(); }
    static String optional(String value) { return value == null ? "" : value; }
    static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
