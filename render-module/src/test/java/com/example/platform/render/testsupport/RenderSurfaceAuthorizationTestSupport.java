package com.example.platform.render.testsupport;

import com.example.platform.render.app.RenderSurfaceAuthorization;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.CanonicalActor;
import java.util.Optional;
import java.util.Set;

/**
 * Test-only doubles for {@link RenderSurfaceAuthorization}. These are test construction aids,
 * never a production fallback: production wiring always injects the canonical decision port.
 */
public final class RenderSurfaceAuthorizationTestSupport {

    private RenderSurfaceAuthorizationTestSupport() {}

    /** An authorization boundary whose canonical port allows every scoped decision. */
    public static RenderSurfaceAuthorization allowAll() {
        return new RenderSurfaceAuthorization(
                request -> AuthorizationDecision.allow("TEST_ALLOW"),
                () -> Optional.of(CanonicalActor.user(
                        "test-user", com.example.platform.shared.web.TenantContext.get(),
                        Set.of("EDITOR"), "test")));
    }
}
