package com.example.platform.prompt.domain;

/**
 * Types supported for prompt template variables.
 */
public enum PromptVariableType {
    STRING,
    NUMBER,
    BOOLEAN,
    ENUM,
    ARRAY,
    OBJECT,
    SECRET_REFERENCE,
    FILE_REFERENCE
}
