package com.example.platform.render.app.aaf;

public record AafConversionResult(
        String conversionId,
        boolean success,
        String manifestJson,
        String status,
        String errorMessage) {

    public static AafConversionResult success(String conversionId, String manifestJson, String status) {
        return new AafConversionResult(conversionId, true, manifestJson, status, null);
    }

    public static AafConversionResult failed(String conversionId, String error) {
        return new AafConversionResult(conversionId, false, null, "FAILED", error);
    }
}
