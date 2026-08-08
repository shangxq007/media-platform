package com.example.platform.workflow.definition.port;

import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for the user workflow definition aggregate. Operates only
 * on domain objects — never exposes JdbcTemplate, ResultSet, SQL exceptions
 * or database row DTOs (repository-port-contract.txt). Lifecycle invariants
 * require explicit operations; there is no generic CRUD repository and no
 * execution-state storage.
 */
public interface UserWorkflowDefinitionRepository {

    /** Inserts a new lineage (definition + version 1 + nodes + edges), one transaction. */
    void insertDraft(UserWorkflowDefinition draft);

    /** Tenant-scoped exact-version lookup; cross-tenant lookups return empty. */
    Optional<UserWorkflowDefinition> findExactVersion(
            String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version);

    /** Tenant-scoped latest-version lookup (highest versionNumber). */
    Optional<UserWorkflowDefinition> findLatest(
            String tenantId, UserWorkflowDefinitionId definitionId);

    /** Tenant-scoped list; projectId null returns all tenant definitions. */
    List<UserWorkflowDefinition> listByTenant(String tenantId, String projectId);

    /**
     * Updates a DRAFT row (content and/or status) with optimistic compare-and-set;
     * bumps the stored optimistic_version. Throws OPTIMISTIC_LOCK_CONFLICT when
     * the expected version does not match, PUBLISHED_IMMUTABLE when the stored row
     * is published/archived.
     */
    UserWorkflowDefinition updateDraft(UserWorkflowDefinition updated);

    /** VALIDATED -> PUBLISHED with optimistic compare-and-set. */
    UserWorkflowDefinition publish(UserWorkflowDefinition published);

    /** -> ARCHIVED with optimistic compare-and-set. */
    UserWorkflowDefinition archive(UserWorkflowDefinition archived);

    /** Inserts a new version row (copied content, status DRAFT), one transaction. */
    void insertVersion(UserWorkflowDefinition newDraft);
}
