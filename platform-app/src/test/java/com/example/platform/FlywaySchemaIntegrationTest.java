package com.example.platform;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Applies consolidated V1 schema on PostgreSQL Testcontainers.
 */
class FlywaySchemaIntegrationTest extends PostgresTestContainerSupport {

    @BeforeAll
    static void migrateDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(), username(), password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void flywayCreatesCoreTables() throws Exception {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(jdbcUrl());
        ds.setUsername(username());
        ds.setPassword(password());

        try (var conn = ds.getConnection()) {
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
