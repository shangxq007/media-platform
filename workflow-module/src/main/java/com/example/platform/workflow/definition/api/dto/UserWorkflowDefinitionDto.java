package com.example.platform.workflow.definition.api.dto;

import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionEdge;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionNode;
import com.example.platform.workflow.definition.domain.UserWorkflowParameterDeclaration;
import com.example.platform.workflow.definition.domain.UserWorkflowTriggerBinding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W2 response DTO (public-api-contract.tsv). Domain entities are never
 * exposed directly; the controller maps to/from this record. configValues is
 * a JSON object carried as Map<String,Object> so both the Jackson 2 and
 * Jackson 3 (tools.jackson) runtimes deserialize it; canonicalization happens
 * in the controller layer with a sorted-keys mapper.
 */
public record UserWorkflowDefinitionDto(
        String definitionId,
        int versionNumber,
        String tenantId,
        String projectId,
        String name,
        String description,
        String status,
        int schemaVersion,
        List<NodeDto> nodes,
        List<EdgeDto> edges,
        List<ParameterDto> parameters,
        TriggerDto trigger,
        long optimisticVersion,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Instant publishedAt,
        String publishedBy,
        Instant archivedAt,
        String archivedBy) {

    public static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    public record NodeDto(String nodeId, String nodeType, String name, String configSchemaRef,
                          Map<String, Object> configValues, List<ParameterDto> inputDeclarations,
                          List<ParameterDto> outputDeclarations, String errorPolicy) {
    }

    public record EdgeDto(String edgeId, String sourceNodeId, String targetNodeId,
                          String conditionRef, int sortOrder) {
    }

    public record ParameterDto(String parameterId, String name, String type, String schemaRef,
                               boolean required, String defaultValueRef) {
    }

    public record TriggerDto(String triggerType, String referenceId, String referenceVersion) {
    }

    public static UserWorkflowDefinitionDto from(UserWorkflowDefinition d) {
        return new UserWorkflowDefinitionDto(
                d.definitionId().value(), d.version().versionNumber(), d.tenantId(), d.projectId(),
                d.name(), d.description(), d.status().name(), d.schemaVersion(),
                d.nodes().stream().map(UserWorkflowDefinitionDto::nodeDto).toList(),
                d.edges().stream().map(UserWorkflowDefinitionDto::edgeDto).toList(),
                d.parameters().stream().map(UserWorkflowDefinitionDto::parameterDto).toList(),
                triggerDto(d.triggerBinding()),
                d.optimisticVersion(), d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(),
                d.publishedAt(), d.publishedBy(), d.archivedAt(), d.archivedBy());
    }

    public static NodeDto nodeDto(UserWorkflowDefinitionNode n) {
        return new NodeDto(n.nodeId(), n.nodeType().name(), n.name(), n.configSchemaRef(),
                parseConfig(n.configValues().canonicalJson()),
                n.inputDeclarations().stream().map(UserWorkflowDefinitionDto::parameterDto).toList(),
                n.outputDeclarations().stream().map(UserWorkflowDefinitionDto::parameterDto).toList(),
                n.errorPolicy().name());
    }

    public static EdgeDto edgeDto(UserWorkflowDefinitionEdge e) {
        return new EdgeDto(e.edgeId(), e.sourceNodeId(), e.targetNodeId(),
                e.conditionRef() == null ? "" : e.conditionRef(), e.sortOrder());
    }

    public static ParameterDto parameterDto(UserWorkflowParameterDeclaration p) {
        return new ParameterDto(p.parameterId(), p.name(), p.type(), p.schemaRef(),
                p.required(), p.defaultValueRef());
    }

    public static TriggerDto triggerDto(UserWorkflowTriggerBinding t) {
        return new TriggerDto(t.triggerType().name(), t.referenceId(), t.referenceVersion());
    }

    public static Map<String, Object> parseConfig(String canonicalJson) {
        try {
            return CANONICAL.readValue(canonicalJson, MAP_TYPE);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static String canonicalConfig(Map<String, Object> config) {
        try {
            return CANONICAL.writeValueAsString(config == null ? new LinkedHashMap<>() : config);
        } catch (Exception e) {
            throw new IllegalArgumentException("config is not serializable: " + e.getMessage());
        }
    }
}
