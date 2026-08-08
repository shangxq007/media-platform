package com.example.platform.workflow.definition.domain;

/**
 * Typed parameter declaration — the input/output contract between nodes.
 * Values are reference-oriented and schema-referenced (configuration-parameter-contract.txt).
 */
public record UserWorkflowParameterDeclaration(
        String parameterId,
        String name,
        String type,
        String schemaRef,
        boolean required,
        String defaultValueRef) {

    public UserWorkflowParameterDeclaration {
        if (parameterId == null || parameterId.isBlank()) {
            throw new IllegalArgumentException("parameter id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("parameter name must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("parameter type must not be blank");
        }
        if (schemaRef == null || schemaRef.isBlank()) {
            throw new IllegalArgumentException("parameter schemaRef must not be blank");
        }
    }
}
