package com.example.platform.config.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Phase 5 acceptance P5-A: the coordinator is injectable in a COMBINED context. */
class RevisionRenderPlanningAndBindingServiceWiringTest {

    @Test
    void coordinatorIsInjectableUnderCombinedRole() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        RevisionRenderPlanningApplicationService.class,
                        RevisionRenderPlanningAndBindingService.class,
                        RevisionRenderPlanningAndBindingConfiguration.class)
                .withBean(RenderJobService.class, () -> mock(RenderJobService.class))
                .withBean(com.example.platform.render.api.GraphRenderLifecycle.class,
                        () -> mock(com.example.platform.render.api.GraphRenderLifecycle.class))
                .withBean(TimelineRevisionSaveService.class, () -> mock(TimelineRevisionSaveService.class))
                .withBean(TimelineRevisionQueryService.class, () -> mock(TimelineRevisionQueryService.class))
                .withBean(ArtifactQueryService.class, () -> mock(ArtifactQueryService.class))
                .withBean(ProviderInteroperabilityAuthority.class, () -> mock(ProviderInteroperabilityAuthority.class))
                .withBean(EffectSemanticSnapshotStore.class, () -> mock(EffectSemanticSnapshotStore.class))
                .withBean(ProviderPluginCatalog.class, () -> mock(ProviderPluginCatalog.class))
                .withPropertyValues("platform.runtime.role=COMBINED")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RevisionRenderPlanningAndBindingService.class);
                });
    }
}
