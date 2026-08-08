package com.example.platform.workflow.definition.api.dto;

import java.util.List;

/**
 * Create-request DTO (public-api-contract.tsv): name required; description and
 * projectId optional; schemaVersion required == 1.
 */
public record UserWorkflowDefinitionCreateRequest(
        String name,
        String description,
        String projectId,
        int schemaVersion,
        List<UserWorkflowDefinitionDto.NodeDto> nodes,
        List<UserWorkflowDefinitionDto.EdgeDto> edges,
        List<UserWorkflowDefinitionDto.ParameterDto> parameters,
        UserWorkflowDefinitionDto.TriggerDto trigger) {
}
