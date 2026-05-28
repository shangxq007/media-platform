package com.example.platform.audit.api;

import com.example.platform.audit.api.dto.*;
import com.example.platform.audit.app.AuditCategory;
import com.example.platform.audit.app.AuditQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Admin-only audit query API.
 *
 * <p>Provides paginated, filtered access to audit_records and CSV export.
 * Requires ADMIN role (OAuth2 ROLE_ADMIN or Legacy JWT roles containing ADMIN).
 */
@RestController
@RequestMapping("/api/v1/audit/admin")
public class AuditQueryController {

    private static final Logger log = LoggerFactory.getLogger(AuditQueryController.class);
    private static final int MAX_EXPORT_LIMIT = 1000;
    private static final int DEFAULT_EXPORT_LIMIT = 1000;

    private final AuditQueryService queryService;

    public AuditQueryController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/records")
    public ResponseEntity<AuditRecordPage> listRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String targetTenantId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        AuditRecordQuery query = new AuditRecordQuery(
                page, size, category, action, actorType, actorId,
                resourceType, resourceId, targetTenantId, result, from, to);

        log.debug("Audit query: page={} size={} category={} action={}", page, size, category, action);

        AuditRecordPage pageResult = queryService.query(query);
        return ResponseEntity.ok(pageResult);
    }

    @GetMapping("/records/{id}")
    public ResponseEntity<AuditRecordDetail> getRecord(@PathVariable String id) {
        log.debug("Audit detail: id={}", id);

        return queryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> listCategories() {
        return ResponseEntity.ok(
                java.util.Arrays.stream(AuditCategory.values())
                        .map(Enum::name)
                        .sorted()
                        .toList());
    }

    /**
     * Export audit records as CSV.
     *
     * <p>Reuses the same query parameters as listRecords, but returns all matching
     * records up to {@code limit} (default 1000, max 10000) as a CSV file download.
     *
     * <p>Payload is NOT exported — only summary fields.
     * CSV injection is prevented by prefixing dangerous cell values.
     */
    @GetMapping("/records/export")
    public void exportCsv(
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String targetTenantId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletResponse response) {

        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_EXPORT_LIMIT);
        AuditRecordQuery query = new AuditRecordQuery(
                0, effectiveLimit, category, action, actorType, actorId,
                resourceType, resourceId, targetTenantId, result, from, to);

        log.info("Audit CSV export: limit={} category={} action={}", effectiveLimit, category, action);

        List<AuditRecordSummary> records = queryService.exportRecords(query, effectiveLimit);

        String filename = "audit-records-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
                ".csv";

        response.setContentType("text/csv; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try {
            PrintWriter writer = response.getWriter();
            // CSV header
            writer.println("createdAt,category,action,actorType,actorId,resourceType,resourceId,targetTenantId,result,requestId,traceId");

            for (AuditRecordSummary r : records) {
                writer.println(String.join(",",
                        csvEscape(r.createdAt()),
                        csvEscape(r.category()),
                        csvEscape(r.action()),
                        csvEscape(r.actorType()),
                        csvEscape(r.actorId()),
                        csvEscape(r.resourceType()),
                        csvEscape(r.resourceId()),
                        csvEscape(r.targetTenantId()),
                        csvEscape(r.result()),
                        csvEscape(r.requestId()),
                        csvEscape(r.traceId())
                ));
            }

            writer.flush();
        } catch (Exception e) {
            log.error("Failed to write CSV export: {}", e.getMessage());
        }
    }

    /**
     * CSV cell escaping with CSV injection prevention.
     *
     * <p>Prefixes cells starting with =, +, -, @, tab, or CR with a single quote
     * to prevent formula injection in spreadsheet applications.
     */
    static String csvEscape(String value) {
        if (value == null) return "";
        // CSV injection prevention: prefix dangerous leading chars
        if (!value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' ||
                    first == '\t' || first == '\r') {
                value = "'" + value;
            }
        }
        // Standard CSV escaping: quote if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
