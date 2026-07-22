package com.example.platform.test;

import org.jooq.impl.DSL;

/**
 * Fixture 02: DSL.field() call — MUST FAIL guard detection.
 * This is an untyped field reference that should use generated constants.
 */
public class Fixture02_DslField {
    public void query() {
        var field = DSL.field(DSL.name("render_job", "status"));
    }
}
