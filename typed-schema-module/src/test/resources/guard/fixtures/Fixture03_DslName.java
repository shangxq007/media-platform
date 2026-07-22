package com.example.platform.test;

import org.jooq.impl.DSL;

/**
 * Fixture 03: DSL.name() call — MUST FAIL guard detection.
 * This is an untyped name reference that should use generated constants.
 */
public class Fixture03_DslName {
    public void query() {
        var name = DSL.name("render_job", "id");
    }
}
