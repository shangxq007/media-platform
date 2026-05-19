package com.example.platform.extension.app;

import com.example.platform.extension.domain.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExtensionAuditService {

    private static final Logger log = LoggerFactory.getLogger(ExtensionAuditService.class);
    private static final int MAX_IN_MEMORY_EVENTS = 10000;

    private final AuditPort auditPort;
    private final List<ExtensionAuditEvent> recentEvents = new ArrayList<>();
    private final ConcurrentHashMap<String, List<ExtensionAuditEvent>> eventsByExtension = new ConcurrentHashMap<>();

    public ExtensionAuditService(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public ExtensionAuditEvent recordEvent(String extensionCode, String extensionVersion,
                                            ExtensionAuditEvent.EventType eventType,
                                            String actor, String tenantId, String userId,
                                            String traceId, String trustLevel,
                                            Map<String, Object> details) {
        return recordEvent(extensionCode, extensionVersion, eventType, actor, tenantId, userId,
                traceId, trustLevel, details, ExtensionAuditEvent.Severity.INFO.name());
    }

    public ExtensionAuditEvent recordEvent(String extensionCode, String extensionVersion,
                                            ExtensionAuditEvent.EventType eventType,
                                            String actor, String tenantId, String userId,
                                            String traceId, String trustLevel,
                                            Map<String, Object> details, String severity) {
        String id = Ids.newId("extaud");
        String detailsJson = details != null ? Jsons.toJson(details) : null;
        ExtensionAuditEvent event = new ExtensionAuditEvent(
                id, extensionCode, extensionVersion, eventType.name(),
                actor, tenantId, userId, traceId, trustLevel,
                detailsJson, severity, OffsetDateTime.now());

        synchronized (recentEvents) {
            recentEvents.add(event);
            if (recentEvents.size() > MAX_IN_MEMORY_EVENTS) {
                recentEvents.remove(0);
            }
        }
        eventsByExtension.computeIfAbsent(extensionCode, k -> new ArrayList<>()).add(event);

        auditPort.record(actor, eventType.name(), "EXTENSION",
                "extension", extensionCode, details != null ? details : Map.of());

        log.debug("Extension audit event: {} {} by {} [{}]", eventType, extensionCode, actor, severity);
        return event;
    }

    public ExtensionAuditEvent recordRegistration(String extensionCode, String extensionVersion,
                                                    String trustLevel, String actor,
                                                    Map<String, Object> details) {
        return recordEvent(extensionCode, extensionVersion,
                ExtensionAuditEvent.EventType.EXTENSION_REGISTERED,
                actor, null, null, null, trustLevel, details);
    }

    public ExtensionAuditEvent recordUnload(String extensionCode, String extensionVersion,
                                              String actor) {
        return recordEvent(extensionCode, extensionVersion,
                ExtensionAuditEvent.EventType.EXTENSION_UNLOADED,
                actor, null, null, null, null, null);
    }

    public ExtensionAuditEvent recordExecutionStart(String extensionCode, String extensionVersion,
                                                      String tenantId, String userId,
                                                      String traceId, String trustLevel) {
        return recordEvent(extensionCode, extensionVersion,
                ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_STARTED,
                userId, tenantId, userId, traceId, trustLevel,
                Map.of("tenantId", tenantId));
    }

    public ExtensionAuditEvent recordExecutionComplete(String extensionCode, String extensionVersion,
                                                        String tenantId, String userId,
                                                        String traceId, String trustLevel,
                                                        long durationMs, long outputSize) {
        return recordEvent(extensionCode, extensionVersion,
                ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_COMPLETED,
                userId, tenantId, userId, traceId, trustLevel,
                Map.of("durationMs", durationMs, "outputSize", outputSize));
    }

    public ExtensionAuditEvent recordExecutionTimeout(String extensionCode, String extensionVersion,
                                                        String tenantId, String userId,
                                                        String traceId, long timeoutMs) {
        return recordEvent(extensionCode, extensionVersion,
                ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_TIMEOUT,
                userId, tenantId, userId, traceId, null,
                Map.of("timeoutMs", timeoutMs),
                ExtensionAuditEvent.Severity.WARN.name());
    }

    public ExtensionAuditEvent recordExecutionFailed(String extensionCode, String extensionVersion,
                                                       String tenantId, String userId,
                                                       String traceId, String errorCode,
                                                       String errorMessage) {
        return recordEvent(extensionCode, extensionVersion,
                ExtensionAuditEvent.EventType.EXTENSION_EXECUTION_FAILED,
                userId, tenantId, userId, traceId, null,
                Map.of("errorCode", errorCode, "errorMessage", errorMessage),
                ExtensionAuditEvent.Severity.ERROR.name());
    }

    public ExtensionAuditEvent recordRollback(String extensionCode, String fromVersion,
                                               String toVersion, String actor) {
        return recordEvent(extensionCode, fromVersion,
                ExtensionAuditEvent.EventType.EXTENSION_ROLLED_BACK,
                actor, null, null, null, null,
                Map.of("fromVersion", fromVersion, "toVersion", toVersion),
                ExtensionAuditEvent.Severity.WARN.name());
    }

    public ExtensionAuditEvent recordSecurityViolation(String extensionCode, String actor,
                                                         String reason) {
        return recordEvent(extensionCode, null,
                ExtensionAuditEvent.EventType.SECURITY_VIOLATION,
                actor, null, null, null, null,
                Map.of("reason", reason),
                ExtensionAuditEvent.Severity.CRITICAL.name());
    }

    public ExtensionAuditEvent recordReviewRequired(String extensionCode, String extensionVersion,
                                                      String actor) {
        return recordEvent(extensionCode, extensionVersion,
                ExtensionAuditEvent.EventType.REVIEW_REQUIRED,
                actor, null, null, null, null, null);
    }

    public List<ExtensionAuditEvent> getRecentEvents(int limit) {
        synchronized (recentEvents) {
            int fromIndex = Math.max(0, recentEvents.size() - limit);
            return List.copyOf(recentEvents.subList(fromIndex, recentEvents.size()));
        }
    }

    public List<ExtensionAuditEvent> getEventsByExtension(String extensionCode) {
        return List.copyOf(eventsByExtension.getOrDefault(extensionCode, List.of()));
    }
}
