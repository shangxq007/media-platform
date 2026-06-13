package com.example.platform;

import com.example.platform.shared.test.PostgresTestContainer;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Applies consolidated V1 schema on PostgreSQL Testcontainers.
 */
class FlywaySchemaIntegrationTest extends PostgresTestContainer {

    @BeforeAll
    static void migrateDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    @Test
    void flywayCreatesCoreTables() throws Exception {
        try (var conn = POSTGRES.createConnection("")) {
            // Verify core render tables exist
            ResultSet renderJob = conn.getMetaData().getColumns(null, null, "render_job", "id");
            assertTrue(renderJob.next(), "render_job table must exist");

            ResultSet asset = conn.getMetaData().getColumns(null, null, "asset", "id");
            assertTrue(asset.next(), "asset table must exist");

            ResultSet artifactNode = conn.getMetaData().getColumns(null, null, "artifact_node", "id");
            assertTrue(artifactNode.next(), "artifact_node table must exist");

            ResultSet renderBilling = conn.getMetaData().getColumns(null, null, "render_billing_record", "id");
            assertTrue(renderBilling.next(), "render_billing_record table must exist");
        }
    }
}
