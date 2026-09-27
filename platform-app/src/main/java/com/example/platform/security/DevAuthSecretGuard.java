package com.example.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * AUTH-UNPROTECTED-FIX-002 — fail-closed gate for the dev token surface.
 *
 * <p>The dev auth endpoint mints a JWT carrying {@code roles=[USER,ADMIN]} for an arbitrary
 * tenant/user, so it must never be reachable by an unauthenticated caller and must never exist in a
 * production-like runtime. Two independent conditions are required:</p>
 *
 * <ol>
 *   <li><b>Profile insurance</b> — {@link #NOT_PRODUCTION_PROFILES} (also used by the controller's
 *       {@code @Profile}) must hold, so {@code prod} / {@code safe-mode} / {@code oidc} never
 *       register the surface even if the property is switched on by accident.</li>
 *   <li><b>Dev secret</b> — {@code app.security.dev-auth-secret} (env {@code DEV_AUTH_SECRET}) must
 *       be configured and match the {@value #HEADER} request header.</li>
 * </ol>
 *
 * <p>Neither the configured secret nor the reason for a rejection is ever disclosed.</p>
 */
@Component
public class DevAuthSecretGuard {

    /** Request header carrying the dev secret. */
    public static final String HEADER = "X-Dev-Auth-Secret";

    /**
     * Profile expression under which the dev auth surface may exist. Shared by the controller's
     * {@code @Profile} so the bean condition and this guard cannot drift apart.
     */
    public static final String NOT_PRODUCTION_PROFILES = "!prod & !safe-mode & !oidc";

    private static final Set<String> PRODUCTION_LIKE_PROFILES = Set.of("prod", "safe-mode", "oidc");
    private static final String DENIAL_MESSAGE = "dev auth endpoint unavailable";

    private final boolean endpointEnabled;
    private final boolean productionProfileActive;
    private final String configuredSecret;

    public DevAuthSecretGuard(Environment environment,
            @Value("${app.security.dev-auth-endpoint:false}") boolean endpointEnabled,
            @Value("${app.security.dev-auth-secret:}") String configuredSecret) {
        this.endpointEnabled = endpointEnabled;
        this.productionProfileActive = Arrays.stream(environment.getActiveProfiles())
                .filter(profile -> profile != null && !profile.isBlank())
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PRODUCTION_LIKE_PROFILES::contains);
        this.configuredSecret = configuredSecret == null ? "" : configuredSecret.trim();
    }

    /** True only when the property is on AND no production-like profile is active. */
    public boolean active() {
        return endpointEnabled && !productionProfileActive;
    }

    public boolean secretConfigured() {
        return !configuredSecret.isEmpty();
    }

    /**
     * Constant-time secret comparison. Always false when the gate is inactive, when no secret is
     * configured (fail closed), or when no secret was presented.
     */
    public boolean matches(String presentedSecret) {
        if (!active() || !secretConfigured() || presentedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                presentedSecret.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** Controller-side gate: rejects with 401 and never discloses why. */
    public void requireAuthorized(String presentedSecret) {
        if (!matches(presentedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, DENIAL_MESSAGE);
        }
    }

    /**
     * URL-layer decision. When the surface cannot exist (property off or a production-like profile)
     * the request is allowed through so the dispatcher answers 404; when it can exist the request is
     * allowed only with a matching secret, and denied otherwise.
     */
    public AuthorizationDecision authorizeRequest(
            org.springframework.security.web.access.intercept.RequestAuthorizationContext context) {
        if (!active()) {
            return new AuthorizationDecision(true);
        }
        return matches(context.getRequest().getHeader(HEADER))
                ? new AuthorizationDecision(true)
                : new AuthorizationDecision(false);
    }
}
