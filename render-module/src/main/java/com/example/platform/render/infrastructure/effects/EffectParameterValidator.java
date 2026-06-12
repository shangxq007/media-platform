package com.example.platform.render.infrastructure.effects;

import com.example.platform.render.infrastructure.EffectParameterSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates effect parameters against their schema definitions.
 */
public class EffectParameterValidator {

    /**
     * Validate parameters against a schema.
     * 
     * @param params the parameters to validate
     * @param schema the parameter schema definitions
     * @return list of validation errors (empty if valid)
     */
    public static List<String> validate(Map<String, Object> params, List<EffectParameterSchema> schema) {
        List<String> errors = new ArrayList<>();
        
        if (schema == null || schema.isEmpty()) {
            return errors;
        }
        
        // Check required parameters and validate types
        for (EffectParameterSchema paramDef : schema) {
            String name = paramDef.name();
            Object value = params.get(name);
            
            if (value == null) {
                // Check if there's a default value
                if (paramDef.defaultValue() == null) {
                    errors.add("Missing required parameter: " + name);
                }
                continue;
            }
            
            // Type validation
            String type = paramDef.type();
            if (type != null) {
                switch (type.toLowerCase()) {
                    case "float":
                    case "number":
                        if (!(value instanceof Number)) {
                            errors.add("Parameter '" + name + "' must be a number, got: " + value.getClass().getSimpleName());
                        } else {
                            double numValue = ((Number) value).doubleValue();
                            if (paramDef.min() != null && paramDef.min() instanceof Number) {
                                double min = ((Number) paramDef.min()).doubleValue();
                                if (numValue < min) {
                                    errors.add("Parameter '" + name + "' must be >= " + min + ", got: " + numValue);
                                }
                            }
                            if (paramDef.max() != null && paramDef.max() instanceof Number) {
                                double max = ((Number) paramDef.max()).doubleValue();
                                if (numValue > max) {
                                    errors.add("Parameter '" + name + "' must be <= " + max + ", got: " + numValue);
                                }
                            }
                        }
                        break;
                    case "int":
                    case "integer":
                        if (!(value instanceof Integer) && !(value instanceof Long)) {
                            errors.add("Parameter '" + name + "' must be an integer, got: " + value.getClass().getSimpleName());
                        } else {
                            long intValue = ((Number) value).longValue();
                            if (paramDef.min() != null && paramDef.min() instanceof Number) {
                                long min = ((Number) paramDef.min()).longValue();
                                if (intValue < min) {
                                    errors.add("Parameter '" + name + "' must be >= " + min + ", got: " + intValue);
                                }
                            }
                            if (paramDef.max() != null && paramDef.max() instanceof Number) {
                                long max = ((Number) paramDef.max()).longValue();
                                if (intValue > max) {
                                    errors.add("Parameter '" + name + "' must be <= " + max + ", got: " + intValue);
                                }
                            }
                        }
                        break;
                    case "string":
                        if (!(value instanceof String)) {
                            errors.add("Parameter '" + name + "' must be a string, got: " + value.getClass().getSimpleName());
                        }
                        break;
                    case "boolean":
                        if (!(value instanceof Boolean)) {
                            errors.add("Parameter '" + name + "' must be a boolean, got: " + value.getClass().getSimpleName());
                        }
                        break;
                }
            }
        }
        
        return errors;
    }
}
