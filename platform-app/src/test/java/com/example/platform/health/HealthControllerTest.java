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
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(ds);

        jdbc.execute("CREATE TABLE IF NOT EXISTS outbox_events (id VARCHAR(64), status VARCHAR(32))");
        jdbc.execute("INSERT INTO outbox_events VALUES ('e1', 'PENDING')");
        jdbc.execute("INSERT INTO outbox_events VALUES ('e2', 'PROCESSED')");

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
