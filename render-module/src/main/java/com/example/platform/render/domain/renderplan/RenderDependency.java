package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioMixInput;

/**
 * Typed render dependency variants (C5). Sealed — no universal untyped bag.
 * Each edge carries exactly one variant.
 */
public sealed interface RenderDependency permits
        RenderDependency.DecodedFrames,
        RenderDependency.EffectInput,
        RenderDependency.AudioInput,
        RenderDependency.SubtitleRaster,
        RenderDependency.CompositeInput {

    /**
     * Canonical variant key for deterministic edge ordering.
     */
    String variantKey();

    /** Consumer depends on a DECODE node's decoded media output. */
    record DecodedFrames() implements RenderDependency {
        @Override
        public String variantKey() {
            return "DECODED_FRAMES";
        }
    }

    /** Consumer depends on an EFFECT node's processed output. */
    record EffectInput() implements RenderDependency {
        @Override
        public String variantKey() {
            return "EFFECT_INPUT";
        }
    }

    /** Audio consumer depends on a clip audio source/process/mix node. */
    record AudioInput(AudioMixInput mixInput) implements RenderDependency {
        public AudioInput {
            if (mixInput == null) {
                throw new IllegalArgumentException("AudioInput mixInput required");
            }
        }

        @Override
        public String variantKey() {
            return "AUDIO_INPUT:" + mixInput;
        }
    }

    /**
     * TIMED_TEXT consumer depends on a future raster-input node (reserved;
     * unused in this slice).
     */
    record SubtitleRaster() implements RenderDependency {
        @Override
        public String variantKey() {
            return "SUBTITLE_RASTER";
        }
    }

    /**
     * ROADMAP20 correction F2: COMPOSITE/OUTPUT consumer depends on the typed
     * visual composition input (video producer and/or TIMED_TEXT raster output).
     */
    record CompositeInput() implements RenderDependency {
        @Override
        public String variantKey() {
            return "COMPOSITE_INPUT";
        }
    }
}
