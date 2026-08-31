package com.example.platform.studio.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.studio.camera.*;
import com.example.platform.studio.directorintent.DirectorIntentVersion;
import com.example.platform.studio.digest.CanonicalStudioVersion;
import com.example.platform.studio.digest.VerifiedStudioVersion;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.scene.SceneVersion;
import com.example.platform.studio.scope.*;
import com.example.platform.studio.screenplay.*;
import com.example.platform.studio.shot.ShotVersion;
import com.example.platform.studio.shotplan.ShotPlanVersion;
import com.example.platform.studio.shotscene.SceneElement;
import com.example.platform.studio.shotscene.ShotSceneVersion;
import com.example.platform.studio.storyboard.*;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class MajorVersionDeterminismTest {
    private static final StudioScope SCOPE=new StudioScope(new TenantId("tenant-a"),new ProjectId("project-a"));
    private static final Transform IDENTITY=new Transform(Vector3.ZERO,Quaternion.IDENTITY,Vector3.ONE);

    @Test void allEightMajorVersionsAreDeterministicAndSemantic(){
        var heading=new ScreenplayElementId("heading");
        var sp1=screenplay("Action",heading);var sp2=screenplay("Action",heading);var spChanged=screenplay("Changed",heading);prove(sp1.canonicalBytes(),sp1.semanticDigest(),sp2.canonicalBytes(),sp2.semanticDigest(),spChanged.semanticDigest());
        var sc1=scene(sp1,heading,"Purpose");var sc2=scene(sp2,heading,"Purpose");var scChanged=scene(sp1,heading,"Changed");prove(sc1.canonicalBytes(),sc1.semanticDigest(),sc2.canonicalBytes(),sc2.semanticDigest(),scChanged.semanticDigest());
        var sh1=shot("shot-a",sc1,"Beat");var sh2=shot("shot-a",sc2,"Beat");var shChanged=shot("shot-a",sc1,"Changed");prove(sh1.canonicalBytes(),sh1.semanticDigest(),sh2.canonicalBytes(),sh2.semanticDigest(),shChanged.semanticDigest());
        var extra1=shot("shot-b",sc1,"Extra");var extra2=shot("shot-b",sc2,"Extra");var plan1=plan(sp1,sc1,List.of(sh1,extra1));var plan2=plan(sp2,sc2,List.of(sh2,extra2));var planChanged=plan(sp1,sc1,List.of(extra1,sh1));prove(plan1.canonicalBytes(),plan1.semanticDigest(),plan2.canonicalBytes(),plan2.semanticDigest(),planChanged.semanticDigest());
        var di1=intent(DirectorIntentVersion.EmotionalTone.CALM,List.of("First","Second"));var di2=intent(DirectorIntentVersion.EmotionalTone.CALM,List.of("First","Second"));var diChanged=intent(DirectorIntentVersion.EmotionalTone.CALM,List.of("Second","First"));prove(di1.canonicalBytes(),di1.semanticDigest(),di2.canonicalBytes(),di2.semanticDigest(),diChanged.semanticDigest());
        var cp1=camera(sh1,plan1,di1,CameraPlanVersion.Framing.MEDIUM);var cp2=camera(sh2,plan2,di2,CameraPlanVersion.Framing.MEDIUM);var cpChanged=camera(sh1,plan1,di1,CameraPlanVersion.Framing.WIDE);prove(cp1.canonicalBytes(),cp1.semanticDigest(),cp2.canonicalBytes(),cp2.semanticDigest(),cpChanged.semanticDigest());
        var p1=new StoryboardPanel(new StoryboardPanelId("p1"),sh1.pin(),"One",null,new PanelImage.Planned());var p2=new StoryboardPanel(new StoryboardPanelId("p2"),extra1.pin(),"Two",null,new PanelImage.Planned());var board1=StoryboardVersion.create(new StoryboardId("board"),new StoryboardVersionId("v1"),SCOPE,null,plan1,List.of(p1,p2));var board2=StoryboardVersion.create(new StoryboardId("board"),new StoryboardVersionId("v1"),SCOPE,null,plan2,List.of(new StoryboardPanel(new StoryboardPanelId("p1"),sh2.pin(),"One",null,new PanelImage.Planned()),new StoryboardPanel(new StoryboardPanelId("p2"),extra2.pin(),"Two",null,new PanelImage.Planned())));var boardChanged=StoryboardVersion.create(new StoryboardId("board"),new StoryboardVersionId("v1"),SCOPE,null,plan1,List.of(p2,p1));prove(board1.canonicalBytes(),board1.semanticDigest(),board2.canonicalBytes(),board2.semanticDigest(),boardChanged.semanticDigest());
        var e1=new SceneElement(new SceneElementId("a"),SceneElement.Kind.MARKER,null,IDENTITY,List.of(),"One");var e2=new SceneElement(new SceneElementId("b"),SceneElement.Kind.PROP,null,IDENTITY,List.of(),"Two");var ss1=ShotSceneVersion.create(new ShotSceneId("ss"),new ShotSceneVersionId("v1"),SCOPE,null,plan1,sp1,sc1,List.of(e1,e2));var ss2=ShotSceneVersion.create(new ShotSceneId("ss"),new ShotSceneVersionId("v1"),SCOPE,null,plan2,sp2,sc2,List.of(e1,e2));var ssChanged=ShotSceneVersion.create(new ShotSceneId("ss"),new ShotSceneVersionId("v1"),SCOPE,null,plan1,sp1,sc1,List.of(e2,e1));prove(ss1.canonicalBytes(),ss1.semanticDigest(),ss2.canonicalBytes(),ss2.semanticDigest(),ssChanged.semanticDigest());
        var allMajorVersions=List.of(sp1,sc1,sh1,plan1,di1,cp1,board1,ss1);
        assertThat(allMajorVersions).allMatch(CanonicalStudioVersion.class::isInstance);
        allMajorVersions.stream().map(CanonicalStudioVersion.class::cast)
                .forEach(version->assertThat(VerifiedStudioVersion.verify(version,version.semanticDigest())).isSameAs(version));
        var left=new LinkedHashMap<String,String>();left.put("z","2");left.put("a","1");var right=new LinkedHashMap<String,String>();right.put("a","1");right.put("z","2");assertThat(CanonicalJson.object(left)).isEqualTo(CanonicalJson.object(right));
    }

    private static void prove(byte[]a,ContentDigest ad,byte[]b,ContentDigest bd,ContentDigest changed){assertThat(a).isEqualTo(b);assertThat(ad).isEqualTo(bd).isNotEqualTo(changed);}
    private static ScreenplayVersion screenplay(String action,ScreenplayElementId heading){return ScreenplayVersion.create(new ScreenplayId("sp"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(new ScreenplayElement.SceneHeading(heading,ScreenplayElement.InteriorExterior.INTERIOR,"Room",ScreenplayElement.TimeOfDay.DAY),new ScreenplayElement.Action(new ScreenplayElementId("action"),action)));}
    private static SceneVersion scene(ScreenplayVersion sp,ScreenplayElementId heading,String purpose){return SceneVersion.create(new SceneId("scene"),new SceneVersionId("v1"),SCOPE,null,sp,heading,"Synopsis",purpose);}
    private static ShotVersion shot(String id,SceneVersion scene,String description){return ShotVersion.create(new ShotId(id),new ShotVersionId("v1"),SCOPE,null,scene,description,"Subject","Continuity",MediaTime.ofTicks(1,1),null,null);}
    private static ShotPlanVersion plan(ScreenplayVersion sp,SceneVersion scene,List<ShotVersion>shots){return ShotPlanVersion.create(new ShotPlanId("plan"),new ShotPlanVersionId("v1"),SCOPE,null,sp,scene,shots);}
    private static DirectorIntentVersion intent(DirectorIntentVersion.EmotionalTone tone,List<String>annotations){return DirectorIntentVersion.create(new DirectorIntentId("di"),new DirectorIntentVersionId("v1"),SCOPE,null,DirectorIntentVersion.Emphasis.SUBJECT,tone,DirectorIntentVersion.CameraMovementIntent.STATIC,DirectorIntentVersion.LightingMood.NATURAL,annotations);}
    private static CameraPlanVersion camera(ShotVersion shot,ShotPlanVersion plan,DirectorIntentVersion intent,CameraPlanVersion.Framing framing){var pose=new CameraPose.TransformPose(IDENTITY);return CameraPlanVersion.create(new CameraPlanId("camera"),new CameraPlanVersionId("v1"),SCOPE,null,CameraPlanVersion.Projection.PERSPECTIVE,framing,CameraPlanVersion.TargetIntent.SUBJECT,pose,new CameraOptics.FocalLengthMm(DecimalValue.of("50")),shot,plan,intent,List.of(new CameraKeyframe(MediaTime.ZERO,pose)));}
}
