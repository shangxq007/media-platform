package com.example.platform.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.workerfabric.domain.DefaultHostResourceProbe;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.LocalHostRegistrationLoop;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricRegistrationBoundary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * P2-5b-2a-1-2-R9: bounded local host/runtime registration round-trip against real PostgreSQL.
 *
 * <p>Covers the bounded V1 happy path (owner Q2): each tick advances the snapshot generation by one,
 * exactly one durable host registration row exists, the incarnation identity is stable, and the
 * validity window is the owner-decided 30 s.
 */
@Testcontainers
class LocalHostRegistrationRoundTripIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String HOST_ID = WorkerRegistrationConfiguration.LOCAL_HOST_ID;

    private static DSLContext dsl;
    private static JooqWorkerFabricRegistrationBoundary boundary;

    @TempDir
    static Path workRoot;

    @BeforeAll
    static void setup() throws Exception {
        PG.start();
        Path migrationDir = locateMigrationDir();
        PG.copyFileToContainer(
                MountableFile.forHostPath(migrationDir.resolve("V1__initial_schema.sql")),
                "/migrations/V1.sql");
        PG.copyFileToContainer(
                MountableFile.forHostPath(migrationDir.resolve("V2__render_binding_inputs.sql")),
                "/migrations/V2.sql");
        applyMigration("/migrations/V1.sql");
        applyMigration("/migrations/V2.sql");
        dsl = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        boundary = new JooqWorkerFabricRegistrationBoundary(dsl);
    }

    private static void applyMigration(String path) throws Exception {
        var result = PG.execInContainer("psql", "-U", PG.getUsername(), "-d", PG.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-f", path);
        assertEquals(0, result.getExitCode(), "migration " + path + " failed: " + result.getStderr());
    }

    private static Path locateMigrationDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("platform-app/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate db/migration directory");
    }

    @Test
    void registrationTicksAreDurableIdempotentAndGenerationAdvancing() {
        DefaultHostResourceProbe probe = DefaultHostResourceProbe.forLocalHost(workRoot);
        assertEquals(workRoot, probe.workRoot(), "probe exposes its configured temporary-storage root");
        LocalHostRegistrationLoop loop = new LocalHostRegistrationLoop(
                probe, boundary, Clock.systemUTC(), PhysicalHostId.of(HOST_ID));

        loop.registerOnce();

        assertEquals(1, dsl.fetchCount(DSL.table("wf_host_registration")), "one host registration row");
        String incarnation = hostIncarnation();
        assertNotNull(incarnation);
        assertTrue(dsl.fetchOne("select active from wf_host_registration where physical_host_id = ?",
                HOST_ID).get("active", Boolean.class));
        OffsetDateTime registeredAt = dsl.fetchOne(
                "select registered_at from wf_host_registration where physical_host_id = ?",
                HOST_ID).get("registered_at", OffsetDateTime.class);
        OffsetDateTime validUntil = dsl.fetchOne(
                "select valid_until from wf_host_registration where physical_host_id = ?",
                HOST_ID).get("valid_until", OffsetDateTime.class);
        assertEquals(LocalHostRegistrationLoop.REGISTRATION_VALIDITY,
                Duration.between(registeredAt.toInstant(), validUntil.toInstant()),
                "validity window is the owner-decided 30s");
        assertEquals(1L, generation(incarnation).value(), "first tick publishes generation 1");

        loop.registerOnce();

        assertEquals(1, dsl.fetchCount(DSL.table("wf_host_registration")),
                "second tick is idempotent: one durable row for the stable incarnation");
        assertEquals(incarnation, hostIncarnation(), "incarnation identity is stable across ticks");
        assertEquals(2L, generation(incarnation).value(), "second tick advances the generation to 2");
        assertEquals(1, dsl.fetchCount(DSL.table("wf_runtime_registration")),
                "runtime registration is upserted for the bounded local runtime");
    }

    private static HostResourceSnapshotGeneration generation(String incarnation) {
        return boundary.currentSnapshotGeneration(
                        PhysicalHostId.of(HOST_ID), PhysicalHostIncarnationId.of(incarnation))
                .orElseThrow(() -> new IllegalStateException("no durable snapshot generation"));
    }

    private static String hostIncarnation() {
        return dsl.fetchOne("select physical_host_incarnation_id from wf_host_registration "
                + "where physical_host_id = ?", HOST_ID)
                .get("physical_host_incarnation_id", String.class);
    }
}
