package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.PlatformException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FeatureFlagController {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagController.class);

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagAuditService auditService;
    private final AuthoritativeWorkspaceScope workspaceScope;

    public FeatureFlagController(FeatureFlagService featureFlagService,
                                  FeatureFlagAuditService auditService) {
        this(featureFlagService, auditService, null);
    }

    @Autowired
    public FeatureFlagController(FeatureFlagService featureFlagService,
                                  FeatureFlagAuditService auditService,
                                  AuthoritativeWorkspaceScope workspaceScope) {
        this.featureFlagService = featureFlagService;
        this.auditService = auditService;
        this.workspaceScope = workspaceScope;
    }

    @PostMapping("/admin/feature-flags")
    public ResponseEntity<FeatureFlagDefinition> createFlag(@Valid @RequestBody CreateFlagRequest request) {
        checkAdminAccess();
        checkAdminRole();
        FeatureFlagDefinition definition = new FeatureFlagDefinition(
                request.flagKey(), request.name(), request.description(),
                request.flagType(), request.defaultValue(),
                request.variants() != null ? request.variants() : List.of(),
                request.targetingRules() != null ? request.targetingRules() : List.of(),
                request.enabled() != null ? request.enabled() : true,
                request.owner(), request.tags() != null ? request.tags() : List.of(),
                Instant.now(), Instant.now(), false
        );
        FeatureFlagDefinition created = featureFlagService.createFlag(definition);
        auditService.auditFlagCreated(created, getCurrentActor());
        log.info("FeatureFlagController: created flag '{}'", created.flagKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/admin/feature-flags")
    public ResponseEntity<List<FeatureFlagDefinition>> listFlags() {
        checkAdminAccess();
        checkAdminRole();
        return ResponseEntity.ok(featureFlagService.listFlags());
    }

    @GetMapping("/admin/feature-flags/{flagKey}")
    public ResponseEntity<FeatureFlagDefinition> getFlag(@PathVariable String flagKey) {
        checkAdminAccess();
        checkAdminRole();
        return featureFlagService.getFlag(flagKey)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new PlatformException(
                        new ConfigurableErrorCode("FF-404-001", 404701,
                                Map.of("en", "Feature flag not found", "zh", "功能标志不存在"),
                                "feature-flag", 404),
                        "Flag not found: " + flagKey,
                        Map.of("flagKey", flagKey), "en"));
    }

    @PutMapping("/admin/feature-flags/{flagKey}")
    public ResponseEntity<FeatureFlagDefinition> updateFlag(
            @PathVariable String flagKey,
            @Valid @RequestBody CreateFlagRequest request) {
        checkAdminAccess();
        checkAdminRole();
        FeatureFlagDefinition existing = featureFlagService.getFlag(flagKey)
                .orElseThrow(() -> notFound(flagKey));
        FeatureFlagDefinition updated = featureFlagService.updateFlag(flagKey, new FeatureFlagDefinition(
                flagKey, request.name(), request.description(),
                request.flagType(), request.defaultValue(),
                request.variants() != null ? request.variants() : List.of(),
                request.targetingRules() != null ? request.targetingRules() : List.of(),
                request.enabled() != null ? request.enabled() : existing.enabled(),
                request.owner(), request.tags() != null ? request.tags() : List.of(),
                existing.createdAt(), Instant.now(), existing.archived()
        ));
        auditService.auditFlagUpdated(flagKey, existing, updated, getCurrentActor());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/admin/feature-flags/{flagKey}/archive")
    public ResponseEntity<FeatureFlagDefinition> archiveFlag(@PathVariable String flagKey) {
        checkAdminAccess();
        checkAdminRole();
        FeatureFlagDefinition archived = featureFlagService.archiveFlag(flagKey);
        auditService.auditFlagArchived(flagKey, getCurrentActor());
        return ResponseEntity.ok(archived);
    }

    @PostMapping("/admin/feature-flags/{flagKey}/enable")
    public ResponseEntity<FeatureFlagDefinition> enableFlag(@PathVariable String flagKey) {
        checkAdminAccess();
        checkAdminRole();
        FeatureFlagDefinition enabled = featureFlagService.enableFlag(flagKey);
        auditService.auditFlagEnabled(flagKey, getCurrentActor());
        return ResponseEntity.ok(enabled);
    }

    @PostMapping("/admin/feature-flags/{flagKey}/disable")
    public ResponseEntity<FeatureFlagDefinition> disableFlag(@PathVariable String flagKey) {
        checkAdminAccess();
        checkAdminRole();
        FeatureFlagDefinition disabled = featureFlagService.disableFlag(flagKey);
        auditService.auditFlagDisabled(flagKey, getCurrentActor());
        return ResponseEntity.ok(disabled);
    }

    @PostMapping("/admin/feature-flags/{flagKey}/rules")
    public ResponseEntity<Map<String, Object>> addRule(
            @PathVariable String flagKey,
            @RequestBody FeatureFlagTargetingRule rule) {
        checkAdminAccess();
        checkAdminRole();
        if (rule.ruleId() == null) {
            throw new PlatformException(
                    new ConfigurableErrorCode("FF-400-001", 400701,
                            Map.of("en", "Rule ID is required", "zh", "规则ID是必需的"),
                            "feature-flag", 400),
                    "Rule ID is required",
                    Map.of("flagKey", flagKey), "en");
        }
        featureFlagService.addTargetingRule(flagKey, rule);
        auditService.auditRuleCreated(flagKey, rule, getCurrentActor());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "created", "flagKey", flagKey, "ruleId", rule.ruleId()));
    }

    @GetMapping("/admin/feature-flags/{flagKey}/evaluations")
    public ResponseEntity<List<FeatureFlagAuditService.FeatureFlagAuditEvent>> getFlagEvaluations(
            @PathVariable String flagKey,
            @RequestParam(defaultValue = "50") int limit) {
        checkAdminAccess();
        checkAdminRole();
        return ResponseEntity.ok(auditService.getEventsByFlag(flagKey));
    }

    @GetMapping("/me/feature-flags")
    public ResponseEntity<List<FeatureFlagDefinition>> getMyFlags(HttpServletRequest request) {
        FeatureFlagContext context = buildCurrentContext(request, null);
        List<FeatureFlagDefinition> flags = featureFlagService.getFlagsForContext(context);
        return ResponseEntity.ok(flags);
    }

    @PostMapping("/feature-flags/evaluate")
    public ResponseEntity<FeatureFlagEvaluationResult> evaluateFlag(
            @RequestBody FeatureFlagEvaluationRequest request,
            HttpServletRequest servletRequest) {
        try {
            // Browser supplied tenant/workspace/user fields are intentionally ignored.
            // Scope is reconstructed from the authenticated server request boundary.
            rejectForgedTenantOrUserScope(request.context(), servletRequest);
            FeatureFlagEvaluationRequest authoritative = new FeatureFlagEvaluationRequest(
                    request.flagKey(), buildCurrentContext(servletRequest, request.context()), request.defaultValue());
            FeatureFlagEvaluationResult result = featureFlagService.evaluate(authoritative);
            auditService.auditEvaluated(result.decision(), getCurrentActor(servletRequest));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            auditService.auditEvaluationFailed(
                    request.flagKey(), "FF-EVAL-001", e.getMessage(), getCurrentActor(servletRequest));
            throw e;
        }
    }

    @PostMapping("/feature-flags/batch-evaluate")
    public ResponseEntity<List<FeatureFlagEvaluationResult>> batchEvaluate(
            @RequestBody List<FeatureFlagEvaluationRequest> requests,
            HttpServletRequest servletRequest) {
        requests.forEach(r -> rejectForgedTenantOrUserScope(r.context(), servletRequest));
        FeatureFlagContext authoritativeContext = buildCurrentContext(servletRequest, null);
        List<FeatureFlagEvaluationRequest> authoritative = requests.stream()
                .map(r -> new FeatureFlagEvaluationRequest(r.flagKey(), authoritativeContext, r.defaultValue()))
                .toList();
        List<FeatureFlagEvaluationResult> results = featureFlagService.evaluateBatch(authoritative);
        results.forEach(r -> auditService.auditEvaluated(r.decision(), getCurrentActor(servletRequest)));
        return ResponseEntity.ok(results);
    }

    private String getCurrentActor() {
        return "system";
    }

    private void checkAdminAccess() {
    }

    private void checkAdminRole() {
    }

    private String getCurrentActor(HttpServletRequest request) {
        Object principal = request.getAttribute("jwt.subject");
        return principal == null || String.valueOf(principal).isBlank() ? "system" : String.valueOf(principal);
    }

    private FeatureFlagContext buildCurrentContext(HttpServletRequest request, FeatureFlagContext supplied) {
        String tenantId = TenantContext.get();
        Object subject = request.getAttribute("auth.subject");
        Object role = request.getAttribute("jwt.roles");
        String userId = subject == null ? null : String.valueOf(subject);
        List<String> roles = role instanceof List<?> values ? values.stream().map(String::valueOf).toList() : List.of();
        String workspaceId = request.getHeader("X-Workspace-Id");
        if (workspaceId != null && !workspaceId.isBlank()
                && (workspaceScope == null || !workspaceScope.isActiveMember(tenantId, workspaceId, userId))) {
            throw forbidden("Workspace is not an active member scope");
        }
        if (supplied != null && supplied.workspaceId() != null && !supplied.workspaceId().isBlank()
                && !supplied.workspaceId().equals(workspaceId)) {
            throw forbidden("Workspace scope must match the server-selected workspace");
        }
        return new FeatureFlagContext(tenantId, workspaceId, userId, roles, List.of(),
                null, "server", null, null, null, Map.of());
    }

    private void rejectForgedTenantOrUserScope(FeatureFlagContext supplied, HttpServletRequest request) {
        if (supplied == null) return;
        String tenant = TenantContext.get();
        Object subject = request.getAttribute("auth.subject");
        String user = subject == null ? null : String.valueOf(subject);
        if (supplied.tenantId() != null && !supplied.tenantId().equals(tenant)) {
            throw new PlatformException(new ConfigurableErrorCode("SECURITY-403-001", 403001,
                    Map.of("en", "Forbidden", "zh", "无权访问"), "security", 403),
                    "Tenant scope does not match authenticated identity", Map.of(), "en");
        }
        if (supplied.userId() != null && !supplied.userId().equals(user)) {
            throw new PlatformException(new ConfigurableErrorCode("SECURITY-403-001", 403001,
                    Map.of("en", "Forbidden", "zh", "无权访问"), "security", 403),
                    "User scope does not match authenticated identity", Map.of(), "en");
        }
        String selectedWorkspace = request.getHeader("X-Workspace-Id");
        if (supplied.workspaceId() != null && !supplied.workspaceId().isBlank()
                && (selectedWorkspace == null || !supplied.workspaceId().equals(selectedWorkspace)))
            throw forbidden("Workspace scope must match the server-selected workspace");
    }

    private static PlatformException forbidden(String message) {
        return new PlatformException(new ConfigurableErrorCode("SECURITY-403-001", 403001,
                Map.of("en", "Forbidden", "zh", "无权访问"), "security", 403),
                message, Map.of(), "en");
    }

    private PlatformException notFound(String flagKey) {
        return new PlatformException(
                new ConfigurableErrorCode("FF-404-001", 404701,
                        Map.of("en", "Feature flag not found", "zh", "功能标志不存在"),
                        "feature-flag", 404),
                "Flag not found: " + flagKey,
                Map.of("flagKey", flagKey), "en");
    }

    public record CreateFlagRequest(
            @NotBlank String flagKey,
            String name,
            String description,
            @NotNull FeatureFlagType flagType,
            Object defaultValue,
            List<FeatureFlagVariant> variants,
            List<FeatureFlagTargetingRule> targetingRules,
            Boolean enabled,
            String owner,
            List<String> tags
    ) {}
}
