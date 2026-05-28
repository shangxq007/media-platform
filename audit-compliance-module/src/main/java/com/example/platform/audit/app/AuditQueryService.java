package com.example.platform.audit.app;

import com.example.platform.audit.api.dto.AuditRecordDetail;
import com.example.platform.audit.api.dto.AuditRecordPage;
import com.example.platform.audit.api.dto.AuditRecordQuery;
import com.example.platform.audit.api.dto.AuditRecordSummary;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Query service for audit records.
 *
 * <p>Provides filtered, paginated access to audit_records.
 * All queries are tenant-scoped when TenantContext is available.
 */
@Service
public class AuditQueryService {

    private final AuditService auditService;

    public AuditQueryService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Query audit records with filters and pagination.
     *
     * @param query the query parameters
     * @return a page of audit record summaries
     */
    public AuditRecordPage query(AuditRecordQuery query) {
        // For now, delegate to existing AuditService methods based on the most specific filter.
        // A future optimization would add a single jOOQ query with dynamic WHERE clauses.
        List<Map<String, Object>> allRecords;

        if (query.category() != null) {
            AuditCategory category = parseCategory(query.category());
            allRecords = auditService.findByCategory(category, 1000);
        } else if (query.resourceType() != null && query.resourceId() != null) {
            allRecords = auditService.findByResource(query.resourceType(), query.resourceId());
        } else {
            allRecords = auditService.recent(1000);
        }

        // Apply additional filters in-memory (for first version; optimize to DB query later)
        List<Map<String, Object>> filtered = allRecords.stream()
                .filter(r -> query.action() == null || query.action().equals(r.get("action")))
                .filter(r -> query.actorType() == null || query.actorType().equals(r.get("actor_type")))
                .filter(r -> query.actorId() == null || query.actorId().equals(r.get("actor_id")))
                .filter(r -> query.resourceType() == null || query.resourceType().equals(r.get("resource_type")))
                .filter(r -> query.resourceId() == null || query.resourceId().equals(r.get("resource_id")))
                .filter(r -> matchesTimeRange(r, query.from(), query.to()))
                .filter(r -> matchesPayloadResult(r, query.result()))
                .filter(r -> matchesPayloadTenantId(r, query.targetTenantId()))
                .collect(java.util.stream.Collectors.toList());

        // Sort by created_at DESC (default)
        filtered.sort((a, b) -> {
            Object ta = a.get("created_at");
            Object tb = b.get("created_at");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return -ta.toString().compareTo(tb.toString());
        });

        // Paginate
        int total = filtered.size();
        int page = Math.max(query.page(), 0);
        int size = clamp(query.size(), 1, 200);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<AuditRecordSummary> items;
        if (fromIndex >= total) {
            items = List.of();
        } else {
            items = filtered.subList(fromIndex, toIndex).stream()
                    .map(this::toSummary)
                    .toList();
        }

        return new AuditRecordPage(items, page, size, total);
    }

    /**
     * Get a single audit record by ID.
     */
    public Optional<AuditRecordDetail> findById(String id) {
        List<Map<String, Object>> records = auditService.recent(1000);
        return records.stream()
                .filter(r -> id.equals(r.get("id")))
                .findFirst()
                .map(this::toDetail);
    }

    /**
     * Export records for CSV — returns all matching records up to limit, sorted by createdAt DESC.
     * No pagination, no payload (summary fields only).
     */
    public List<AuditRecordSummary> exportRecords(AuditRecordQuery query, int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 10000);

        List<Map<String, Object>> allRecords;
        if (query.category() != null) {
            AuditCategory category = parseCategory(query.category());
            allRecords = auditService.findByCategory(category, effectiveLimit);
        } else if (query.resourceType() != null && query.resourceId() != null) {
            allRecords = auditService.findByResource(query.resourceType(), query.resourceId());
        } else {
            allRecords = auditService.recent(effectiveLimit);
        }

        List<Map<String, Object>> filtered = allRecords.stream()
                .filter(r -> query.action() == null || query.action().equals(r.get("action")))
                .filter(r -> query.actorType() == null || query.actorType().equals(r.get("actor_type")))
                .filter(r -> query.actorId() == null || query.actorId().equals(r.get("actor_id")))
                .filter(r -> query.resourceType() == null || query.resourceType().equals(r.get("resource_type")))
                .filter(r -> query.resourceId() == null || query.resourceId().equals(r.get("resource_id")))
                .filter(r -> matchesTimeRange(r, query.from(), query.to()))
                .filter(r -> matchesPayloadResult(r, query.result()))
                .filter(r -> matchesPayloadTenantId(r, query.targetTenantId()))
                .collect(Collectors.toList());

        filtered.sort((a, b) -> {
            Object ta = a.get("created_at");
            Object tb = b.get("created_at");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return -ta.toString().compareTo(tb.toString());
        });

        return filtered.stream()
                .limit(effectiveLimit)
                .map(this::toSummary)
                .toList();
    }

    // ==================== Mapping ====================

    private AuditRecordSummary toSummary(Map<String, Object> row) {
        return new AuditRecordSummary(
                (String) row.get("id"),
                (String) row.get("created_at"),
                (String) row.get("category"),
                (String) row.get("action"),
                (String) row.get("actor_type"),
                (String) row.get("actor_id"),
                (String) row.get("resource_type"),
                (String) row.get("resource_id"),
                extractPayloadField(row, "targetTenantId"),
                extractPayloadField(row, "result"),
                extractPayloadField(row, "requestId"),
                extractPayloadField(row, "traceId")
        );
    }

    private AuditRecordDetail toDetail(Map<String, Object> row) {
        String rawPayload = (String) row.get("payload");
        Map<String, Object> payload = parsePayload(rawPayload);
        return new AuditRecordDetail(
                (String) row.get("id"),
                (String) row.get("created_at"),
                (String) row.get("category"),
                (String) row.get("action"),
                (String) row.get("actor_type"),
                (String) row.get("actor_id"),
                (String) row.get("resource_type"),
                (String) row.get("resource_id"),
                sanitizePayload(payload)
        );
    }

    // ==================== Filter helpers ====================

    private static boolean matchesTimeRange(Map<String, Object> row, String from, String to) {
        String createdAt = (String) row.get("created_at");
        if (createdAt == null) return false;
        if (from != null && createdAt.compareTo(from) < 0) return false;
        if (to != null && createdAt.compareTo(to) > 0) return false;
        return true;
    }

    private static boolean matchesPayloadResult(Map<String, Object> row, String result) {
        if (result == null) return true;
        String payloadResult = extractPayloadField(row, "result");
        return result.equals(payloadResult);
    }

    private static boolean matchesPayloadTenantId(Map<String, Object> row, String tenantId) {
        if (tenantId == null) return true;
        String payloadTenantId = extractPayloadField(row, "targetTenantId");
        return tenantId.equals(payloadTenantId);
    }

    private static String extractPayloadField(Map<String, Object> row, String field) {
        String rawPayload = (String) row.get("payload");
        if (rawPayload == null || rawPayload.isBlank()) return null;
        try {
            Map<?, ?> payload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawPayload, Map.class);
            Object value = payload.get(field);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parsePayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(rawPayload, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ==================== Sanitization ====================

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "cookie", "token", "accesstoken", "refreshtoken",
            "apikey", "api_key", "key", "secret", "password", "passwd",
            "signedurl", "signed_url", "virtualkey", "virtual_key",
            "litellmkey", "litellm_key", "bearer"
    );

    /**
     * Recursively sanitize payload map, replacing sensitive values with [REDACTED].
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey().toLowerCase().replace("-", "").replace("_", "");
            if (SENSITIVE_KEYS.contains(key)) {
                sanitized.put(entry.getKey(), "[REDACTED]");
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                sanitized.put(entry.getKey(), sanitizePayload((Map<String, Object>) nested));
            } else if (entry.getValue() instanceof List<?> list) {
                sanitized.put(entry.getKey(), list.stream()
                        .map(item -> item instanceof Map<?, ?> m ? sanitizePayload((Map<String, Object>) m) : item)
                        .toList());
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }

    private static AuditCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return AuditCategory.UNKNOWN;
        }
        try {
            return AuditCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AuditCategory.UNKNOWN;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
