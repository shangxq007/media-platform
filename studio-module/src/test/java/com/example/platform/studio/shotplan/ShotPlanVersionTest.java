package com.example.platform.studio.shotplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.scene.SceneVersion;
import com.example.platform.studio.scope.*;
import com.example.platform.studio.screenplay.*;
import com.example.platform.studio.shot.ShotVersion;
import com.example.platform.studio.diff.StudioSemanticDiff;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotPlanVersionTest {
    private static final StudioScope SCOPE = new StudioScope(new TenantId("tenant-a"), new ProjectId("project-a"));

    @Test
    void preservesExactScenePinAndOrderedExactShotPins() {
        var screenplay = screenplay("screenplay-a", "heading-a");
        var scene = scene("scene-a", screenplay, "heading-a");
        var one = shot("shot-a", "shot-v1", scene);
        var two = shot("shot-b", "shot-v1", scene);
        var ordered = ShotPlanVersion.create(new ShotPlanId("plan-a"), new ShotPlanVersionId("plan-v1"), SCOPE,
                null, screenplay, scene, List.of(one, two));
        var reordered = ShotPlanVersion.create(new ShotPlanId("plan-a"), new ShotPlanVersionId("plan-v1"), SCOPE,
                null, screenplay, scene, List.of(two, one));

        assertThat(ordered.shotPins()).extracting(pin -> pin.aggregateId().value()).containsExactly("shot-a", "shot-b");
        assertThat(ordered.semanticDigest()).isNotEqualTo(reordered.semanticDigest());
        assertThat(StudioSemanticDiff.between(ordered, reordered).changes())
                .extracting(change -> change.path().canonical()).contains("shots/order");
        assertThat(one.planningDuration()).isEqualTo(MediaTime.ofRational(5, 2));
    }

    @Test
    void rejectsDuplicateShotsAndMismatchedSceneLineage() {
        var screenplay = screenplay("screenplay-a", "heading-a");
        var scene = scene("scene-a", screenplay, "heading-a");
        var shot = shot("shot-a", "shot-v1", scene);
        assertThatThrownBy(() -> ShotPlanVersion.create(new ShotPlanId("plan-a"), new ShotPlanVersionId("plan-v1"),
                SCOPE, null, screenplay, scene, List.of(shot, shot)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");

        var otherScreenplay = screenplay("screenplay-b", "heading-b");
        assertThatThrownBy(() -> ShotPlanVersion.create(new ShotPlanId("plan-a"), new ShotPlanVersionId("plan-v1"),
                SCOPE, null, otherScreenplay, scene, List.of(shot)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lineage");
    }

    private static ShotVersion shot(String id, String version, SceneVersion scene) {
        return ShotVersion.create(new ShotId(id), new ShotVersionId(version), SCOPE, null, scene,
                "Visual beat", "Subject advances", "Match direction", MediaTime.ofRational(5, 2), null, null);
    }
    private static SceneVersion scene(String id, ScreenplayVersion screenplay, String heading) {
        return SceneVersion.create(new SceneId(id), new SceneVersionId(id + "-v1"), SCOPE, null, screenplay,
                new ScreenplayElementId(heading), "Synopsis", "Purpose");
    }
    private static ScreenplayVersion screenplay(String id, String heading) {
        return ScreenplayVersion.create(new ScreenplayId(id), new ScreenplayVersionId(id + "-v1"), SCOPE, null,
                List.of(new ScreenplayElement.SceneHeading(new ScreenplayElementId(heading),
                        ScreenplayElement.InteriorExterior.INTERIOR, "Room", ScreenplayElement.TimeOfDay.DAY)));
    }
}
