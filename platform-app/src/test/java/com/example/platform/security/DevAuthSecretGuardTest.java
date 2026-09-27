package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.server.ResponseStatusException;

/**
 * AUTH-UNPROTECTED-FIX-002 — the dev-auth gate is fail closed on both axes (profile insurance and
 * dev secret) and never discloses why a request was rejected.
 */
class DevAuthSecretGuardTest {

    private static final String SECRET = "dev-secret-value";

    @Test
    void activeOnlyWhenPropertyOnAndNoProductionProfile() {
        assertTrue(guard(new String[0], true, SECRET).active());
        assertFalse(guard(new String[0], false, SECRET).active(), "property off must disable");
        assertTrue(guard(new String[] {"preview"}, true, SECRET).active(),
                "a non-production profile keeps the surface available");
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "safe-mode", "oidc", "PROD", "Oidc"})
    void productionLikeProfilesDisableTheSurface(String profile) {
        DevAuthSecretGuard guard = guard(new String[] {profile}, true, SECRET);
        assertFalse(guard.active(), profile + " must disable the dev auth surface");
        assertFalse(guard.matches(SECRET), "secret must not be accepted in " + profile);
    }

    @Test
    void missingConfiguredSecretFailsClosed() {
        DevAuthSecretGuard guard = guard(new String[0], true, "");
        assertFalse(guard.secretConfigured());
        assertFalse(guard.matches(null));
        assertFalse(guard.matches(""));
        assertFalse(guard.matches(SECRET), "no configured secret can never be matched");
    }

    @Test
    void onlyTheExactSecretMatches() {
        DevAuthSecretGuard guard = guard(new String[0], true, SECRET);
        assertTrue(guard.matches(SECRET));
        assertTrue(guard.matches("  " + SECRET + "  "), "surrounding whitespace is tolerated");
        assertFalse(guard.matches(SECRET + "x"));
        assertFalse(guard.matches(SECRET.substring(0, SECRET.length() - 1)));
        assertFalse(guard.matches(null));
        assertFalse(guard.matches(""));
    }

    @Test
    void requireAuthorizedRejectsWithoutDisclosingWhy() {
        DevAuthSecretGuard configured = guard(new String[0], true, SECRET);
        DevAuthSecretGuard unconfigured = guard(new String[0], true, "");

        ResponseStatusException wrongSecret = assertThrows(ResponseStatusException.class,
                () -> configured.requireAuthorized("nope"));
        ResponseStatusException noSecretConfigured = assertThrows(ResponseStatusException.class,
                () -> unconfigured.requireAuthorized(SECRET));

        assertEquals(401, wrongSecret.getStatusCode().value());
        assertEquals(401, noSecretConfigured.getStatusCode().value());
        assertEquals(wrongSecret.getReason(), noSecretConfigured.getReason(),
                "the rejection reason must not reveal whether a secret is configured");
    }

    @Test
    void urlRuleAllowsFallThroughWhenTheSurfaceCannotExist() {
        DevAuthSecretGuard disabled = guard(new String[0], false, SECRET);
        assertTrue(disabled.authorizeRequest(context(null)).isGranted(),
                "a non-existent surface must fall through so the dispatcher answers 404");
    }

    @Test
    void urlRuleDeniesWithoutAMatchingSecretWhenActive() {
        DevAuthSecretGuard guard = guard(new String[0], true, SECRET);
        assertTrue(guard.authorizeRequest(context(SECRET)).isGranted());
        assertFalse(guard.authorizeRequest(context(null)).isGranted());
        assertFalse(guard.authorizeRequest(context("wrong")).isGranted());
    }

    private static RequestAuthorizationContext context(String presentedSecret) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dev/auth/token");
        if (presentedSecret != null) {
            request.addHeader(DevAuthSecretGuard.HEADER, presentedSecret);
        }
        return new RequestAuthorizationContext(request);
    }

    private static DevAuthSecretGuard guard(String[] activeProfiles, boolean endpointEnabled, String secret) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new DevAuthSecretGuard(environment, endpointEnabled, secret);
    }
}
