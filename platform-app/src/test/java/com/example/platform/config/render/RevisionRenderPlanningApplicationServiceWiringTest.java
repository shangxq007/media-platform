package com.example.platform.config.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Phase 3 acceptance P3-A: the Planning Application Service is injectable in a COMBINED context. */
class RevisionRenderPlanningApplicationServiceWiringTest {

    @Test
    void serviceIsInjectableUnderCombinedRole() {
        new ApplicationContextRunner()
                .withUserConfiguration(RevisionRenderPlanningApplicationService.class)
                .withBean(RenderJobService.class, () -> mock(RenderJobService.class))
                .withBean(TimelineRevisionSaveService.class, () -> mock(TimelineRevisionSaveService.class))
                .withBean(TimelineRevisionQueryService.class, () -> mock(TimelineRevisionQueryService.class))
                .withBean(ArtifactQueryService.class, () -> mock(ArtifactQueryService.class))
                .withBean(ProviderInteroperabilityAuthority.class, () -> mock(ProviderInteroperabilityAuthority.class))
                .withPropertyValues("platform.runtime.role=COMBINED")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RevisionRenderPlanningApplicationService.class);
                });
    }
}
