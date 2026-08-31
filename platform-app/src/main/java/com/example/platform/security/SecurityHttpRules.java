package com.example.platform.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Shared URL authorization rules for JWT and OAuth2 security filter chains.
 */
public final class SecurityHttpRules {

    private SecurityHttpRules() {}

    public static void applyApiAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        PhaseZeroContainmentPolicy.applyEnabled(auth);
    }
}
