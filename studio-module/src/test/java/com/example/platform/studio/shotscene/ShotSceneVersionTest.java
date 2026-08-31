package com.example.platform.studio.shotscene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.camera.*;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.scene.SceneVersion;
import com.example.platform.studio.scope.*;
import com.example.platform.studio.screenplay.*;
import com.example.platform.studio.shot.ShotVersion;
import com.example.platform.studio.shotplan.ShotPlanVersion;
import com.example.platform.studio.diff.SemanticChange;
import com.example.platform.studio.diff.StudioSemanticDiff;
import com.example.platform.studio.reference.StudioVersionPin;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotSceneVersionTest {
    private static final StudioScope SCOPE=new StudioScope(new TenantId("tenant-a"),new ProjectId("project-a"));

    @Test void preservesStableHierarchyAndExactPlanningPins(){var c=context();var root=new SceneElement(new SceneElementId("root"),SceneElement.Kind.ENVIRONMENT,null,TransformIdentity.VALUE,List.of(),"Room");var child=new SceneElement(new SceneElementId("actor"),SceneElement.Kind.CHARACTER,root.id(),TransformIdentity.VALUE,List.of(root.id()),"Lead");var version=ShotSceneVersion.create(new ShotSceneId("shot-scene-a"),new ShotSceneVersionId("shot-scene-v1"),SCOPE,null,c.plan,c.screenplay,c.scene,List.of(root,child));assertThat(version.elements()).containsExactly(root,child);assertThat(version.shotPlanPin()).isEqualTo(c.plan.pin());assertThat(version.semanticDigest()).isNotNull();}

    @Test void diffAddressesTransformChangeByStableElementIdentity(){var c=context();var beforeElement=new SceneElement(new SceneElementId("actor"),SceneElement.Kind.CHARACTER,null,TransformIdentity.VALUE,List.of(),"Lead");var moved=new Transform(new Vector3(DecimalValue.of("1"),DecimalValue.of("0"),DecimalValue.of("0")),Quaternion.IDENTITY,Vector3.ONE);var afterElement=new SceneElement(beforeElement.id(),beforeElement.kind(),null,moved,List.of(),"Lead");var before=ShotSceneVersion.create(new ShotSceneId("shot-scene-a"),new ShotSceneVersionId("v1"),SCOPE,null,c.plan,c.screenplay,c.scene,List.of(beforeElement));var after=ShotSceneVersion.create(new ShotSceneId("shot-scene-a"),new ShotSceneVersionId("v2"),SCOPE,new ShotSceneVersionId("v1"),c.plan,c.screenplay,c.scene,List.of(afterElement));assertThat(StudioSemanticDiff.between(before,after).changes()).anyMatch(change->change.kind()==SemanticChange.ChangeKind.TRANSFORM_CHANGED&&change.path().canonical().equals("elements/actor/transform"));}

    @Test void cameraElementCarriesExactProviderNeutralCameraPlanPin(){var c=context();var pin=new StudioVersionPin<>(StudioVersionPin.AggregateKind.CAMERA_PLAN,new CameraPlanId("camera-a"),new CameraPlanVersionId("camera-v1"),SCOPE,1,ContentDigest.sha256("a".repeat(64)));var camera=new SceneElement(new SceneElementId("camera"),SceneElement.Kind.CAMERA_REFERENCE,null,TransformIdentity.VALUE,List.of(),pin,"Authored camera");var version=ShotSceneVersion.create(new ShotSceneId("shot-scene-a"),new ShotSceneVersionId("v1"),SCOPE,null,c.plan,c.screenplay,c.scene,List.of(camera));assertThat(version.elements().getFirst().cameraPlanPin()).isEqualTo(pin);}

    @Test void rejectsCyclesAndDanglingLocalReferences(){var c=context();var a=new SceneElement(new SceneElementId("a"),SceneElement.Kind.PROP,new SceneElementId("b"),TransformIdentity.VALUE,List.of(),"A");var b=new SceneElement(new SceneElementId("b"),SceneElement.Kind.PROP,new SceneElementId("a"),TransformIdentity.VALUE,List.of(),"B");assertThatThrownBy(()->ShotSceneVersion.create(new ShotSceneId("shot-scene-a"),new ShotSceneVersionId("shot-scene-v1"),SCOPE,null,c.plan,c.screenplay,c.scene,List.of(a,b))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycle");var dangling=new SceneElement(new SceneElementId("a"),SceneElement.Kind.MARKER,null,TransformIdentity.VALUE,List.of(new SceneElementId("missing")),"Mark");assertThatThrownBy(()->ShotSceneVersion.create(new ShotSceneId("shot-scene-a"),new ShotSceneVersionId("shot-scene-v1"),SCOPE,null,c.plan,c.screenplay,c.scene,List.of(dangling))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dangling");}

    private static Context context(){var heading=new ScreenplayElementId("heading-a");var screenplay=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("screenplay-v1"),SCOPE,null,List.of(new ScreenplayElement.SceneHeading(heading,ScreenplayElement.InteriorExterior.INTERIOR,"Room",ScreenplayElement.TimeOfDay.DAY)));var scene=SceneVersion.create(new SceneId("scene-a"),new SceneVersionId("scene-v1"),SCOPE,null,screenplay,heading,"Synopsis","Purpose");var shot=ShotVersion.create(new ShotId("shot-a"),new ShotVersionId("shot-v1"),SCOPE,null,scene,"Beat","Subject","Continuity",MediaTime.ofTicks(1,1),null,null);var plan=ShotPlanVersion.create(new ShotPlanId("plan-a"),new ShotPlanVersionId("plan-v1"),SCOPE,null,screenplay,scene,List.of(shot));return new Context(screenplay,scene,plan);}
    private record Context(ScreenplayVersion screenplay,SceneVersion scene,ShotPlanVersion plan){}
    private static final class TransformIdentity{private static final Transform VALUE=new Transform(Vector3.ZERO,Quaternion.IDENTITY,Vector3.ONE);}
}
