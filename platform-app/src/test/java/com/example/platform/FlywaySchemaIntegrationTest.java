package com.example.platform;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                .baselineOnMigrate(false)
                .load();
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

            ResultSet artifact = conn.getMetaData().getColumns(null, null, "artifact", "id");
            assertTrue(artifact.next(), "canonical Artifact identity table must exist (MCMV2 Artifact-only ownership)");
            ResultSet retired = conn.getMetaData().getColumns(null, null, "media_asset", "id");
            assertFalse(retired.next(), "retired media_asset must not become a parallel identity authority");

            ResultSet artifactNode = conn.getMetaData().getColumns(null, null, "artifact_node", "id");
            assertTrue(artifactNode.next(), "artifact_node table must exist");

            ResultSet renderBilling = conn.getMetaData().getColumns(null, null, "render_billing_record", "id");
            assertFalse(renderBilling.next(), "render_billing_record shadow table must be absent");
        }
    }
}
