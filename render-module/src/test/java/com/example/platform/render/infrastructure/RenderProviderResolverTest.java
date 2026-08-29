package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.render.domain.interchange.TimelineExtensions;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.planning.FinalComposerHint;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderProviderResolverTest {

    private final RenderProviderRegistry registry = mock(RenderProviderRegistry.class);
    private final RenderProviderSelectionPolicy selectionPolicy =
            mock(RenderProviderSelectionPolicy.class);
    private final RenderProviderResolver resolver =
            new RenderProviderResolver(registry, selectionPolicy);

    @Test
    void unboundTypedPluginStrategyFailsClosedBeforeRegistryLookup() {
        TimelineSpec timeline = TimelineSpec.create(
                "tl-unbound", "Unbound", TimelineOutputSpec.mp4_1080p30());

        assertEquals(FinalComposerHint.TYPED_PROVIDER_PLUGIN,
                resolver.selectComposer(timeline, TimelineExtensions.defaults()));
        assertNull(resolver.backendKey(FinalComposerHint.TYPED_PROVIDER_PLUGIN));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(
                        timeline, TimelineExtensions.defaults(), "default_1080p", List.of()));
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", error.getMessage());
        verifyNoInteractions(registry, selectionPolicy);
    }
}
