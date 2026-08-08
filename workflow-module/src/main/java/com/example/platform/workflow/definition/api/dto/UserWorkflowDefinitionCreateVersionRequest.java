package com.example.platform.workflow.definition.api.dto;

/**
 * Create-version request DTO (public-api-contract.tsv): sourceVersion optional
 * (default = latest PUBLISHED).
 */
public record UserWorkflowDefinitionCreateVersionRequest(Integer sourceVersion) {
}
