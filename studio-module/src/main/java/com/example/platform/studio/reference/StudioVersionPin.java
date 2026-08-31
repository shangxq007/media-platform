package com.example.platform.studio.reference;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.identity.StudioId;
import com.example.platform.studio.scope.StudioScope;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.Map;

public record StudioVersionPin<A extends StudioId, V extends StudioId>(AggregateKind kind, A aggregateId,
        V versionId, StudioScope scope, int schemaVersion, ContentDigest semanticDigest) {
    public StudioVersionPin {
        if (kind == null || aggregateId == null || versionId == null || scope == null || semanticDigest == null) {
            throw new IllegalArgumentException("exact version pin fields are required");
        }
        if (schemaVersion < 1) throw new IllegalArgumentException("version zero is forbidden");
    }

    public String canonicalJson() {
        return CanonicalJson.object(Map.of(
                "aggregateId", CanonicalJson.quote(aggregateId.value()),
                "kind", CanonicalJson.quote(kind.name()),
                "projectId", CanonicalJson.quote(scope.projectId().value()),
                "schemaVersion", Integer.toString(schemaVersion),
                "semanticDigest", CanonicalJson.quote(semanticDigest.canonicalValue()),
                "tenantId", CanonicalJson.quote(scope.tenantId().value()),
                "versionId", CanonicalJson.quote(versionId.value())));
    }

    public enum AggregateKind { SCREENPLAY, SCENE, SHOT, SHOT_PLAN, DIRECTOR_INTENT, CAMERA_PLAN, STORYBOARD, SHOT_SCENE }
}
