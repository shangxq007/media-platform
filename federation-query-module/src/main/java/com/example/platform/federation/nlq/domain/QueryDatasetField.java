package com.example.platform.federation.nlq.domain;

public record QueryDatasetField(
    String fieldName,
    String displayName,
    String type,
    String description,
    boolean nullable,
    boolean filterable,
    boolean groupable,
    boolean aggregatable,
    boolean sortable,
    boolean sensitive,
    String piiCategory,
    String redactionStrategy,
    String exampleValue
) {}
