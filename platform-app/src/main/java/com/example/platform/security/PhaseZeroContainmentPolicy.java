package com.example.platform.security;

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

    private PhaseZeroContainmentPolicy() {}

    public static void apply(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        // Extension execution, lifecycle, arbitrary tools, and routing mutation.
        auth.requestMatchers(HttpMethod.POST, "/api/extensions/*/execute").denyAll()
                .requestMatchers(HttpMethod.DELETE, "/api/extensions/*").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/extensions/*/rollback").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/extensions/*/rollback-point").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/extensions/tool-run").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/extensions/cli-tools/*/run").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/extensions/*/routing-rules").denyAll()

                // Global analytics, billing, and asset-governance mutations.
                .requestMatchers(HttpMethod.POST, "/api/analytics/internal/rebuild-profiles").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/analytics/internal/rebuild-segments").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/billing/cycles/process-due").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/asset-governance/integrity/scan-global").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/asset-governance/segment-cache/cleanup").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/asset-governance/storage-orphans/purge").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/media/assets/integrity/scan-global").denyAll()

                // Render routes without tenant/project ownership and the development upload.
                .requestMatchers(HttpMethod.GET, "/api/render/jobs/*/artifacts").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/render/jobs/*/cancel").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/render/jobs/*/status-history").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/render/jobs/*/artifacts/*/content").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/render/jobs/*/artifacts/*/access").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/preview/media").denyAll()

                // Legacy in-memory worker authority.
                .requestMatchers(HttpMethod.POST, "/api/remote-worker/register").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/remote-worker/deregister/*").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/remote-worker/heartbeat/*").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/remote-worker/jobs/*/callback").denyAll()

                // Product dependencies currently use a literal system tenant and unscoped delete.
                .requestMatchers(HttpMethod.POST, "/api/products/*/dependencies").denyAll()
                .requestMatchers(HttpMethod.DELETE, "/api/products/*/dependencies/*").denyAll()

                // Notification fake operations, mock history, and unscoped/global delivery reads.
                .requestMatchers(HttpMethod.GET, "/api/tenants/*/notifications/*").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/tenants/*/notifications/*/deliveries").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/tenants/*/notifications/*/retry").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/notifications/deliveries").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/notifications/mock-sent").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/me/notification-channels/*/verify").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/me/notification-channels/*/test").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/admin/notifications/deliveries").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/admin/notifications/deliveries/*/retry").denyAll();
    }
}
