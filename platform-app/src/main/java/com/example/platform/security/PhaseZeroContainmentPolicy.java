package com.example.platform.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsUtils;

/**
 * Phase-0 classification and fail-closed authorization policy for MVC routes.
 *
 * <p>The policy deliberately does not enumerate runtime routes. Spring MVC's live
 * {@code RequestMappingHandlerMapping} is the route authority; this class only classifies a
 * discovered method/path pair and supplies matching authorization rules. A route outside these
 * declared families is unclassified and prevents application startup.
 */
public final class PhaseZeroContainmentPolicy {

    public enum Classification {
        PUBLIC_SAFE,
        AUTHENTICATED_READ,
        AUTHENTICATED_MUTATION,
        ADMIN_ONLY,
        INTERNAL_CONTROL_PLANE,
        DISABLED_CONTAINED,
        TEST_ONLY
    }

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<HttpMethod> READ_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD);

    private static final List<String> PUBLIC_READ_FAMILIES = List.of(
            "/healthz",
            "/readyz",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/app/**");

    private static final List<String> TEST_ONLY_FAMILIES = List.of(
            "/api/dev/**",
            "/dev/**");

    private static final List<String> ADMIN_FAMILIES = List.of(
            "/api/admin/**",
            "/api/audit/admin/**",
            "/api/identity/admin/**");

    private static final List<String> INTERNAL_CONTROL_FAMILIES = List.of(
            "/api/mcp/**",
            "/api/remote-worker/**",
            "/api/internal/outbox/**",
            "/api/outbox/**",
            "/api/scheduler/**",
            "/api/observability/**",
            "/api/secrets/**",
            "/api/configs/**",
            "/api/extensions/**",
            "/api/storage/providers");

    /**
     * Families whose present implementations do not yet establish canonical actor, tenant,
     * resource, payment, delivery, or execution authority. Phase 0 contains them; it does not
     * invent their future authorization semantics.
     */
    private static final List<String> DISABLED_FAMILIES = List.of(
            "/api/webhooks/**",
            "/api/product/**",
            "/api/social/**",
            "/api/ai/**",
            "/api/analytics/nlq/**",
            "/api/analytics/reports/**",
            "/api/federation/**",
            "/api/policy/**",
            "/api/tenants/*/workflow-executions/**",
            "/api/identity/tenants/**",
            "/api/identity/projects/**",
            "/api/workspaces/**",
            "/api/me/shared-resources/**",
            "/api/admin/shared-resources/**",
            "/api/entitlements/**",
            "/api/tenants/*/entitlements/**",
            "/api/commerce/**",
            "/api/billing/**",
            "/api/payments/**",
            "/api/tenants/*/delivery/**",
            "/api/tenants/*/projects/*/delivery/**",
            "/api/tenants/*/projects/*/render-jobs/*/deliver",
            "/api/tenants/*/projects/*/render-jobs/*/deliveries/**",
            "/api/timeline-git/**",
            "/api/timelines/**",
            "/api/reviews/**",
            "/api/render/**",
            "/api/tenants/*/projects/*/render-jobs/**",
            "/api/tenants/*/projects/*/render/**",
            "/api/tenants/*/projects/*/timeline/**",
            "/api/tenants/*/projects/*/caption-template/**",
            "/api/products/**",
            "/api/projects/*/products/**",
            "/api/marketplace/**",
            "/api/assets/**",
            "/api/projects/*/assets/**",
            "/api/media/assets/**",
            "/api/asset-governance/**",
            "/api/artifacts/**",
            "/api/preview/**",
            "/api/projects/*/dashboard",
            "/api/projects/*/dashboard/**",
            "/api/notifications/**",
            "/api/tenants/*/notifications/**",
            "/api/admin/notifications/**",
            "/api/me/notifications/**",
            "/api/me/notification-*/**",
            "/api/tenants/*/tier",
            "/api/prompts/**");

    /** Known application families whose current boundary is ordinary authenticated access. */
    private static final List<String> AUTHENTICATED_FAMILIES = List.of(
            "/api/me/**",
            "/api/audit/compliance/**",
            "/api/datasources/**",
            "/api/effect-packs/**",
            "/api/feature-flags/**",
            "/api/navigation/**",
            "/api/semantic/**",
            "/api/storage/*",
            "/api/identity/access/**",
            "/api/artifact/catalog/**",
            "/api/tenants/*/workflow-definitions/**",
            "/api/tenants/*/projects/*/upload/**");

    private PhaseZeroContainmentPolicy() {}

    public static Optional<Classification> classify(HttpMethod method, String mappingPath) {
        String path = normalize(mappingPath);
        if (READ_METHODS.contains(method) && matchesAny(PUBLIC_READ_FAMILIES, path)) {
            return Optional.of(Classification.PUBLIC_SAFE);
        }
        if (matchesAny(TEST_ONLY_FAMILIES, path)) {
            return Optional.of(Classification.TEST_ONLY);
        }
        if (matchesAny(DISABLED_FAMILIES, path)
                || (matchesAny(PUBLIC_READ_FAMILIES, path) && !READ_METHODS.contains(method))) {
            return Optional.of(Classification.DISABLED_CONTAINED);
        }
        if (matchesAny(INTERNAL_CONTROL_FAMILIES, path)) {
            return Optional.of(Classification.INTERNAL_CONTROL_PLANE);
        }
        if (matchesAny(ADMIN_FAMILIES, path)) {
            return Optional.of(Classification.ADMIN_ONLY);
        }
        if (matchesAny(AUTHENTICATED_FAMILIES, path)) {
            return Optional.of(READ_METHODS.contains(method)
                    ? Classification.AUTHENTICATED_READ
                    : Classification.AUTHENTICATED_MUTATION);
        }
        return Optional.empty();
    }

    /** Apply the enabled-security rules in the same precedence order as {@link #classify}. */
    public static void applyEnabled(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(CorsUtils::isPreFlightRequest).permitAll();
        requestMatchers(auth, DISABLED_FAMILIES).denyAll();
        requestMatchers(auth, INTERNAL_CONTROL_FAMILIES).denyAll();
        requestMatchers(auth, ADMIN_FAMILIES).hasAuthority("ROLE_ADMIN");
        requestMatchers(auth, HttpMethod.GET, PUBLIC_READ_FAMILIES).permitAll();
        requestMatchers(auth, HttpMethod.HEAD, PUBLIC_READ_FAMILIES).permitAll();
        requestMatchers(auth, TEST_ONLY_FAMILIES).permitAll();
        requestMatchers(auth, AUTHENTICATED_FAMILIES).authenticated();
        auth.anyRequest().denyAll();
    }

    /**
     * Security-disabled means authentication is unavailable, not that authority checks disappear.
     * Only explicit public reads and genuine CORS pre-flight requests remain reachable.
     */
    public static void applySecurityDisabled(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(CorsUtils::isPreFlightRequest).permitAll();
        requestMatchers(auth, HttpMethod.GET, PUBLIC_READ_FAMILIES).permitAll();
        requestMatchers(auth, HttpMethod.HEAD, PUBLIC_READ_FAMILIES).permitAll();
        auth.anyRequest().denyAll();
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private static String normalize(String path) {
        return path.replaceAll("\\{[^/}]+}", "*");
    }

    private static AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl requestMatchers(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
            List<String> patterns) {
        return auth.requestMatchers(patterns.toArray(String[]::new));
    }

    private static AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl requestMatchers(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
            HttpMethod method,
            List<String> patterns) {
        return auth.requestMatchers(method, patterns.toArray(String[]::new));
    }
}
