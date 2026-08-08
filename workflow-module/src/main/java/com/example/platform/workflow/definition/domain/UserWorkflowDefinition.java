package com.example.platform.workflow.definition.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * UserWorkflowDefinition — the W2 V1 persisted, versioned, tenant-scoped
 * workflow-definition aggregate (domain-aggregate-contract.json).
 *
 * <p>Immutable value object; every transition returns a NEW instance. The
 * optimisticVersion field is the row's optimistic concurrency token (the
 * expected value for compare-and-set); the persistence adapter bumps it on
 * every successful mutation. Identity = (definitionId, versionNumber);
 * lineage identity = definitionId. No public setters; no persistence
 * annotations; no Instant.now() (timestamps are injected).
 */
public record UserWorkflowDefinition(
        UserWorkflowDefinitionId definitionId,
        UserWorkflowDefinitionVersion version,
        String tenantId,
        String projectId,
        String name,
        String description,
        UserWorkflowDefinitionStatus status,
        List<UserWorkflowDefinitionNode> nodes,
        List<UserWorkflowDefinitionEdge> edges,
        List<UserWorkflowParameterDeclaration> parameters,
        UserWorkflowTriggerBinding triggerBinding,
        int schemaVersion,
        long optimisticVersion,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Instant publishedAt,
        String publishedBy,
        Instant archivedAt,
        String archivedBy) {

    public UserWorkflowDefinition {
        if (definitionId == null) {
            throw new IllegalArgumentException("definition id must not be null");
        }
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schema version must be >= 1");
        }
        if (optimisticVersion < 1) {
            throw new IllegalArgumentException("optimistic version must be >= 1");
        }
        if (createdAt == null || createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("creation audit must be present");
        }
        if (updatedAt == null || updatedBy == null || updatedBy.isBlank()) {
            throw new IllegalArgumentException("update audit must be present");
        }
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        triggerBinding = triggerBinding == null ? UserWorkflowTriggerBinding.manual() : triggerBinding;
        if (status == UserWorkflowDefinitionStatus.PUBLISHED
                && (publishedAt == null || publishedBy == null || publishedBy.isBlank())) {
            throw new IllegalArgumentException("published status requires publication audit");
        }
        if (status == UserWorkflowDefinitionStatus.ARCHIVED
                && (archivedAt == null || archivedBy == null || archivedBy.isBlank())) {
            throw new IllegalArgumentException("archived status requires archive audit");
        }
    }

    // ── Factories ──────────────────────────────────────────────────────────

    /** Creates a new DRAFT version 1 lineage (CREATE use case). */
    public static UserWorkflowDefinition newDraft(
            UserWorkflowDefinitionId definitionId,
            String tenantId,
            String projectId,
            String name,
            String description,
            List<UserWorkflowDefinitionNode> nodes,
            List<UserWorkflowDefinitionEdge> edges,
            List<UserWorkflowParameterDeclaration> parameters,
            UserWorkflowTriggerBinding triggerBinding,
            int schemaVersion,
            String actorId,
            Instant now) {
        return new UserWorkflowDefinition(
                definitionId, UserWorkflowDefinitionVersion.of(1), tenantId, projectId,
                name, description, UserWorkflowDefinitionStatus.DRAFT,
                nodes, edges, parameters, triggerBinding, schemaVersion,
                1L, now, actorId, now, actorId, null, null, null, null);
    }

    // ── Lifecycle transitions (lifecycle-contract.tsv) ─────────────────────

    /** DRAFT -> VALIDATED. */
    public UserWorkflowDefinition markValidated(String actorId, Instant now) {
        requireStatus(UserWorkflowDefinitionStatus.DRAFT, "only DRAFT may be validated");
        return withStatus(UserWorkflowDefinitionStatus.VALIDATED, actorId, now);
    }

    /** VALIDATED -> DRAFT. */
    public UserWorkflowDefinition reopenDraft(String actorId, Instant now) {
        requireStatus(UserWorkflowDefinitionStatus.VALIDATED, "only VALIDATED may be reopened");
        return withStatus(UserWorkflowDefinitionStatus.DRAFT, actorId, now);
    }

    /** VALIDATED -> PUBLISHED (immutable afterwards). */
    public UserWorkflowDefinition publish(String actorId, Instant now) {
        requireStatus(UserWorkflowDefinitionStatus.VALIDATED, "only VALIDATED may be published");
        return new UserWorkflowDefinition(definitionId, version, tenantId, projectId,
                name, description, UserWorkflowDefinitionStatus.PUBLISHED,
                nodes, edges, parameters, triggerBinding, schemaVersion,
                optimisticVersion, createdAt, createdBy, now, actorId, now, actorId, null, null);
    }

    /** DRAFT | VALIDATED | PUBLISHED -> ARCHIVED (terminal). */
    public UserWorkflowDefinition archive(String actorId, Instant now) {
        if (status == UserWorkflowDefinitionStatus.ARCHIVED) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.INVALID_LIFECYCLE_TRANSITION,
                    "archived definitions are terminal");
        }
        return new UserWorkflowDefinition(definitionId, version, tenantId, projectId,
                name, description, UserWorkflowDefinitionStatus.ARCHIVED,
                nodes, edges, parameters, triggerBinding, schemaVersion,
                optimisticVersion, createdAt, createdBy, now, actorId,
                publishedAt, publishedBy, now, actorId);
    }

    /**
     * UPDATE_DRAFT content mutation. DRAFT only; PUBLISHED/ARCHIVED are
     * immutable (PUBLISHED_IMMUTABLE); VALIDATED must be reopened first.
     */
    public UserWorkflowDefinition updatedDraft(
            String name,
            String description,
            List<UserWorkflowDefinitionNode> nodes,
            List<UserWorkflowDefinitionEdge> edges,
            List<UserWorkflowParameterDeclaration> parameters,
            UserWorkflowTriggerBinding triggerBinding,
            String actorId,
            Instant now) {
        if (status == UserWorkflowDefinitionStatus.PUBLISHED
                || status == UserWorkflowDefinitionStatus.ARCHIVED) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.PUBLISHED_IMMUTABLE,
                    "published or archived versions are immutable; create a new version");
        }
        if (status != UserWorkflowDefinitionStatus.DRAFT) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.INVALID_LIFECYCLE_TRANSITION,
                    "only DRAFT versions may be updated; reopen the draft first");
        }
        return new UserWorkflowDefinition(definitionId, version, tenantId, projectId,
                name, description, status, nodes, edges, parameters, triggerBinding,
                schemaVersion, optimisticVersion, createdAt, createdBy, now, actorId,
                null, null, null, null);
    }

    /**
     * CREATE_VERSION: new DRAFT from a PUBLISHED (or ARCHIVED) source.
     * Content is copied; lifecycle resets to DRAFT; optimisticVersion = 1.
     */
    public UserWorkflowDefinition createNextVersion(String actorId, Instant now) {
        if (status != UserWorkflowDefinitionStatus.PUBLISHED
                && status != UserWorkflowDefinitionStatus.ARCHIVED) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.NO_PUBLISHED_SOURCE,
                    "new versions require a published (or archived) source");
        }
        return new UserWorkflowDefinition(definitionId, version.next(), tenantId, projectId,
                name, description, UserWorkflowDefinitionStatus.DRAFT,
                nodes, edges, parameters, triggerBinding, schemaVersion,
                1L, now, actorId, now, actorId, null, null, null, null);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void requireStatus(UserWorkflowDefinitionStatus expected, String message) {
        if (status != expected) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.INVALID_LIFECYCLE_TRANSITION,
                    message + " (current status: " + status + ")");
        }
    }

    private UserWorkflowDefinition withStatus(
            UserWorkflowDefinitionStatus newStatus, String actorId, Instant now) {
        return new UserWorkflowDefinition(definitionId, version, tenantId, projectId,
                name, description, newStatus, nodes, edges, parameters, triggerBinding,
                schemaVersion, optimisticVersion, createdAt, createdBy, now, actorId,
                null, null, null, null);
    }

    /**
     * Typed error codes (error-contract.tsv; sealed-interface pattern per the
     * media-execution-plan ExecutionPlanErrorCode convention). Nested in the
     * aggregate file because the frozen required-path allowlist contains no
     * separate error-code file; nested types follow the AutomationFlow
     * nested-type convention.
     */
    public sealed interface UserWorkflowErrorCode extends Serializable {

        String codeString();

        String title();

        int status();

        enum Code implements UserWorkflowErrorCode {
            DEFINITION_NOT_FOUND("WORKFLOW-404-001", "Definition not found", 404),
            VERSION_NOT_FOUND("WORKFLOW-404-002", "Version not found", 404),
            TENANT_MISMATCH("WORKFLOW-403-001", "Tenant mismatch", 403),
            INVALID_LIFECYCLE_TRANSITION("WORKFLOW-409-001", "Invalid lifecycle transition", 409),
            PUBLISHED_IMMUTABLE("WORKFLOW-409-002", "Published definition is immutable", 409),
            OPTIMISTIC_LOCK_CONFLICT("WORKFLOW-409-003", "Optimistic lock conflict", 409),
            NO_PUBLISHED_SOURCE("WORKFLOW-409-004", "No published source version", 409),
            GRAPH_TOO_LARGE("WORKFLOW-400-001", "Graph too large", 400),
            DUPLICATE_NODE("WORKFLOW-400-002", "Duplicate node", 400),
            MISSING_EDGE_ENDPOINT("WORKFLOW-400-003", "Missing edge endpoint", 400),
            SELF_EDGE("WORKFLOW-400-004", "Self edge prohibited", 400),
            MULTIPLE_ENTRY_NODES("WORKFLOW-400-005", "Multiple entry nodes", 400),
            DISCONNECTED_GRAPH("WORKFLOW-400-006", "Disconnected graph", 400),
            CYCLE_DETECTED("WORKFLOW-400-007", "Cycle detected", 400),
            UNREACHABLE_NODE("WORKFLOW-400-008", "Unreachable node", 400),
            INVALID_NODE_TYPE_CONFIGURATION("WORKFLOW-400-009", "Invalid node type configuration", 400),
            SECRET_LIKE_VALUE_PROHIBITED("WORKFLOW-400-010", "Secret-like value prohibited", 400),
            CONFIGURATION_TOO_LARGE("WORKFLOW-400-011", "Configuration too large", 400),
            INVALID_SCHEMA_VERSION("WORKFLOW-400-012", "Invalid schema version", 400),
            DUPLICATE_EDGE("WORKFLOW-400-013", "Duplicate edge", 400),
            NO_TERMINAL_NODE("WORKFLOW-400-014", "No terminal node", 400),
            VALIDATION_FAILED("WORKFLOW-422-001", "Validation failed", 422);

            private final String codeString;
            private final String title;
            private final int status;

            Code(String codeString, String title, int status) {
                this.codeString = codeString;
                this.title = title;
                this.status = status;
            }

            @Override
            public String codeString() {
                return codeString;
            }

            @Override
            public String title() {
                return title;
            }

            @Override
            public int status() {
                return status;
            }
        }
    }

    /** Domain/application exception carrying the typed error code. */
    public static class UserWorkflowException extends RuntimeException {

        private final UserWorkflowErrorCode errorCode;
        private final String detail;

        public UserWorkflowException(UserWorkflowErrorCode errorCode, String detail) {
            super(errorCode.title() + (detail == null ? "" : ": " + detail));
            this.errorCode = errorCode;
            this.detail = detail;
        }

        public UserWorkflowErrorCode errorCode() {
            return errorCode;
        }

        public String detail() {
            return detail;
        }
    }
}
