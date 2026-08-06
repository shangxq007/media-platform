package com.example.platform.extension.domain;

import java.util.List;

/**
 * Handled-object descriptor (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>P1 subset: ONE handled object — {@code RenderExecutionPlan} schema
 * version {@code 1}. The Java boundary type is DECLARED (canonical type
 * name), never resolved by the registry: plugins must not receive
 * repositories, database entities, jOOQ records, ApplicationContext, raw
 * tenant secrets, mutable Timeline aggregates, Timeline persistence services
 * or ProductRuntimeService.</p>
 *
 * @param objectTypeId       stable object type ID (e.g. {@code "RenderExecutionPlan"})
 * @param schemaVersion      handled-object schema version (separate version layer)
 * @param javaBoundaryType   canonical Java type name (declared, not resolved)
 * @param matchingProperties matching property names (e.g. {@code ["profile","timelineSnapshotId"]})
 * @param prohibitedFields   prohibited field names (empty in P1)
 * @param tenantBehavior     tenant behavior declaration
 */
public record HandledObjectDescriptor(
        String objectTypeId,
        String schemaVersion,
        String javaBoundaryType,
        List<String> matchingProperties,
        List<String> prohibitedFields,
        TenantBehavior tenantBehavior) {

    /** Tenant behavior of the handled object. */
    public enum TenantBehavior {
        TENANT_SCOPED
    }

    public HandledObjectDescriptor {
        if (objectTypeId == null) {
            throw new NullPointerException("objectTypeId must not be null");
        }
        if (schemaVersion == null) {
            throw new NullPointerException("schemaVersion must not be null");
        }
        if (javaBoundaryType == null) {
            throw new NullPointerException("javaBoundaryType must not be null");
        }
        if (matchingProperties == null) {
            throw new NullPointerException("matchingProperties must not be null");
        }
        if (prohibitedFields == null) {
            throw new NullPointerException("prohibitedFields must not be null");
        }
        if (tenantBehavior == null) {
            throw new NullPointerException("tenantBehavior must not be null");
        }
        matchingProperties = List.copyOf(matchingProperties);
        prohibitedFields = List.copyOf(prohibitedFields);
    }
}
