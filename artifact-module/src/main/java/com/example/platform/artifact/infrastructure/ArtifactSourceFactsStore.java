package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.ArtifactSourceFactsQuery;
import com.example.platform.artifact.app.ArtifactSourceCatalog;
import com.example.platform.artifact.contract.ArtifactScope;
import com.example.platform.artifact.contract.ArtifactSourceFacts;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Source structure is written once alongside the Artifact commit and read only by exact pin. */
@Repository
public class ArtifactSourceFactsStore implements ArtifactSourceFactsQuery, ArtifactSourceCatalog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DSLContext dsl;
    public ArtifactSourceFactsStore(DSLContext dsl) { this.dsl = dsl; }

    public void record(ArtifactScope scope, ArtifactId id, ContentDigest digest, ArtifactSourceFacts facts) {
        TenantGuard.assertSameTenant(scope.tenantId());
        String value = encode(facts, true);
        int changed = dsl.execute("""
                update artifact set audit_provenance=jsonb_set(audit_provenance,'{sourceProbeV1}',
                    coalesce(audit_provenance->'sourceProbeV1',cast(? as jsonb)),true)
                where tenant_id=? and project_id=? and workspace_id=? and id=? and content_digest=?
                  and (audit_provenance->'sourceProbeV1' is null or audit_provenance->'sourceProbeV1'=cast(? as jsonb)
                       or audit_provenance->'sourceProbeV1'=cast(? as jsonb))
                """, value, scope.tenantId(), scope.projectId(), scope.workspaceId(), id.value(),
                digest.canonicalValue(), value, encode(facts, false));
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

    @Override
    public void recordDisplayName(String tenant, String project, ArtifactId id, ContentDigest digest, String name) {
        TenantGuard.assertSameTenant(tenant);
        if (name == null || name.isBlank() || name.length() > 256 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("bounded source display name required");
        }
        int changed = dsl.execute("""
                update artifact set audit_provenance=jsonb_set(audit_provenance,'{sourceUploadV1}',
                    jsonb_build_object('name',cast(? as text)),true)
                where tenant_id=? and project_id=? and id=? and content_digest=? and artifact_kind='SOURCE_MEDIA'
                  and (audit_provenance->'sourceUploadV1' is null
                       or audit_provenance->'sourceUploadV1'->>'name'=?)
                """, name, tenant, project, id.value(), digest.canonicalValue(), name);
        if (changed != 1) throw new IllegalStateException("source display metadata conflict or scoped Artifact unavailable");
    }

    @Override
    public List<SourceEntry> list(String tenant, String project, int limit) {
        TenantGuard.assertSameTenant(tenant);
        int bounded = Math.max(1, Math.min(limit, 200));
        return dsl.fetch("""
                select id,content_digest,byte_length,
                       coalesce(audit_provenance->'sourceUploadV1'->>'name',id) as display_name,
                       (audit_provenance->'sourceProbeV1')::text as source_facts
                from artifact where tenant_id=? and project_id=? and artifact_kind='SOURCE_MEDIA'
                  and state='AVAILABLE' and jsonb_exists(audit_provenance,'sourceProbeV1')
                order by created_at desc,id asc limit ?
                """, tenant, project, bounded).map(row -> new SourceEntry(
                new ArtifactId(row.get("id", String.class)),
                ContentDigest.sha256(row.get("content_digest", String.class)),
                row.get("byte_length", Long.class), row.get("display_name", String.class),
                decode(row.get("source_facts", String.class))));
    }

    private static String encode(ArtifactSourceFacts facts, boolean includeDisplayAspect) {
        try {
            var root = JSON.createObjectNode();
            root.put("version", 1); root.put("durationTicks", facts.duration().ticks());
            root.put("durationScale", facts.duration().timeScale());
            var streams = root.putArray("streams");
            facts.streams().stream().sorted(java.util.Comparator.comparing(ArtifactSourceFacts.Stream::streamId))
                    .forEach(stream -> { var node = streams.addObject(); node.put("id", stream.streamId());
                        node.put("kind", stream.kind().name()); node.put("durationTicks", stream.duration().ticks());
                        node.put("durationScale", stream.duration().timeScale());
                        if (includeDisplayAspect && stream.displayAspectRatio() != null) {
                            node.put("displayAspectV1", stream.displayAspectRatio());
                        }
                    });
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
                var display = node.get("displayAspectV1");
                if (display != null && !display.isTextual()) {
                    throw new IllegalArgumentException("invalid source display aspect fact");
                }
                streams.add(new ArtifactSourceFacts.Stream(node.path("id").asText(),
                        ArtifactSourceFacts.Kind.valueOf(node.path("kind").asText()),
                        MediaTime.ofTicks(node.path("durationTicks").longValue(), node.path("durationScale").longValue()),
                        display == null ? null : display.textValue()));
            }
            return new ArtifactSourceFacts(MediaTime.ofTicks(root.path("durationTicks").longValue(),
                    root.path("durationScale").longValue()), streams);
        } catch (Exception invalid) { throw new IllegalStateException("invalid persisted source facts", invalid); }
    }
}
