package com.example.platform;

import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.EntitlementCommandType;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

final class TestEntitlementGrantSupport {
    private static final Instant EFFECTIVE_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2100-01-01T00:00:00Z");

    private TestEntitlementGrantSupport() {}

    static void grant(EntitlementService service, String tenantId, String entitlementKey) {
        service.execute(new EntitlementGrantCommand(
                EntitlementCommandType.GRANT,
                PrincipalRef.tenantScoped(tenantId, PrincipalType.ORGANIZATION, tenantId),
                grantId(tenantId, entitlementKey),
                entitlementKey, null, "TEST", "platform-render-tests",
                "test-grant:" + tenantId + ":" + entitlementKey,
                "test", "integration entitlement", "trace-" + tenantId,
                EFFECTIVE_AT, EXPIRES_AT, 0));
    }

    static String grantId(String tenantId, String entitlementKey) {
        MessageDigest digest = sha256();
        updateLengthFramed(digest, tenantId);
        updateLengthFramed(digest, entitlementKey);
        return "test-grant-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static void updateLengthFramed(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
