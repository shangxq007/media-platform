package com.example.platform.studio.scene;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.digest.StudioDigest;
import com.example.platform.studio.identity.SceneId;
import com.example.platform.studio.identity.SceneVersionId;
import com.example.platform.studio.identity.ScreenplayElementId;
import com.example.platform.studio.identity.ScreenplayId;
import com.example.platform.studio.identity.ScreenplayVersionId;
import com.example.platform.studio.reference.StudioVersionPin;
import com.example.platform.studio.scope.StudioScope;
import com.example.platform.studio.screenplay.ScreenplayElement;
import com.example.platform.studio.screenplay.ScreenplayVersion;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SceneVersion implements com.example.platform.studio.digest.CanonicalStudioVersion {
    public static final int SCHEMA_VERSION = 1;
    private final SceneId id;
    private final SceneVersionId versionId;
    private final StudioScope scope;
    private final SceneVersionId parentVersionId;
    private final StudioVersionPin<ScreenplayId, ScreenplayVersionId> screenplayPin;
    private final ScreenplayElementId headingElementId;
    private final String synopsis;
    private final String narrativePurpose;
    private final byte[] canonicalBytes;
    private final ContentDigest semanticDigest;

    private SceneVersion(SceneId id, SceneVersionId versionId, StudioScope scope, SceneVersionId parentVersionId,
            ScreenplayVersion screenplay, ScreenplayElementId headingElementId, String synopsis, String narrativePurpose) {
        if (id == null || versionId == null || scope == null || screenplay == null || headingElementId == null) {
            throw new IllegalArgumentException("scene version fields are required");
        }
        if (!scope.equals(screenplay.scope())) throw new IllegalArgumentException("screenplay scope mismatch");
        if (versionId.equals(parentVersionId)) throw new IllegalArgumentException("version cannot parent itself");
        boolean headingExists = screenplay.elements().stream()
                .anyMatch(element -> element instanceof ScreenplayElement.SceneHeading && element.id().equals(headingElementId));
        if (!headingExists) throw new IllegalArgumentException("heading element does not exist in exact screenplay version");
        this.id = id; this.versionId = versionId; this.scope = scope; this.parentVersionId = parentVersionId;
        this.screenplayPin = screenplay.pin(); this.headingElementId = headingElementId;
        this.synopsis = CanonicalJson.requiredText(synopsis, "synopsis");
        this.narrativePurpose = CanonicalJson.requiredText(narrativePurpose, "narrative purpose");
        this.canonicalBytes = serialize(); this.semanticDigest = StudioDigest.sha256(canonicalBytes);
    }

    public static SceneVersion create(SceneId id, SceneVersionId versionId, StudioScope scope, SceneVersionId parentVersionId,
            ScreenplayVersion screenplay, ScreenplayElementId headingElementId, String synopsis, String narrativePurpose) {
        return new SceneVersion(id, versionId, scope, parentVersionId, screenplay, headingElementId, synopsis, narrativePurpose);
    }

    private byte[] serialize() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("aggregateId", CanonicalJson.quote(id.value()));
        m.put("headingElementId", CanonicalJson.quote(headingElementId.value()));
        m.put("narrativePurpose", CanonicalJson.quote(narrativePurpose));
        if (parentVersionId != null) m.put("parentVersionId", CanonicalJson.quote(parentVersionId.value()));
        m.put("projectId", CanonicalJson.quote(scope.projectId().value()));
        m.put("schemaVersion", "1"); m.put("screenplayPin", screenplayPin.canonicalJson());
        m.put("synopsis", CanonicalJson.quote(synopsis)); m.put("tenantId", CanonicalJson.quote(scope.tenantId().value()));
        m.put("type", CanonicalJson.quote("SCENE_VERSION")); m.put("versionId", CanonicalJson.quote(versionId.value()));
        return CanonicalJson.utf8(CanonicalJson.object(m));
    }

    public StudioVersionPin<SceneId, SceneVersionId> pin() { return new StudioVersionPin<>(StudioVersionPin.AggregateKind.SCENE, id, versionId, scope, SCHEMA_VERSION, semanticDigest); }
    public SceneId id() { return id; } public SceneVersionId versionId() { return versionId; }
    public StudioScope scope() { return scope; } public SceneVersionId parentVersionId() { return parentVersionId; }
    public StudioVersionPin<ScreenplayId, ScreenplayVersionId> screenplayPin() { return screenplayPin; }
    public ScreenplayElementId headingElementId() { return headingElementId; } public String synopsis() { return synopsis; }
    public String narrativePurpose() { return narrativePurpose; } public byte[] canonicalBytes() { return canonicalBytes.clone(); }
    public ContentDigest semanticDigest() { return semanticDigest; }
}
