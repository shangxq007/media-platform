package com.example.platform.studio.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.studio.identity.SceneId;
import com.example.platform.studio.identity.SceneVersionId;
import com.example.platform.studio.identity.ScreenplayElementId;
import com.example.platform.studio.identity.ScreenplayId;
import com.example.platform.studio.identity.ScreenplayVersionId;
import com.example.platform.studio.scope.ProjectId;
import com.example.platform.studio.scope.StudioScope;
import com.example.platform.studio.scope.TenantId;
import com.example.platform.studio.screenplay.ScreenplayElement;
import com.example.platform.studio.screenplay.ScreenplayVersion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SceneVersionTest {
    private static final StudioScope SCOPE = new StudioScope(new TenantId("tenant-a"), new ProjectId("project-a"));

    @Test
    void pinsExactScreenplayAndHeadingAndNormalizesSemanticText() {
        var headingId = new ScreenplayElementId("heading-a");
        var screenplay = screenplay(headingId);
        var a = SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v1"), SCOPE, null,
                screenplay, headingId, "Cafe\u0301 encounter", "Reveal");
        var b = SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v1"), SCOPE, null,
                screenplay, headingId, "Caf\u00e9 encounter", "Reveal");

        assertThat(a.canonicalBytes()).isEqualTo(b.canonicalBytes());
        assertThat(a.screenplayPin().versionId()).isEqualTo(screenplay.versionId());
        assertThat(new String(a.canonicalBytes(), StandardCharsets.UTF_8)).doesNotContain("null");
    }

    @Test
    void rejectsMissingHeadingAndCrossScopeScreenplay() {
        var screenplay = screenplay(new ScreenplayElementId("heading-a"));
        assertThatThrownBy(() -> SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v1"),
                SCOPE, null, screenplay, new ScreenplayElementId("missing"), "Synopsis", "Purpose"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heading");
        var otherScope = new StudioScope(new TenantId("tenant-a"), new ProjectId("project-b"));
        assertThatThrownBy(() -> SceneVersion.create(new SceneId("scene-a"), new SceneVersionId("scene-v1"),
                otherScope, null, screenplay, new ScreenplayElementId("heading-a"), "Synopsis", "Purpose"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scope");
    }

    private static ScreenplayVersion screenplay(ScreenplayElementId headingId) {
        return ScreenplayVersion.create(new ScreenplayId("screenplay-a"), new ScreenplayVersionId("screenplay-v1"),
                SCOPE, null, List.of(new ScreenplayElement.SceneHeading(headingId,
                        ScreenplayElement.InteriorExterior.INTERIOR, "Cafe", ScreenplayElement.TimeOfDay.DAY)));
    }
}
