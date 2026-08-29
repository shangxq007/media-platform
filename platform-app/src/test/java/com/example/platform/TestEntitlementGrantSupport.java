package com.example.platform;

import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.EntitlementCommandType;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;

final class TestEntitlementGrantSupport {
    private static final Instant EFFECTIVE_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2100-01-01T00:00:00Z");

    private TestEntitlementGrantSupport() {}

    static void grant(EntitlementService service, String tenantId, String entitlementKey) {
        service.execute(new EntitlementGrantCommand(
                EntitlementCommandType.GRANT,
                PrincipalRef.tenantScoped(tenantId, PrincipalType.ORGANIZATION, tenantId),
                "test-grant-" + tenantId + "-" + entitlementKey,
                entitlementKey, null, "TEST", "platform-render-tests",
                "test-grant:" + tenantId + ":" + entitlementKey,
                "test", "integration entitlement", "trace-" + tenantId,
                EFFECTIVE_AT, EXPIRES_AT, 0));
    }
}
