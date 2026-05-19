package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.entitlement.domain.EntitlementPolicy;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;

@Repository
@ConditionalOnBean(DSLContext.class)
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
        return new EntitlementPolicy(
                "custom-" + tenantId,
                "CUSTOM",
                3840, 2160, 6000,
                false,
                Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac", "remote-javacv"),
                true, true, 20, true,
                Set.of("basic", "pro", "team", "enterprise"),
                Set.of("mp4", "webm", "mov", "dash", "hls", "cmaf"),
                50,
                Map.of("source", "custom_policy_db"));
    }
}
