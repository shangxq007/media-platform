package com.example.platform.render.domain.timeline.semantics.effect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VideoEffectSemanticsTest {

    @Test
    @DisplayName("Transform identity produces neutral values")
    void transformIdentity() {
        VideoEffectSemantics.TransformEffect t = VideoEffectSemantics.TransformEffect.identity();
        assertEquals(0, t.translateX());
        assertEquals(0, t.translateY());
        assertEquals(1, t.scaleX());
        assertEquals(1, t.scaleY());
        assertEquals(0, t.rotationDegrees());
    }

    @Test
    @DisplayName("Transform rejects zero scale")
    void transformZeroScale() {
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.TransformEffect(0, 0, 0, 1, 0, 0.5, 0.5));
    }

    @Test
    @DisplayName("Crop rejects invalid ratios")
    void cropInvalidRatios() {
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.CropEffect(-0.1, 0.5, 0, 1)); // left negative
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.CropEffect(0.3, 0.2, 0, 1)); // left >= right
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.CropEffect(0, 0.8, 0.6, 0.5)); // top >= bottom
    }

    @Test
    @DisplayName("Opacity rejects out-of-range values")
    void opacityRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.OpacityEffect(-0.1));
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.OpacityEffect(1.1));
    }

    @Test
    @DisplayName("BlendMode canonical name lookup")
    void blendModeLookup() {
        assertEquals(VideoEffectSemantics.BlendMode.NORMAL,
            VideoEffectSemantics.BlendMode.fromCanonical("normal"));
        assertEquals(VideoEffectSemantics.BlendMode.MULTIPLY,
            VideoEffectSemantics.BlendMode.fromCanonical("multiply"));
    }

    @Test
    @DisplayName("BlendMode rejects unknown string")
    void blendModeUnknown() {
        assertThrows(IllegalArgumentException.class, () ->
            VideoEffectSemantics.BlendMode.fromCanonical("unknown-mode"));
    }

    @Test
    @DisplayName("ColorAdjustment rejects out-of-range values")
    void colorAdjustmentRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.ColorAdjustmentEffect(-2, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.ColorAdjustmentEffect(0, 3, 1, 0));
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.ColorAdjustmentEffect(0, 1, 3, 0));
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.ColorAdjustmentEffect(0, 1, 1, 200));
    }

    @Test
    @DisplayName("GaussianBlur rejects negative radius")
    void blurNegativeRadius() {
        assertThrows(IllegalArgumentException.class, () ->
            new VideoEffectSemantics.GaussianBlurEffect(-1));
    }

    @Test
    @DisplayName("VideoFade in/out")
    void videoFade() {
        VideoEffectSemantics.VideoFadeEffect fadeIn = VideoEffectSemantics.VideoFadeEffect.fadeIn();
        assertEquals(0, fadeIn.startOpacity());
        assertEquals(1, fadeIn.endOpacity());

        VideoEffectSemantics.VideoFadeEffect fadeOut = VideoEffectSemantics.VideoFadeEffect.fadeOut();
        assertEquals(1, fadeOut.startOpacity());
        assertEquals(0, fadeOut.endOpacity());
    }
}

class AudioEffectSemanticsTest {

    @Test
    @DisplayName("Gain rejects out-of-range dB values")
    void gainRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.GainEffect(-100));
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.GainEffect(30));
    }

    @Test
    @DisplayName("Gain dB to linear conversion")
    void gainToLinear() {
        assertEquals(1.0, new AudioEffectSemantics.GainEffect(0).toLinear(), 0.0001);
        assertEquals(2.0, new AudioEffectSemantics.GainEffect(6.0206).toLinear(), 0.01);
    }

    @Test
    @DisplayName("Pan rejects out-of-range values")
    void panRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.PanEffect(-1.5));
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.PanEffect(1.5));
    }

    @Test
    @DisplayName("EqBand rejects invalid frequency and Q")
    void eqBandRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.EqBand(10, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.EqBand(1000, 0, 0));
    }

    @Test
    @DisplayName("Compressor rejects invalid ratio and attack")
    void compressorRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.CompressorEffect(-24, 0.5, 5, 50, 3, 6));
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.CompressorEffect(-24, 4, 600, 50, 3, 6));
    }

    @Test
    @DisplayName("Limiter rejects positive threshold")
    void limiterRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.LimiterEffect(1, 50));
    }

    @Test
    @DisplayName("SimpleEqualizer max 10 bands")
    void eqMaxBands() {
        List<AudioEffectSemantics.EqBand> bands = java.util.stream.IntStream.range(0, 11)
            .mapToObj(i -> new AudioEffectSemantics.EqBand(100 + i * 100, 0, 1))
            .toList();
        assertThrows(IllegalArgumentException.class, () ->
            new AudioEffectSemantics.SimpleEqualizerEffect(bands));
    }

    @Test
    @DisplayName("Audio fade in/out")
    void audioFade() {
        AudioEffectSemantics.AudioFadeEffect fadeIn = AudioEffectSemantics.AudioFadeEffect.fadeIn();
        assertEquals(-96, fadeIn.startGainDb());
        assertEquals(0, fadeIn.endGainDb());
    }
}
