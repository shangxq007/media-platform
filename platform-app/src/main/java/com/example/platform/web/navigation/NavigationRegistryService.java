package com.example.platform.web.navigation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class NavigationRegistryService {

    private static final Logger log = LoggerFactory.getLogger(NavigationRegistryService.class);

    private final Map<String, FrontendRouteDefinition> routeDefinitions = new ConcurrentHashMap<>();
    private final Map<String, NavigationPolicy> policies = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public NavigationRegistryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadBuiltinRoutes();
        NavigationCanonicalRoutes.registerAll(this, objectMapper);
        loadBuiltinPolicies();
    }

    public List<FrontendRouteDefinition> loadAllRouteDefinitions() {
        return new ArrayList<>(routeDefinitions.values());
    }

    public Optional<FrontendRouteDefinition> getRouteByKey(String routeKey) {
        return Optional.ofNullable(routeDefinitions.get(routeKey));
    }

    public FrontendRouteDefinition saveRoute(FrontendRouteDefinition def) {
        routeDefinitions.put(def.routeKey(), def);
        return def;
    }

    public void removeRoute(String routeKey) {
        routeDefinitions.remove(routeKey);
        policies.values().removeIf(p -> p.routeKey().equals(routeKey));
    }

    public Map<String, NavigationPolicy> loadAllPolicies() {
        return new HashMap<>(policies);
    }

    public NavigationPolicy savePolicy(NavigationPolicy policy) {
        policies.put(policy.policyKey(), policy);
        return policy;
    }

    public void removePolicy(String policyKey) {
        policies.remove(policyKey);
    }

    public List<FrontendRouteDefinition> getRoutesByMenuGroup(String menuGroup) {
        return routeDefinitions.values().stream()
                .filter(r -> menuGroup.equals(r.menuGroup()))
                .sorted(Comparator.comparingInt(r -> r.order() != null ? r.order() : 0))
                .collect(Collectors.toList());
    }

    public List<String> getAllMenuGroups() {
        return routeDefinitions.values().stream()
                .map(FrontendRouteDefinition::menuGroup)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @PostConstruct
    public void init() {
        log.info("NavigationRegistry initialized with {} routes and {} policies",
                routeDefinitions.size(), policies.size());
    }

    private void loadBuiltinRoutes() {
        List<Map<String, Object>> builtinRoutes = List.of(
                buildRouteMap("editor", "/", "EditorPage", "Editor", "main", "🎬", 10, null, null, null, null),
                buildRouteMap("project", "/project/:id", "EditorPage", "Project", "main", "📁", 20, null, null, null, null),
                buildRouteMap("prompts", "/prompts", "PromptManagementPage", "Prompts", "main", "💬", 30, null, null, null, null),
                buildRouteMap("prompt-editor", "/prompts/:templateId", "PromptManagementPage", "Prompt Editor", "main", null, null, "prompts", null, null, null),
                buildRouteMap("effect-packs", "/effect-packs", "EffectPackEditor", "Effect Packs", "main", "✨", 40, null, null, null, null),
                buildRouteMap("admin", "/admin", "AdminLayout", "Admin", "admin", "🛡️", 100, null, List.of("ADMIN", "TENANT_ADMIN"), null, List.of("WEB", "ADMIN")),
                buildRouteMap("admin-dashboard", "/admin", "AdminDashboard", "Dashboard", "admin", "📊", 10, "admin", null, null, null),
                buildRouteMap("admin-tenants", "/admin/tenants", "TenantManagement", "Tenants", "admin", "🏢", 20, "admin", null, List.of("tenant:read"), null),
                buildRouteMap("admin-render-jobs", "/admin/render-jobs", "RenderJobManagement", "Render Jobs", "admin", "🎬", 30, "admin", null, null, null),
                buildRouteMap("admin-extensions", "/admin/extensions", "ExtensionManagement", "Extensions", "admin", "🔌", 40, "admin", null, null, null),
                buildRouteMap("admin-quota-billing", "/admin/quota-billing", "QuotaBilling", "Quota & Billing", "admin", "💰", 50, "admin", null, List.of("billing:read"), null),
                buildRouteMap("admin-analytics", "/admin/analytics", "UserAnalytics", "Analytics", "admin", "📈", 60, "admin", null, null, null),
                buildRouteMap("admin-notifications", "/admin/notifications", "NotificationManagement", "Notifications", "admin", "🔔", 70, "admin", null, null, null),
                buildRouteMap("admin-audit", "/admin/audit", "AuditCompliance", "Audit & Outbox", "admin", "📋", 80, "admin", null, List.of("audit:read"), null),
                buildRouteMap("admin-config", "/admin/config", "ConfigManagement", "Config", "admin", "⚙️", 90, "admin", null, List.of("config:read"), null),
                buildRouteMap("admin-feature-flags", "/admin/feature-flags", "FeatureFlags", "Feature Flags", "admin", "🚩", 100, "admin", null, List.of("config:read"), null),
                buildRouteMap("admin-routes", "/admin/routes", "RouteManagementPage", "Route Management", "admin", "🔀", 110, "admin", null, List.of("config:read", "config:write"), null)
        );

        for (Map<String, Object> routeMap : builtinRoutes) {
            try {
                FrontendRouteDefinition def = objectMapper.convertValue(routeMap, FrontendRouteDefinition.class);
                routeDefinitions.put(def.routeKey(), def);
            } catch (Exception e) {
                log.warn("Failed to load builtin route: {}", routeMap.get("routeKey"), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildRouteMap(
            String routeKey, String path, String componentKey, String title,
            String menuGroup, String icon, Integer order, String parentRouteKey,
            List<String> requiredRoles, List<String> requiredPermissions,
            List<String> supportedSources) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("routeKey", routeKey);
        map.put("path", path);
        map.put("componentKey", componentKey);
        map.put("title", title);
        map.put("menuGroup", menuGroup);
        map.put("visible", true);
        map.put("enabled", true);
        if (icon != null) map.put("icon", icon);
        if (order != null) map.put("order", order);
        if (parentRouteKey != null) map.put("parentRouteKey", parentRouteKey);
        if (requiredRoles != null) map.put("requiredRoles", requiredRoles);
        if (requiredPermissions != null) map.put("requiredPermissions", requiredPermissions);
        if (supportedSources != null) map.put("supportedSources", supportedSources);
        return map;
    }

    private void loadBuiltinPolicies() {
        savePolicy(new NavigationPolicy(
                "pol-free-ai", "ai-features", "ENTITLEMENT",
                "tier=FREE", "DISABLE",
                "NAV-403-TIER", "AI features require a paid tier",
                List.of("STANDARD", "PROFESSIONAL"), 10, true
        ));
    }
}
