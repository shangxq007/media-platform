package com.example.platform.workflow.definition.app;

import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowErrorCode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowException;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionEdge;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionNode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;
import com.example.platform.workflow.definition.domain.UserWorkflowParameterDeclaration;
import com.example.platform.workflow.definition.domain.UserWorkflowTriggerBinding;
import com.example.platform.workflow.definition.port.UserWorkflowDefinitionRepository;
import com.example.platform.workflow.definition.validation.UserWorkflowDefinitionValidator;
import com.example.platform.workflow.definition.validation.UserWorkflowValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * W2 V1 application service — the 9 frozen use cases
 * (application-usecase-contract.tsv). No operation named or equivalent to
 * start/run/execute/resume/retry/cancel exists. The injected Clock keeps
 * domain/audit timestamps deterministic; no Instant.now() in domain code.
 */
@Service
public class UserWorkflowDefinitionService {

    private final UserWorkflowDefinitionRepository repository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public UserWorkflowDefinitionService(UserWorkflowDefinitionRepository repository) {
        // Spring wiring uses the system clock; deterministic tests inject a
        // fixed Clock via the two-argument constructor. Domain code never
        // calls Instant.now() (audit-contract.txt).
        this(repository, Clock.systemUTC());
    }

    public UserWorkflowDefinitionService(UserWorkflowDefinitionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // ── CreateWorkflowDefinition ───────────────────────────────────────────

    @Transactional
    public UserWorkflowDefinition create(
            String tenantId, String projectId, String name, String description,
            List<UserWorkflowDefinitionNode> nodes, List<UserWorkflowDefinitionEdge> edges,
            List<UserWorkflowParameterDeclaration> parameters,
            UserWorkflowTriggerBinding triggerBinding, int schemaVersion, String actorId) {
        Instant now = clock.instant();
        UserWorkflowDefinition draft = UserWorkflowDefinition.newDraft(
                UserWorkflowDefinitionId.generate(), tenantId, projectId, name, description,
                nodes, edges, parameters, triggerBinding, schemaVersion, actorId, now);
        requireValid(draft);
        repository.insertDraft(draft);
        return draft;
    }

    // ── UpdateWorkflowDraft ────────────────────────────────────────────────

    @Transactional
    public UserWorkflowDefinition updateDraft(
            String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version,
            long expectedOptimisticVersion, String name, String description,
            List<UserWorkflowDefinitionNode> nodes, List<UserWorkflowDefinitionEdge> edges,
            List<UserWorkflowParameterDeclaration> parameters,
            UserWorkflowTriggerBinding triggerBinding, String actorId) {
        Instant now = clock.instant();
        UserWorkflowDefinition current = requireVersion(tenantId, definitionId, version);
        requireOptimisticVersion(current, expectedOptimisticVersion);
        UserWorkflowDefinition updated = current.updatedDraft(
                name, description, nodes, edges, parameters, triggerBinding, actorId, now);
        requireValid(updated);
        return repository.updateDraft(updated);
    }

    // ── ValidateWorkflowDefinition ─────────────────────────────────────────

    @Transactional
    public UserWorkflowValidationResult validate(
            String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version,
            String actorId) {
        Instant now = clock.instant();
        UserWorkflowDefinition current = requireVersion(tenantId, definitionId, version);
        UserWorkflowValidationResult result = UserWorkflowDefinitionValidator.validate(current);
        if (result.valid() && current.status() == com.example.platform.workflow.definition.domain.UserWorkflowDefinitionStatus.DRAFT) {
            repository.updateDraft(current.markValidated(actorId, now));
        }
        return result;
    }

    // ── PublishWorkflowDefinition ──────────────────────────────────────────

    @Transactional
    public UserWorkflowDefinition publish(
            String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version,
            long expectedOptimisticVersion, String actorId) {
        Instant now = clock.instant();
        UserWorkflowDefinition current = requireVersion(tenantId, definitionId, version);
        requireOptimisticVersion(current, expectedOptimisticVersion);
        if (current.status() != com.example.platform.workflow.definition.domain.UserWorkflowDefinitionStatus.VALIDATED) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.INVALID_LIFECYCLE_TRANSITION,
                    "only VALIDATED versions may be published (current: " + current.status() + ")");
        }
        UserWorkflowValidationResult revalidation = UserWorkflowDefinitionValidator.validate(current);
        if (!revalidation.valid()) {
            throw validationFailed(revalidation);
        }
        return repository.publish(current.publish(actorId, now));
    }

    // ── CreateWorkflowDefinitionVersion ────────────────────────────────────

    @Transactional
    public UserWorkflowDefinition createVersion(
            String tenantId, UserWorkflowDefinitionId definitionId, Integer sourceVersion, String actorId) {
        Instant now = clock.instant();
        UserWorkflowDefinition source;
        if (sourceVersion == null) {
            source = repository.findLatest(tenantId, definitionId)
                    .orElseThrow(() -> new UserWorkflowException(UserWorkflowErrorCode.Code.DEFINITION_NOT_FOUND,
                            "definition not found: " + definitionId));
        } else {
            source = repository.findExactVersion(tenantId, definitionId,
                            UserWorkflowDefinitionVersion.of(sourceVersion))
                    .orElseThrow(() -> new UserWorkflowException(UserWorkflowErrorCode.Code.VERSION_NOT_FOUND,
                            "version not found: " + definitionId + "/" + sourceVersion));
        }
        UserWorkflowDefinition next = source.createNextVersion(actorId, now);
        repository.insertVersion(next);
        return next;
    }

    // ── ArchiveWorkflowDefinition ──────────────────────────────────────────

    @Transactional
    public UserWorkflowDefinition archive(
            String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version,
            long expectedOptimisticVersion, String actorId) {
        Instant now = clock.instant();
        UserWorkflowDefinition current = requireVersion(tenantId, definitionId, version);
        requireOptimisticVersion(current, expectedOptimisticVersion);
        return repository.archive(current.archive(actorId, now));
    }

    // ── reads ──────────────────────────────────────────────────────────────

    public UserWorkflowDefinition get(String tenantId, UserWorkflowDefinitionId definitionId) {
        return repository.findLatest(tenantId, definitionId)
                .orElseThrow(() -> new UserWorkflowException(UserWorkflowErrorCode.Code.DEFINITION_NOT_FOUND,
                        "definition not found: " + definitionId));
    }

    public UserWorkflowDefinition getVersion(
            String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version) {
        return repository.findExactVersion(tenantId, definitionId, version)
                .orElseThrow(() -> new UserWorkflowException(UserWorkflowErrorCode.Code.VERSION_NOT_FOUND,
                        "version not found: " + definitionId + "/" + version));
    }

    public List<UserWorkflowDefinition> list(String tenantId, String projectId) {
        return repository.listByTenant(tenantId, projectId);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private UserWorkflowDefinition requireVersion(
            String tenantId, UserWorkflowDefinitionId definitionId, UserWorkflowDefinitionVersion version) {
        return repository.findExactVersion(tenantId, definitionId, version)
                .orElseThrow(() -> new UserWorkflowException(UserWorkflowErrorCode.Code.VERSION_NOT_FOUND,
                        "version not found: " + definitionId + "/" + version));
    }

    private static void requireOptimisticVersion(
            UserWorkflowDefinition current, long expected) {
        if (current.optimisticVersion() != expected) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.OPTIMISTIC_LOCK_CONFLICT,
                    "expected optimisticVersion " + expected + " but current is "
                            + current.optimisticVersion());
        }
    }

    private static void requireValid(UserWorkflowDefinition draft) {
        UserWorkflowValidationResult result = UserWorkflowDefinitionValidator.validate(draft);
        if (!result.valid()) {
            // Configuration violations surface their specific 400 code
            // (WORKFLOW-400-009..012, error-contract.tsv); graph violations
            // surface as 422 VALIDATION_FAILED (api-test-contract.txt).
            java.util.Optional<com.example.platform.workflow.definition.validation.UserWorkflowValidationIssue> configIssue =
                    result.blockingIssues().stream()
                            .filter(i -> i.issueCode().name().startsWith("CONFIG_"))
                            .findFirst();
            if (configIssue.isPresent()) {
                throw new UserWorkflowException(configIssue.get().issueCode().errorCode(),
                        configIssue.get().message());
            }
            throw validationFailed(result);
        }
    }

    private static UserWorkflowException validationFailed(UserWorkflowValidationResult result) {
        return new ValidationFailedException(result);
    }

    /** 422 VALIDATION_FAILED carrying the deterministic issue list. */
    public static final class ValidationFailedException extends UserWorkflowException {

        private final UserWorkflowValidationResult validationResult;

        public ValidationFailedException(UserWorkflowValidationResult validationResult) {
            super(UserWorkflowErrorCode.Code.VALIDATION_FAILED,
                    "validation failed with " + validationResult.blockingIssues().size() + " blocking issue(s)");
            this.validationResult = validationResult;
        }

        public UserWorkflowValidationResult validationResult() {
            return validationResult;
        }
    }
}
