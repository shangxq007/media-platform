package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class NotificationQueryService {
    private final DSLContext dsl;
    public NotificationQueryService(DSLContext dsl) { this.dsl = dsl; }

    public List<Map<String, Object>> listDeliveries() {
        return dsl.select().from(table("notification_delivery")).orderBy(field("created_at").desc()).fetchMaps();
    }
}