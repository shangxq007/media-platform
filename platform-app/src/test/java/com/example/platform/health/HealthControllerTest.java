package com.example.platform.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class HealthControllerTest extends PostgresTestContainerSupport {

    private HealthController controller;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(jdbcUrl());
        ds.setUsername(username());
        ds.setPassword(password());
        var jdbc = new JdbcTemplate(ds);

        // Create table with full schema matching production
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS outbox_events (
                id varchar(64) primary key,
                aggregate_type varchar(100) not null,
                aggregate_id varchar(100) not null,
                event_type varchar(150) not null,
                event_version int not null,
                payload text not null,
                status varchar(50) not null,
                created_at timestamp not null,
                published_at timestamp,
                retry_count int not null default 0,
                next_attempt_at timestamp,
                idempotency_key varchar(255)
            )
        """);
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
