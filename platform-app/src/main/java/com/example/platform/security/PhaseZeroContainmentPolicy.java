package com.example.platform.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Temporary Phase-0 containment for externally reachable runtime surfaces that do not yet
 * enforce canonical actor, tenant, resource, or service authority.
 *
 * <p>This is deliberately a method/path-exact deny list, not a second authorization engine.
 * Apply it before every normal web authorization rule, including the security-disabled local
 * chain. Delete each entry when its route is replaced by a tested canonical boundary; delete
 * this class when the Phase-0 list is empty.
 */
public final class PhaseZeroContainmentPolicy {

    private static final List<ContainedRoute> MUTATIONS = List.of(
            route(HttpMethod.POST, "/api/extensions/*/execute"),
            route(HttpMethod.DELETE, "/api/extensions/*"),
            route(HttpMethod.POST, "/api/extensions/*/rollback"),
            route(HttpMethod.POST, "/api/extensions/*/rollback-point"),
            route(HttpMethod.POST, "/api/extensions/tool-run"),
            route(HttpMethod.POST, "/api/extensions/cli-tools/*/run"),
            route(HttpMethod.POST, "/api/extensions/*/routing-rules"),
            route(HttpMethod.POST, "/api/render/auto-captions"),
            route(HttpMethod.POST, "/api/social/platforms/*/connect"),
            route(HttpMethod.POST, "/api/social/posts/*/publish"),
            route(HttpMethod.POST, "/api/social/posts/*/schedule"),
            route(HttpMethod.POST, "/api/social/posts/*/retry"),
            route(HttpMethod.POST, "/api/notifications/events"),
            route(HttpMethod.POST, "/api/analytics/internal/rebuild-profiles"),
            route(HttpMethod.POST, "/api/analytics/internal/rebuild-segments"),
            route(HttpMethod.POST, "/api/billing/cycles/process-due"),
            route(HttpMethod.POST, "/api/asset-governance/integrity/scan-global"),
            route(HttpMethod.POST, "/api/asset-governance/segment-cache/cleanup"),
            route(HttpMethod.POST, "/api/asset-governance/storage-orphans/purge"),
            route(HttpMethod.POST, "/api/media/assets/integrity/scan-global"),
            route(HttpMethod.POST, "/api/render/jobs/*/cancel"),
            route(HttpMethod.POST, "/api/preview/media"),
            route(HttpMethod.POST, "/api/remote-worker/register"),
            route(HttpMethod.POST, "/api/remote-worker/deregister/*"),
            route(HttpMethod.POST, "/api/remote-worker/heartbeat/*"),
            route(HttpMethod.POST, "/api/remote-worker/jobs/*/callback"),
            route(HttpMethod.POST, "/api/products/*/dependencies"),
            route(HttpMethod.DELETE, "/api/products/*/dependencies/*"),
            route(HttpMethod.POST, "/api/tenants/*/notifications/*/retry"),
            route(HttpMethod.POST, "/api/me/notification-channels/*/verify"),
            route(HttpMethod.POST, "/api/me/notification-channels/*/test"),
            route(HttpMethod.POST, "/api/admin/notifications/deliveries/*/retry"),
            route(HttpMethod.POST, "/api/artifacts/*/tombstone"),
            route(HttpMethod.POST, "/api/artifacts/gc/run"),
            route(HttpMethod.POST, "/api/media/assets/*/tombstone"),
            route(HttpMethod.POST, "/api/media/assets/gc/run"));

    private static final List<String> READS = List.of(
            "/api/render/jobs/*/artifacts",
            "/api/render/jobs/*/status-history",
            "/api/render/jobs/*/artifacts/*/content",
            "/api/render/jobs/*/artifacts/*/access",
            "/api/tenants/*/notifications",
            "/api/tenants/*/notifications/*",
            "/api/tenants/*/notifications/*/deliveries",
            "/api/notifications/deliveries",
            "/api/notifications/mock-sent",
            "/api/admin/notifications/deliveries",
            "/api/admin/notifications/provider-status",
            "/api/social/analytics/posts/*");

    private PhaseZeroContainmentPolicy() {}

    public static void apply(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        containedRoutes().forEach(contained -> auth
                .requestMatchers(contained.method(), contained.pattern()).denyAll());
    }

    static List<ContainedRoute> containedRoutes() {
        return java.util.stream.Stream.concat(
                        MUTATIONS.stream(),
                        READS.stream().flatMap(pattern -> java.util.stream.Stream.of(
                                route(HttpMethod.GET, pattern), route(HttpMethod.HEAD, pattern))))
                .toList();
    }

    private static ContainedRoute route(HttpMethod method, String pattern) {
        return new ContainedRoute(method, pattern);
    }

    record ContainedRoute(HttpMethod method, String pattern) {}
}
