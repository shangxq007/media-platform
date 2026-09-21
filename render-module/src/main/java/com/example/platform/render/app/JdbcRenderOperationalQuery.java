package com.example.platform.render.app;
import com.example.platform.render.api.RenderOperationalQuery;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import static com.example.platform.typedschema.jooq.generated.tables.ClientExportSession.CLIENT_EXPORT_SESSION;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;
@Component
public class JdbcRenderOperationalQuery implements RenderOperationalQuery {
    private final DSLContext dsl;
    public JdbcRenderOperationalQuery(DSLContext dsl) { this.dsl = dsl; }
    public Map<String, Long> exportSessionCounts() {
        return Map.copyOf(dsl.select(CLIENT_EXPORT_SESSION.STATUS, DSL.count().cast(Long.class))
                .from(CLIENT_EXPORT_SESSION).groupBy(CLIENT_EXPORT_SESSION.STATUS).fetchMap(r -> r.value1(), r -> r.value2()));
    }
    public Map<String, Long> renderJobCounts() {
        return Map.copyOf(dsl.select(RENDER_JOB.STATUS, DSL.count().cast(Long.class))
                .from(RENDER_JOB).groupBy(RENDER_JOB.STATUS).fetchMap(r -> r.value1(), r -> r.value2()));
    }
}
