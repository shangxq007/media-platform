package com.example.platform.operation.application;

import com.example.platform.operation.operation.OperationDefinition;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/** Projects only the Jackson creators reachable from a code-owned Text definition. */
public final class TextParameterSchema {
    private TextParameterSchema() {}

    public static Map<String, Object> field(OperationDefinition definition, String name) {
        var component = Arrays.stream(definition.parameterType().getRecordComponents())
                .filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
        return shape(component.getGenericType());
    }

    /** Domain constructors subsequently check semantic ranges and conditional requirements. */
    @SuppressWarnings("unchecked")
    static void validate(Map<String, Object> schema, Object value) {
        if (schema.containsKey("anyOf")) {
            if (value == null) return;
            validate((Map<String, Object>) ((List<?>) schema.get("anyOf")).getFirst(), value);
            return;
        }
        boolean valid = switch ((String) schema.get("type")) {
            case "string" -> value instanceof String && (!schema.containsKey("enum") || ((List<?>) schema.get("enum")).contains(value));
            case "boolean" -> value instanceof Boolean;
            case "integer" -> value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger;
            case "array" -> {
                if (!(value instanceof List<?> items)) yield false;
                items.forEach(item -> validate((Map<String, Object>) schema.get("items"), item));
                yield true;
            }
            case "object" -> {
                if (!(value instanceof Map<?, ?> object)) yield false;
                var fields = (Map<String, Map<String, Object>>) schema.get("properties");
                if (!fields.keySet().containsAll(object.keySet()) || !object.keySet().containsAll((List<?>) schema.get("required"))) yield false;
                object.forEach((key, child) -> validate(fields.get(key), child));
                yield true;
            }
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Invalid typed text parameter");
    }

    private static Map<String, Object> shape(Type type) {
        if (type instanceof ParameterizedType collection && collection.getRawType() == List.class) {
            return Map.of("type", "array", "items", shape(collection.getActualTypeArguments()[0]));
        }
        Class<?> c = (Class<?>) type;
        if (c == String.class) return Map.of("type", "string");
        if (c == boolean.class || c == Boolean.class) return Map.of("type", "boolean");
        if (c == int.class || c == long.class || c == java.math.BigInteger.class) return Map.of("type", "integer");
        if (c.isEnum()) return Map.of("type", "string", "enum", Arrays.stream(c.getEnumConstants())
                .map(value -> ((Enum<?>) value).name()).toList());
        if (c.isRecord()) {
            var properties = new TreeMap<String, Object>();
            for (var component : c.getRecordComponents()) properties.put(component.getName(), shape(component.getGenericType()));
            return Map.of("type", "object", "properties", properties, "required", properties.keySet().stream().sorted().toList(),
                    "additionalProperties", false);
        }
        var creator = Arrays.stream(c.getDeclaredConstructors()).filter(k -> k.isAnnotationPresent(JsonCreator.class))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing wire creator for " + c.getSimpleName()));
        if (creator.getAnnotation(JsonCreator.class).mode() == JsonCreator.Mode.DELEGATING) {
            return shape(creator.getGenericParameterTypes()[0]);
        }
        var fields = new TreeMap<String, Object>();
        var required = new ArrayList<String>();
        for (var parameter : creator.getParameters()) {
            var key = parameter.getAnnotation(JsonProperty.class).value();
            var field = shape(parameter.getParameterizedType());
            boolean optional = switch (c.getSimpleName() + "." + key) {
                case "TextFrame.heightConstraint", "TextSemanticRun.language", "TextSemanticRun.script",
                        "OpticalSizingIntent.explicitCoordinate" -> true;
                default -> false;
            };
            fields.put(key, optional ? Map.of("anyOf", List.of(field, Map.of("type", "null"))) : field);
            if (!optional) required.add(key);
        }
        return Map.of("type", "object", "properties", fields, "required", required.stream().sorted().toList(), "additionalProperties", false);
    }
}
