package com.example.platform.test;

import org.jooq.impl.DSL;

/**
 * Fixture 06: DSL.field() inline — MUST FAIL guard detection.
 * Field is constructed inline without DSL.name() intermediary.
 */
public class Fixture06_DslFieldInline {
    public void query() {
        var field = DSL.field("status");
    }
}
