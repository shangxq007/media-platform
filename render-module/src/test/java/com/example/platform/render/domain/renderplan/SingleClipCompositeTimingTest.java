package com.example.platform.render.domain.renderplan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SingleClipCompositeTimingTest {
    @Test
    void singleClipHasTimedCompositeCoverage() {
        var input = TestPlans.canonicalInput();
        var result = new DefaultRenderMaterializer().materialize(input);
        var composite = result.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Composite)
                .findFirst().orElseThrow();
        assertThat(result.edges()).anySatisfy(edge -> {
            if (edge.consumerId().equals(composite.id())) {
                assertThat(edge.dependency()).isInstanceOf(RenderDependency.TimedCompositeInput.class);
            }
        });
    }
}
