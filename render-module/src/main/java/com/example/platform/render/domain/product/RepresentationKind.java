package com.example.platform.render.domain.product;

/**
 * How the product is physically represented.
 */
public enum RepresentationKind {
    MEDIA_FILE,
    JSON_DOCUMENT,
    VECTOR_REFERENCE,
    TIMELINE_PLAN,
    TIMELINE_REVISION,
    GRAPH,
    SEARCH_INDEX,
    EXTERNAL_REFERENCE
}
