package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import java.util.List;
import java.util.stream.Stream;

/**
 * ROADMAP20 correction F3 test helper: all CapabilityIds produced by the render
 * capability vocabulary, for structural boundary assertions.
 */
final class RenderCapabilityVocabularyIds {

    private RenderCapabilityVocabularyIds() {
    }

    static List<CapabilityId> all() {
        return Stream.of(
                        "video.decode",
                        "video.effect.transform",
                        "video.effect.crop",
                        "video.effect.opacity",
                        "video.effect.blend-mode",
                        "video.effect.color-adjustment",
                        "video.effect.gaussian-blur",
                        "video.effect.fade",
                        "audio.effect.gain",
                        "audio.effect.pan",
                        "audio.effect.equalizer",
                        "audio.effect.compressor",
                        "audio.effect.limiter",
                        "audio.process",
                        "audio.mix",
                        "subtitle.rasterize",
                        "render.composite",
                        "render.output")
                .map(CapabilityId::of)
                .toList();
    }
}
