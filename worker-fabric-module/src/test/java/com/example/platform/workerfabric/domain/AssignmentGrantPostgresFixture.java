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
                .defaultSchema("public").baselineOnMigrate(false)
                .load()
                .migrate();
    }

    static void truncate(DSLContext dsl) {
        dsl.execute("""
            truncate table worker_fabric.wf_completion_event, worker_fabric.wf_execution_observation,
                worker_fabric.wf_physical_release_confirmation, worker_fabric.wf_local_admission,
                worker_fabric.wf_request_work_resolution, worker_fabric.wf_task_ownership,
                worker_fabric.wf_task_lease_reservation, worker_fabric.wf_task_lease, worker_fabric.wf_reservation_device,
                worker_fabric.wf_reservation, worker_fabric.wf_execution_assignment_device, worker_fabric.wf_execution_assignment,
                worker_fabric.wf_execution_attempt, worker_fabric.wf_execution_ownership_generation,
                worker_fabric.wf_execution_backend_selection, worker_fabric.wf_runtime_registration,
                worker_fabric.wf_host_resource_snapshot_device, worker_fabric.wf_host_resource_snapshot,
                worker_fabric.wf_host_snapshot_generation_authority,
                worker_fabric.wf_worker_runtime_connection, worker_fabric.wf_physical_host_connection,
                worker_fabric.wf_host_registration cascade
            """);
    }

    private static Path canonicalMigrationDirectory() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : new Path[] {
                workingDirectory.resolve("../platform-app/src/main/resources/db/migration"),
                workingDirectory.resolve("platform-app/src/main/resources/db/migration")}) {
            Path normalized = candidate.normalize();
            if (Files.isRegularFile(normalized.resolve("V1__baseline.sql"))) {
                return normalized;
            }
        }
        throw new IllegalStateException(
                "canonical platform-app Flyway migration directory is unavailable from "
                        + workingDirectory);
    }
}
