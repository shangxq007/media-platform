package com.example.platform.prompt.infrastructure;

import com.example.platform.prompt.domain.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JDBC persistence for prompt templates, versions, and execution runs (Flyway V3).
 */
@Repository

public class PromptJdbcRepository {

    private final JdbcTemplate jdbc;

    public PromptJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveTemplate(PromptTemplate template) {
        String tags = template.tags() == null || template.tags().isEmpty()
                ? null
                : String.join(",", template.tags());
        int updated = jdbc.update("""
                UPDATE prompt_template SET
                name = ?, description = ?, category = ?, tags = ?, owner = ?, status = ?,
                schema_version = ?, current_prompt_version = ?, updated_at = ?
                WHERE template_id = ?
                """,
                template.name(),
                template.description(),
                template.category(),
                tags,
                template.owner(),
                template.status().name(),
                template.schemaVersion(),
                template.currentPromptVersion(),
                Timestamp.from(template.updatedAt().toInstant()),
                template.templateId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO prompt_template
                    (template_id, name, description, category, tags, owner, status,
                     schema_version, current_prompt_version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    template.templateId(),
                    template.name(),
                    template.description(),
                    template.category(),
                    tags,
                    template.owner(),
                    template.status().name(),
                    template.schemaVersion(),
                    template.currentPromptVersion(),
                    Timestamp.from(template.createdAt().toInstant()),
                    Timestamp.from(template.updatedAt().toInstant()));
        }
    }

    public void saveVersion(PromptTemplateVersion version) {
        int updated = jdbc.update("""
                UPDATE prompt_template_version SET
                template_body = ?, variable_schema_json = ?, changelog = ?, created_by = ?,
                checksum = ?, previous_version = ?, deprecated = ?
                WHERE version_id = ?
                """,
                version.templateBody(),
                version.variableSchemaJson(),
                version.changelog(),
                version.createdBy(),
                version.checksum(),
                version.previousVersion(),
                version.deprecated(),
                version.versionId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO prompt_template_version
                    (version_id, template_id, prompt_version, template_body, variable_schema_json,
                     changelog, created_by, created_at, checksum, previous_version, deprecated)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    version.versionId(),
                    version.templateId(),
                    version.promptVersion(),
                    version.templateBody(),
                    version.variableSchemaJson(),
                    version.changelog(),
                    version.createdBy(),
                    Timestamp.from(version.createdAt().toInstant()),
                    version.checksum(),
                    version.previousVersion(),
                    version.deprecated());
        }
    }

    public void saveExecution(PromptExecutionRun run) {
        int updated = jdbc.update("""
                UPDATE prompt_execution_run SET
                status = ?, risk_level = ?, token_estimate = ?, cost_estimate = ?,
                output_summary = ?, finished_at = ?, error_code = ?, error_details_json = ?
                WHERE execution_id = ?
                """,
                run.status().name(),
                run.riskLevel().name(),
                run.tokenEstimate(),
                run.costEstimate(),
                run.outputSummary(),
                run.finishedAt() != null ? Timestamp.from(run.finishedAt().toInstant()) : null,
                run.errorCode(),
                run.errorDetailsJson(),
                run.executionId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO prompt_execution_run
                    (execution_id, template_id, prompt_version, tenant_id, user_id,
                     model_provider, model_name, rendered_prompt_hash, redacted_prompt_preview,
                     input_variables_redacted_json, output_summary, status, risk_level,
                     token_estimate, cost_estimate, started_at, finished_at,
                     error_code, error_details_json, related_prompt_file, related_manifest_entry)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    run.executionId(),
                    run.templateId(),
                    run.promptVersion(),
                    run.tenantId(),
                    run.userId(),
                    run.modelProvider(),
                    run.modelName(),
                    run.renderedPromptHash(),
                    run.redactedPromptPreview(),
                    run.inputVariablesRedactedJson(),
                    run.outputSummary(),
                    run.status().name(),
                    run.riskLevel().name(),
                    run.tokenEstimate(),
                    run.costEstimate(),
                    Timestamp.from(run.startedAt().toInstant()),
                    run.finishedAt() != null ? Timestamp.from(run.finishedAt().toInstant()) : null,
                    run.errorCode(),
                    run.errorDetailsJson(),
                    run.relatedPromptFile(),
                    run.relatedManifestEntry());
        }
    }

    public boolean existsByDerivedCode(String code) {
        List<PromptTemplate> all = loadAllTemplates();
        return all.stream().anyMatch(t -> derivedCode(t.name()).equals(code));
    }

    public List<PromptTemplate> loadAllTemplates() {
        return jdbc.query("SELECT * FROM prompt_template ORDER BY created_at", this::mapTemplate);
    }

    public List<PromptTemplateVersion> loadVersionsForTemplate(String templateId) {
        return jdbc.query(
                "SELECT * FROM prompt_template_version WHERE template_id = ? ORDER BY created_at",
                this::mapVersion,
                templateId);
    }

    public List<PromptExecutionRun> loadAllExecutions() {
        return jdbc.query("SELECT * FROM prompt_execution_run ORDER BY started_at", this::mapExecution);
    }

    public Optional<PromptTemplate> findTemplateById(String templateId) {
        List<PromptTemplate> rows = jdbc.query(
                "SELECT * FROM prompt_template WHERE template_id = ?",
                this::mapTemplate,
                templateId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private PromptTemplate mapTemplate(ResultSet rs, int rowNum) throws SQLException {
        String tagsRaw = rs.getString("tags");
        List<String> tags = tagsRaw == null || tagsRaw.isBlank()
                ? List.of()
                : Arrays.stream(tagsRaw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new PromptTemplate(
                rs.getString("template_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("category"),
                tags,
                rs.getString("owner"),
                PromptTemplateStatus.valueOf(rs.getString("status")),
                rs.getString("schema_version"),
                rs.getString("current_prompt_version"),
                toOffset(rs.getTimestamp("created_at")),
                toOffset(rs.getTimestamp("updated_at")));
    }

    private PromptTemplateVersion mapVersion(ResultSet rs, int rowNum) throws SQLException {
        return new PromptTemplateVersion(
                rs.getString("version_id"),
                rs.getString("template_id"),
                rs.getString("prompt_version"),
                rs.getString("template_body"),
                rs.getString("variable_schema_json"),
                rs.getString("changelog"),
                rs.getString("created_by"),
                toOffset(rs.getTimestamp("created_at")),
                rs.getString("checksum"),
                rs.getString("previous_version"),
                rs.getBoolean("deprecated"));
    }

    private PromptExecutionRun mapExecution(ResultSet rs, int rowNum) throws SQLException {
        Timestamp finished = rs.getTimestamp("finished_at");
        return new PromptExecutionRun(
                rs.getString("execution_id"),
                rs.getString("template_id"),
                rs.getString("prompt_version"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("model_provider"),
                rs.getString("model_name"),
                rs.getString("rendered_prompt_hash"),
                rs.getString("redacted_prompt_preview"),
                rs.getString("input_variables_redacted_json"),
                rs.getString("output_summary"),
                PromptExecutionStatus.valueOf(rs.getString("status")),
                PromptRiskLevel.valueOf(rs.getString("risk_level")),
                rs.getInt("token_estimate"),
                rs.getDouble("cost_estimate"),
                toOffset(rs.getTimestamp("started_at")),
                finished != null ? toOffset(finished) : null,
                rs.getString("error_code"),
                rs.getString("error_details_json"),
                rs.getString("related_prompt_file"),
                rs.getString("related_manifest_entry"));
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        if (ts == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        return ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    public static String derivedCode(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
