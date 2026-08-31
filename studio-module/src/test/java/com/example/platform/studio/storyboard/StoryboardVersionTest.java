package com.example.platform.studio.storyboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.scene.SceneVersion;
import com.example.platform.studio.scope.*;
import com.example.platform.studio.screenplay.*;
import com.example.platform.studio.shot.ShotVersion;
import com.example.platform.studio.shotplan.ShotPlanVersion;
import com.example.platform.studio.diff.StudioSemanticDiff;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StoryboardVersionTest {
    private static final StudioScope SCOPE=new StudioScope(new TenantId("tenant-a"),new ProjectId("project-a"));
    private static final ContentDigest DIGEST=ContentDigest.sha256("a".repeat(64));

    @Test void preservesStablePanelOrderAndSealedImageIdentity(){
        var c=context();var one=new StoryboardPanel(new StoryboardPanelId("panel-a"),c.shot.pin(),"Opening",null,new PanelImage.Planned());
        var two=new StoryboardPanel(new StoryboardPanelId("panel-b"),c.shot.pin(),"Reaction",null,new PanelImage.Materialized(new ArtifactId("artifact-a"),DIGEST));
        var version=StoryboardVersion.create(new StoryboardId("board-a"),new StoryboardVersionId("board-v1"),SCOPE,null,c.plan,List.of(one,two));
        assertThat(version.panels()).containsExactly(one,two);assertThat(((PanelImage.Materialized)two.image()).contentDigest()).isEqualTo(DIGEST);
        assertThat(new String(version.canonicalBytes(),StandardCharsets.UTF_8))
                .contains("\"contentDigest\":{\"algorithm\":\"SHA_256\",\"value\":\""+"a".repeat(64)+"\"}");
        var reordered=StoryboardVersion.create(new StoryboardId("board-a"),new StoryboardVersionId("board-v2"),SCOPE,
                new StoryboardVersionId("board-v1"),c.plan,List.of(two,one));
        assertThat(StudioSemanticDiff.between(version,reordered).changes())
                .extracting(change->change.path().canonical()).contains("panels/order");
    }

    @Test void rejectsDuplicatePanelsAndIncompleteMaterialization(){
        var c=context();var panel=new StoryboardPanel(new StoryboardPanelId("panel-a"),c.shot.pin(),"Opening",null,new PanelImage.Planned());
        assertThatThrownBy(()->StoryboardVersion.create(new StoryboardId("board-a"),new StoryboardVersionId("board-v1"),SCOPE,null,c.plan,List.of(panel,panel)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
        assertThatThrownBy(()->new PanelImage.Materialized(new ArtifactId("artifact-a"),null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Context context(){var heading=new ScreenplayElementId("heading-a");var screenplay=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("screenplay-v1"),SCOPE,null,List.of(new ScreenplayElement.SceneHeading(heading,ScreenplayElement.InteriorExterior.INTERIOR,"Room",ScreenplayElement.TimeOfDay.DAY)));var scene=SceneVersion.create(new SceneId("scene-a"),new SceneVersionId("scene-v1"),SCOPE,null,screenplay,heading,"Synopsis","Purpose");var shot=ShotVersion.create(new ShotId("shot-a"),new ShotVersionId("shot-v1"),SCOPE,null,scene,"Beat","Subject","Continuity",MediaTime.ofTicks(1,1),null,null);var plan=ShotPlanVersion.create(new ShotPlanId("plan-a"),new ShotPlanVersionId("plan-v1"),SCOPE,null,screenplay,scene,List.of(shot));return new Context(shot,plan);}
    private record Context(ShotVersion shot,ShotPlanVersion plan){}
}
