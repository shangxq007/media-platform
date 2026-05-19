package com.example.platform.billing.domain;

/**
 * Source of reconciliation data.
 */
public record ReconciliationSource(
        String sourceId,
        String sourceType,
        String sourceName,
        String format,
        boolean active) {

    public static final String TYPE_CSV = "CSV";
    public static final String TYPE_JSON = "JSON";
    public static final String TYPE_API = "API";
}
