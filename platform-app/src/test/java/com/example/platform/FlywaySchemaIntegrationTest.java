package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Applies central {@code db/migration} scripts (V1–V23) on PostgreSQL without booting the full app.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywaySchemaIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("media_platform_it")
            .withUsername("test")
            .withPassword("test");

    @Test
    void flywayCreatesTimelineRevisionTableWithLabels() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        try (var conn = POSTGRES.createConnection("");
                ResultSet labels = conn.getMetaData().getColumns(null, null, "timeline_revision", "labels_json");
                ResultSet cartTenant = conn.getMetaData().getColumns(null, null, "commerce_cart", "tenant_id")) {
            assertTrue(labels.next(), "timeline_revision.labels_json column must exist (V22)");
            assertTrue(cartTenant.next(), "commerce_cart.tenant_id column must exist (V23)");
        }
    }
}
