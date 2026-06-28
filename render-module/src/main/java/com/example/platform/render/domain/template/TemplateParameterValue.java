package com.example.platform.render.domain.template;

/**
 * Typed parameter value for template operations.
 * Internal domain model.
 */
public record TemplateParameterValue(
        String stringValue,
        Integer intValue,
        Double doubleValue,
        Boolean boolValue) {

    public static TemplateParameterValue ofString(String v) {
        return new TemplateParameterValue(v, null, null, null);
    }

    public static TemplateParameterValue ofInt(int v) {
        return new TemplateParameterValue(null, v, null, null);
    }

    public static TemplateParameterValue ofDouble(double v) {
        return new TemplateParameterValue(null, null, v, null);
    }

    public static TemplateParameterValue ofBoolean(boolean v) {
        return new TemplateParameterValue(null, null, null, v);
    }
}
