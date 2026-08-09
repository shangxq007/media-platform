package com.example.platform.workflow.execution.infrastructure;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.workflow.execution.domain.WorkflowExecution;
import com.example.platform.workflow.execution.domain.WorkflowExecutionId;
import com.example.platform.workflow.execution.domain.WorkflowExecutionStatus;
import com.example.platform.workflow.execution.domain.WorkflowExecutionTrigger;
import com.example.platform.workflow.execution.port.WorkflowExecutionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC persistence for {@link WorkflowExecution} (minimal single-table
 * product authority, UWE-ADR-006).
 */
@Repository
public class WorkflowExecutionJdbcRepository implements WorkflowExecutionRepository {

    private final JdbcTemplate jdbc;

    public WorkflowExecutionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkflowExecution> MAPPER = (rs, rowNum) -> new WorkflowExecution(
            new WorkflowExecutionId(rs.getString("execution_id"), rs.getString("tenant_id")),
            new CanonicalActorRef(rs.getString("actor_id"), rs.getString("actor_type")),
            rs.getString("definition_id"),
            rs.getInt("definition_version"),
            WorkflowExecutionTrigger.valueOf(rs.getString("trigger_type")),
            WorkflowExecutionStatus.valueOf(rs.getString("status")),
            rs.getString("temporal_workflow_id"),
            rs.getString("idempotency_key"),
            rs.getString("input_refs_json"),
            rs.getString("result_summary_json"),
            rs.getString("error_category"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("started_at")),
            toInstant(rs.getTimestamp("completed_at")));

    private static Instant toInstant(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    @Override
    public void insert(WorkflowExecution e) {
        jdbc.update("""
                insert into workflow_execution (
                    execution_id, tenant_id, actor_type, actor_id,
                    definition_id, definition_version, trigger_type, status,
                    temporal_workflow_id, idempotency_key, input_refs_json,
                    result_summary_json, error_category, created_at, started_at, completed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                e.executionId().executionId(), e.executionId().tenantId(),
                e.actor().actorType(), e.actor().actorId(),
                e.definitionId(), e.definitionVersion(), e.trigger().name(), e.status().name(),
                e.temporalWorkflowId(), e.idempotencyKey(), e.inputRefsJson(),
                e.resultSummaryJson(), e.errorCategory(),
                java.sql.Timestamp.from(e.createdAt()),
                e.startedAt() == null ? null : java.sql.Timestamp.from(e.startedAt()),
                e.completedAt() == null ? null : java.sql.Timestamp.from(e.completedAt()));
    }

    @Override
    public void updateStatus(WorkflowExecution e) {
        jdbc.update("""
                update workflow_execution
                set status = ?, result_summary_json = ?, error_category = ?,
                    started_at = ?, completed_at = ?
                where execution_id = ? and tenant_id = ?
                """,
                e.status().name(), e.resultSummaryJson(), e.errorCategory(),
                e.startedAt() == null ? null : java.sql.Timestamp.from(e.startedAt()),
                e.completedAt() == null ? null : java.sql.Timestamp.from(e.completedAt()),
                e.executionId().executionId(), e.executionId().tenantId());
    }

    @Override
    public Optional<WorkflowExecution> findById(WorkflowExecutionId id) {
        return jdbc.query("""
                select * from workflow_execution
                where execution_id = ? and tenant_id = ?
                """, MAPPER, id.executionId(), id.tenantId())
                .stream().findFirst();
    }

    @Override
    public Optional<WorkflowExecution> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return jdbc.query("""
                select * from workflow_execution
                where tenant_id = ? and idempotency_key = ?
                """, MAPPER, tenantId, idempotencyKey)
                .stream().findFirst();
    }
}
