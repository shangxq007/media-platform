package com.example.platform.web;

import com.example.platform.audit.app.AuditService;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.identity.app.PermissionService;
import com.example.platform.identity.app.ProjectRepository;
import com.example.platform.identity.app.TenantRepository;
import com.example.platform.identity.domain.Project;
import com.example.platform.policy.featureflag.FeatureFlagService;
import com.example.platform.policy.featureflag.domain.FeatureFlagDefinition;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.web.collaboration.SharedResourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private static final Logger log = LoggerFactory.getLogger(MeController.class);

    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;
    private final EntitlementService entitlementService;
    private final EntitlementPolicyService entitlementPolicyService;
    private final FeatureFlagService featureFlagService;
    private final AuditService auditService;
    private final SharedResourceService sharedResourceService;

    public MeController(TenantRepository tenantRepository,
                        ProjectRepository projectRepository,
                        PermissionService permissionService,
                        EntitlementService entitlementService,
                        EntitlementPolicyService entitlementPolicyService,
                        FeatureFlagService featureFlagService,
                        AuditService auditService,
                        SharedResourceService sharedResourceService) {
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.permissionService = permissionService;
        this.entitlementService = entitlementService;
        this.entitlementPolicyService = entitlementPolicyService;
        this.featureFlagService = featureFlagService;
        this.auditService = auditService;
        this.sharedResourceService = sharedResourceService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(HttpServletRequest req) {
        CallerContext ctx = buildCallerContext(req);
        String tenantId = ctx.tenantId();
        String userId = ctx.userId();

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("tenantId", tenantId);
        dashboard.put("userId", userId != null ? maskUserId(userId) : "anonymous");
        dashboard.put("timestamp", Instant.now().toString());

        Map<String, Object> workspace = new LinkedHashMap<>();
        if (tenantId != null) {
            tenantRepository.findById(tenantId).ifPresent(t -> {
                workspace.put("id", t.id());
                workspace.put("name", t.name());
                workspace.put("status", t.status().name());
            });
        }
        workspace.put("role", resolveUserRole(userId, tenantId));
        dashboard.put("workspace", workspace);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        try {
            String effectiveTenant = tenantId != null ? tenantId : "tenant-1";
            String tier = entitlementPolicyService.getTier(effectiveTenant);
            capabilities.put("tier", tier);
            var policy = entitlementPolicyService.getPolicy(effectiveTenant);
            if (policy != null) {
                capabilities.put("monthlyRenderMinutes", policy.monthlyRenderMinutes());
                capabilities.put("maxConcurrentJobs", policy.maxConcurrentJobs());
                capabilities.put("gpuAllowed", policy.gpuAllowed());
                capabilities.put("remoteWorkerAllowed", policy.remoteWorkerAllowed());
                capabilities.put("customFontsAllowed", policy.customFontsAllowed());
                capabilities.put("watermark", policy.watermark());
                capabilities.put("allowedExportFormats", policy.exportFormats());
                capabilities.put("allowedPresets", List.of("720p", "1080p", "4k"));
            }
            var exportCaps = entitlementPolicyService.getExportCapabilities(effectiveTenant);
            if (exportCaps != null) {
                capabilities.put("exportFormats", exportCaps.allowedFormats());
                capabilities.put("exportPresets", exportCaps.allowedPresets());
                capabilities.put("maxExportResolutionWidth", exportCaps.maxResolutionWidth());
                capabilities.put("maxExportResolutionHeight", exportCaps.maxResolutionHeight());
                capabilities.put("gpuExportAllowed", exportCaps.gpuExportAllowed());
                capabilities.put("maxConcurrentExports", exportCaps.maxConcurrentExports());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve capabilities: {}", e.getMessage());
            capabilities.put("tier", "UNKNOWN");
        }
        dashboard.put("capabilities", capabilities);

        List<Map<String, Object>> featureFlags = new ArrayList<>();
        try {
            for (FeatureFlagDefinition flag : featureFlagService.listFlags()) {
                Map<String, Object> ff = new LinkedHashMap<>();
                ff.put("flagKey", flag.flagKey());
                ff.put("displayName", flag.name());
                ff.put("enabled", flag.enabled());
                ff.put("description", flag.description());
                featureFlags.add(ff);
            }
        } catch (Exception e) {
            log.warn("Failed to list feature flags: {}", e.getMessage());
        }
        dashboard.put("featureFlags", featureFlags);

        List<Map<String, Object>> recentProjects = new ArrayList<>();
        try {
            if (tenantId != null) {
                projectRepository.findByTenantId(tenantId).stream()
                        .sorted((a, b) -> compareProjectsByDate(a, b))
                        .limit(5)
                        .forEach(p -> recentProjects.add(projectToMap(p)));
            }
        } catch (Exception e) {
            log.warn("Failed to load recent projects: {}", e.getMessage());
        }
        dashboard.put("recentProjects", recentProjects);
        dashboard.put("quickActions", buildQuickActions(userId, tenantId));

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("period", java.time.YearMonth.now().toString());
        try {
            var policy = entitlementPolicyService.getPolicy(tenantId != null ? tenantId : "tenant-1");
            if (policy != null) {
                usage.put("renderMinutesUsed", 0);
                usage.put("renderMinutesLimit", policy.monthlyRenderMinutes());
                usage.put("storageGbUsed", 0);
                usage.put("storageGbLimit", 10);
                usage.put("apiCallsUsed", 0);
                usage.put("apiCallsLimit", 10000);
                usage.put("exportsUsed", 0);
                usage.put("exportsLimit", policy.maxConcurrentJobs() * 10);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve usage: {}", e.getMessage());
        }
        dashboard.put("usage", usage);

        Map<String, Object> onboarding = new LinkedHashMap<>();
        onboarding.put("hasProjects", !recentProjects.isEmpty());
        onboarding.put("hasCompletedProfile", userId != null && !userId.equals("anonymous"));
        onboarding.put("hasInvitedTeamMembers", false);
        onboarding.put("hasCompletedFirstExport", false);
        onboarding.put("hasSetBilling", false);
        dashboard.put("onboarding", onboarding);

        auditService.record("USER", userId, "VIEW_DASHBOARD", "DASHBOARD", "summary", null);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/projects")
    public ResponseEntity<Map<String, Object>> getProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.get();
        Map<String, Object> result = new LinkedHashMap<>();
        if (tenantId == null) {
            result.put("projects", Collections.emptyList());
            result.put("total", 0);
            result.put("page", page);
            result.put("size", size);
            return ResponseEntity.ok(result);
        }
        try {
            List<Project> allProjects = projectRepository.findByTenantId(tenantId);
            int total = allProjects.size();
            int start = Math.min(page * size, total);
            int end = Math.min(start + size, total);
            List<Map<String, Object>> projects = new ArrayList<>();
            for (int i = start; i < end; i++) {
                projects.add(projectToMap(allProjects.get(i)));
            }
            result.put("projects", projects);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
        } catch (Exception e) {
            log.warn("Failed to load projects: {}", e.getMessage());
            result.put("projects", Collections.emptyList());
            result.put("total", 0);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/shared-resources")
    public ResponseEntity<Map<String, Object>> getSharedResources(HttpServletRequest req) {
        CallerContext ctx = buildCallerContext(req);
        String tenantId = ctx.tenantId() != null ? ctx.tenantId() : TenantContext.get();
        String userId = ctx.userId();
        return ResponseEntity.ok(sharedResourceService.listSharedResources(tenantId, userId));
    }

    @GetMapping("/exports")
    public ResponseEntity<Map<String, Object>> getExports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exports", Collections.emptyList());
        result.put("total", 0);
        result.put("page", page);
        result.put("size", size);
        result.put("note", "EXPORTS_MODULE_STUB");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", Collections.emptyList());
        result.put("total", 0);
        result.put("page", page);
        result.put("size", size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notifications", Collections.emptyList());
        result.put("total", 0);
        result.put("page", page);
        result.put("size", size);
        result.put("unreadCount", 0);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Object>> markNotificationRead(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("id", id, "status", "READ"));
    }

    @GetMapping("/feedback")
    public ResponseEntity<Map<String, Object>> getFeedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feedback", Collections.emptyList());
        result.put("total", 0);
        result.put("page", page);
        result.put("size", size);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        CallerContext ctx = buildCallerContext(req);
        String userId = ctx.userId();
        String type = (String) body.getOrDefault("type", "GENERAL");
        String severity = (String) body.getOrDefault("severity", "low");
        String title = (String) body.get("title");
        String description = (String) body.get("description");

        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errorCode", "VALIDATION_ERROR",
                    "message", "Title is required",
                    "details", Map.of("field", "title")
            ));
        }

        String id = Ids.newId("fb");
        auditService.record("USER", userId, "SUBMIT_FEEDBACK", "FEEDBACK", id,
                Map.of("type", type, "severity", severity));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("type", type);
        result.put("severity", severity);
        result.put("status", "OPEN");
        result.put("title", title);
        result.put("createdAt", Instant.now().toString());
        return ResponseEntity.ok(result);
    }

    private CallerContext buildCallerContext(HttpServletRequest req) {
        String path = req.getRequestURI();
        String source = path.startsWith("/api/v1/mcp/") ? CallerContext.SOURCE_MCP : CallerContext.SOURCE_WEB;
        String authType = source.equals(CallerContext.SOURCE_MCP) ? CallerContext.AUTH_API_KEY : CallerContext.AUTH_JWT;
        Object subject = req.getAttribute("jwt.subject");
        String userId = subject != null ? subject.toString() : "anonymous";
        Object tenantAttr = req.getAttribute("jwt.tenantId");
        String tenantId = tenantAttr != null ? tenantAttr.toString() : TenantContext.get();
        return new CallerContext(source, userId, tenantId, authType, null);
    }

    private String resolveUserRole(String userId, String tenantId) {
        if (userId == null || tenantId == null || "anonymous".equals(userId)) return "VIEWER";
        try {
            Set<String> permissions = permissionService.resolvePermissions(userId, tenantId);
            if (permissions.contains("ADMIN")) return "ADMIN";
            if (permissions.contains("WRITE")) return "MEMBER";
            return "VIEWER";
        } catch (Exception e) {
            return "VIEWER";
        }
    }

    private List<Map<String, Object>> buildQuickActions(String userId, String tenantId) {
        List<Map<String, Object>> actions = new ArrayList<>();
        boolean canAdmin = false;
        try {
            if (userId != null && tenantId != null && !"anonymous".equals(userId)) {
                Set<String> perms = permissionService.resolvePermissions(userId, tenantId);
                canAdmin = perms.contains("ADMIN") || perms.contains("MEMBER_MANAGE");
            }
        } catch (Exception e) {
            log.debug("Failed to check admin permission: {}", e.getMessage());
        }
        actions.add(Map.of("key", "new_project", "label", "New Project", "icon", "➕", "path", "/project/new", "enabled", true, "visible", true));
        actions.add(Map.of("key", "upload_media", "label", "Upload Media", "icon", "📁", "path", "/", "enabled", true, "visible", true));
        actions.add(Map.of("key", "try_demo", "label", "Try Demo", "icon", "🎬", "path", "/?demo=true", "enabled", true, "visible", true));
        actions.add(Map.of("key", "invite_team", "label", "Invite Team", "icon", "👥", "path", "/workspace/current/members", "enabled", canAdmin, "visible", true, "disabledReason", canAdmin ? null : "ADMIN_PERMISSION_REQUIRED"));
        actions.add(Map.of("key", "view_reports", "label", "View Reports", "icon", "📊", "path", "/me/reports", "enabled", true, "visible", true));
        actions.add(Map.of("key", "manage_billing", "label", "Manage Billing", "icon", "💳", "path", "/me/billing", "enabled", canAdmin, "visible", true, "disabledReason", canAdmin ? null : "ADMIN_PERMISSION_REQUIRED"));
        return actions;
    }

    private Map<String, Object> projectToMap(Project p) {
        Map<String, Object> proj = new LinkedHashMap<>();
        proj.put("id", p.id());
        proj.put("name", p.name());
        proj.put("description", p.description() != null ? p.description() : "");
        proj.put("tenantId", p.tenantId());
        proj.put("status", p.status().name());
        proj.put("createdAt", p.createdAt() != null ? p.createdAt().toString() : null);
        return proj;
    }

    private int compareProjectsByDate(Project a, Project b) {
        if (a.createdAt() == null && b.createdAt() == null) return 0;
        if (a.createdAt() == null) return 1;
        if (b.createdAt() == null) return -1;
        return b.createdAt().compareTo(a.createdAt());
    }

    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 4) return "***";
        return userId.substring(0, 2) + "***" + userId.substring(userId.length() - 2);
    }
}
