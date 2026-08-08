package com.example.platform.workflow.definition.api.dto;

/**
 * Archive-request DTO (public-api-contract.tsv): optimisticVersion required.
 */
public record UserWorkflowDefinitionArchiveRequest(long optimisticVersion) {
}
