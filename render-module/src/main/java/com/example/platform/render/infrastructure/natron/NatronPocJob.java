package com.example.platform.render.infrastructure.natron;

import java.util.Map;

/**
 * Resolved inputs for a single Natron POC render (one effect on one video clip).
 */
public record NatronPocJob(
        String effectKey,
        String inputLocalPath,
        String outputLocalPath,
        Map<String, Object> parameters) {

    public double intensity() {
        return numericParameter("intensity", 0.5);
    }

    public double saturation() {
        return numericParameter("saturation", 1.15);
    }

    private double numericParameter(String key, double defaultValue) {
        Object v = parameters != null ? parameters.get(key) : null;
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(v.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
