package com.example.platform.test;

import com.example.platform.typedschema.jooq.generated.tables.RenderJob;

/**
 * Fixture 08: Uses generated table — ALLOWED (must pass guard).
 * This file uses the generated typed table constants and should NOT
 * trigger any guard violations.
 */
public class Fixture08_UsesGeneratedTable {
    public void query() {
        var table = RenderJob.RENDER_JOB;
        var id = RenderJob.RENDER_JOB.ID;
        var status = RenderJob.RENDER_JOB.STATUS;
    }
}
