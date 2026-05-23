package com.example.platform.delivery.app;

import com.example.platform.delivery.infrastructure.DeliveryConfigParser;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Indexes URI prefixes from {@code delivery_destination.config_json} (base URL, bucket, prefix) for orphan scans.
 */
@Service
@ConditionalOnBean(DSLContext.class)
public class DeliveryDestinationUriIndexService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDestinationUriIndexService.class);

    private static final List<String> CONFIG_URI_KEYS =
            List.of("baseUrl", "baseUri", "base_uri", "endpoint", "host", "bucket", "prefix", "rootPath", "root_path");

    private final DSLContext dsl;

    public DeliveryDestinationUriIndexService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Set<String> collectDestinationUriPrefixes() {
        Set<String> prefixes = new HashSet<>();
        try {
            var rows = dsl.select(field("config_json", String.class))
                    .from(table("delivery_destination"))
                    .where(field("enabled").isNull().or(field("enabled").eq(true)))
                    .fetch(field("config_json", String.class));
            for (String json : rows) {
                indexConfig(prefixes, json);
            }
        } catch (DataAccessException e) {
            log.debug("delivery_destination URI index skipped: {}", e.getMessage());
        }
        return prefixes;
    }

    private static void indexConfig(Set<String> prefixes, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return;
        }
        try {
            var config = DeliveryConfigParser.parseConfig(configJson);
            for (String key : CONFIG_URI_KEYS) {
                String value = DeliveryConfigParser.stringVal(config, key, "").trim();
                if (value.isEmpty()) {
                    continue;
                }
                if ("bucket".equals(key) && !value.contains("://")) {
                    prefixes.add("s3://" + value + "/");
                    prefixes.add(value + "/");
                } else {
                    prefixes.add(normalizePrefix(value));
                }
            }
        } catch (Exception ignored) {
            // skip malformed destination config
        }
    }

    static String normalizePrefix(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed;
        }
        return trimmed + "/";
    }
}
