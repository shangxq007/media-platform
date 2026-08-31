package com.example.platform.studio.camera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.studio.directorintent.DirectorIntentVersion;
import com.example.platform.studio.diff.SemanticChange;
import com.example.platform.studio.diff.StudioSemanticDiff;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.scene.SceneVersion;
import com.example.platform.studio.scope.*;
import com.example.platform.studio.screenplay.*;
import com.example.platform.studio.shot.ShotVersion;
import com.example.platform.studio.shotplan.ShotPlanVersion;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DirectorCameraVersionTest {
    private static final StudioScope SCOPE = new StudioScope(new TenantId("tenant-a"), new ProjectId("project-a"));

    @Test
    void preservesOptionalProviderNeutralImagingIntentInCanonicalSemantics() {
        var context = context();
        var intent = directorIntent();
        var pose = identityPose();
        var none = camera(context, intent, pose, CameraImagingIntent.none());
        var authored = camera(context, intent, pose, CameraImagingIntent.authored(
                new CameraImagingIntent.Sensor(DecimalValue.of("36"), DecimalValue.of("24")),
                new CameraImagingIntent.Aperture(DecimalValue.of("2.8")),
                new CameraImagingIntent.FocusDistance(DecimalValue.of("3.5")),
                new CameraImagingIntent.Exposure(MediaTime.ofTicks(1, 48), 800)));

        assertThat(new String(none.canonicalBytes(), StandardCharsets.UTF_8)).doesNotContain("imagingIntent");
        assertThat(new String(authored.canonicalBytes(), StandardCharsets.UTF_8))
                .contains("\"imagingIntent\"")
                .contains("\"sensor\"")
                .contains("\"aperture\"")
                .contains("\"focusDistance\"")
                .contains("\"exposure\"")
                .doesNotContain("null");
        assertThat(authored.semanticDigest()).isNotEqualTo(none.semanticDigest());
        assertThat(authored.imagingIntent().exposure()).isPresent();
    }

    @Test
    void rejectsOutOfRangeImagingIntent() {
        assertThatThrownBy(() -> new CameraImagingIntent.Sensor(DecimalValue.of("0"), DecimalValue.of("24")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sensor");
        assertThatThrownBy(() -> new CameraImagingIntent.Aperture(DecimalValue.of("129")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aperture");
        assertThatThrownBy(() -> new CameraImagingIntent.FocusDistance(DecimalValue.of("0")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("focus");
        assertThatThrownBy(() -> new CameraImagingIntent.Exposure(MediaTime.ZERO, 800))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("shutter");
        assertThatThrownBy(() -> new CameraImagingIntent.Exposure(MediaTime.ofTicks(1, 48), 204801))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISO");
    }

    @Test
    void normalizesNontrivialQuaternionsAtCanonicalScaleWithSignEquivalence() {
        var positive = new Quaternion(DecimalValue.of("1"), DecimalValue.of("2"),
                DecimalValue.of("3"), DecimalValue.of("4"));
        var negative = new Quaternion(DecimalValue.of("-1"), DecimalValue.of("-2"),
                DecimalValue.of("-3"), DecimalValue.of("-4"));

        assertThat(positive.canonicalJson()).isEqualTo(negative.canonicalJson());
        assertThat(positive.x().canonical()).isEqualTo("0.182574185835");
        assertThat(positive.y().canonical()).isEqualTo("0.36514837167");
        assertThat(positive.z().canonical()).isEqualTo("0.547722557505");
        assertThat(positive.w().canonical()).isEqualTo("0.73029674334");
    }

    @Test
    void rejectsNonUniformScaleWithoutExplicitSubsystemOptIn() {
        assertThatThrownBy(() -> new Transform(Vector3.ZERO, Quaternion.IDENTITY,
                new Vector3(DecimalValue.of("1"), DecimalValue.of("2"), DecimalValue.of("1"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("uniform scale");
    }

    @Test
    void rejectsZeroLookAtUpVector() {
        assertThatThrownBy(() -> new CameraPose.LookAtPose(Vector3.ZERO,
                new Vector3(DecimalValue.of("0"), DecimalValue.of("0"), DecimalValue.of("-1")),
                Vector3.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("up vector");
    }

    @Test
    void rejectsLookAtUpVectorCollinearWithTargetDirection() {
        assertThatThrownBy(() -> new CameraPose.LookAtPose(Vector3.ZERO,
                new Vector3(DecimalValue.of("1"), DecimalValue.of("2"), DecimalValue.of("3")),
                new Vector3(DecimalValue.of("2"), DecimalValue.of("4"), DecimalValue.of("6"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("collinear");
    }

    @Test
    void cameraDiffRetainsEveryIndependentTypedChangeWhenPoseAlsoChanges() {
        var heading = new ScreenplayElementId("heading-a");
        var screenplay1 = ScreenplayVersion.create(new ScreenplayId("screenplay-a"), new ScreenplayVersionId("sp-v1"),
                SCOPE, null, List.of(new ScreenplayElement.SceneHeading(heading,
                        ScreenplayElement.InteriorExterior.INTERIOR, "Room", ScreenplayElement.TimeOfDay.DAY)));
        var scene1 = SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v1"), SCOPE, null,
                screenplay1, heading, "Synopsis", "Purpose");
        var shot1 = ShotVersion.create(new ShotId("shot-a"), new ShotVersionId("shot-v1"), SCOPE, null, scene1,
                "Beat", "Subject", "Continuity", MediaTime.ofTicks(2, 1), null, null);
        var plan1 = ShotPlanVersion.create(new ShotPlanId("plan-a"), new ShotPlanVersionId("plan-v1"), SCOPE, null,
                screenplay1, scene1, List.of(shot1));
        var intent1 = directorIntent();
        var pose1 = identityPose();
        var before = CameraPlanVersion.create(new CameraPlanId("camera-a"), new CameraPlanVersionId("camera-v1"),
                SCOPE, null, CameraPlanVersion.Projection.PERSPECTIVE, CameraPlanVersion.Framing.WIDE,
                CameraPlanVersion.TargetIntent.SUBJECT, pose1,
                new CameraOptics.FocalLengthMm(DecimalValue.of("50")), CameraImagingIntent.none(),
                shot1, plan1, intent1, List.of(new CameraKeyframe(MediaTime.ZERO, pose1)));

        var screenplay2 = ScreenplayVersion.create(new ScreenplayId("screenplay-a"), new ScreenplayVersionId("sp-v2"),
                SCOPE, new ScreenplayVersionId("sp-v1"), screenplay1.elements());
        var scene2 = SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v2"), SCOPE,
                new SceneVersionId("scene-v1"), screenplay2, heading, "Synopsis", "Purpose");
        var shot2 = ShotVersion.create(new ShotId("shot-a"), new ShotVersionId("shot-v2"), SCOPE,
                new ShotVersionId("shot-v1"), scene2, "Beat", "Subject", "Continuity", MediaTime.ofTicks(2, 1), null, null);
        var plan2 = ShotPlanVersion.create(new ShotPlanId("plan-a"), new ShotPlanVersionId("plan-v2"), SCOPE,
                new ShotPlanVersionId("plan-v1"), screenplay2, scene2, List.of(shot2));
        var intent2 = DirectorIntentVersion.create(new DirectorIntentId("intent-a"), new DirectorIntentVersionId("intent-v2"),
                SCOPE, new DirectorIntentVersionId("intent-v1"), DirectorIntentVersion.Emphasis.RELATIONSHIP,
                DirectorIntentVersion.EmotionalTone.CALM, DirectorIntentVersion.CameraMovementIntent.PAN,
                DirectorIntentVersion.LightingMood.HIGH_KEY, List.of("Airy"));
        var pose2 = new CameraPose.TransformPose(new Transform(
                new Vector3(DecimalValue.of("1"), DecimalValue.of("0"), DecimalValue.of("0")),
                Quaternion.IDENTITY, Vector3.ONE));
        var imaging = CameraImagingIntent.authored(null, new CameraImagingIntent.Aperture(DecimalValue.of("4")),
                null, new CameraImagingIntent.Exposure(MediaTime.ofTicks(1, 96), 400));
        var after = CameraPlanVersion.create(new CameraPlanId("camera-a"), new CameraPlanVersionId("camera-v2"),
                SCOPE, new CameraPlanVersionId("camera-v1"), CameraPlanVersion.Projection.ORTHOGRAPHIC,
                CameraPlanVersion.Framing.CLOSE_UP, CameraPlanVersion.TargetIntent.DETAIL, pose2,
                new CameraOptics.FieldOfViewDegrees(DecimalValue.of("75")), imaging,
                shot2, plan2, intent2, List.of(new CameraKeyframe(MediaTime.ZERO, pose2),
                        new CameraKeyframe(MediaTime.ofTicks(1, 1), pose2)));

        assertThat(StudioSemanticDiff.between(before, after).changes())
                .extracting(change -> change.path().canonical())
                .contains("pose", "projection", "framing", "targetIntent", "optics", "imagingIntent",
                        "shotPin", "shotPlanPin", "directorIntentPin", "keyframes/semantics");
    }

    @Test
    void canonicalizesProviderNeutralPoseOpticsAndQuaternion() {
        var context = context();
        var intent = DirectorIntentVersion.create(new DirectorIntentId("intent-a"), new DirectorIntentVersionId("intent-v1"),
                SCOPE, null, DirectorIntentVersion.Emphasis.SUBJECT, DirectorIntentVersion.EmotionalTone.TENSE,
                DirectorIntentVersion.CameraMovementIntent.PUSH_IN, DirectorIntentVersion.LightingMood.LOW_KEY,
                List.of("Hold on the reaction."));
        var negativeIdentity = new Quaternion(DecimalValue.of("0"), DecimalValue.of("0"), DecimalValue.of("0"), DecimalValue.of("-1"));
        var pose = new CameraPose.TransformPose(new Transform(
                new Vector3(DecimalValue.of("1.00"), DecimalValue.of("2"), DecimalValue.of("3")), negativeIdentity,
                new Vector3(DecimalValue.of("1"), DecimalValue.of("1"), DecimalValue.of("1"))));
        var version = CameraPlanVersion.create(new CameraPlanId("camera-a"), new CameraPlanVersionId("camera-v1"), SCOPE,
                null, CameraPlanVersion.Projection.PERSPECTIVE, CameraPlanVersion.Framing.MEDIUM,
                CameraPlanVersion.TargetIntent.SUBJECT, pose, new CameraOptics.FocalLengthMm(DecimalValue.of("50.0")),
                context.shot, context.plan, intent, List.of(new CameraKeyframe(MediaTime.ZERO, pose),
                        new CameraKeyframe(MediaTime.ofTicks(1, 1), pose)));

        assertThat(version.semanticDigest()).isNotNull();
        assertThat(negativeIdentity.w().canonical()).isEqualTo("1");
        assertThat(DecimalValue.of("1.00").canonical()).isEqualTo("1");
        var changedPose = new CameraPose.TransformPose(new Transform(
                new Vector3(DecimalValue.of("2"), DecimalValue.of("2"), DecimalValue.of("3")),
                Quaternion.IDENTITY, Vector3.ONE));
        var changed = CameraPlanVersion.create(new CameraPlanId("camera-a"), new CameraPlanVersionId("camera-v2"),
                SCOPE, new CameraPlanVersionId("camera-v1"), CameraPlanVersion.Projection.PERSPECTIVE,
                CameraPlanVersion.Framing.MEDIUM, CameraPlanVersion.TargetIntent.SUBJECT, changedPose,
                new CameraOptics.FocalLengthMm(DecimalValue.of("50")), context.shot, context.plan, intent,
                List.of(new CameraKeyframe(MediaTime.ZERO, changedPose)));
        assertThat(StudioSemanticDiff.between(version, changed).changes())
                .anyMatch(change -> change.kind() == SemanticChange.ChangeKind.TRANSFORM_CHANGED
                        && change.path().canonical().equals("pose"));
    }

    @Test
    void rejectsExecutableAnnotationsNonFiniteNumbersAndUnorderedKeyframes() {
        assertThatThrownBy(() -> DirectorIntentVersion.create(new DirectorIntentId("intent-a"),
                new DirectorIntentVersionId("intent-v1"), SCOPE, null, DirectorIntentVersion.Emphasis.SUBJECT,
                DirectorIntentVersion.EmotionalTone.TENSE, DirectorIntentVersion.CameraMovementIntent.STATIC,
                DirectorIntentVersion.LightingMood.NATURAL, List.of("exec --render scene")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("annotation");
        assertThatThrownBy(() -> DecimalValue.of("NaN")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Quaternion(DecimalValue.of("0"), DecimalValue.of("0"),
                DecimalValue.of("0"), DecimalValue.of("0"))).isInstanceOf(IllegalArgumentException.class);

        var context = context();
        var intent = DirectorIntentVersion.create(new DirectorIntentId("intent-a"), new DirectorIntentVersionId("intent-v1"),
                SCOPE, null, DirectorIntentVersion.Emphasis.SUBJECT, DirectorIntentVersion.EmotionalTone.TENSE,
                DirectorIntentVersion.CameraMovementIntent.STATIC, DirectorIntentVersion.LightingMood.NATURAL, List.of());
        var pose = identityPose();
        assertThatThrownBy(() -> CameraPlanVersion.create(new CameraPlanId("camera-a"), new CameraPlanVersionId("camera-v1"),
                SCOPE, null, CameraPlanVersion.Projection.PERSPECTIVE, CameraPlanVersion.Framing.WIDE,
                CameraPlanVersion.TargetIntent.ENVIRONMENT, pose, new CameraOptics.FieldOfViewDegrees(DecimalValue.of("60")),
                context.shot, context.plan, intent, List.of(new CameraKeyframe(MediaTime.ofTicks(2, 1), pose),
                        new CameraKeyframe(MediaTime.ofTicks(1, 1), pose))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("increasing");
        assertThat(new CameraPose.LookAtPose(Vector3.ZERO,
                new Vector3(DecimalValue.of("0"), DecimalValue.of("0"), DecimalValue.of("-1")),
                new Vector3(DecimalValue.of("0"), DecimalValue.of("1"), DecimalValue.of("0"))))
                .isInstanceOf(CameraPose.LookAtPose.class);
    }

    private static CameraPose identityPose() {
        return new CameraPose.TransformPose(new Transform(Vector3.ZERO, Quaternion.IDENTITY, Vector3.ONE));
    }
    private static DirectorIntentVersion directorIntent() {
        return DirectorIntentVersion.create(new DirectorIntentId("intent-a"), new DirectorIntentVersionId("intent-v1"),
                SCOPE, null, DirectorIntentVersion.Emphasis.SUBJECT, DirectorIntentVersion.EmotionalTone.TENSE,
                DirectorIntentVersion.CameraMovementIntent.STATIC, DirectorIntentVersion.LightingMood.NATURAL, List.of());
    }
    private static CameraPlanVersion camera(Context context, DirectorIntentVersion intent, CameraPose pose,
            CameraImagingIntent imagingIntent) {
        return CameraPlanVersion.create(new CameraPlanId("camera-a"), new CameraPlanVersionId("camera-v1"), SCOPE,
                null, CameraPlanVersion.Projection.PERSPECTIVE, CameraPlanVersion.Framing.WIDE,
                CameraPlanVersion.TargetIntent.SUBJECT, pose,
                new CameraOptics.FocalLengthMm(DecimalValue.of("50")), imagingIntent,
                context.shot, context.plan, intent, List.of(new CameraKeyframe(MediaTime.ZERO, pose)));
    }
    private static Context context() {
        var heading = new ScreenplayElementId("heading-a");
        var screenplay = ScreenplayVersion.create(new ScreenplayId("screenplay-a"), new ScreenplayVersionId("screenplay-v1"),
                SCOPE, null, List.of(new ScreenplayElement.SceneHeading(heading,
                        ScreenplayElement.InteriorExterior.INTERIOR, "Room", ScreenplayElement.TimeOfDay.DAY)));
        var scene = SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v1"), SCOPE, null,
                screenplay, heading, "Synopsis", "Purpose");
        var shot = ShotVersion.create(new ShotId("shot-a"), new ShotVersionId("shot-v1"), SCOPE, null, scene,
                "Beat", "Subject", "Continuity", MediaTime.ofTicks(2, 1), null, null);
        var plan = ShotPlanVersion.create(new ShotPlanId("plan-a"), new ShotPlanVersionId("plan-v1"), SCOPE, null,
                screenplay, scene, List.of(shot));
        return new Context(shot, plan);
    }
    private record Context(ShotVersion shot, ShotPlanVersion plan) {}
}
