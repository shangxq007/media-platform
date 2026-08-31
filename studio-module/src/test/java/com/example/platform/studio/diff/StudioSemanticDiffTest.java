package com.example.platform.studio.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.studio.identity.*;
import com.example.platform.studio.scope.*;
import com.example.platform.studio.screenplay.*;
import com.example.platform.studio.scene.SceneVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudioSemanticDiffTest {
    private static final StudioScope SCOPE=new StudioScope(new TenantId("tenant-a"),new ProjectId("project-a"));

    @Test void addressesAddsAndReordersByStableElementIdentity(){var heading=new ScreenplayElement.SceneHeading(new ScreenplayElementId("heading"),ScreenplayElement.InteriorExterior.INTERIOR,"Room",ScreenplayElement.TimeOfDay.DAY);var action=new ScreenplayElement.Action(new ScreenplayElementId("action"),"Move");var before=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(heading));var after=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("v2"),SCOPE,new ScreenplayVersionId("v1"),List.of(action,heading));var diff=StudioSemanticDiff.between(before,after);assertThat(diff.changes()).extracting(change->change.path().canonical()).contains("elements/action","elements/order","parentVersionId");assertThat(diff.changes()).isSortedAccordingTo(SemanticChange.CANONICAL_ORDER);}

    @Test void rejectsCrossAggregateComparisonAndDeclaresAllMajorTypes(){var one=ScreenplayVersion.create(new ScreenplayId("one"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(new ScreenplayElement.Action(new ScreenplayElementId("a"),"A")));var two=ScreenplayVersion.create(new ScreenplayId("two"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(new ScreenplayElement.Action(new ScreenplayElementId("a"),"A")));assertThatThrownBy(()->StudioSemanticDiff.between(one,two)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same aggregate");assertThat(StudioSemanticDiff.supportedAggregateTypeCount()).isEqualTo(8);}

    @Test void rejectsDifferentAggregateFamilies(){var headingId=new ScreenplayElementId("heading");var screenplay=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(new ScreenplayElement.SceneHeading(headingId,ScreenplayElement.InteriorExterior.INTERIOR,"Room",ScreenplayElement.TimeOfDay.DAY)));var scene=SceneVersion.create(new SceneId("scene-a"),new SceneVersionId("v1"),SCOPE,null,screenplay,headingId,"Synopsis","Purpose");assertThatThrownBy(()->StudioSemanticDiff.between((Object)screenplay,(Object)scene)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aggregate type");}
}
