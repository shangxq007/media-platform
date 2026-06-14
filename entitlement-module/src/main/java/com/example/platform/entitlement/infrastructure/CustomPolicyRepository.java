package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.entitlement.domain.EntitlementPolicy;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;

@Repository

public class CustomPolicyRepository {

    private final DSLContext dsl;

    public CustomPolicyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<EntitlementPolicy> findCustomPolicy(String tenantId) {
        return dsl.select()
                .from(table("ENTITLEMENT_OVERRIDE"))
                .where(field("SUBJECT_ID").eq(tenantId))
                .and(field("OVERRIDE_KIND").eq("CUSTOM_POLICY"))
                .and(field("STATUS").eq("ACTIVE"))
                .fetchOptional(r -> mapToPolicy(tenantId, r));
    }

    public List<Map<String, Object>> findAllCustomPolicies() {
        return dsl.select()
                .from(table("ENTITLEMENT_OVERRIDE"))
                .where(field("OVERRIDE_KIND").eq("CUSTOM_POLICY"))
                .and(field("STATUS").eq("ACTIVE"))
                .fetch(r -> Map.of(
                        "id", r.get(field("ID"), String.class),
                        "subjectId", r.get(field("SUBJECT_ID"), String.class),
                        "payload", r.get(field("OVERRIDE_PAYLOAD"), String.class)));
    }

    private EntitlementPolicy mapToPolicy(String tenantId, Record r) {
        String overrideId = r.get(field("ID"), String.class);
        String payload = r.get(field("OVERRIDE_PAYLOAD"), String.class);
        return CustomPolicyPayloadParser.parse(tenantId, overrideId, payload);
    }
}
