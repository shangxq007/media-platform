package com.example.platform.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.platform.shared.test.PostgresTestContainer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class HealthControllerTest extends PostgresTestContainer {

    private HealthController controller;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(POSTGRES_URL);
        ds.setUsername(POSTGRES_USERNAME);
        ds.setPassword(POSTGRES_PASSWORD);
        var jdbc = new JdbcTemplate(ds);

        // Create table if it doesn't exist
        jdbc.execute("CREATE TABLE IF NOT EXISTS outbox_events (id VARCHAR(64), status VARCHAR(32))");
        jdbc.execute("DELETE FROM outbox_events");
        jdbc.execute("INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, event_version, payload, status, created_at, retry_count) VALUES ('e1', 'test', 'test', 'test', 1, '{}', 'PENDING', NOW(), 0)");
        jdbc.execute("INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, event_version, payload, status, created_at, retry_count) VALUES ('e2', 'test', 'test', 'test', 1, '{}', 'PROCESSED', NOW(), 0)");

        controller = new HealthController(jdbc, ds);
    }

    @Test
    void livenessReturnsOk() {
        Map<String, Object> result = controller.liveness();
        assertEquals("ok", result.get("status"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    void readinessReturnsOkWhenDbUp() {
        Map<String, Object> result = controller.readiness();
        assertNotNull(result.get("status"));
        Map<String, Object> checks = (Map<String, Object>) result.get("checks");
        Map<String, Object> dbCheck = (Map<String, Object>) checks.get("database");
        assertEquals("ok", dbCheck.get("status"));
    }

    @Test
    void metricsSummaryReturnsCounts() {
        Map<String, Object> result = controller.metricsSummary();
        assertNotNull(result.get("outbox"));
        assertNotNull(result.get("timestamp"));
    }
}
