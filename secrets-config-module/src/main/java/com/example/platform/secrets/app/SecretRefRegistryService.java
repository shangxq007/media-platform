package com.example.platform.secrets.app;

import static com.example.platform.typedschema.jooq.generated.tables.SecretRef.SECRET_REF;

import com.example.platform.secrets.api.port.SecretRefRegistryPort;
import com.example.platform.shared.Ids;
import java.time.LocalDateTime;
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
        int updated = dsl.update(SECRET_REF)
                .set(SECRET_REF.BACKEND_TYPE, backendType)
                .set(SECRET_REF.BACKEND_REF, backendRef)
                .where(SECRET_REF.NAMESPACE_KEY.eq(namespaceKey))
                .and(SECRET_REF.SECRET_KEY.eq(secretKey))
                .execute();
        if (updated == 0) {
            String id = Ids.newId("sec");
            dsl.insertInto(SECRET_REF)
                    .columns(SECRET_REF.ID, SECRET_REF.NAMESPACE_KEY, SECRET_REF.SECRET_KEY,
                            SECRET_REF.BACKEND_TYPE, SECRET_REF.BACKEND_REF, SECRET_REF.CREATED_AT)
                    .values(id, namespaceKey, secretKey, backendType, backendRef, LocalDateTime.now())
                    .execute();
        }
    }
}
