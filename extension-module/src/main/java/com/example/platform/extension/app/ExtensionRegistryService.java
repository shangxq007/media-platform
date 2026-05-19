package com.example.platform.extension.app;

import com.example.platform.extension.domain.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExtensionRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRegistryService.class);

    private final AuditPort auditPort;
    private final SandboxExecutionService sandboxExecutionService;
    private final ExtensionAuditService auditService;
    private final ExtensionResourceLimiter resourceLimiter;
    private final ExtensionRouter router;

    private final ConcurrentHashMap<String, ExtensionHolder> providerExtensions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExtensionHolder> promptExtensions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExtensionHolder> workflowStepExtensions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ExtensionVersionRecord>> extensionHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RollbackPoint> rollbackPoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> spiInstances = new ConcurrentHashMap<>();

    public ExtensionRegistryService(AuditPort auditPort,
                                     SandboxExecutionService sandboxExecutionService,
                                     ExtensionAuditService auditService,
                                     ExtensionResourceLimiter resourceLimiter,
                                     ExtensionRouter router) {
        this.auditPort = auditPort;
        this.sandboxExecutionService = sandboxExecutionService;
        this.auditService = auditService;
        this.resourceLimiter = resourceLimiter;
        this.router = router;
    }

    public void registerProviderExtension(String key, ProviderExtensionSPI extension,
                                           ExtensionTrustLevel trustLevel, String registeredBy) {
        validateExtension(key, extension);
        ExtensionHolder holder = new ExtensionHolder(key, extension.version(),
                extension.providerType(), "PROVIDER",
                OffsetDateTime.now(), registeredBy, ExtensionStatus.ACTIVE, trustLevel);

        ExtensionHolder previous = providerExtensions.put(key, holder);
        spiInstances.put(key, extension);

        if (previous != null) {
            saveVersionHistory(key, previous);
            createRollbackPoint(key, previous);
            log.info("Upgraded provider extension {} from {} to {}", key, previous.version(), extension.version());
        }

        ExtensionResourceLimits limits = ExtensionResourceLimits.forTrustLevel(trustLevel);
        if (extension instanceof ProviderExtensionSPIV2 v2) {
            limits = v2.resourceLimits().overrideWith(limits);
        }
        resourceLimiter.registerLimits(key, limits);

        auditService.recordRegistration(key, extension.version(),
                trustLevel.name(), registeredBy, Map.of(
                        "type", extension.providerType(),
                        "action", previous != null ? "UPGRADE" : "REGISTER",
                        "trustLevel", trustLevel.name()));

        log.info("Registered provider extension: {} v{} trust={}", key, extension.version(), trustLevel);
    }

    public void registerPromptExtension(String key, PromptExtensionSPI extension,
                                         ExtensionTrustLevel trustLevel, String registeredBy) {
        validateExtension(key, extension);
        ExtensionHolder holder = new ExtensionHolder(key, extension.version(),
                extension.extensionType(), "PROMPT",
                OffsetDateTime.now(), registeredBy, ExtensionStatus.ACTIVE, trustLevel);

        ExtensionHolder previous = promptExtensions.put(key, holder);
        spiInstances.put(key, extension);

        if (previous != null) {
            saveVersionHistory(key, previous);
            createRollbackPoint(key, previous);
        }

        ExtensionResourceLimits limits = ExtensionResourceLimits.forTrustLevel(trustLevel);
        if (extension instanceof PromptExtensionSPIV2 v2) {
            limits = v2.resourceLimits().overrideWith(limits);
        }
        resourceLimiter.registerLimits(key, limits);

        auditService.recordRegistration(key, extension.version(),
                trustLevel.name(), registeredBy, Map.of(
                        "type", extension.extensionType(),
                        "trustLevel", trustLevel.name()));

        log.info("Registered prompt extension: {} v{} trust={}", key, extension.version(), trustLevel);
    }

    public void registerWorkflowStepExtension(String key, WorkflowStepExtensionSPI extension,
                                                ExtensionTrustLevel trustLevel, String registeredBy) {
        validateExtension(key, extension);
        ExtensionHolder holder = new ExtensionHolder(key, extension.version(),
                extension.stepType(), "WORKFLOW_STEP",
                OffsetDateTime.now(), registeredBy, ExtensionStatus.ACTIVE, trustLevel);

        ExtensionHolder previous = workflowStepExtensions.put(key, holder);
        spiInstances.put(key, extension);

        if (previous != null) {
            saveVersionHistory(key, previous);
            createRollbackPoint(key, previous);
        }

        ExtensionResourceLimits limits = ExtensionResourceLimits.forTrustLevel(trustLevel);
        if (extension instanceof WorkflowStepExtensionSPIV2 v2) {
            limits = v2.resourceLimits().overrideWith(limits);
        }
        resourceLimiter.registerLimits(key, limits);

        auditService.recordRegistration(key, extension.version(),
                trustLevel.name(), registeredBy, Map.of(
                        "type", extension.stepType(),
                        "trustLevel", trustLevel.name()));

        log.info("Registered workflow step extension: {} v{} trust={}", key, extension.version(), trustLevel);
    }

    public ExtensionResult executeProvider(String key, String inputJson, String tenantId,
                                            String userId) throws ExtensionExecutionException {
        ExtensionHolder holder = providerExtensions.get(key);
        if (holder == null) {
            throw new ExtensionExecutionException(key, "EXT-404", "Provider extension not found: " + key);
        }

        Optional<String> routedVersion = router.resolveVersion(key, holder.version(), tenantId, userId, null);
        String targetVersion = routedVersion.orElse(holder.version());

        ExtensionContext context = ExtensionContext.builder()
                .extensionKey(key)
                .extensionVersion(targetVersion)
                .tenantId(tenantId)
                .userId(userId)
                .traceId(Ids.newId("trace"))
                .trustLevel(holder.trustLevel())
                .build();

        ExtensionResourceLimits limits = resourceLimiter.getLimits(key);
        return sandboxExecutionService.executeExtension(context, inputJson, limits);
    }

    public ExtensionResult executePromptExtension(String key, String templateBody, String variables,
                                                    String tenantId, String userId) throws ExtensionExecutionException {
        ExtensionHolder holder = promptExtensions.get(key);
        if (holder == null) {
            throw new ExtensionExecutionException(key, "EXT-404", "Prompt extension not found: " + key);
        }

        ExtensionContext context = ExtensionContext.builder()
                .extensionKey(key)
                .extensionVersion(holder.version())
                .tenantId(tenantId)
                .userId(userId)
                .traceId(Ids.newId("trace"))
                .trustLevel(holder.trustLevel())
                .build();

        ExtensionResourceLimits limits = resourceLimiter.getLimits(key);
        Object spi = spiInstances.get(key);
        if (spi instanceof PromptExtensionSPIV2 v2) {
            return v2.execute(context, templateBody, variables);
        }
        if (spi instanceof PromptExtensionSPI v1) {
            String result = v1.execute(templateBody, variables, "{}");
            return ExtensionResult.success(result);
        }
        return sandboxExecutionService.executeExtension(context, templateBody, limits);
    }

    public ExtensionResult executeWorkflowStep(String key, String stepInput, String tenantId,
                                                String userId) throws ExtensionExecutionException {
        ExtensionHolder holder = workflowStepExtensions.get(key);
        if (holder == null) {
            throw new ExtensionExecutionException(key, "EXT-404", "Workflow step extension not found: " + key);
        }

        ExtensionContext context = ExtensionContext.builder()
                .extensionKey(key)
                .extensionVersion(holder.version())
                .tenantId(tenantId)
                .userId(userId)
                .traceId(Ids.newId("trace"))
                .trustLevel(holder.trustLevel())
                .build();

        ExtensionResourceLimits limits = resourceLimiter.getLimits(key);
        Object spi = spiInstances.get(key);
        if (spi instanceof WorkflowStepExtensionSPIV2 v2) {
            return v2.execute(context, stepInput);
        }
        if (spi instanceof WorkflowStepExtensionSPI v1) {
            String result = v1.executeStep(stepInput, "{}");
            return ExtensionResult.success(result);
        }
        return sandboxExecutionService.executeExtension(context, stepInput, limits);
    }

    public boolean unloadExtension(String key, String unloadedBy) {
        ExtensionHolder removed = providerExtensions.remove(key);
        if (removed == null) removed = promptExtensions.remove(key);
        if (removed == null) removed = workflowStepExtensions.remove(key);

        if (removed != null) {
            Object spi = spiInstances.remove(key);
            if (spi instanceof ProviderExtensionSPI p) p.onUnload();
            if (spi instanceof PromptExtensionSPI p) p.onUnload();
            if (spi instanceof WorkflowStepExtensionSPI p) p.onUnload();

            auditService.recordUnload(key, removed.version(), unloadedBy);
            log.info("Unloaded extension: {} v{}", key, removed.version());
            return true;
        }
        return false;
    }

    public boolean rollbackExtension(String key, String targetVersion, String rolledBackBy) {
        List<ExtensionVersionRecord> history = extensionHistory.get(key);
        if (history == null || history.isEmpty()) {
            log.warn("No version history for extension: {}", key);
            return false;
        }

        ExtensionVersionRecord target = history.stream()
                .filter(v -> v.version().equals(targetVersion))
                .findFirst()
                .orElse(null);

        if (target == null) {
            log.warn("Version {} not found in history for extension: {}", targetVersion, key);
            return false;
        }

        ExtensionHolder current = providerExtensions.get(key);
        if (current == null) current = promptExtensions.get(key);
        if (current == null) current = workflowStepExtensions.get(key);

        String currentVersion = current != null ? current.version() : "none";

        providerExtensions.remove(key);
        promptExtensions.remove(key);
        workflowStepExtensions.remove(key);
        spiInstances.remove(key);

        auditService.recordRollback(key, currentVersion, targetVersion, rolledBackBy);
        log.info("Rolled back extension {} from {} to {}", key, currentVersion, targetVersion);
        return true;
    }

    public RollbackPoint createRollbackPoint(String extensionKey, String createdBy) {
        ExtensionHolder holder = providerExtensions.get(extensionKey);
        if (holder == null) holder = promptExtensions.get(extensionKey);
        if (holder == null) holder = workflowStepExtensions.get(extensionKey);

        if (holder == null) return null;

        String id = Ids.newId("rbp");
        List<RoutingRule> rules = router.getRules(extensionKey);
        String ruleIds = rules.stream().map(RoutingRule::id).reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);

        RollbackPoint point = new RollbackPoint(
                id, extensionKey, holder.version(), null, null,
                ruleIds, OffsetDateTime.now(), createdBy, true);
        rollbackPoints.put(id, point);

        auditPort.record(createdBy, "ROLLBACK_POINT_CREATED", "EXTENSION",
                "rollback_point", id, Map.of("extensionCode", extensionKey,
                        "version", holder.version()));
        return point;
    }

    public List<ExtensionInfo> listExtensions() {
        List<ExtensionInfo> all = new ArrayList<>();
        providerExtensions.forEach((k, v) -> all.add(new ExtensionInfo(k, v.version(), v.extensionType(), "PROVIDER", v.status().name(), v.trustLevel().name())));
        promptExtensions.forEach((k, v) -> all.add(new ExtensionInfo(k, v.version(), v.extensionType(), "PROMPT", v.status().name(), v.trustLevel().name())));
        workflowStepExtensions.forEach((k, v) -> all.add(new ExtensionInfo(k, v.version(), v.extensionType(), "WORKFLOW_STEP", v.status().name(), v.trustLevel().name())));
        return all;
    }

    public Optional<ExtensionInfo> getExtension(String key) {
        ExtensionHolder h = providerExtensions.get(key);
        if (h == null) h = promptExtensions.get(key);
        if (h == null) h = workflowStepExtensions.get(key);
        if (h == null) return Optional.empty();
        return Optional.of(new ExtensionInfo(key, h.version(), h.extensionType(), h.category(), h.status().name(), h.trustLevel().name()));
    }

    public List<ExtensionVersionRecord> getVersionHistory(String key) {
        return extensionHistory.getOrDefault(key, List.of());
    }

    public ExtensionRouter getRouter() {
        return router;
    }

    public ExtensionResourceLimiter getResourceLimiter() {
        return resourceLimiter;
    }

    public ExtensionAuditService getAuditService() {
        return auditService;
    }

    private void validateExtension(String key, Object extension) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Extension key must not be blank");
        }
        if (extension == null) {
            throw new IllegalArgumentException("Extension must not be null");
        }
    }

    private void saveVersionHistory(String key, ExtensionHolder holder) {
        extensionHistory.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new ExtensionVersionRecord(holder.version(), holder.extensionType(),
                        holder.category(), holder.registeredAt(), holder.registeredBy()));
    }

    private void createRollbackPoint(String key, ExtensionHolder holder) {
        String id = Ids.newId("rbp");
        RollbackPoint point = new RollbackPoint(
                id, key, holder.version(), null, null, null,
                OffsetDateTime.now(), "system", true);
        rollbackPoints.put(id, point);
    }

    private record ExtensionHolder(
            String key, String version, String extensionType, String category,
            OffsetDateTime registeredAt, String registeredBy, ExtensionStatus status,
            ExtensionTrustLevel trustLevel
    ) {}

    public record ExtensionInfo(
            String key, String version, String extensionType, String category,
            String status, String trustLevel
    ) {}

    public record ExtensionVersionRecord(
            String version, String extensionType, String category,
            OffsetDateTime registeredAt, String registeredBy
    ) {}

    private enum ExtensionStatus {
        ACTIVE, INACTIVE, ERROR, UNLOADING, PENDING_REVIEW
    }
}
