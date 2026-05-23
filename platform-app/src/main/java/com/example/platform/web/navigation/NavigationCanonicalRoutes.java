package com.example.platform.web.navigation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical frontend routes aligned with {@code frontend/src/router/index.ts} and fallback navigation.
 */
final class NavigationCanonicalRoutes {

    private static final Logger log = LoggerFactory.getLogger(NavigationCanonicalRoutes.class);

    private NavigationCanonicalRoutes() {}

    static void registerAll(NavigationRegistryService registry, ObjectMapper objectMapper) {
        List<Map<String, Object>> routes = List.of(
                // User / me portal
                route("me-dashboard", "/me", "UserDashboardPage", "Dashboard", "main", "📊", 10),
                route("me-projects", "/me/projects", "MyProjectsPage", "Projects", "main", "📁", 20),
                route("me-shared-resources", "/me/shared-resources", "MySharedResourcesPage", "Shared", "content", "🔗", 25),
                route("editor", "/", "EditorPage", "Editor", "main", "🎬", 30),
                route("project", "/project/:id", "EditorPage", "Project", "main", null, 35),
                route("me-publish", "/me/publish", "SocialPublishPage", "Publish", "content", "📱", 35),
                route("me-scheduler", "/me/scheduler", "PostSchedulerPage", "Scheduler", "content", "📅", 36),
                route("me-publish-history", "/me/publish-history", "PublishHistoryPage", "Publish History", "content", "📋", 37),
                route("me-exports", "/me/exports", "MyExportsPage", "Exports", "content", "📤", 40),
                route("me-analytics", "/me/analytics", "AnalyticsAssistantPage", "Analytics", "content", "📈", 45),
                route("me-reports", "/me/reports", "MyReportsPage", "Reports", "content", "📊", 90),
                route("me-capabilities", "/me/capabilities", "UserMyCapabilitiesPage", "Capabilities", "account", "🛡️", 50),
                route("me-usage", "/me/usage", "MyUsagePage", "Usage", "account", "📈", 60),
                route("me-billing", "/me/billing", "MyBillingPage", "Billing", "account", "💳", 70),
                route("me-credits", "/me/credits", "MyCreditsPage", "Credits", "account", "💰", 80),
                route("me-delivery-destinations", "/me/delivery-destinations", "DeliveryDestinationsPage", "Delivery", "account", "📦", 85),
                route("me-settings", "/me/settings", "MySettingsPage", "Settings", "account", "⚙️", 120),
                route("me-feedback", "/me/feedback", "MyFeedbackPage", "Feedback", "support", "💬", 100),
                route("me-notifications", "/me/notifications", "MyNotificationsPage", "Notifications", "support", "🔔", 110),
                route("me-notification-settings", "/me/notification-settings", "NotificationSettingsPage", "Notification Settings", "support", "🔔", 115),
                route("prompts", "/prompts", "PromptManagementPage", "Prompts", "main", "💬", 38),
                route("prompt-editor", "/prompts/:templateId", "PromptManagementPage", "Prompt Editor", "main", null, 39, "prompts"),
                route("effect-packs", "/effect-packs", "EffectPackEditor", "Effect Packs", "main", "✨", 40),
                // Admin layout + children
                route("admin", "/admin", "AdminLayout", "Admin", "admin", "🛡️", 100, null, List.of("ADMIN", "TENANT_ADMIN"), null, List.of("WEB", "ADMIN")),
                route("admin-dashboard", "/admin", "AdminDashboard", "Dashboard", "admin", "📊", 10, "admin"),
                route("admin-tenants", "/admin/tenants", "TenantManagement", "Tenants", "admin", "🏢", 20, "admin"),
                route("admin-render-jobs", "/admin/render-jobs", "RenderJobManagement", "Render Jobs", "admin", "🎬", 30, "admin"),
                route("admin-delivery", "/admin/delivery", "DeliveryAdminPage", "Delivery", "admin", "📤", 35, "admin"),
                route("admin-extensions", "/admin/extensions", "ExtensionManagement", "Extensions", "admin", "🔌", 40, "admin"),
                route("admin-quota-billing", "/admin/quota-billing", "QuotaBilling", "Quota & Billing", "admin", "💰", 50, "admin"),
                route("admin-analytics", "/admin/analytics", "UserAnalytics", "Analytics", "admin", "📈", 60, "admin"),
                route("admin-notifications", "/admin/notifications", "NotificationManagement", "Notifications", "admin", "🔔", 70, "admin"),
                route("admin-notifications-overview", "/admin/notifications/overview", "NotificationAdminPage", "Notifications Overview", "admin", "🔔", 71, "admin"),
                route("admin-notification-events", "/admin/notifications/events", "NotificationEventDefinitionPage", "Notification Events", "admin", "🔔", 72, "admin"),
                route("admin-notification-deliveries", "/admin/notifications/deliveries", "NotificationDeliveryLogPage", "Notification Deliveries", "admin", "🔔", 73, "admin"),
                route("admin-audit", "/admin/audit", "AuditCompliance", "Audit & Outbox", "admin", "📋", 80, "admin"),
                route("admin-audit-log", "/admin/audit-log", "AuditLogPage", "Audit Log", "admin", "📋", 81, "admin"),
                route("admin-config", "/admin/config", "ConfigManagement", "Config", "admin", "⚙️", 90, "admin"),
                route("admin-feature-flags", "/admin/feature-flags", "FeatureFlags", "Feature Flags", "admin", "🚩", 100, "admin"),
                route("admin-feature-flag-mgmt", "/admin/feature-flags/manage", "FeatureFlagManagementPage", "Feature Flag Mgmt", "admin", "🚩", 101, "admin"),
                route("admin-policies", "/admin/policies", "PolicyManagementPage", "Policies", "admin", "📜", 102, "admin"),
                route("admin-routes", "/admin/routes", "RouteManagementPage", "Route Management", "admin", "🔀", 110, "admin"),
                route("admin-monitoring", "/admin/monitoring", "MonitoringFeedbackPage", "Monitoring", "admin", "📡", 112, "admin"),
                route("admin-feedback", "/admin/feedback", "FeedbackAdminPage", "Feedback", "admin", "💬", 113, "admin"),
                route("admin-nlq-datasets", "/admin/analytics/datasets", "DatasetCatalogPage", "NLQ Datasets", "admin", "📊", 114, "admin"),
                route("admin-nlq-query-audit", "/admin/analytics/query-audit", "QueryAuditPage", "NLQ Query Audit", "admin", "📊", 115, "admin"),
                route("admin-entitlement-bundles", "/admin/entitlements/bundles", "EntitlementBundleList", "Entitlement Bundles", "admin", "🎁", 120, "admin"),
                route("admin-billing-plans", "/admin/billing/plans", "BillingPlanManagementPage", "Billing Plans", "admin", "💳", 130, "admin")
        );

        for (Map<String, Object> routeMap : routes) {
            try {
                FrontendRouteDefinition def = objectMapper.convertValue(routeMap, FrontendRouteDefinition.class);
                registry.saveRoute(def);
            } catch (Exception e) {
                log.warn("Failed to register canonical route {}: {}", routeMap.get("routeKey"), e.getMessage());
            }
        }
    }

    private static Map<String, Object> route(
            String routeKey,
            String path,
            String componentKey,
            String title,
            String menuGroup,
            String icon,
            Integer order) {
        return route(routeKey, path, componentKey, title, menuGroup, icon, order, null, null, null, null);
    }

    private static Map<String, Object> route(
            String routeKey,
            String path,
            String componentKey,
            String title,
            String menuGroup,
            String icon,
            Integer order,
            String parentRouteKey) {
        return route(routeKey, path, componentKey, title, menuGroup, icon, order, parentRouteKey, null, null, null);
    }

    private static Map<String, Object> route(
            String routeKey,
            String path,
            String componentKey,
            String title,
            String menuGroup,
            String icon,
            Integer order,
            String parentRouteKey,
            List<String> requiredRoles,
            List<String> requiredPermissions,
            List<String> supportedSources) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("routeKey", routeKey);
        map.put("path", path);
        map.put("componentKey", componentKey);
        map.put("title", title);
        map.put("menuGroup", menuGroup);
        map.put("visible", true);
        map.put("enabled", true);
        if (icon != null) {
            map.put("icon", icon);
        }
        if (order != null) {
            map.put("order", order);
        }
        if (parentRouteKey != null) {
            map.put("parentRouteKey", parentRouteKey);
        }
        if (requiredRoles != null) {
            map.put("requiredRoles", requiredRoles);
        }
        if (requiredPermissions != null) {
            map.put("requiredPermissions", requiredPermissions);
        }
        if (supportedSources != null) {
            map.put("supportedSources", supportedSources);
        }
        return map;
    }
}
