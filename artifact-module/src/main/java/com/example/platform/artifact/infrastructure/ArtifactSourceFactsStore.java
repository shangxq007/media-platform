package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.ArtifactSourceFactsQuery;
import com.example.platform.artifact.contract.ArtifactScope;
import com.example.platform.artifact.contract.ArtifactSourceFacts;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Source structure is written once alongside the Artifact commit and read only by exact pin. */
@Repository
public class ArtifactSourceFactsStore implements ArtifactSourceFactsQuery {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DSLContext dsl;
    public ArtifactSourceFactsStore(DSLContext dsl) { this.dsl = dsl; }

    public void record(ArtifactScope scope, ArtifactId id, ContentDigest digest, ArtifactSourceFacts facts) {
        TenantGuard.assertSameTenant(scope.tenantId());
        String value = encode(facts);
        int changed = dsl.execute("""
                update artifact set audit_provenance=jsonb_set(audit_provenance,'{sourceProbeV1}',cast(? as jsonb),true)
                where tenant_id=? and project_id=? and workspace_id=? and id=? and content_digest=?
                  and (audit_provenance->'sourceProbeV1' is null or audit_provenance->'sourceProbeV1'=cast(? as jsonb))
                """, value, scope.tenantId(), scope.projectId(), scope.workspaceId(), id.value(),
                digest.canonicalValue(), value);
        if (changed != 1) throw new IllegalStateException("source facts conflict or scoped Artifact unavailable");
    }

    @Override
    public Optional<ArtifactSourceFacts> find(String tenant, String project, ArtifactId id, ContentDigest digest) {
        TenantGuard.assertSameTenant(tenant);
        var row = dsl.fetchOne("""
                select (audit_provenance->'sourceProbeV1')::text from artifact
                where tenant_id=? and project_id=? and id=? and content_digest=? and state='AVAILABLE'
                """, tenant, project, id.value(), digest.canonicalValue());
        if (row == null || row.get(0) == null) return Optional.empty();
        return Optional.of(decode(row.get(0, String.class)));
    }

    private static String encode(ArtifactSourceFacts facts) {
        try {
            var root = JSON.createObjectNode();
            root.put("version", 1); root.put("durationTicks", facts.duration().ticks());
            root.put("durationScale", facts.duration().timeScale());
            var streams = root.putArray("streams");
            facts.streams().stream().sorted(java.util.Comparator.comparing(ArtifactSourceFacts.Stream::streamId))
                    .forEach(stream -> { var node = streams.addObject(); node.put("id", stream.streamId());
                        node.put("kind", stream.kind().name()); node.put("durationTicks", stream.duration().ticks());
                        node.put("durationScale", stream.duration().timeScale()); });
            return JSON.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("cannot encode source facts", invalid);
        }
    }

    private static ArtifactSourceFacts decode(String value) {
        try {
            var root = JSON.readTree(value);
            if (root.path("version").asInt() != 1 || !root.path("durationTicks").isIntegralNumber()
                    || !root.path("durationScale").isIntegralNumber() || !root.path("streams").isArray()) {
                throw new IllegalArgumentException("invalid source facts");
            }
            var streams = new ArrayList<ArtifactSourceFacts.Stream>();
            for (var node : root.path("streams")) {
                streams.add(new ArtifactSourceFacts.Stream(node.path("id").asText(),
                        ArtifactSourceFacts.Kind.valueOf(node.path("kind").asText()),
                        MediaTime.ofTicks(node.path("durationTicks").longValue(), node.path("durationScale").longValue())));
            }
            return new ArtifactSourceFacts(MediaTime.ofTicks(root.path("durationTicks").longValue(),
                    root.path("durationScale").longValue()), streams);
        } catch (Exception invalid) { throw new IllegalStateException("invalid persisted source facts", invalid); }
    }
}
