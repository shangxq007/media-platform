package com.example.platform.artifact.domain.typed;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** Declarative, provider-neutral parameter definition. */
public record ParameterDefinition(String name, ParameterType type, boolean required, JsonNode defaultValue) {
    public ParameterDefinition {
        if (name == null || name.isBlank() || !name.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) throw new IllegalArgumentException("invalid parameter name");
        Objects.requireNonNull(type, "type");
        if (required && defaultValue != null && !defaultValue.isNull()) throw new IllegalArgumentException("required parameter cannot have a default");
        if (defaultValue != null && !type.accepts(defaultValue)) throw new IllegalArgumentException("default does not match parameter type");
    }
    public enum ParameterType { STRING, INTEGER, NUMBER, BOOLEAN, OBJECT, ARRAY, ENUM;
        boolean accepts(JsonNode n) { return switch(this) { case STRING, ENUM -> n.isTextual(); case INTEGER -> n.isIntegralNumber(); case NUMBER -> n.isNumber(); case BOOLEAN -> n.isBoolean(); case OBJECT -> n.isObject(); case ARRAY -> n.isArray(); }; }
    }
}
