package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricRegistrationBoundary;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Correction 3 durable snapshot-generation monotonicity and immutability acceptance. */
class WorkerFabricRegistrationPostgresTest extends PostgresTestContainerSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2026-08-24T12:00:00Z");
    private static DataSource dataSource;
    private static DSLContext dsl;

    @BeforeAll
    static void startDatabaseAuthority() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        AssignmentGrantPostgresFixture.migrate(dataSource);
    }

    @AfterAll
    static void closeDatabaseAuthority() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void resetAuthority() {
        AssignmentGrantPostgresFixture.truncate(dsl);
    }

    @Test
    void repositoryRestartReloadsGenerationAndContinuesStrictlyMonotonic() {
        RequestWork request = TaskCTestFixture.runtime("snapshot-restart").requestWork();
        HostResourceSnapshot first = request.hostResourceSnapshot();
        var firstProcess = new JooqWorkerFabricRegistrationBoundary(dsl);
        firstProcess.registerHost(registration(first));

        DSLContext reloadedDsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        var restartedProcess = new JooqWorkerFabricRegistrationBoundary(reloadedDsl);
        HostResourceSnapshotGeneration reloaded = restartedProcess
                .currentSnapshotGeneration(first.physicalHostId(), first.physicalHostIncarnationId())
                .orElseThrow();
        HostResourceSnapshot second = snapshot(first, reloaded.next(), REGISTERED_AT.plusSeconds(1));
        restartedProcess.registerHost(registration(second));

        assertThat(reloaded).isEqualTo(HostResourceSnapshotGeneration.first());
        assertThat(restartedProcess.currentSnapshotGeneration(
                        first.physicalHostId(), first.physicalHostIncarnationId()))
                .contains(new HostResourceSnapshotGeneration(2));
        assertThat(dsl.fetchOne(
                        "select count(*) from wf_host_resource_snapshot where physical_host_id = ?",
                        first.physicalHostId().value())
                .get(0, Integer.class))
                .isEqualTo(2);
        assertThat(dsl.fetchOne(
                        "select s.snapshot_generation from wf_host_resource_snapshot s "
                                + "join wf_host_snapshot_generation_authority a "
                                + "on a.physical_host_id = s.physical_host_id "
                                + "and a.physical_host_incarnation_id = s.physical_host_incarnation_id "
                                + "and a.current_generation = s.snapshot_generation "
                                + "where s.physical_host_id = ?",
                        first.physicalHostId().value())
                .get(0, Long.class))
                .isEqualTo(2L);
    }

    @Test
    void sameIncarnationAgentRestartResumesAtNextDurableGeneration() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("agent-restart");
        var boundary = new JooqWorkerFabricRegistrationBoundary(dsl);
        HostResourceAgent firstProcess = durableAgent(runtime, boundary);

        HostResourceSnapshot generationOne = firstProcess.capture(
                runtime.context().hostAvailability(), REGISTERED_AT, Optional.empty());

        HostResourceAgent restartedProcess = durableAgent(runtime, boundary);
        HostResourceSnapshot generationTwo = restartedProcess.capture(
                runtime.context().hostAvailability(),
                REGISTERED_AT.plusSeconds(1),
                Optional.empty());

        assertThat(generationOne.snapshotGeneration())
                .isEqualTo(HostResourceSnapshotGeneration.first());
        assertThat(generationTwo.snapshotGeneration())
                .isEqualTo(generationOne.snapshotGeneration().next());
        assertThat(boundary.currentSnapshotGeneration(
                        generationTwo.physicalHostId(),
                        generationTwo.physicalHostIncarnationId()))
                .contains(generationTwo.snapshotGeneration());
        assertThat(dsl.fetchOne(
                        "select count(*) from wf_host_resource_snapshot where physical_host_id = ?",
                        generationTwo.physicalHostId().value())
                .get(0, Integer.class))
                .isEqualTo(2);
    }

    @Test
    void durableAuthorityRejectsEqualAndLowerGenerationWithoutMutatingPriorSnapshot() {
        RequestWork request = TaskCTestFixture.runtime("snapshot-regression").requestWork();
        HostResourceSnapshot first = request.hostResourceSnapshot();
        var boundary = new JooqWorkerFabricRegistrationBoundary(dsl);
        boundary.registerHost(registration(first));
        HostResourceSnapshot second = snapshot(
                first, new HostResourceSnapshotGeneration(2), REGISTERED_AT.plusSeconds(1));
        boundary.registerHost(registration(second));

        HostResourceSnapshot equalGenerationChangedContent = snapshot(
                first, new HostResourceSnapshotGeneration(2), REGISTERED_AT.plusSeconds(2));
        assertThatThrownBy(() -> boundary.registerHost(registration(equalGenerationChangedContent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly greater");
        assertThatThrownBy(() -> boundary.registerHost(registration(first)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly greater");

        assertThat(boundary.currentSnapshotGeneration(
                        first.physicalHostId(), first.physicalHostIncarnationId()))
                .contains(new HostResourceSnapshotGeneration(2));
        assertThat(dsl.fetchOne(
                        "select captured_at from wf_host_resource_snapshot "
                                + "where physical_host_id = ? and snapshot_generation = 2",
                        first.physicalHostId().value())
                .get(0, java.time.OffsetDateTime.class)
                .toInstant())
                .isEqualTo(REGISTERED_AT.plusSeconds(1));
    }

    @Test
    void databaseRejectsPublishedSnapshotUpdateAndGenerationRegression() {
        HostResourceSnapshot first =
                TaskCTestFixture.runtime("snapshot-db-guard").requestWork().hostResourceSnapshot();
        new JooqWorkerFabricRegistrationBoundary(dsl).registerHost(registration(first));

        assertThatThrownBy(() -> dsl.execute(
                        "update wf_host_resource_snapshot set captured_at = captured_at + interval '1 second' "
                                + "where physical_host_id = ?",
                        first.physicalHostId().value()))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> dsl.execute(
                        "update wf_host_snapshot_generation_authority set current_generation = 1 "
                                + "where physical_host_id = ?",
                        first.physicalHostId().value()))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("must increase");
    }

    @Test
    void databaseRejectsPublishedSnapshotDelete() {
        HostResourceSnapshot snapshot = publish("snapshot-delete-guard");

        assertThatThrownBy(() -> dsl.execute(
                        "delete from wf_host_resource_snapshot where physical_host_id = ?",
                        snapshot.physicalHostId().value()))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("snapshot publication is immutable");
    }

    @Test
    void databaseRejectsPublishedDeviceMembershipUpdateAndDelete() {
        HostResourceSnapshot snapshot = publish("snapshot-device-mutation-guard");
        assertThat(dsl.fetchOne(
                        "select count(*) from wf_host_resource_snapshot_device "
                                + "where physical_host_id = ?",
                        snapshot.physicalHostId().value())
                .get(0, Integer.class))
                .isOne();

        assertThatThrownBy(() -> dsl.execute(
                        "update wf_host_resource_snapshot_device "
                                + "set vram_bytes = vram_bytes + 1 "
                                + "where physical_host_id = ?",
                        snapshot.physicalHostId().value()))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("device membership is immutable");
        assertThatThrownBy(() -> dsl.execute(
                        "delete from wf_host_resource_snapshot_device where physical_host_id = ?",
                        snapshot.physicalHostId().value()))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("device membership is immutable");
    }

    @Test
    void databaseAllowsDeviceMembershipOnlyInSnapshotPublicationTransaction() {
        HostResourceSnapshot snapshot = publish("snapshot-device-late-insert-guard");

        assertThatThrownBy(() -> dsl.execute(
                        """
                        insert into wf_host_resource_snapshot_device (
                            physical_host_id, physical_host_incarnation_id, snapshot_generation,
                            device_id, vram_bytes, compute_units, encoder_engines, decoder_engines,
                            safety_headroom_vram_bytes, safety_headroom_compute_units,
                            safety_headroom_encoder_engines, safety_headroom_decoder_engines)
                        values (?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0)
                        """,
                        snapshot.physicalHostId().value(),
                        snapshot.physicalHostIncarnationId().value(),
                        snapshot.snapshotGeneration().value(),
                        "late-gpu"))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("must be inserted in snapshot publication transaction");
    }

    private static HostResourceSnapshot publish(String suffix) {
        HostResourceSnapshot snapshot =
                TaskCTestFixture.runtime(suffix).requestWork().hostResourceSnapshot();
        new JooqWorkerFabricRegistrationBoundary(dsl).registerHost(registration(snapshot));
        return snapshot;
    }

    private static HostResourceAgent durableAgent(
            TaskCTestFixture.RuntimeFixture runtime,
            JooqWorkerFabricRegistrationBoundary boundary) {
        HostResourceSnapshot evidence = runtime.requestWork().hostResourceSnapshot();
        HostResourceAgent.ResourceProbe probe = new HostResourceAgent.ResourceProbe() {
            @Override
            public PhysicalHostDescriptor fingerprintStaticHostResources(PhysicalHostId hostId) {
                assertThat(hostId).isEqualTo(evidence.physicalHostId());
                return runtime.context().physicalHost();
            }

            @Override
            public CapacitySnapshot collectStaticCapacity(PhysicalHostDescriptor ignored) {
                return evidence.staticCapacity();
            }

            @Override
            public ObservedUsage collectHostAndDeviceObservation(
                    PhysicalHostDescriptor ignored) {
                return evidence.observedUsage();
            }
        };
        return new HostResourceAgent(
                evidence.physicalHostId(),
                probe,
                boundary::currentSnapshotGeneration,
                snapshot -> boundary.registerHost(registration(snapshot)));
    }

    private static WorkerFabricRegistrationBoundary.HostRegistration registration(
            HostResourceSnapshot snapshot) {
        return new WorkerFabricRegistrationBoundary.HostRegistration(
                snapshot.physicalHostId(),
                snapshot.physicalHostIncarnationId(),
                snapshot,
                SafetyHeadroom.none(),
                REGISTERED_AT.minusSeconds(1),
                REGISTERED_AT.plusSeconds(3600));
    }

    private static HostResourceSnapshot snapshot(
            HostResourceSnapshot template,
            HostResourceSnapshotGeneration generation,
            Instant capturedAt) {
        return new HostResourceSnapshot(
                template.physicalHostId(),
                template.physicalHostIncarnationId(),
                generation,
                capturedAt,
                template.schemaVersion(),
                template.staticCapacity(),
                template.observedUsage(),
                Optional.empty());
    }
}
