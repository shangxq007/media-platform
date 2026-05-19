package com.example.platform.policy.featureflag;

import com.example.platform.policy.featureflag.domain.*;
import com.example.platform.policy.featureflag.FeatureFlagService;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class FeatureFlagController {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagController.class);

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagAuditService auditService;

    public FeatureFlagController(FeatureFlagService featureFlagService,
                                  FeatureFlagAuditService auditService) {
        this.featureFlagService = featureFlagService;
        this.auditService = auditService;
    }

    @PostMapping("/admin/feature-flags")
    public ResponseEntity<FeatureFlagDefinition> createFlag(@RequestBody CreateFlagRequest request) {
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
            @RequestBody CreateFlagRequest request) {
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
    public ResponseEntity<List<FeatureFlagDefinition>> getMyFlags() {
        FeatureFlagContext context = buildCurrentContext();
        List<FeatureFlagDefinition> flags = featureFlagService.getFlagsForContext(context);
        return ResponseEntity.ok(flags);
    }

    @PostMapping("/feature-flags/evaluate")
    public ResponseEntity<FeatureFlagEvaluationResult> evaluateFlag(
            @RequestBody FeatureFlagEvaluationRequest request) {
        try {
            FeatureFlagEvaluationResult result = featureFlagService.evaluate(request);
            auditService.auditEvaluated(result.decision(), getCurrentActor());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            auditService.auditEvaluationFailed(
                    request.flagKey(), "FF-EVAL-001", e.getMessage(), getCurrentActor());
            throw e;
        }
    }

    @PostMapping("/feature-flags/batch-evaluate")
    public ResponseEntity<List<FeatureFlagEvaluationResult>> batchEvaluate(
            @RequestBody List<FeatureFlagEvaluationRequest> requests) {
        List<FeatureFlagEvaluationResult> results = featureFlagService.evaluateBatch(requests);
        results.forEach(r -> auditService.auditEvaluated(r.decision(), getCurrentActor()));
        return ResponseEntity.ok(results);
    }

    private void checkAdminAccess() {
    }

    private void checkAdminRole() {
    }

    private String getCurrentActor() {
        return "system";
    }

    private FeatureFlagContext buildCurrentContext() {
        return new FeatureFlagContext(null, null, null, List.of(), List.of(),
                null, null, null, null, null, Map.of());
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
            String flagKey,
            String name,
            String description,
            FeatureFlagType flagType,
            Object defaultValue,
            List<FeatureFlagVariant> variants,
            List<FeatureFlagTargetingRule> targetingRules,
            Boolean enabled,
            String owner,
            List<String> tags
    ) {}
}
