package com.example.platform.studio.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.studio.identity.*;
import com.example.platform.studio.scope.*;
import com.example.platform.studio.screenplay.*;
import com.example.platform.studio.scene.SceneVersion;
import com.example.platform.studio.shot.ShotVersion;
import com.example.platform.studio.directorintent.DirectorIntentVersion;
import com.example.platform.studio.reference.StudioVersionPin;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudioSemanticDiffTest {
    private static final StudioScope SCOPE=new StudioScope(new TenantId("tenant-a"),new ProjectId("project-a"));

    @Test void addressesAddsAndReordersByStableElementIdentity(){var heading=new ScreenplayElement.SceneHeading(new ScreenplayElementId("heading"),ScreenplayElement.InteriorExterior.INTERIOR,"Room",ScreenplayElement.TimeOfDay.DAY);var action=new ScreenplayElement.Action(new ScreenplayElementId("action"),"Move");var before=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(heading));var after=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("v2"),SCOPE,new ScreenplayVersionId("v1"),List.of(action,heading));var diff=StudioSemanticDiff.between(before,after);assertThat(diff.changes()).extracting(change->change.path().canonical()).contains("elements/action","elements/order","parentVersionId");assertThat(diff.changes()).isSortedAccordingTo(SemanticChange.CANONICAL_ORDER);}

    @Test void rejectsCrossAggregateComparisonAndDeclaresAllMajorTypes(){var one=ScreenplayVersion.create(new ScreenplayId("one"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(new ScreenplayElement.Action(new ScreenplayElementId("a"),"A")));var two=ScreenplayVersion.create(new ScreenplayId("two"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(new ScreenplayElement.Action(new ScreenplayElementId("a"),"A")));assertThatThrownBy(()->StudioSemanticDiff.between(one,two)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same aggregate");assertThat(StudioSemanticDiff.supportedAggregateTypeCount()).isEqualTo(8);}

    @Test void rejectsDifferentAggregateFamilies(){var headingId=new ScreenplayElementId("heading");var screenplay=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(new ScreenplayElement.SceneHeading(headingId,ScreenplayElement.InteriorExterior.INTERIOR,"Room",ScreenplayElement.TimeOfDay.DAY)));var scene=SceneVersion.create(new SceneId("scene-a"),new SceneVersionId("v1"),SCOPE,null,screenplay,headingId,"Synopsis","Purpose");assertThatThrownBy(()->StudioSemanticDiff.between((Object)screenplay,(Object)scene)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aggregate type");}

    @Test
    void sceneShotAndDirectorIntentDiffsUseBoundedTypedSemanticPaths() {
        var heading1 = new ScreenplayElementId("heading-1");
        var heading2 = new ScreenplayElementId("heading-2");
        var screenplay1 = ScreenplayVersion.create(new ScreenplayId("screenplay-a"), new ScreenplayVersionId("sp-v1"),
                SCOPE, null, List.of(new ScreenplayElement.SceneHeading(heading1,
                        ScreenplayElement.InteriorExterior.INTERIOR, "Room", ScreenplayElement.TimeOfDay.DAY)));
        var screenplay2 = ScreenplayVersion.create(new ScreenplayId("screenplay-a"), new ScreenplayVersionId("sp-v2"),
                SCOPE, new ScreenplayVersionId("sp-v1"), List.of(new ScreenplayElement.SceneHeading(heading2,
                        ScreenplayElement.InteriorExterior.EXTERIOR, "Street", ScreenplayElement.TimeOfDay.NIGHT)));
        var scene1 = SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v1"), SCOPE, null,
                screenplay1, heading1, "Synopsis one", "Purpose one");
        var scene2 = SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v2"), SCOPE,
                new SceneVersionId("scene-v1"), screenplay2, heading2, "Synopsis two", "Purpose two");

        assertThat(StudioSemanticDiff.between(scene1, scene2).changes())
                .extracting(change -> change.path().canonical())
                .contains("screenplayPin", "headingElementId", "synopsis", "narrativePurpose")
                .doesNotContain("scene/semantics");

        var digest = ContentDigest.sha256("b".repeat(64));
        var directorPin = new StudioVersionPin<>(StudioVersionPin.AggregateKind.DIRECTOR_INTENT,
                new DirectorIntentId("intent-a"), new DirectorIntentVersionId("intent-v1"), SCOPE, 1, digest);
        var cameraPin = new StudioVersionPin<>(StudioVersionPin.AggregateKind.CAMERA_PLAN,
                new CameraPlanId("camera-a"), new CameraPlanVersionId("camera-v1"), SCOPE, 1, digest);
        var shot1 = ShotVersion.create(new ShotId("shot-a"), new ShotVersionId("shot-v1"), SCOPE, null, scene1,
                "Description one", "Action one", "Continuity one", MediaTime.ofTicks(1, 1), null, null);
        var shot2 = ShotVersion.create(new ShotId("shot-a"), new ShotVersionId("shot-v2"), SCOPE,
                new ShotVersionId("shot-v1"), scene2, "Description two", "Action two", "Continuity two",
                MediaTime.ofTicks(2, 1), directorPin, cameraPin);
        assertThat(StudioSemanticDiff.between(shot1, shot2).changes())
                .extracting(change -> change.path().canonical())
                .contains("scenePin", "description", "subjectActionIntent", "continuityIntent", "planningDuration",
                        "directorIntentPin", "cameraPlanPin")
                .doesNotContain("shot/semantics");

        var intent1 = DirectorIntentVersion.create(new DirectorIntentId("intent-a"), new DirectorIntentVersionId("v1"),
                SCOPE, null, DirectorIntentVersion.Emphasis.SUBJECT, DirectorIntentVersion.EmotionalTone.TENSE,
                DirectorIntentVersion.CameraMovementIntent.STATIC, DirectorIntentVersion.LightingMood.NATURAL,
                List.of("First", "Second"));
        var intent2 = DirectorIntentVersion.create(new DirectorIntentId("intent-a"), new DirectorIntentVersionId("v2"),
                SCOPE, new DirectorIntentVersionId("v1"), DirectorIntentVersion.Emphasis.RELATIONSHIP,
                DirectorIntentVersion.EmotionalTone.CALM, DirectorIntentVersion.CameraMovementIntent.PAN,
                DirectorIntentVersion.LightingMood.HIGH_KEY, List.of("Second", "Changed"));
        assertThat(StudioSemanticDiff.between(intent1, intent2).changes())
                .extracting(change -> change.path().canonical())
                .contains("emphasis", "emotionalTone", "cameraMovementIntent", "lightingMood", "annotations/semantics")
                .doesNotContain("directorIntent/semantics");
    }
}
