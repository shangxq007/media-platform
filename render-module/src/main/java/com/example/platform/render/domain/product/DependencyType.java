package com.example.platform.render.domain.product;

/**
 * Type of dependency between two products in the Product Graph.
 */
public enum DependencyType {
    DERIVED_FROM,
    GENERATED_FROM,
    REFERENCES,
    REQUIRES,
    VERSION_OF
}
