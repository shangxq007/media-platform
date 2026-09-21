package com.example.platform.outbox.app;
import com.example.platform.outbox.operations.OutboxOperationalQuery;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import static com.example.platform.typedschema.jooq.generated.tables.OutboxEvents.OUTBOX_EVENTS;
@Component
public class JdbcOutboxOperationalQuery implements OutboxOperationalQuery {
    private final DSLContext dsl;
    public JdbcOutboxOperationalQuery(DSLContext dsl) { this.dsl = dsl; }
    public Snapshot snapshot() {
        return new Snapshot(dsl.select(OUTBOX_EVENTS.STATUS, DSL.count().cast(Long.class))
                .from(OUTBOX_EVENTS).groupBy(OUTBOX_EVENTS.STATUS).fetchMap(r -> r.value1(), r -> r.value2()));
    }
}
