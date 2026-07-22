package com.example.platform.test;

import org.jooq.impl.DSL;

/**
 * Fixture 01: DSL.table() call — MUST FAIL guard detection.
 * This is an untyped table reference that should use generated constants.
 */
public class Fixture01_DslTable {
    public void query() {
        var table = DSL.table("render_job");
    }
}
