package com.example.platform.test;

import org.jooq.impl.DSL;

/**
 * Fixture 07: DSL calls in static context — MUST FAIL guard detection.
 * Tests detection when calls appear in static initializer or method.
 */
public class Fixture07_DslStaticContext {
    private static final String TABLE_NAME = "render_job";

    public static org.jooq.Table<?> resolve() {
        return DSL.table(TABLE_NAME);
    }
}
