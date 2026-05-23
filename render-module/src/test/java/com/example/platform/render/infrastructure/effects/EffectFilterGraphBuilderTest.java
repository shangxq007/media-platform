package com.example.platform.render.infrastructure.effects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.infrastructure.EffectMappingService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectFilterGraphBuilderTest {

    @Test
    void shouldBuildBlurFilterChain() {
        EffectFilterGraphBuilder builder = new EffectFilterGraphBuilder(new EffectMappingService());
        var chain = builder.buildVideoFilterChain(List.of(
                TimelineClipEffect.ofKey("video.blur", Map.of("radius", 2.5))));
        assertThat(chain).isPresent();
        assertThat(chain.get()).contains("boxblur");
    }
}
