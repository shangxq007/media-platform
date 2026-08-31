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
import org.junit.jupiter.api.Test;

class DirectorCameraVersionTest {
    private static final StudioScope SCOPE = new StudioScope(new TenantId("tenant-a"), new ProjectId("project-a"));

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
