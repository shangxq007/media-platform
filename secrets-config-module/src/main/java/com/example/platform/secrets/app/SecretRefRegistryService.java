package com.example.platform.secrets.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.shared.Ids;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecretRefRegistryService implements SecretRefRegistryPort {

    private final DSLContext dsl;

    public SecretRefRegistryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public void register(String namespaceKey, String secretKey, String backendType, String backendRef) {
        int updated = dsl.update(table("secret_ref"))
                .set(field("backend_type"), backendType)
                .set(field("backend_ref"), backendRef)
                .where(field("namespace_key").eq(namespaceKey))
                .and(field("secret_key").eq(secretKey))
                .execute();
        if (updated == 0) {
            String id = Ids.newId("sec");
            dsl.insertInto(table("secret_ref"))
                    .columns(field("id"), field("namespace_key"), field("secret_key"),
                            field("backend_type"), field("backend_ref"), field("created_at"))
                    .values(id, namespaceKey, secretKey, backendType, backendRef, OffsetDateTime.now())
                    .execute();
        }
    }
}
