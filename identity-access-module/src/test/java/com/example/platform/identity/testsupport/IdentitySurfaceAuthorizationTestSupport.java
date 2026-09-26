package com.example.platform.identity.testsupport;

import com.example.platform.identity.app.IdentitySurfaceAuthorization;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.Optional;
import java.util.Set;

/**
 * Test-only construction aid for {@link IdentitySurfaceAuthorization}. Never a production fallback:
 * production wiring always injects the canonical decision port.
 */
public final class IdentitySurfaceAuthorizationTestSupport {

    private IdentitySurfaceAuthorizationTestSupport() {}

    /** An authorization boundary whose canonical port allows every scoped decision. */
    public static IdentitySurfaceAuthorization allowAll() {
        return new IdentitySurfaceAuthorization(
                request -> AuthorizationDecision.allow("TEST_ALLOW"),
                () -> Optional.of(CanonicalActor.user(
                        "test-user", TenantContext.get(), Set.of("ADMIN"), "test")));
    }
}
