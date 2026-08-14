package com.example.platform.render.infrastructure.ffmpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.audio.domain.mix.StereoBalance;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AUDIO_V2 (A15/A7): unit tests for the bounded canonical → FFmpeg translation in
 * {@link AudioMixFfmpegAdapter}. One-way boundary only: these tests assert FFmpeg
 * fragments and provider-neutrality — they never parse FFmpeg back into the canonical
 * model and never touch audio-module types.
 */
class AudioMixFfmpegAdapterTest {

    private final AudioMixFfmpegAdapter adapter = new AudioMixFfmpegAdapter();

    // ---- gain -> volume= ----

    @Test
    void gainOneTranslatesToVolumeOne() {
        AudioMix mix = mixOf(route("t1", "c1", AudioGain.of(1.0), StereoBalance.neutral()));
        List<String> fragments = adapter.buildAudioFilterFragments(mix);
        assertEquals(1, fragments.size());
        assertEquals("volume=1.0", volumeSegment(fragments.get(0)));
    }

    @Test
    void gainZeroPointEightTranslatesToVolumeZeroPointEight() {
        AudioMix mix = mixOf(route("t1", "c1", AudioGain.of(0.8), StereoBalance.neutral()));
        List<String> fragments = adapter.buildAudioFilterFragments(mix);
        assertEquals("volume=0.8", volumeSegment(fragments.get(0)));
    }

    // ---- mute -> volume=0 ----

    @Test
    void muteCompilesToVolumeZero() {
        AudioMix mix = mixOf(route("t1", "c1", AudioGain.of(1.0), StereoBalance.neutral())
                .withMute(AudioMute.mutedState()));
        List<String> fragments = adapter.buildAudioFilterFragments(mix);
        assertEquals(1, fragments.size());
        // mute short-circuits the whole route fragment: no pan, exact "volume=0"
        assertEquals("volume=0", fragments.get(0));
    }

    @Test
    void muteStaysDistinctFromZeroGain() {
        // A4/A5: canonical mute is not gain=0; the FFmpeg layer preserves the distinction.
        AudioMix muted = mixOf(route("t1", "c1", AudioGain.of(1.0), StereoBalance.neutral())
                .withMute(AudioMute.mutedState()));
        AudioMix zeroGain = mixOf(route("t1", "c1", AudioGain.of(0.0), StereoBalance.neutral()));
        assertEquals("volume=0", adapter.buildAudioFilterFragments(muted).get(0));
        assertEquals("volume=0.0", volumeSegment(adapter.buildAudioFilterFragments(zeroGain).get(0)));
    }

    // ---- stereo balance -> pan= ----

    @Test
    void balanceFullLeftMapsToPanFullLeft() {
        AudioMix mix = mixOf(route("t1", "c1", AudioGain.of(1.0), StereoBalance.fullLeft()));
        assertEquals("pan=stereo|c0=1.0*c0+0.0*c1|c1=1.0*c0+0.0*c1",
                panSegment(adapter.buildAudioFilterFragments(mix).get(0)));
    }

    @Test
    void balanceNeutralMapsToPanCenter() {
        AudioMix mix = mixOf(route("t1", "c1", AudioGain.of(1.0), StereoBalance.neutral()));
        assertEquals("pan=stereo|c0=0.5*c0+0.5*c1|c1=0.5*c0+0.5*c1",
                panSegment(adapter.buildAudioFilterFragments(mix).get(0)));
    }

    @Test
    void balanceFullRightMapsToPanFullRight() {
        AudioMix mix = mixOf(route("t1", "c1", AudioGain.of(1.0), StereoBalance.fullRight()));
        assertEquals("pan=stereo|c0=0.0*c0+1.0*c1|c1=0.0*c0+1.0*c1",
                panSegment(adapter.buildAudioFilterFragments(mix).get(0)));
    }

    // ---- amix ----

    @Test
    void amixAppendedWithInputCountForTwoRoutes() {
        AudioMix mix = mixOf(
                route("t1", "c1", AudioGain.of(1.0), StereoBalance.neutral()),
                route("t1", "c2", AudioGain.of(0.5), StereoBalance.neutral()));
        List<String> fragments = adapter.buildAudioFilterFragments(mix);
        assertEquals(3, fragments.size());
        assertEquals("amix=inputs=2:normalize=0", fragments.get(2));
    }

    @Test
    void amixInputCountMatchesRouteCount() {
        AudioMix mix = mixOf(
                route("t1", "c1", AudioGain.of(1.0), StereoBalance.neutral()),
                route("t1", "c2", AudioGain.of(0.5), StereoBalance.neutral()),
                route("t2", "c3", AudioGain.of(0.8), StereoBalance.neutral()));
        List<String> fragments = adapter.buildAudioFilterFragments(mix);
        assertEquals(4, fragments.size());
        assertEquals("amix=inputs=3:normalize=0", fragments.get(3));
    }

    @Test
    void singleRouteHasNoAmix() {
        AudioMix mix = mixOf(route("t1", "c1", AudioGain.of(1.0), StereoBalance.neutral()));
        List<String> fragments = adapter.buildAudioFilterFragments(mix);
        assertEquals(1, fragments.size());
        assertFalse(fragments.get(0).startsWith("amix="));
    }

    @Test
    void emptyMixYieldsNoFragments() {
        assertEquals(0, adapter.buildAudioFilterFragments(AudioMix.EMPTY).size());
    }

    // ---- provider-neutrality (canonical -> FFmpeg only) ----

    @Test
    void adapterOutputIsProviderNeutral() {
        // ids deliberately non-colliding with FFmpeg channel tokens (c0/c1)
        AudioMix mix = mixOf(
                route("track-alpha", "clip-beta", AudioGain.of(0.8), StereoBalance.of(0.3)),
                route("track-gamma", "clip-delta", AudioGain.of(1.0), StereoBalance.fullLeft())
                        .withMute(AudioMute.mutedState()));
        String canonicalBefore = mix.toString();

        List<String> fragments = adapter.buildAudioFilterFragments(mix);

        // Output is pure FFmpeg filter fragments — only volume=, pan=, amix= — and
        // never leaks canonical serialization (no "route(", "AudioMix{", ids, etc.)
        assertEquals(3, fragments.size());
        for (String fragment : fragments) {
            assertTrue(fragment.startsWith("volume=")
                            || fragment.startsWith("pan=stereo|")
                            || fragment.startsWith("amix=inputs="),
                    "unexpected fragment: " + fragment);
            assertFalse(fragment.contains("route("));
            assertFalse(fragment.contains("AudioMix{"));
            assertFalse(fragment.contains("track-alpha"));
            assertFalse(fragment.contains("clip-beta"));
            assertFalse(fragment.contains("track-gamma"));
            assertFalse(fragment.contains("clip-delta"));
        }
        assertTrue(fragments.contains("volume=0"));
        assertTrue(fragments.contains("amix=inputs=2:normalize=0"));

        // The adapter never feeds back: canonical mix is unchanged after translation.
        assertEquals(canonicalBefore, mix.toString());
    }

    // ---- helpers ----

    private static AudioRoute route(String trackId, String clipId, AudioGain gain,
            StereoBalance balance) {
        return new AudioRoute(AudioMixInput.of(trackId, clipId), gain, AudioMute.defaultMute(),
                balance, List.of());
    }

    private static AudioMix mixOf(AudioRoute... routes) {
        return AudioMix.of(AudioMasterBus.master(), List.of(routes));
    }

    private static String volumeSegment(String fragment) {
        return fragment.split(",", 2)[0];
    }

    private static String panSegment(String fragment) {
        int comma = fragment.indexOf(',');
        assertTrue(comma >= 0, "expected a pan fragment in: " + fragment);
        return fragment.substring(comma + 1);
    }
}
