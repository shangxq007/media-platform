package com.example.platform.bmf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BmfVideoEffectTransitionGraphTest {
    @Test
    void graphExecutesEffectsFadesAndCrossDissolve() {
        String graph = BmfCpuRuntimeAdapter.MULTITRACK_GRAPH_PROGRAM;
        assertThat(graph).contains("'eq'", "'hue'", "'fade'", "dissolve_duration", "colorchannelmixer");
    }

    @Test
    void placementTokenCarriesEffectAndTransitionSemantics() {
        String graph = BmfCpuRuntimeAdapter.MULTITRACK_GRAPH_PROGRAM;
        assertThat(graph).contains("fx = placement[16].split(';')");
        assertThat(graph).contains("brightness, contrast, saturation");
        assertThat(graph).contains("effect_enabled and fade_in > 0");
        assertThat(graph).contains("dissolve_duration > 0");
        assertThat(graph.indexOf("PTS-STARTPTS+' + fx[7]")).isLessThan(graph.indexOf("px, py = transformed(clip"));
        assertThat(graph.indexOf("px, py = transformed(clip")).isLessThan(graph.lastIndexOf("PTS-STARTPTS+' + start"));
    }
}
