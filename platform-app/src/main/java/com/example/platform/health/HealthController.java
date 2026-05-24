package com.example.platform.health;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public HealthController(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    public Map<String, Object> liveness() {
        return Map.of("status", "ok", "timestamp", System.currentTimeMillis());
    }

    @GetMapping("/readyz")
    public Map<String, Object> readiness() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean allHealthy = true;

        checks.put("database", checkDatabase());
        if (!((Map<String, Object>) checks.get("database")).get("status").equals("ok")) {
            allHealthy = false;
        }

        checks.put("storage", checkStorage());
        if (!((Map<String, Object>) checks.get("storage")).get("status").equals("ok")) {
            allHealthy = false;
        }

        checks.put("outbox", checkOutbox());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", allHealthy ? "ok" : "degraded");
        result.put("timestamp", System.currentTimeMillis());
        result.put("checks", checks);
        return result;
    }

    @GetMapping("/metrics/summary")
    public Map<String, Object> metricsSummary() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        try {
            metrics.put("exportSessions", getExportSessionCounts());
        } catch (Exception e) {
            metrics.put("exportSessions", Map.of("error", e.getMessage()));
        }

        try {
            metrics.put("outbox", getOutboxCounts());
        } catch (Exception e) {
            metrics.put("outbox", Map.of("error", e.getMessage()));
        }

        try {
            metrics.put("renderJobs", getRenderJobCounts());
        } catch (Exception e) {
            metrics.put("renderJobs", Map.of("error", e.getMessage()));
        }

        metrics.put("timestamp", System.currentTimeMillis());
        return metrics;
    }

    private Map<String, Object> checkDatabase() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.warn("Health check: database error: {}", e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    private Map<String, Object> checkStorage() {
        try {
            Connection conn = dataSource.getConnection();
            conn.close();
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.warn("Health check: storage error: {}", e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    private Map<String, Object> checkOutbox() {
        try {
            Integer pending = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'", Integer.class);
            return Map.of("status", "ok", "pendingCount", pending != null ? pending : 0);
        } catch (Exception e) {
            return Map.of("status", "skipped", "reason", "outbox table not available");
        }
    }

    private Map<String, Object> getExportSessionCounts() {
        try {
            var rows = jdbc.queryForList(
                    "SELECT status, COUNT(*) as cnt FROM client_export_session GROUP BY status");
            Map<String, Object> counts = new LinkedHashMap<>();
            for (var row : rows) {
                counts.put((String) row.get("status"), ((Number) row.get("cnt")).intValue());
            }
            return counts;
        } catch (Exception e) {
            return Map.of("error", "table not available");
        }
    }

    private Map<String, Object> getOutboxCounts() {
        try {
            var rows = jdbc.queryForList(
                    "SELECT status, COUNT(*) as cnt FROM outbox_events GROUP BY status");
            Map<String, Object> counts = new LinkedHashMap<>();
            for (var row : rows) {
                counts.put((String) row.get("status"), ((Number) row.get("cnt")).intValue());
            }
            return counts;
        } catch (Exception e) {
            return Map.of("error", "table not available");
        }
    }

    private Map<String, Object> getRenderJobCounts() {
        try {
            var rows = jdbc.queryForList(
                    "SELECT status, COUNT(*) as cnt FROM render_job GROUP BY status");
            Map<String, Object> counts = new LinkedHashMap<>();
            for (var row : rows) {
                counts.put((String) row.get("status"), ((Number) row.get("cnt")).intValue());
            }
            return counts;
        } catch (Exception e) {
            return Map.of("error", "table not available");
        }
    }
}
