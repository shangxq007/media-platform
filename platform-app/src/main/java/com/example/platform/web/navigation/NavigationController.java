package com.example.platform.web.navigation;

import com.example.platform.identity.app.PermissionService;
import com.example.platform.web.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Navigation API", description = "Dynamic navigation and route visibility evaluation")
public class NavigationController {

    private static final Logger log = LoggerFactory.getLogger(NavigationController.class);

    private final NavigationDecisionService decisionService;
    private final MenuCompositionService menuCompositionService;
    private final PermissionService permissionService;

    public NavigationController(NavigationDecisionService decisionService,
                                 MenuCompositionService menuCompositionService,
                                 PermissionService permissionService) {
        this.decisionService = decisionService;
        this.menuCompositionService = menuCompositionService;
        this.permissionService = permissionService;
    }

    @GetMapping("/me/navigation")
    @Operation(summary = "Get navigation profile for current user",
               description = "Returns visible routes grouped by menu group, filtered by user context")
    public ResponseEntity<NavigationDecisionService.NavigationProfile> getNavigation(HttpServletRequest req) {
        CallerContext ctx = buildCallerContext(req);
        Set<String> roles = resolveRoles(req);
        Set<String> permissions = resolvePermissions(ctx);

        NavigationDecisionService.NavigationProfile profile = menuCompositionService.composeMenu(
                ctx.userId(), ctx.tenantId(), ctx.source(),
                null, roles, permissions, Set.of());

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/me/routes")
    @Operation(summary = "Get route visibility decisions for current user",
               description = "Returns all routes with visibility/enablement decisions")
    public ResponseEntity<List<NavigationDecisionService.RouteVisibilityDecision>> getRoutes(HttpServletRequest req) {
        CallerContext ctx = buildCallerContext(req);
        Set<String> roles = resolveRoles(req);
        Set<String> permissions = resolvePermissions(ctx);

        List<NavigationDecisionService.RouteVisibilityDecision> decisions = decisionService.evaluateRoutes(
                ctx.userId(), ctx.tenantId(), ctx.source(),
                null, roles, permissions, Set.of());

        return ResponseEntity.ok(decisions);
    }

    @PostMapping("/navigation/preview")
    @Operation(summary = "Preview navigation for a given user context",
               description = "Admin-only: preview how navigation would appear for a specific user/role/tier combination")
    public ResponseEntity<NavigationDecisionService.NavigationProfile> previewNavigation(
            @RequestBody PreviewRequest request,
            HttpServletRequest req) {
        CallerContext ctx = buildCallerContext(req);

        Set<String> roles = request.roles() != null ? new HashSet<>(request.roles()) : resolveRoles(req);
        Set<String> permissions = request.permissions() != null ? new HashSet<>(request.permissions()) : resolvePermissions(ctx);
        Set<String> features = request.features() != null ? new HashSet<>(request.features()) : Set.of();

        NavigationDecisionService.NavigationProfile profile = decisionService.buildProfile(
                request.userId() != null ? request.userId() : ctx.userId(),
                request.tenantId() != null ? request.tenantId() : ctx.tenantId(),
                request.source() != null ? request.source() : ctx.source(),
                request.tier(), roles, permissions, features);

        return ResponseEntity.ok(profile);
    }

    private CallerContext buildCallerContext(HttpServletRequest req) {
        String path = req.getRequestURI();
        String source = path.startsWith("/api/v1/mcp/") ? CallerContext.SOURCE_MCP : CallerContext.SOURCE_WEB;
        String authType = source.equals(CallerContext.SOURCE_MCP) ? CallerContext.AUTH_API_KEY : CallerContext.AUTH_JWT;
        String userId = getUserId(req, source);
        String tenantId = getTenantId(req, source);
        String traceId = MDC.get("traceId");
        return new CallerContext(source, userId, tenantId, authType, traceId);
    }

    private String getUserId(HttpServletRequest req, String source) {
        Object subject = req.getAttribute("jwt.subject");
        return subject != null ? subject.toString() : "anonymous";
    }

    private String getTenantId(HttpServletRequest req, String source) {
        Object tenantId = req.getAttribute("jwt.tenantId");
        return tenantId != null ? tenantId.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private Set<String> resolveRoles(HttpServletRequest req) {
        Object rolesAttr = req.getAttribute("jwt.roles");
        if (rolesAttr instanceof List<?> rolesList) {
            return new HashSet<>(rolesList.stream().map(Object::toString).toList());
        }
        return Set.of();
    }

    private Set<String> resolvePermissions(CallerContext ctx) {
        if (ctx.userId() == null || ctx.userId().equals("anonymous")) return Set.of();
        try {
            return permissionService.resolvePermissions(ctx.userId(), "default-workspace");
        } catch (Exception e) {
            log.debug("Failed to resolve permissions for user {}: {}", ctx.userId(), e.getMessage());
            return Set.of();
        }
    }

    public record PreviewRequest(
            String userId,
            String tenantId,
            String source,
            String tier,
            List<String> roles,
            List<String> permissions,
            List<String> features,
            List<String> entitlements
    ) {}
}
