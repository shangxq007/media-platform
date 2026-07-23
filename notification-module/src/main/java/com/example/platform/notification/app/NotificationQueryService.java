package com.example.platform.notification.app;

import static com.example.platform.typedschema.jooq.generated.tables.NotificationDelivery.NOTIFICATION_DELIVERY;

import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class NotificationQueryService {
    private final DSLContext dsl;
    public NotificationQueryService(DSLContext dsl) { this.dsl = dsl; }

    public List<Map<String, Object>> listDeliveries() {
        return dsl.select().from(NOTIFICATION_DELIVERY).orderBy(NOTIFICATION_DELIVERY.CREATED_AT.desc()).fetchMaps();
    }
}
