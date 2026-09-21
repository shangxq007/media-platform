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

    private final com.example.platform.outbox.operations.OutboxOperationalQuery outbox;
    private final com.example.platform.render.api.RenderOperationalQuery render;
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public HealthController(JdbcTemplate jdbc, DataSource dataSource,
            com.example.platform.outbox.operations.OutboxOperationalQuery outbox,
            com.example.platform.render.api.RenderOperationalQuery render) {
        this.outbox = outbox;
        this.render = render;
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

        var outboxCheck = checkOutbox();
        checks.put("outbox", outboxCheck);
        allHealthy &= "ok".equals(outboxCheck.get("status"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", allHealthy ? "ok" : "degraded");
        result.put("timestamp", System.currentTimeMillis());
        result.put("checks", checks);
        return result;
    }

    @GetMapping("/metrics/summary")
    public Map<String, Object> metricsSummary(jakarta.servlet.http.HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute("identity.platformAdministrator"))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        Map<String, Object> metrics = new LinkedHashMap<>();

        try {
            metrics.put("exportSessions", render.exportSessionCounts());
        } catch (Exception e) {
            metrics.put("exportSessions", Map.of("error", "dependency unavailable"));
        }

        try {
            metrics.put("outbox", outbox.snapshot().counts());
        } catch (Exception e) {
            metrics.put("outbox", Map.of("error", "dependency unavailable"));
        }

        try {
            metrics.put("renderJobs", render.renderJobCounts());
        } catch (Exception e) {
            metrics.put("renderJobs", Map.of("error", "dependency unavailable"));
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
            return Map.of("status", "error", "error", "dependency unavailable");
        }
    }

    private Map<String, Object> checkStorage() {
        try {
            Connection conn = dataSource.getConnection();
            conn.close();
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.warn("Health check: storage error: {}", e.getMessage());
            return Map.of("status", "error", "error", "dependency unavailable");
        }
    }

    private Map<String, Object> checkOutbox() {
        try {
            outbox.snapshot();
            // Public readiness reports availability, not privileged global tenant counts.
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.warn("Health check: outbox unavailable", e);
            return Map.of("status", "error", "error", "dependency unavailable");
        }
    }
}
