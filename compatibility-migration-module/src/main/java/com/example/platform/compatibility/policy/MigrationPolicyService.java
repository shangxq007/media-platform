package com.example.platform.compatibility.policy;

import com.example.platform.compatibility.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Policy service for migration strategy selection using LiteFlow-style routing.
 */
@Service
public class MigrationPolicyService {
    private static final Logger log = LoggerFactory.getLogger(MigrationPolicyService.class);

    public MigrationPolicyDecision decide(MigrationPolicyContext context) {
        log.info("MigrationPolicy: deciding for family={}, {} -> {}, tier={}",
                context.schemaFamily(), context.sourceVersion(), context.targetVersion(), context.userTier());

        // Rule 1: Cross-major-version migrations require manual review
        if (context.sourceVersion().major() != context.targetVersion().major() && !context.force()) {
            return new MigrationPolicyDecision(
                    "manual-review",
                    "Cross-major-version migration requires manual review",
                    List.of(new MigrationPolicyConflict("CROSS_MAJOR_VERSION",
                            "Cannot auto-migrate across major versions without force flag")),
                    List.of("json-patch", "java-migration"),
                    false
            );
        }

        // Rule 2: Script migrations require experimental tier
        if (context.requiresScript() && !"EXPERIMENTAL".equals(context.userTier())) {
            return new MigrationPolicyDecision(
                    "reject-script",
                    "Script migration requires EXPERIMENTAL tier",
                    List.of(new MigrationPolicyConflict("SCRIPT_NOT_ALLOWED",
                            "User tier " + context.userTier() + " cannot use script migration")),
                    List.of("json-patch", "java-migration"),
                    false
            );
        }

        // Rule 3: Large payloads prefer Java migration
        if (context.payloadSize() > 1_000_000) {
            return new MigrationPolicyDecision(
                    "java-migration",
                    "Large payload prefers Java migration handler",
                    List.of(),
                    List.of("java-migration", "json-patch"),
                    true
            );
        }

        // Rule 4: Default to json-patch for simple migrations
        return new MigrationPolicyDecision(
                "json-patch",
                "Standard JSON patch migration",
                List.of(),
                List.of("json-patch", "java-migration"),
                true
        );
    }

    public String explain(MigrationPolicyDecision decision) {
        return "Selected adapter '" + decision.selectedAdapter() + "': " + decision.reason();
    }

    /**
     * Context for migration policy decisions.
     */
    public record MigrationPolicyContext(
            SchemaFamily schemaFamily,
            SchemaVersion sourceVersion,
            SchemaVersion targetVersion,
            String tenantId,
            String userTier,
            boolean experimentalFlag,
            long payloadSize,
            boolean requiresScript,
            boolean requiresProvider,
            boolean force
    ) {}

    /**
     * Result of a migration policy decision.
     */
    public record MigrationPolicyDecision(
            String selectedAdapter,
            String reason,
            List<MigrationPolicyConflict> conflicts,
            List<String> candidateAdapters,
            boolean autoMigratable
    ) {}

    /**
     * A conflict detected during policy evaluation.
     */
    public record MigrationPolicyConflict(
            String conflictCode,
            String description
    ) {}

    /**
     * A chain of migration policies.
     */
    public record MigrationPolicyChain(
            String chainId,
            List<MigrationPolicyDecision> decisions,
            boolean hasConflicts
    ) {}
}
