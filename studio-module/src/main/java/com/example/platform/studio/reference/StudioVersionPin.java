package com.example.platform.studio.reference;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.identity.*;
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
        if (!matchesIdentityFamily(kind, aggregateId, versionId)) {
            throw new IllegalArgumentException("aggregate kind and identity family mismatch");
        }
    }

    public String canonicalJson() {
        return CanonicalJson.object(Map.of(
                "aggregateId", CanonicalJson.quote(aggregateId.value()),
                "kind", CanonicalJson.quote(kind.name()),
                "projectId", CanonicalJson.quote(scope.projectId().value()),
                "schemaVersion", Integer.toString(schemaVersion),
                "semanticDigest", CanonicalJson.object(Map.of(
                        "algorithm", CanonicalJson.quote(semanticDigest.algorithm().name()),
                        "value", CanonicalJson.quote(semanticDigest.canonicalValue()))),
                "tenantId", CanonicalJson.quote(scope.tenantId().value()),
                "versionId", CanonicalJson.quote(versionId.value())));
    }

    private static boolean matchesIdentityFamily(AggregateKind kind, StudioId aggregateId, StudioId versionId) {
        return switch (kind) {
            case SCREENPLAY -> aggregateId instanceof ScreenplayId && versionId instanceof ScreenplayVersionId;
            case SCENE -> aggregateId instanceof SceneId && versionId instanceof SceneVersionId;
            case SHOT -> aggregateId instanceof ShotId && versionId instanceof ShotVersionId;
            case SHOT_PLAN -> aggregateId instanceof ShotPlanId && versionId instanceof ShotPlanVersionId;
            case DIRECTOR_INTENT -> aggregateId instanceof DirectorIntentId && versionId instanceof DirectorIntentVersionId;
            case CAMERA_PLAN -> aggregateId instanceof CameraPlanId && versionId instanceof CameraPlanVersionId;
            case STORYBOARD -> aggregateId instanceof StoryboardId && versionId instanceof StoryboardVersionId;
            case SHOT_SCENE -> aggregateId instanceof ShotSceneId && versionId instanceof ShotSceneVersionId;
        };
    }

    public enum AggregateKind { SCREENPLAY, SCENE, SHOT, SHOT_PLAN, DIRECTOR_INTENT, CAMERA_PLAN, STORYBOARD, SHOT_SCENE }
}
