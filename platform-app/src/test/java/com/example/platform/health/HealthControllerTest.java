package com.example.platform.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class HealthControllerTest {

    private HealthController controller;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:health_test_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);

        jdbc.execute("create table if not exists outbox_events (id varchar(64), status varchar(32))");
        jdbc.execute("insert into outbox_events values ('e1', 'PENDING')");
        jdbc.execute("insert into outbox_events values ('e2', 'PROCESSED')");

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
