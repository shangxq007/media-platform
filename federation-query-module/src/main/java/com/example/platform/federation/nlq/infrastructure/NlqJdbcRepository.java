package com.example.platform.federation.nlq.infrastructure;

import com.example.platform.federation.nlq.domain.QueryHistoryRecord;
import com.example.platform.federation.nlq.domain.ReportDefinition;
import com.example.platform.federation.nlq.domain.ReportExecution;
import com.example.platform.federation.nlq.domain.ReportSchedule;
import com.example.platform.federation.nlq.domain.ReportWidget;
import com.example.platform.shared.Jsons;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class NlqJdbcRepository {

    private static final TypeReference<List<ReportWidget>> WIDGET_LIST =
            new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public NlqJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveReport(ReportDefinition report) {
        int updated = jdbc.update("""
                UPDATE nlq_report_definition SET
                tenant_id = ?, workspace_id = ?, name = ?, description = ?,
                widgets_json = ?, query_definitions_json = ?, created_by = ?,
                visibility = ?, schedule_json = ?, updated_at = ?, archived = ?
                WHERE report_id = ?
                """,
                report.tenantId(),
                report.workspaceId(),
                report.name(),
                report.description(),
                Jsons.toJson(report.widgets()),
                Jsons.toJson(report.queryDefinitions()),
                report.createdBy(),
                report.visibility(),
                report.schedule() != null ? Jsons.toJson(report.schedule()) : null,
                Timestamp.from(report.updatedAt()),
                report.archived(),
                report.reportId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO nlq_report_definition
                    (report_id, tenant_id, workspace_id, name, description, widgets_json,
                     query_definitions_json, created_by, visibility, schedule_json,
                     created_at, updated_at, archived)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    report.reportId(),
                    report.tenantId(),
                    report.workspaceId(),
                    report.name(),
                    report.description(),
                    Jsons.toJson(report.widgets()),
                    Jsons.toJson(report.queryDefinitions()),
                    report.createdBy(),
                    report.visibility(),
                    report.schedule() != null ? Jsons.toJson(report.schedule()) : null,
                    Timestamp.from(report.createdAt()),
                    Timestamp.from(report.updatedAt()),
                    report.archived());
        }
    }

    public void saveQueryHistory(QueryHistoryRecord record) {
        jdbc.update("""
                INSERT INTO nlq_query_history
                (query_id, user_id, tenant_id, workspace_id, question_redacted, sql_hash,
                 datasets_json, row_count, duration_ms, risk_level, status, error_code, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.queryId(),
                record.userId(),
                record.tenantId(),
                record.workspaceId(),
                record.questionRedacted(),
                record.sqlHash(),
                Jsons.toJson(record.datasets()),
                record.rowCount(),
                record.durationMs(),
                record.riskLevel(),
                record.status(),
                record.errorCode(),
                Timestamp.from(record.createdAt()));
    }

    public void saveReportExecution(ReportExecution execution) {
        jdbc.update("""
                INSERT INTO nlq_report_execution
                (execution_id, report_id, status, row_count, duration_ms, error_code, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                execution.executionId(),
                execution.reportId(),
                execution.status(),
                execution.rowCount(),
                execution.durationMs(),
                execution.errorCode(),
                Timestamp.from(execution.createdAt()));
    }

    public List<ReportDefinition> loadAllReports() {
        return jdbc.query("SELECT * FROM nlq_report_definition ORDER BY updated_at DESC", this::mapReport);
    }

    public List<QueryHistoryRecord> loadAllQueryHistory() {
        return jdbc.query("SELECT * FROM nlq_query_history ORDER BY created_at DESC", this::mapHistory);
    }

    public List<ReportExecution> loadAllReportExecutions() {
        return jdbc.query("SELECT * FROM nlq_report_execution ORDER BY created_at DESC", this::mapExecution);
    }

    private ReportDefinition mapReport(ResultSet rs, int rowNum) throws SQLException {
        String scheduleJson = rs.getString("schedule_json");
        ReportSchedule schedule = scheduleJson != null && !scheduleJson.isBlank()
                ? Jsons.fromJson(scheduleJson, ReportSchedule.class)
                : null;
        return new ReportDefinition(
                rs.getString("report_id"),
                rs.getString("tenant_id"),
                rs.getString("workspace_id"),
                rs.getString("name"),
                rs.getString("description"),
                parseWidgets(rs.getString("widgets_json")),
                parseStringList(rs.getString("query_definitions_json")),
                rs.getString("created_by"),
                rs.getString("visibility"),
                schedule,
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getBoolean("archived"));
    }

    private QueryHistoryRecord mapHistory(ResultSet rs, int rowNum) throws SQLException {
        return new QueryHistoryRecord(
                rs.getString("query_id"),
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                rs.getString("workspace_id"),
                rs.getString("question_redacted"),
                rs.getString("sql_hash"),
                parseStringList(rs.getString("datasets_json")),
                rs.getInt("row_count"),
                rs.getLong("duration_ms"),
                rs.getString("risk_level"),
                rs.getString("status"),
                rs.getString("error_code"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private ReportExecution mapExecution(ResultSet rs, int rowNum) throws SQLException {
        return new ReportExecution(
                rs.getString("execution_id"),
                rs.getString("report_id"),
                rs.getString("status"),
                rs.getInt("row_count"),
                rs.getLong("duration_ms"),
                rs.getString("error_code"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private static List<ReportWidget> parseWidgets(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return Jsons.fromJson(json, WIDGET_LIST);
    }

    private static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return Jsons.fromJson(json, STRING_LIST);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }
}
