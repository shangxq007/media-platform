package com.example.platform.workerfabric.reuse;

/** Fail-closed execution cacheability policy result. */
public enum Cacheability {
    CACHEABLE,
    CACHEABLE_WHEN_FULLY_PINNED,
    NOT_CACHEABLE
}
