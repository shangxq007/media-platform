package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workerfabric.infrastructure.JooqExecutionBackendSelectionAuthority;
import java.time.Instant;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Durable ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY_V1 PostgreSQL acceptance. */
class ExecutionBackendSelectionPostgresTest extends PostgresTestContainerSupport {

    private static final Instant SELECTED_AT = Instant.parse("2026-08-24T12:00:00Z");
    private static DataSource dataSource;
    private static DSLContext dsl;

    @BeforeAll
    static void migrateCanonicalSchema() {
        dataSource = createDataSource();
        AssignmentGrantPostgresFixture.migrate(dataSource);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
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
    void f4SecondSameBackendSelectionWhileActiveIsRejected() {
        var scenario = TaskBTestFixture.scenario("f4-same", "unit");
        var selection = TaskBTestFixture.selection(
                scenario, ExecutionBackend.NATIVE_PULL_WORKER);
        var authority = new JooqExecutionBackendSelectionAuthority(dsl);

        var first = authority.activate(selection, SELECTED_AT);
        var second = authority.activate(selection, SELECTED_AT.plusSeconds(1));

        assertThat(first.activated()).isTrue();
        assertThat(second.status()).isEqualTo(
                ExecutionBackendSelectionAuthority.ActivationStatus.REJECTED_ACTIVE_SELECTION);
        assertThat(second.authoritativeSelection()).isEqualTo(first.authoritativeSelection());
        assertThat(activeCount(scenario.task().id().sha256Hex())).isOne();
    }

    @Test
    void f4SecondDifferentBackendSelectionWhileActiveIsRejected() {
        var scenario = TaskBTestFixture.scenario("f4-different", "unit");
        var nativeSelection = TaskBTestFixture.selection(
                scenario, ExecutionBackend.NATIVE_PULL_WORKER);
        var delegatedSelection = TaskBTestFixture.selection(
                scenario, ExecutionBackend.OPEN_CUE_FARM);
        var authority = new JooqExecutionBackendSelectionAuthority(dsl);

        var first = authority.activate(nativeSelection, SELECTED_AT);
        var second = authority.activate(delegatedSelection, SELECTED_AT.plusSeconds(1));

        assertThat(first.activated()).isTrue();
        assertThat(second.status()).isEqualTo(
                ExecutionBackendSelectionAuthority.ActivationStatus.REJECTED_ACTIVE_SELECTION);
        assertThat(second.authoritativeSelection().backend())
                .isEqualTo(ExecutionBackend.NATIVE_PULL_WORKER);
        assertThat(activeCount(scenario.task().id().sha256Hex())).isOne();
    }

    @Test
    void f4ReplacementAfterTerminalStateIsAllowed() {
        var scenario = TaskBTestFixture.scenario("f4-terminal", "unit");
        var authority = new JooqExecutionBackendSelectionAuthority(dsl);
        var first = authority.activate(
                TaskBTestFixture.selection(scenario, ExecutionBackend.NATIVE_PULL_WORKER),
                SELECTED_AT);

        assertThat(authority.markTerminal(
                first.authoritativeSelection().id(), SELECTED_AT.plusSeconds(10))).isTrue();
        var replacement = authority.activate(
                TaskBTestFixture.selection(scenario, ExecutionBackend.OPEN_CUE_FARM),
                SELECTED_AT.plusSeconds(11));

        assertThat(replacement.activated()).isTrue();
        assertThat(replacement.authoritativeSelection().id())
                .isNotEqualTo(first.authoritativeSelection().id());
        assertThat(replacement.authoritativeSelection().backend())
                .isEqualTo(ExecutionBackend.OPEN_CUE_FARM);
        assertThat(activeCount(scenario.task().id().sha256Hex())).isOne();
    }

    @Test
    void f4UniquenessAndSelectionSurviveRepositoryReload() {
        var scenario = TaskBTestFixture.scenario("f4-reload", "unit");
        var selection = TaskBTestFixture.selection(
                scenario, ExecutionBackend.REMOTE_PROVIDER);
        var firstProcess = new JooqExecutionBackendSelectionAuthority(dsl);
        var activated = firstProcess.activate(selection, SELECTED_AT);

        DSLContext reloadedDsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        var restartedProcess = new JooqExecutionBackendSelectionAuthority(reloadedDsl);

        assertThat(restartedProcess.findActive(scenario.task().id()))
                .contains(activated.authoritativeSelection());
        assertThat(restartedProcess.activate(selection, SELECTED_AT.plusSeconds(1)).status())
                .isEqualTo(
                        ExecutionBackendSelectionAuthority.ActivationStatus.REJECTED_ACTIVE_SELECTION);
        assertThat(activeCount(scenario.task().id().sha256Hex())).isOne();
    }

    private static int activeCount(String taskId) {
        return dsl.fetchOne(
                "select count(*) from wf_execution_backend_selection where task_id = ? and active",
                taskId).get(0, Integer.class);
    }
}
