package com.example.platform.test;

import org.jooq.impl.DSL;

/**
 * Fixture 04: Multiple DSL calls — MUST FAIL guard detection.
 * Contains DSL.table() and DSL.field() on different lines.
 */
public class Fixture04_MultipleCalls {
    public void query() {
        var table = DSL.table("asset");
        var field = DSL.field(DSL.name("asset", "id"));
    }
}
