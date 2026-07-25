package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementPolicy;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import static com.example.platform.typedschema.jooq.generated.tables.EntitlementOverride.ENTITLEMENT_OVERRIDE;


@Repository

public class CustomPolicyRepository {

    private final DSLContext dsl;

    public CustomPolicyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<EntitlementPolicy> findCustomPolicy(String tenantId) {
        return dsl.select()
                .from(ENTITLEMENT_OVERRIDE)
                .where(ENTITLEMENT_OVERRIDE.SUBJECT_ID.eq(tenantId))
                .and(ENTITLEMENT_OVERRIDE.OVERRIDE_KIND.eq("CUSTOM_POLICY"))
                .and(ENTITLEMENT_OVERRIDE.STATUS.eq("ACTIVE"))
                .fetchOptional(r -> mapToPolicy(tenantId, r));
    }

    public List<Map<String, Object>> findAllCustomPolicies() {
        return dsl.select()
                .from(ENTITLEMENT_OVERRIDE)
                .where(ENTITLEMENT_OVERRIDE.OVERRIDE_KIND.eq("CUSTOM_POLICY"))
                .and(ENTITLEMENT_OVERRIDE.STATUS.eq("ACTIVE"))
                .fetch(r -> Map.of(
                        "id", r.get(ENTITLEMENT_OVERRIDE.ID, String.class),
                        "subjectId", r.get(ENTITLEMENT_OVERRIDE.SUBJECT_ID, String.class),
                        "payload", r.get(ENTITLEMENT_OVERRIDE.OVERRIDE_PAYLOAD, String.class)));
    }

    private EntitlementPolicy mapToPolicy(String tenantId, Record r) {
        String overrideId = r.get(ENTITLEMENT_OVERRIDE.ID, String.class);
        String payload = r.get(ENTITLEMENT_OVERRIDE.OVERRIDE_PAYLOAD, String.class);
        return CustomPolicyPayloadParser.parse(tenantId, overrideId, payload);
    }
}
