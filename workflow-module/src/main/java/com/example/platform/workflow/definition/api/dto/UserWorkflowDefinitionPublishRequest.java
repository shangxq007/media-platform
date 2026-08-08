package com.example.platform.workflow.definition.api.dto;

/**
 * Publish-request DTO (public-api-contract.tsv): optimisticVersion required.
 */
public record UserWorkflowDefinitionPublishRequest(long optimisticVersion) {
}
