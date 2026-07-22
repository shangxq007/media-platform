package com.example.platform.test;

import org.jooq.impl.DSL;

/**
 * Fixture 05: DSL.table() with whitespace — MUST FAIL guard detection.
 * Tests pattern matching with varied whitespace around the call.
 */
public class Fixture05_DslTableWhitespace {
    public void query() {
        var table = DSL.table( "render_job" );
    }
}
