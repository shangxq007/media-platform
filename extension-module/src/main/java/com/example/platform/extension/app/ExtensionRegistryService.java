package com.example.platform.extension.app;

import com.example.platform.extension.api.port.*;
import com.example.platform.extension.api.port.ExtensionQueries.ExtensionInfo;

import com.example.platform.extension.domain.*;
import com.example.platform.extension.runtime.PluginRuntimeProviderBinding;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExtensionRegistryService implements ProviderContributions, ExtensionQueries {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRegistryService.class);

    private final AuditPort auditPort;
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
                                     ExtensionAuditService auditService,
                                     ExtensionResourceLimiter resourceLimiter,
                                     ExtensionRouter router) {
        this.auditPort = auditPort;
        this.auditService = auditService;
        this.resourceLimiter = resourceLimiter;
        this.router = router;
    }

    @Override
    public synchronized void registerProviderExtension(String key, PluginRuntimeProviderBinding extension,
            ExtensionTrustLevel trustLevel, String registeredBy) {
        validateExtension(key, extension);
        register(key, extension, extension.version(), extension.providerType(), "PROVIDER",
                trustLevel, registeredBy, extension.resourceLimits(), providerExtensions);
    }

    public synchronized void registerPromptExtension(String key, PromptExtensionSPI extension,
            ExtensionTrustLevel trustLevel, String registeredBy) {
        validateExtension(key, extension);
        register(key, extension, extension.version(), extension.extensionType(), "PROMPT",
                trustLevel, registeredBy, extension.resourceLimits(), promptExtensions);
    }

    public synchronized void registerWorkflowStepExtension(String key, WorkflowStepExtensionSPI extension,
            ExtensionTrustLevel trustLevel, String registeredBy) {
        validateExtension(key, extension);
        register(key, extension, extension.version(), extension.stepType(), "WORKFLOW_STEP",
                trustLevel, registeredBy, extension.resourceLimits(), workflowStepExtensions);
    }

    private void register(String key, Object instance, String version, String type, String category,
            ExtensionTrustLevel trust, String actor, ExtensionResourceLimits requested,
            Map<String, ExtensionHolder> holders) {
        // Resolve all plugin callbacks and validation before changing advertised state.
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(type, "extensionType");
        var limits = Objects.requireNonNull(requested, "resourceLimits")
                .overrideWith(ExtensionResourceLimits.forTrustLevel(trust));
        var previous = holders.get(key);
        var holder = new ExtensionHolder(key, version, type, category, OffsetDateTime.now(), actor, ExtensionStatus.ACTIVE, trust);
        Map<String, Object> payload = category.equals("PROVIDER")
                ? Map.of("type", type, "action", previous == null ? "REGISTER" : "UPGRADE", "trustLevel", trust.name())
                : Map.of("type", type, "trustLevel", trust.name());
        auditService.recordRegistration(key, version, trust.name(), actor, payload);
        resourceLimiter.registerLimits(key, limits);
        if (previous != null) { saveVersionHistory(key, previous); createRollbackPoint(key, previous); }
        spiInstances.put(key, instance);
        holders.put(key, holder);
        log.info("Registered {} extension: {} v{} trust={}", category, key, version, trust);
    }

    public synchronized boolean unloadExtension(String key, String unloadedBy) {
        ExtensionHolder removed = providerExtensions.remove(key);
        if (removed == null) removed = promptExtensions.remove(key);
        if (removed == null) removed = workflowStepExtensions.remove(key);

        if (removed != null) {
            Object spi = spiInstances.remove(key);
            if (spi instanceof PluginRuntimeProviderBinding p) p.onUnload();
            if (spi instanceof PromptExtensionSPI p) p.onUnload();
            if (spi instanceof WorkflowStepExtensionSPI p) p.onUnload();

            auditService.recordUnload(key, removed.version(), unloadedBy);
            log.info("Unloaded extension: {} v{}", key, removed.version());
            return true;
        }
        return false;
    }

    public synchronized boolean rollbackExtension(String key, String targetVersion, String rolledBackBy) {
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

    public synchronized RollbackPoint createRollbackPoint(String extensionKey, String createdBy) {
        ExtensionHolder holder = providerExtensions.get(extensionKey);
        if (holder == null) holder = promptExtensions.get(extensionKey);
        if (holder == null) holder = workflowStepExtensions.get(extensionKey);

        if (holder == null) return null;

        String id = ("rbp_" + java.util.UUID.randomUUID().toString().replace("-", ""));
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

    public synchronized List<ExtensionInfo> listExtensions() {
        List<ExtensionInfo> all = new ArrayList<>();
        providerExtensions.forEach((k, v) -> all.add(new ExtensionInfo(k, v.version(), v.extensionType(), "PROVIDER", v.status().name(), v.trustLevel().name())));
        promptExtensions.forEach((k, v) -> all.add(new ExtensionInfo(k, v.version(), v.extensionType(), "PROMPT", v.status().name(), v.trustLevel().name())));
        workflowStepExtensions.forEach((k, v) -> all.add(new ExtensionInfo(k, v.version(), v.extensionType(), "WORKFLOW_STEP", v.status().name(), v.trustLevel().name())));
        return List.copyOf(all);
    }

    public synchronized Optional<ExtensionInfo> getExtension(String key) {
        ExtensionHolder h = providerExtensions.get(key);
        if (h == null) h = promptExtensions.get(key);
        if (h == null) h = workflowStepExtensions.get(key);
        if (h == null) return Optional.empty();
        return Optional.of(new ExtensionInfo(key, h.version(), h.extensionType(), h.category(), h.status().name(), h.trustLevel().name()));
    }

    public synchronized List<ExtensionVersionRecord> getVersionHistory(String key) {
        return List.copyOf(extensionHistory.getOrDefault(key, List.of()));
    }

    public ExtensionRouter getRouter() {
        return router;
    }

    /**
     * Looks up the registered provider-binding instance for a provider key (used by the
     * Plugin Runtime V2 compatibility adapter).
     *
     * @param key provider extension key
     * @return the registered provider binding, or {@code null} when not registered
     */
    public synchronized PluginRuntimeProviderBinding findProviderBinding(String key) {
        Object spi = spiInstances.get(key);
        return spi instanceof PluginRuntimeProviderBinding provider ? provider : null;
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
        String id = ("rbp_" + java.util.UUID.randomUUID().toString().replace("-", ""));
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

    public record ExtensionVersionRecord(
            String version, String extensionType, String category,
            OffsetDateTime registeredAt, String registeredBy
    ) {}

    private enum ExtensionStatus {
        ACTIVE, INACTIVE, ERROR, UNLOADING, PENDING_REVIEW
    }
}
