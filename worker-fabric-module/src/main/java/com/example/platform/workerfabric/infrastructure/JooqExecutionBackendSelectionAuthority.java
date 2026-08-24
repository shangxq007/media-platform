package com.example.platform.workerfabric.infrastructure;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.DurableExecutionBackendSelection;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ExecutionBackendSelection;
import com.example.platform.workerfabric.domain.ExecutionBackendSelectionAuthority;
import com.example.platform.workerfabric.domain.ExecutionBackendSelectionId;
import com.example.platform.workerfabric.domain.PlacementAuthorityScope;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Durable unique active placement-authority implementation. */
@Repository
public class JooqExecutionBackendSelectionAuthority
        implements ExecutionBackendSelectionAuthority {

    private final DSLContext dsl;

    public JooqExecutionBackendSelectionAuthority(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public ActivationResult activate(
            ExecutionBackendSelection selection,
            Instant selectedAt) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(selectedAt, "selectedAt");
        return dsl.transactionResult(configuration ->
                activate(DSL.using(configuration), selection, selectedAt));
    }

    static ActivationResult activate(
            DSLContext tx,
            ExecutionBackendSelection selection,
            Instant selectedAt) {
        Record inserted = tx.fetchOne(
                """
                insert into wf_execution_backend_selection (
                    selection_id, task_id, backend, placement_authority_scope,
                    active, selected_at)
                values (?, ?, ?, ?, true, cast(? as timestamptz))
                on conflict (task_id) where active do nothing
                returning *
                """,
                "backend-selection-" + UUID.randomUUID(),
                selection.executableTaskId().sha256Hex(),
                selection.backend().name(),
                selection.placementAuthorityScope().name(),
                databaseTime(selectedAt));
        if (inserted != null) {
            return new ActivationResult(ActivationStatus.ACTIVATED, from(inserted));
        }
        Record active = tx.fetchOne(
                """
                select * from wf_execution_backend_selection
                 where task_id = ? and active
                 for update
                """,
                selection.executableTaskId().sha256Hex());
        if (active == null) {
            throw new IllegalStateException(
                    "active backend selection conflict disappeared inside transaction");
        }
        return new ActivationResult(
                ActivationStatus.REJECTED_ACTIVE_SELECTION,
                from(active));
    }

    @Override
    public boolean markTerminal(
            ExecutionBackendSelectionId selectionId,
            Instant terminalAt) {
        Objects.requireNonNull(selectionId, "selectionId");
        Objects.requireNonNull(terminalAt, "terminalAt");
        return dsl.execute(
                """
                update wf_execution_backend_selection
                   set active = false, terminal_at = cast(? as timestamptz)
                 where selection_id = ? and active
                """,
                databaseTime(terminalAt),
                selectionId.value()) == 1;
    }

    @Override
    public Optional<DurableExecutionBackendSelection> findActive(
            ExecutableTaskId executableTaskId) {
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Record row = dsl.fetchOne(
                "select * from wf_execution_backend_selection where task_id = ? and active",
                executableTaskId.sha256Hex());
        return Optional.ofNullable(row).map(JooqExecutionBackendSelectionAuthority::from);
    }

    private static DurableExecutionBackendSelection from(Record row) {
        return new DurableExecutionBackendSelection(
                new ExecutionBackendSelectionId(row.get("selection_id", String.class)),
                new ExecutableTaskId(row.get("task_id", String.class)),
                ExecutionBackend.valueOf(row.get("backend", String.class)),
                PlacementAuthorityScope.valueOf(
                        row.get("placement_authority_scope", String.class)),
                row.get("selected_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
