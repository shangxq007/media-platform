package com.example.platform.config.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {
    private final DSLContext dsl;
    public ConfigService(DSLContext dsl) { this.dsl = dsl; }

    public void upsert(String namespaceKey, String configKey, String valueJson) {
        Integer current = dsl.select(max(field("value_version", Integer.class)))
                .from(table("config_item"))
                .where(field("namespace_key").eq(namespaceKey))
                .and(field("config_key").eq(configKey))
                .fetchOne(0, Integer.class);
        int next = current == null ? 1 : current + 1;
        dsl.insertInto(table("config_item"))
                .columns(field("namespace_key"), field("config_key"), field("value_json"), field("value_version"), field("updated_at"))
                .values(namespaceKey, configKey, valueJson, next, OffsetDateTime.now())
                .execute();
    }

    public List<Map<String, Object>> list(String namespaceKey) {
        return dsl.select().from(table("config_item")).where(field("namespace_key").eq(namespaceKey)).fetchMaps();
    }
}