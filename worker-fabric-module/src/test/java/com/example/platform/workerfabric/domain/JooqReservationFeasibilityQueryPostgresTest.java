package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workerfabric.infrastructure.JooqAtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.infrastructure.JooqReservationFeasibilityQuery;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricRegistrationBoundary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P1-1A.5: real-PostgreSQL acceptance for the read-only reservation-feasibility model.
 *
 * <p>Proves the three-state contract against the canonical schema: FEASIBLE when capacity is
 * sufficient with no over-reservation, CONFLICT when a live reservation exhausts capacity, and
 * UNKNOWN whenever required evidence is missing, generation-mismatched, device capacity is absent, or
 * the read fails. Reservation rows are produced by the REAL grant boundary (no hand-written chain),
 * so the arithmetic semantics match what the final in-transaction check consumes.
 */
class JooqReservationFeasibilityQueryPostgresTest extends PostgresTestContainerSupport {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

    private static com.example.platform.shared.test.TestDatabaseSchema schema;
    private static DataSource dataSource;
    private static DSLContext dsl;
    private static JooqReservationFeasibilityQuery query;

    @BeforeAll
    static void startDatabaseAuthority() throws Exception {
        schema = com.example.platform.shared.test.TestDatabaseSchema.open(PROVIDER);
        dataSource = createDataSourceForSchema(schema.name());
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        AssignmentGrantPostgresFixture.migrate(dataSource);
        query = new JooqReservationFeasibilityQuery(dsl);
    }

    @AfterAll
    static void closeDatabaseAuthority() throws Exception {
        closeDataSource(dataSource);
        if (schema != null) schema.close();
    }

    @BeforeEach
    void resetAuthority() {
        AssignmentGrantPostgresFixture.truncate(dsl);
    }

    @Test
    void feasibleWhenCapacityIsSufficientAndNothingIsReserved() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("rfeas-feasible");
        registerHost(runtime);
        RequestWork request = runtime.requestWork();

        ReservationFeasibility result = query.assess(
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                request.hostResourceSnapshot().snapshotGeneration(),
                new RuntimeResourceDemand(1_000, 1_000, 1_000, Map.of()));

        assertThat(result).isEqualTo(ReservationFeasibility.FEASIBLE);
    }

    @Test
    void conflictWhenAnActiveReservationExhaustsCapacity() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("rfeas-conflict");
        registerHost(runtime);
        registerRuntime(runtime);

        // Create one real ACTIVE reservation consuming 5000 of the host's 8000 millicores.
        RequestWorkResult granted = new CentralWorkMatcher(boundary()).match(
                runtime.requestWork(),
                runtime.context(),
                List.of(TaskCTestFixture.candidate(
                        901, new RuntimeResourceDemand(5_000, 0, 0, Map.of())).candidate()));
        assertThat(granted).isInstanceOf(RequestWorkResult.Granted.class);
        assertThat(countReservations()).isOne();

        RequestWork request = runtime.requestWork();
        ReservationFeasibility result = query.assess(
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                request.hostResourceSnapshot().snapshotGeneration(),
                new RuntimeResourceDemand(5_000, 0, 0, Map.of()));

        assertThat(result).isEqualTo(ReservationFeasibility.CONFLICT);
    }

    @Test
    void unknownWhenSnapshotGenerationIsMissing() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("rfeas-genmissing");
        registerHost(runtime);
        RequestWork request = runtime.requestWork();

        ReservationFeasibility result = query.assess(
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                new HostResourceSnapshotGeneration(
                        request.hostResourceSnapshot().snapshotGeneration().value() + 1),
                new RuntimeResourceDemand(1_000, 0, 0, Map.of()));

        assertThat(result).isEqualTo(ReservationFeasibility.UNKNOWN);
    }

    @Test
    void unknownWhenRequiredDeviceCapacityIsMissing() {
        TaskCTestFixture.RuntimeFixture runtime = TaskCTestFixture.runtime("rfeas-devicemissing");
        registerHost(runtime);
        RequestWork request = runtime.requestWork();
        DeviceId absent = DeviceId.of("device-not-exposed");

        ReservationFeasibility result = query.assess(
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                request.hostResourceSnapshot().snapshotGeneration(),
                new RuntimeResourceDemand(0, 0, 0, Map.of(
                        absent, new RuntimeResourceDemand.DeviceDemand(absent, 1, 1, 1, 1))));

        assertThat(result).isEqualTo(ReservationFeasibility.UNKNOWN);
    }

    @Test
    void unknownWhenTheReadFails() {
        DSLContext failing = mock(DSLContext.class);
        when(failing.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessException("simulated read failure"));
        JooqReservationFeasibilityQuery failingQuery = new JooqReservationFeasibilityQuery(failing);

        ReservationFeasibility result = failingQuery.assess(
                PhysicalHostId.of("host-x"),
                PhysicalHostIncarnationId.of("inc-x"),
                HostResourceSnapshotGeneration.first(),
                new RuntimeResourceDemand(1_000, 0, 0, Map.of()));

        assertThat(result).isEqualTo(ReservationFeasibility.UNKNOWN);
    }

    private static void registerHost(TaskCTestFixture.RuntimeFixture runtime) {
        RequestWork request = runtime.requestWork();
        new JooqWorkerFabricRegistrationBoundary(dsl).registerHost(
                new WorkerFabricRegistrationBoundary.HostRegistration(
                        request.physicalHostId(),
                        request.physicalHostIncarnationId(),
                        request.hostResourceSnapshot(),
                        SafetyHeadroom.none(),
                        FIXED_CLOCK.instant().minusSeconds(1),
                        FIXED_CLOCK.instant().plusSeconds(3600)));
    }

    private static void registerRuntime(TaskCTestFixture.RuntimeFixture runtime) {
        RequestWork request = runtime.requestWork();
        new JooqWorkerFabricRegistrationBoundary(dsl).registerRuntime(
                new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                        request.workerRuntimeId(),
                        request.workerRuntimeIncarnationId(),
                        request.physicalHostId(),
                        request.physicalHostIncarnationId(),
                        FIXED_CLOCK.instant().minusSeconds(1),
                        FIXED_CLOCK.instant().plusSeconds(3600)));
    }

    private static JooqAtomicAssignmentGrantBoundary boundary() {
        return new JooqAtomicAssignmentGrantBoundary(
                dsl, FIXED_CLOCK, LeaseRenewalContract.NATIVE_PULL_V1);
    }

    private static int countReservations() {
        return dsl.fetchOne("select count(*) from wf_reservation").get(0, Integer.class);
    }
}
