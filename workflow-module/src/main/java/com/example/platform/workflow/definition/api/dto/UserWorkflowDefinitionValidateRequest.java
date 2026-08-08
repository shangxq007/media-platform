package com.example.platform.workflow.definition.api.dto;

/**
 * Validate-request DTO (public-api-contract.tsv): optimisticVersion required
 * by the API contract; the validate use case itself is guarded by the
 * repository compare-and-set.
 */
public record UserWorkflowDefinitionValidateRequest(long optimisticVersion) {
}
