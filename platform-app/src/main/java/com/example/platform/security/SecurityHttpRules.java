package com.example.platform.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Shared URL authorization rules for JWT and OAuth2 security filter chains.
 */
public final class SecurityHttpRules {

    private SecurityHttpRules() {}

    /**
     * Fail-closed default used where no dev-auth guard is wired (e.g. minimal test chains): the dev
     * surface is denied outright.
     */
    public static void applyApiAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        applyApiAuthorization(auth, null);
    }

    /**
     * Shared URL rules for JWT and OAuth2 security filter chains.
     *
     * <p>AUTH-UNPROTECTED-FIX-002: {@code /api/dev/auth/**} is no longer an unconditional
     * {@code permitAll}. When the dev-auth surface can exist it requires a valid
     * {@link DevAuthSecretGuard#HEADER} secret at the URL layer as well (the controller re-checks and
     * fails closed); when it cannot exist the request falls through so the dispatcher answers 404.</p>
     */
    public static void applyApiAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
            DevAuthSecretGuard devAuthSecretGuard) {
        auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/webhooks/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/marketplace/search", "/api/marketplace/listings",
                        "/api/marketplace/listings/*", "/api/marketplace/discovery", "/api/product/marketplace/*/search").permitAll()
                .requestMatchers("/metrics/summary", "/graphql").authenticated()
                .requestMatchers("/api/mcp/**").authenticated();

        if (devAuthSecretGuard == null) {
            auth.requestMatchers("/api/dev/auth/**").denyAll();
        } else {
            auth.requestMatchers("/api/dev/auth/**")
                    .access((authentication, context) -> devAuthSecretGuard.authorizeRequest(context));
        }

        auth.requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll();
    }
}
