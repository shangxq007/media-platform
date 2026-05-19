package com.example.platform.web.navigation;

import com.example.platform.shared.Ids;
import com.example.platform.web.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/navigation")
@Tag(name = "Route Management API", description = "Admin API for managing frontend route definitions and navigation policies")
public class RouteManagementController {

    private static final Logger log = LoggerFactory.getLogger(RouteManagementController.class);

    private final NavigationRegistryService registryService;
    private final NavigationDecisionService decisionService;

    public RouteManagementController(NavigationRegistryService registryService,
                                      NavigationDecisionService decisionService) {
        this.registryService = registryService;
        this.decisionService = decisionService;
    }

    @GetMapping("/routes")
    @Operation(summary = "List all route definitions")
    public ResponseEntity<List<FrontendRouteDefinition>> listRoutes() {
        return ResponseEntity.ok(registryService.loadAllRouteDefinitions());
    }

    @PostMapping("/routes")
    @Operation(summary = "Create a new route definition")
    public ResponseEntity<FrontendRouteDefinition> createRoute(@RequestBody RouteDefinitionRequest request) {
        FrontendRouteDefinition def = new FrontendRouteDefinition(
                request.routeKey(), request.path(), request.componentKey(),
                request.title(), request.description(), request.menuGroup(),
                request.icon(), request.order(), request.parentRouteKey(),
                request.requiredPermissions(), request.requiredRoles(),
                request.requiredEntitlements(), request.requiredTier(),
                request.requiredFeatures(), request.supportedSources(),
                request.visible() != null ? request.visible() : true,
                request.enabled() != null ? request.enabled() : true,
                null, null, null
        );
        registryService.saveRoute(def);
        return ResponseEntity.status(HttpStatus.CREATED).body(def);
    }

    @PutMapping("/routes/{routeKey}")
    @Operation(summary = "Update an existing route definition")
    public ResponseEntity<FrontendRouteDefinition> updateRoute(
            @PathVariable String routeKey,
            @RequestBody RouteDefinitionRequest request) {
        FrontendRouteDefinition existing = registryService.getRouteByKey(routeKey)
                .orElseThrow(() -> new IllegalArgumentException("Route not found: " + routeKey));

        FrontendRouteDefinition updated = new FrontendRouteDefinition(
                routeKey,
                request.path() != null ? request.path() : existing.path(),
                request.componentKey() != null ? request.componentKey() : existing.componentKey(),
                request.title() != null ? request.title() : existing.title(),
                request.description() != null ? request.description() : existing.description(),
                request.menuGroup() != null ? request.menuGroup() : existing.menuGroup(),
                request.icon() != null ? request.icon() : existing.icon(),
                request.order() != null ? request.order() : existing.order(),
                request.parentRouteKey() != null ? request.parentRouteKey() : existing.parentRouteKey(),
                request.requiredPermissions() != null ? request.requiredPermissions() : existing.requiredPermissions(),
                request.requiredRoles() != null ? request.requiredRoles() : existing.requiredRoles(),
                request.requiredEntitlements() != null ? request.requiredEntitlements() : existing.requiredEntitlements(),
                request.requiredTier() != null ? request.requiredTier() : existing.requiredTier(),
                request.requiredFeatures() != null ? request.requiredFeatures() : existing.requiredFeatures(),
                request.supportedSources() != null ? request.supportedSources() : existing.supportedSources(),
                request.visible() != null ? request.visible() : existing.visible(),
                request.enabled() != null ? request.enabled() : existing.enabled(),
                existing.hiddenReason(),
                existing.disabledReason(),
                existing.upgradeOptions()
        );
        registryService.saveRoute(updated);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/routes/{routeKey}/disable")
    @Operation(summary = "Disable a route")
    public ResponseEntity<Map<String, Object>> disableRoute(@PathVariable String routeKey) {
        FrontendRouteDefinition existing = registryService.getRouteByKey(routeKey)
                .orElseThrow(() -> new IllegalArgumentException("Route not found: " + routeKey));

        FrontendRouteDefinition updated = new FrontendRouteDefinition(
                existing.routeKey(), existing.path(), existing.componentKey(),
                existing.title(), existing.description(), existing.menuGroup(),
                existing.icon(), existing.order(), existing.parentRouteKey(),
                existing.requiredPermissions(), existing.requiredRoles(),
                existing.requiredEntitlements(), existing.requiredTier(),
                existing.requiredFeatures(), existing.supportedSources(),
                existing.visible(), false, existing.hiddenReason(),
                "Disabled by administrator", existing.upgradeOptions()
        );
        registryService.saveRoute(updated);
        return ResponseEntity.ok(Map.of("routeKey", routeKey, "enabled", false));
    }

    @PostMapping("/routes/{routeKey}/enable")
    @Operation(summary = "Enable a route")
    public ResponseEntity<Map<String, Object>> enableRoute(@PathVariable String routeKey) {
        FrontendRouteDefinition existing = registryService.getRouteByKey(routeKey)
                .orElseThrow(() -> new IllegalArgumentException("Route not found: " + routeKey));

        FrontendRouteDefinition updated = new FrontendRouteDefinition(
                existing.routeKey(), existing.path(), existing.componentKey(),
                existing.title(), existing.description(), existing.menuGroup(),
                existing.icon(), existing.order(), existing.parentRouteKey(),
                existing.requiredPermissions(), existing.requiredRoles(),
                existing.requiredEntitlements(), existing.requiredTier(),
                existing.requiredFeatures(), existing.supportedSources(),
                existing.visible(), true, existing.hiddenReason(),
                null, existing.upgradeOptions()
        );
        registryService.saveRoute(updated);
        return ResponseEntity.ok(Map.of("routeKey", routeKey, "enabled", true));
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview navigation for a given context",
               description = "Admin-only: see how navigation would appear for a specific user context")
    public ResponseEntity<NavigationDecisionService.NavigationProfile> previewNavigation(
            @RequestBody NavigationController.PreviewRequest request,
            HttpServletRequest req) {
        CallerContext ctx = buildCallerContext(req);

        Set<String> roles = request.roles() != null ? new HashSet<>(request.roles()) : Set.of();
        Set<String> permissions = request.permissions() != null ? new HashSet<>(request.permissions()) : Set.of();
        Set<String> features = request.features() != null ? new HashSet<>(request.features()) : Set.of();

        NavigationDecisionService.NavigationProfile profile = decisionService.buildProfile(
                request.userId() != null ? request.userId() : ctx.userId(),
                request.tenantId() != null ? request.tenantId() : ctx.tenantId(),
                request.source() != null ? request.source() : ctx.source(),
                request.tier(), roles, permissions, features);

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/policies")
    @Operation(summary = "List all navigation policies")
    public ResponseEntity<List<NavigationPolicy>> listPolicies() {
        return ResponseEntity.ok(new ArrayList<>(registryService.loadAllPolicies().values()));
    }

    private CallerContext buildCallerContext(HttpServletRequest req) {
        String source = "ADMIN";
        String authType = CallerContext.AUTH_JWT;
        String userId = getUserId(req);
        String tenantId = getTenantId(req);
        String traceId = MDC.get("traceId");
        return new CallerContext(source, userId, tenantId, authType, traceId);
    }

    private String getUserId(HttpServletRequest req) {
        Object subject = req.getAttribute("jwt.subject");
        return subject != null ? subject.toString() : "anonymous";
    }

    private String getTenantId(HttpServletRequest req) {
        Object tenantId = req.getAttribute("jwt.tenantId");
        return tenantId != null ? tenantId.toString() : null;
    }

    public record RouteDefinitionRequest(
            String routeKey,
            String path,
            String componentKey,
            String title,
            String description,
            String menuGroup,
            String icon,
            Integer order,
            String parentRouteKey,
            List<String> requiredPermissions,
            List<String> requiredRoles,
            List<String> requiredEntitlements,
            String requiredTier,
            List<String> requiredFeatures,
            List<String> supportedSources,
            Boolean visible,
            Boolean enabled
    ) {}
}
