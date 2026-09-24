package com.example.platform.composition.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads only immutable published rows; no caller-supplied revision is trusted. */
@Repository
public final class JdbcCompositionPublishedRevisionResolver implements CompositionPublishedRevisionAuthority {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcCompositionPublishedRevisionResolver(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc); this.json = java.util.Objects.requireNonNull(json);
    }
    @Override public Optional<PublishedRevision> resolve(String tenantId, String workspaceId,
            String compositionId, String version) {
        return jdbc.query("select definition::text from composition_version where tenant_id=? and workspace_id=? and kind='WORKFLOW' and composition_id=? and version=?",
                rs -> rs.next() ? Optional.of(toRevision(rs.getString(1))) : Optional.empty(), tenantId, workspaceId, compositionId, version);
    }
    private PublishedRevision toRevision(String definition) {
        try {
            var workflow = json.readValue(definition, com.example.platform.composition.domain.CompositionModels.TemplateWorkflow.class);
            if (workflow.lifecycle() != com.example.platform.composition.domain.CompositionModels.Lifecycle.PUBLISHED
                    || workflow.id() == null || workflow.version() == null || workflow.revision() < 1)
                throw new IllegalArgumentException("persisted Composition row is not a published immutable revision");
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(definition.getBytes(StandardCharsets.UTF_8)));
            return new PublishedRevision(workflow, fingerprint);
        } catch (Exception e) { throw new IllegalStateException("published Composition revision is unreadable", e); }
    }
}
