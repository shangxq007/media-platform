package com.example.platform.config.app;

import static com.example.platform.typedschema.jooq.generated.tables.ConfigItem.CONFIG_ITEM;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {
    private final DSLContext dsl;
    public ConfigService(DSLContext dsl) { this.dsl = dsl; }

    public void upsert(String namespaceKey, String configKey, String valueJson) {
        Integer current = dsl.select(org.jooq.impl.DSL.max(CONFIG_ITEM.VALUE_VERSION))
                .from(CONFIG_ITEM)
                .where(CONFIG_ITEM.NAMESPACE_KEY.eq(namespaceKey))
                .and(CONFIG_ITEM.CONFIG_KEY.eq(configKey))
                .fetchOne(0, Integer.class);
        int next = current == null ? 1 : current + 1;
        dsl.insertInto(CONFIG_ITEM)
                .columns(CONFIG_ITEM.NAMESPACE_KEY, CONFIG_ITEM.CONFIG_KEY, CONFIG_ITEM.VALUE_JSON, CONFIG_ITEM.VALUE_VERSION, CONFIG_ITEM.UPDATED_AT)
                .values(namespaceKey, configKey, valueJson, next, LocalDateTime.now())
                .execute();
    }

    public List<Map<String, Object>> list(String namespaceKey) {
        return dsl.select().from(CONFIG_ITEM).where(CONFIG_ITEM.NAMESPACE_KEY.eq(namespaceKey)).fetchMaps();
    }
}
