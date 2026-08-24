package com.example.platform.workerfabric.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;

/** Applies and cleans the exact canonical platform-app Flyway schema used in deployment. */
final class AssignmentGrantPostgresFixture {

    private AssignmentGrantPostgresFixture() {}

    static void migrate(DataSource dataSource) {
        Path migrations = canonicalMigrationDirectory();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrations)
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    static void truncate(DSLContext dsl) {
        dsl.execute("""
            truncate table wf_completion_event, wf_execution_observation,
                wf_physical_release_confirmation, wf_local_admission,
                wf_request_work_resolution, wf_task_ownership,
                wf_task_lease_reservation, wf_task_lease, wf_reservation_device,
                wf_reservation, wf_execution_assignment_device, wf_execution_assignment,
                wf_execution_attempt, wf_execution_ownership_generation,
                wf_execution_backend_selection, wf_runtime_registration,
                wf_host_resource_snapshot_device, wf_host_resource_snapshot,
                wf_host_snapshot_generation_authority,
                wf_worker_runtime_connection, wf_physical_host_connection,
                wf_host_registration cascade
            """);
    }

    private static Path canonicalMigrationDirectory() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : new Path[] {
                workingDirectory.resolve("../platform-app/src/main/resources/db/migration"),
                workingDirectory.resolve("platform-app/src/main/resources/db/migration")}) {
            Path normalized = candidate.normalize();
            if (Files.isRegularFile(normalized.resolve("V1__initial_schema.sql"))) {
                return normalized;
            }
        }
        throw new IllegalStateException(
                "canonical platform-app Flyway migration directory is unavailable from "
                        + workingDirectory);
    }
}
