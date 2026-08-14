package com.example.platform.audio.domain.mix;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioV2CanonicalTypesTest {

    // ---- A. Gain ----
    @Test
    void gainDefaultIsOne() {
        assertEquals(1.0, AudioGain.defaultGain().linear());
    }

    @Test
    void gainZeroAllowed() {
        assertEquals(0.0, AudioGain.of(0.0).linear());
    }

    @Test
    void gainNegativeRejected() {
        assertThrows(IllegalArgumentException.class, () -> AudioGain.of(-0.1));
    }

    @Test
    void gainNaNRejected() {
        assertThrows(IllegalArgumentException.class, () -> AudioGain.of(Double.NaN));
    }

    @Test
    void gainInfinityRejected() {
        assertThrows(IllegalArgumentException.class, () -> AudioGain.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void gainDeterministicSerialization() {
        assertEquals("1.0", AudioGain.of(1.0).toString());
        assertEquals("0.5", AudioGain.of(0.5).toString());
    }

    // ---- B. Mute ----
    @Test
    void muteDefaultFalse() {
        assertFalse(AudioMute.defaultMute().muted());
    }

    @Test
    void muteIndependentFromGain() {
        // gain=0 mute=false  !=  gain=1 mute=true  (semantic distinction, A4/A5)
        AudioRoute zeroGain = AudioRoute.of(AudioMixInput.of("t", "c"), AudioGain.of(0.0));
        AudioRoute muted = AudioRoute.of(AudioMixInput.of("t", "c"), AudioGain.of(1.0)).withMute(AudioMute.mutedState());
        assertNotEquals(zeroGain, muted);
        assertNotEquals(zeroGain.toString(), muted.toString());
    }

    // ---- C. Stereo balance ----
    @Test
    void balanceBounds() {
        assertEquals(-1.0, StereoBalance.fullLeft().value());
        assertEquals(0.0, StereoBalance.neutral().value());
        assertEquals(1.0, StereoBalance.fullRight().value());
    }

    @Test
    void balanceOutsideRejected() {
        assertThrows(IllegalArgumentException.class, () -> StereoBalance.of(-1.01));
        assertThrows(IllegalArgumentException.class, () -> StereoBalance.of(1.01));
    }

    @Test
    void balanceNaNInfinityRejected() {
        assertThrows(IllegalArgumentException.class, () -> StereoBalance.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> StereoBalance.of(Double.NEGATIVE_INFINITY));
    }

    // ---- D. Routing / mix invariants ----
    @Test
    void validMixChain() {
        AudioMix mix = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("audio-1", "clip-1"), AudioGain.of(0.8)),
                AudioRoute.of(AudioMixInput.of("audio-1", "clip-2"))));
        assertEquals(2, mix.routes().size());
        assertEquals("master", mix.masterBus().busId());
    }

    @Test
    void duplicateRouteInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c1")),
                AudioRoute.of(AudioMixInput.of("t", "c1")))));
    }

    @Test
    void deterministicRouteRepresentation() {
        AudioMix a = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c1"), AudioGain.of(0.5))));
        AudioMix b = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c1"), AudioGain.of(0.5))));
        assertEquals(a.toString(), b.toString());
        assertEquals(a, b);
    }

    @Test
    void mixChangeDetectable() {
        AudioMix base = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c1"), AudioGain.of(1.0))));
        AudioMix gainChanged = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c1"), AudioGain.of(0.8))));
        AudioMix muted = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c1")).withMute(AudioMute.mutedState())));
        AudioMix routed = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c2"))));
        assertNotEquals(base.toString(), gainChanged.toString());
        assertNotEquals(base.toString(), muted.toString());
        assertNotEquals(base.toString(), routed.toString());
    }

    // ---- DSP ----
    @Test
    void dspNodeBoundedAndTyped() {
        AudioDspNode limiter = AudioDspNode.of(AudioDspNode.DspNodeType.LIMITER,
                AudioDspParam.of("threshold", -12.0));
        assertEquals(AudioDspNode.DspNodeType.LIMITER, limiter.type());
        assertFalse(limiter.toString().contains("ffmpeg"));
        assertFalse(limiter.toString().contains("volume="));
    }

    @Test
    void dspParamFiniteOnly() {
        assertThrows(IllegalArgumentException.class, () -> AudioDspParam.of("ratio", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> AudioDspParam.of("ratio", Double.POSITIVE_INFINITY));
    }

    @Test
    void duplicateDspParamRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AudioDspNode.of(AudioDspNode.DspNodeType.EQ,
                        AudioDspParam.of("freq", 1000.0), AudioDspParam.of("freq", 2000.0)));
    }
}
