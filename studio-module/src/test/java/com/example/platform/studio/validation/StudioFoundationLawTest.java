package com.example.platform.studio.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.studio.digest.VerifiedStudioVersion;
import com.example.platform.studio.identity.*;
import com.example.platform.studio.scope.*;
import com.example.platform.studio.screenplay.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudioFoundationLawTest {
    private static final StudioScope SCOPE=new StudioScope(new TenantId("tenant-a"),new ProjectId("project-a"));

    @Test void keepsIdentityVersionAndDigestDistinctAndNormalizesUnicode(){var id=new ScreenplayId("screenplay-cafe\u0301");var heading=new ScreenplayElement.SceneHeading(new ScreenplayElementId("heading"),ScreenplayElement.InteriorExterior.INTERIOR,"Cafe\u0301",ScreenplayElement.TimeOfDay.DAY);var v1=ScreenplayVersion.create(id,new ScreenplayVersionId("v1"),SCOPE,null,List.of(heading));var v2=ScreenplayVersion.create(id,new ScreenplayVersionId("v2"),SCOPE,new ScreenplayVersionId("v1"),List.of(heading));assertThat(id.value()).isEqualTo("screenplay-caf\u00e9");assertThat(v1.id()).isEqualTo(v2.id());assertThat(v1.versionId()).isNotEqualTo(v2.versionId());assertThat(v1.semanticDigest().canonicalValue()).isNotEqualTo(v1.id().value()).isNotEqualTo(v1.versionId().value());assertThat(new String(v1.canonicalBytes(),StandardCharsets.UTF_8)).doesNotContain(" ").doesNotContain("null");}

    @Test void verifiesProvidedDigestAndRejectsMismatch(){var version=ScreenplayVersion.create(new ScreenplayId("screenplay-a"),new ScreenplayVersionId("v1"),SCOPE,null,List.of(new ScreenplayElement.Action(new ScreenplayElementId("a"),"Action")));assertThat(VerifiedStudioVersion.verify(version,version.semanticDigest())).isSameAs(version);assertThatThrownBy(()->VerifiedStudioVersion.verify(version,ContentDigest.sha256("0".repeat(64)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("digest mismatch");}

    @Test void exposesExactlyNineteenRequiredTypedStudioIdentities(){assertThat(List.of(ScreenplayId.class,ScreenplayVersionId.class,ScreenplayElementId.class,SceneId.class,SceneVersionId.class,ShotId.class,ShotVersionId.class,ShotPlanId.class,ShotPlanVersionId.class,DirectorIntentId.class,DirectorIntentVersionId.class,CameraPlanId.class,CameraPlanVersionId.class,StoryboardId.class,StoryboardVersionId.class,StoryboardPanelId.class,ShotSceneId.class,ShotSceneVersionId.class,SceneElementId.class)).hasSize(19).doesNotHaveDuplicates();}
}
