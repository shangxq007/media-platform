package com.example.platform.workflow.definition.api.dto;

import java.util.List;

/**
 * Update-request DTO (public-api-contract.tsv): optimisticVersion required.
 */
public record UserWorkflowDefinitionUpdateRequest(
        String name,
        String description,
        List<UserWorkflowDefinitionDto.NodeDto> nodes,
        List<UserWorkflowDefinitionDto.EdgeDto> edges,
        List<UserWorkflowDefinitionDto.ParameterDto> parameters,
        UserWorkflowDefinitionDto.TriggerDto trigger,
        long optimisticVersion) {
}
