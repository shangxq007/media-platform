package com.example.platform.extension.api;

import com.example.platform.extension.api.dto.*;
import com.example.platform.extension.app.*;
import com.example.platform.extension.domain.*;
import com.example.platform.extension.domain.ExtensionAuditEvent.EventType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/extensions")
public class ExtensionController {

    private final ExtensionRegistryService registryService;
    private final ExtensionCatalogService extensionCatalogService;
    private final ToolRunner toolRunner;
    private final CliToolInvocationService cliToolInvocationService;
    private final ExtensionRouter router;
    private final ExtensionResourceLimiter resourceLimiter;
    private final ExtensionAuditService auditService;

    public ExtensionController(
            ExtensionRegistryService registryService,
            ExtensionCatalogService extensionCatalogService,
            ToolRunner toolRunner,
            CliToolInvocationService cliToolInvocationService,
            ExtensionRouter router,
            ExtensionResourceLimiter resourceLimiter,
            ExtensionAuditService auditService) {
        this.registryService = registryService;
        this.extensionCatalogService = extensionCatalogService;
        this.toolRunner = toolRunner;
        this.cliToolInvocationService = cliToolInvocationService;
        this.router = router;
        this.resourceLimiter = resourceLimiter;
        this.auditService = auditService;
    }

    @GetMapping
    public List<String> list() {
        return extensionCatalogService.extensionCodes();
    }

    @GetMapping("/catalog")
    public List<ExtensionRegistryService.ExtensionInfo> listExtensions() {
        return registryService.listExtensions();
    }

    @GetMapping("/{key}")
    public ResponseEntity<ExtensionRegistryService.ExtensionInfo> getExtension(@PathVariable String key) {
        return registryService.getExtension(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{key}/history")
    public ResponseEntity<List<ExtensionRegistryService.ExtensionVersionRecord>> getVersionHistory(
            @PathVariable String key) {
        return ResponseEntity.ok(registryService.getVersionHistory(key));
    }

    @PostMapping("/{key}/execute")
    public ResponseEntity<?> executeExtension(
            @PathVariable String key,
            @RequestBody ExecuteExtensionRequest request) {
        try {
            ExtensionResult result = registryService.executeProvider(
                    key, request.inputJson(), request.tenantId(), request.userId());
            if (result.success()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "output", result.outputJson(),
                        "metrics", result.metrics()));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "errorCode", result.errorCode(),
                        "errorMessage", result.errorMessage()));
            }
        } catch (ExtensionExecutionException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "errorCode", e.getErrorCode(),
                    "errorMessage", e.getMessage()));
        }
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> unloadExtension(
            @PathVariable String key,
            @RequestParam(defaultValue = "system") String unloadedBy) {
        boolean unloaded = registryService.unloadExtension(key, unloadedBy);
        if (unloaded) {
            return ResponseEntity.ok(Map.of("status", "unloaded", "key", key));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{key}/rollback")
    public ResponseEntity<?> rollbackExtension(
            @PathVariable String key,
            @RequestBody RollbackRequest request) {
        boolean rolledBack = registryService.rollbackExtension(
                key, request.targetVersion(), request.rolledBackBy());
        if (rolledBack) {
            return ResponseEntity.ok(Map.of(
                    "status", "rolled_back",
                    "key", key,
                    "targetVersion", request.targetVersion()));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Rollback failed - version not found in history"));
    }

    @PostMapping("/{key}/rollback-point")
    public ResponseEntity<?> createRollbackPoint(
            @PathVariable String key,
            @RequestParam(defaultValue = "system") String createdBy) {
        RollbackPoint point = registryService.createRollbackPoint(key, createdBy);
        if (point != null) {
            return ResponseEntity.ok(Map.of(
                    "id", point.id(),
                    "extensionCode", point.extensionCode(),
                    "version", point.version(),
                    "createdAt", point.createdAt().toString()));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/tool-run")
    public Map<String, Object> runTool(@RequestBody ToolRunRequest request) {
        var result = toolRunner.run(request);
        return Map.of("exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr());
    }

    @GetMapping("/cli-tools")
    public Map<String, List<String>> listCliTools() {
        return Map.of("tools", cliToolInvocationService.listToolKeys());
    }

    @PostMapping("/cli-tools/{toolKey}/run")
    public Map<String, Object> runCliTool(
            @PathVariable String toolKey, @RequestBody(required = false) CliToolRunBody body) {
        Map<String, String> params = body != null && body.params() != null ? body.params() : Map.of();
        var result = cliToolInvocationService.run(toolKey, params);
        return Map.of("exitCode", result.exitCode(), "stdout", result.stdout(), "stderr", result.stderr());
    }

    @GetMapping("/{key}/resource-limits")
    public Map<String, Object> getResourceLimits(@PathVariable String key) {
        return resourceLimiter.getUsageStats(key);
    }

    @GetMapping("/{key}/routing-rules")
    public List<RoutingRule> getRoutingRules(@PathVariable String key) {
        return router.getRules(key);
    }

    @PostMapping("/{key}/routing-rules")
    public ResponseEntity<RoutingRule> createRoutingRule(
            @PathVariable String key,
            @RequestBody CreateRoutingRuleRequest request) {
        RoutingRule rule = router.createRule(
                request.ruleName(), key, request.sourceVersion(),
                request.targetVersion(), request.tenantId(), request.userId(),
                request.scene(), request.priority(), request.trafficPercent(),
                request.createdBy());
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @GetMapping("/{key}/audit-events")
    public List<ExtensionAuditEvent> getAuditEvents(@PathVariable String key) {
        return auditService.getEventsByExtension(key);
    }

    @GetMapping("/audit-events/recent")
    public List<ExtensionAuditEvent> getRecentAuditEvents(
            @RequestParam(defaultValue = "50") int limit) {
        return auditService.getRecentEvents(limit);
    }
}
