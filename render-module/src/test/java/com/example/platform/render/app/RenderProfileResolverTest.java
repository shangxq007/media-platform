package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import com.example.platform.render.infrastructure.effects.EffectProviderRouter;
import com.example.platform.render.infrastructure.natron.NatronRenderProviderProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderProfileResolverTest {

    private RenderProfileResolver resolver;
    private NatronRenderProviderProperties natronProps;

    @BeforeEach
    void setUp() {
        EffectProviderRouter effectRouter = new EffectProviderRouter(
                new com.example.platform.render.infrastructure.EffectMappingService());
        RenderProviderRegistry registry = mock(RenderProviderRegistry.class);
        when(registry.getProvider("natron")).thenReturn(Optional.of(mock(RenderProvider.class)));

        natronProps = new NatronRenderProviderProperties();
        natronProps.setEnabled(true);
        natronProps.setAutoSelectProfile(true);

        resolver = new RenderProfileResolver(
                effectRouter,
                new TimelineScriptParser(),
                registry,
                natronProps);
    }

    @Test
    void upgradesDefaultProfileWhenNatronEffectPresent() {
        String script = """
                {"tracks":[{"type":"VIDEO","clips":[{
                  "media_reference":"file:///tmp/a.mp4",
                  "effects":[{"effectKey":"video.natron_vignette"}]
                }]}]}
                """;
        String resolved = resolver.resolve("default_1080p",
                List.of("video.natron_vignette"), script);
        assertEquals(RenderProfileResolver.NATRON_POC_1080P, resolved);
    }

    @Test
    void keepsExplicitNatronProfile() {
        assertEquals("natron_poc_720p",
                resolver.resolve("natron_poc_720p", List.of("video.natron_vignette"), null));
    }

    @Test
    void noChangeWithoutNatronEffect() {
        assertEquals("default_1080p",
                resolver.resolve("default_1080p", List.of("video.blur"), null));
    }
}
