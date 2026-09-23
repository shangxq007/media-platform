package com.example.platform.workflow.definition.api.dto;

import java.util.List;

/**
 * Create-request DTO: Composition definitions must use executable schema 2.
 * Schema 1 is retired and is rejected at the API boundary.
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
