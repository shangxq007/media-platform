package com.example.platform.studio.screenplay;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.digest.StudioDigest;
import com.example.platform.studio.identity.ScreenplayId;
import com.example.platform.studio.identity.ScreenplayVersionId;
import com.example.platform.studio.scope.StudioScope;
import com.example.platform.studio.reference.StudioVersionPin;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScreenplayVersion implements com.example.platform.studio.digest.CanonicalStudioVersion {
    public static final int SCHEMA_VERSION = 1;
    private final ScreenplayId id;
    private final ScreenplayVersionId versionId;
    private final StudioScope scope;
    private final ScreenplayVersionId parentVersionId;
    private final List<ScreenplayElement> elements;
    private final byte[] canonicalBytes;
    private final ContentDigest semanticDigest;

    private ScreenplayVersion(ScreenplayId id, ScreenplayVersionId versionId, StudioScope scope,
            ScreenplayVersionId parentVersionId, List<ScreenplayElement> elements, ContentDigest providedDigest) {
        if (id == null || versionId == null || scope == null || elements == null || elements.isEmpty()) {
            throw new IllegalArgumentException("screenplay version fields are required");
        }
        if (versionId.equals(parentVersionId)) throw new IllegalArgumentException("version cannot parent itself");
        var seen = new HashSet<>();
        for (var element : elements) {
            if (element == null) throw new IllegalArgumentException("screenplay element is required");
            if (!seen.add(element.id())) throw new IllegalArgumentException("duplicate screenplay element identity");
        }
        this.id = id;
        this.versionId = versionId;
        this.scope = scope;
        this.parentVersionId = parentVersionId;
        this.elements = List.copyOf(elements);
        this.canonicalBytes = serialize();
        this.semanticDigest = StudioDigest.sha256(canonicalBytes);
        if (providedDigest != null) StudioDigest.verify(providedDigest, canonicalBytes);
    }

    public static ScreenplayVersion create(ScreenplayId id, ScreenplayVersionId versionId, StudioScope scope,
            ScreenplayVersionId parentVersionId, List<ScreenplayElement> elements) {
        return new ScreenplayVersion(id, versionId, scope, parentVersionId, elements, null);
    }

    public static ScreenplayVersion verify(ScreenplayId id, ScreenplayVersionId versionId, StudioScope scope,
            ScreenplayVersionId parentVersionId, List<ScreenplayElement> elements, ContentDigest digest) {
        return new ScreenplayVersion(id, versionId, scope, parentVersionId, elements, digest);
    }

    private byte[] serialize() {
        Map<String, String> members = new LinkedHashMap<>();
        members.put("aggregateId", CanonicalJson.quote(id.value()));
        members.put("elements", CanonicalJson.array(elements.stream().map(ScreenplayElement::canonicalJson).toList()));
        if (parentVersionId != null) members.put("parentVersionId", CanonicalJson.quote(parentVersionId.value()));
        members.put("projectId", CanonicalJson.quote(scope.projectId().value()));
        members.put("schemaVersion", Integer.toString(SCHEMA_VERSION));
        members.put("tenantId", CanonicalJson.quote(scope.tenantId().value()));
        members.put("type", CanonicalJson.quote("SCREENPLAY_VERSION"));
        members.put("versionId", CanonicalJson.quote(versionId.value()));
        return CanonicalJson.utf8(CanonicalJson.object(members));
    }

    public ScreenplayId id() { return id; }
    public ScreenplayVersionId versionId() { return versionId; }
    public StudioScope scope() { return scope; }
    public ScreenplayVersionId parentVersionId() { return parentVersionId; }
    public List<ScreenplayElement> elements() { return elements; }
    public byte[] canonicalBytes() { return canonicalBytes.clone(); }
    public ContentDigest semanticDigest() { return semanticDigest; }
    public StudioVersionPin<ScreenplayId, ScreenplayVersionId> pin() {
        return new StudioVersionPin<>(StudioVersionPin.AggregateKind.SCREENPLAY, id, versionId, scope, SCHEMA_VERSION, semanticDigest);
    }
}
