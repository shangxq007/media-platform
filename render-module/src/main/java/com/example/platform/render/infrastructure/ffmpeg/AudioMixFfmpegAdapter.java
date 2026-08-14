package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioRoute;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AUDIO_V2 (A15/A7): bounded provider-translation adapter that converts the canonical
 * Audio V2 {@link AudioMix} into FFmpeg audio filter graph fragments ({@code volume=},
 * {@code pan=}, {@code amix}).
 *
 * <p>This is a strict one-way boundary: canonical → FFmpeg. The adapter never parses
 * FFmpeg back into the canonical model, never modifies audio-module types, and never
 * embeds FFmpeg syntax into the canonical domain. FFmpeg stays EXECUTION_ONLY — the
 * canonical {@link AudioMix} remains the single mix authority and the adapter is a
 * pure function: {@code List<String> buildAudioFilterFragments(AudioMix)}.
 *
 * <p>Translation rules (bounded milestone):
 * <ul>
 *   <li>Each route yields one fragment string. A non-muted route always starts with
 *       {@code volume=<linear gain>} (including {@code 1.0} — no silent no-op removal,
 *       deterministic output) and always appends a {@code pan=stereo|...} fragment
 *       derived from the route's
 *       {@link com.example.platform.audio.domain.mix.StereoBalance}: coefficients
 *       {@code w0 = (1-b)/2}, {@code w1 = (1+b)/2}, so {@code -1} → {@code 1.0/0.0}
 *       (full left), {@code 0} → {@code 0.5/0.5} (center), {@code 1} → {@code 0.0/1.0}
 *       (full right).</li>
 *   <li>A muted route short-circuits to exactly {@code volume=0} (mute compiles to zero
 *       gain at execution time; the canonical mute semantic is preserved and stays
 *       distinct from a canonical {@code gain = 0}).</li>
 *   <li>{@link com.example.platform.audio.domain.mix.AudioDspNode} chains
 *       (EQ/COMPRESSOR/LIMITER) are NOT translated in this milestone (bounded catalog,
 *       deferred) — the canonical chain is preserved untouched for future providers.</li>
 *   <li>When the mix has ≥ 2 routes, a final {@code amix=inputs=N:normalize=0} fragment
 *       is appended with {@code N} = route count.</li>
 * </ul>
 */
public class AudioMixFfmpegAdapter {

    private static final Logger log = LoggerFactory.getLogger(AudioMixFfmpegAdapter.class);

    /** Mute compiles to zero gain at execution time (A5): exact {@code volume=0}. */
    private static final String MUTE_FRAGMENT = "volume=0";

    /**
     * Builds one FFmpeg audio filter fragment string per {@link AudioRoute} of the mix,
     * plus a final {@code amix} fragment when there are ≥ 2 routes.
     *
     * @param mix canonical Audio V2 mix (non-null)
     * @return ordered filter fragments; empty list for an empty mix
     */
    public List<String> buildAudioFilterFragments(AudioMix mix) {
        Objects.requireNonNull(mix, "mix");
        List<String> fragments = new ArrayList<>();
        for (AudioRoute route : mix.routes()) {
            fragments.add(buildRouteFragment(route));
        }
        if (fragments.size() >= 2) {
            fragments.add("amix=inputs=" + fragments.size() + ":normalize=0");
        }
        log.debug("Built {} audio filter fragment(s) from AudioMix", fragments.size());
        return fragments;
    }

    private String buildRouteFragment(AudioRoute route) {
        if (route.mute().muted()) {
            return MUTE_FRAGMENT;
        }
        double balance = route.balance().value();
        double leftWeight = (1.0 - balance) / 2.0;
        double rightWeight = (1.0 + balance) / 2.0;
        return "volume=" + route.gain().linear()
                + ",pan=stereo|c0=" + leftWeight + "*c0+" + rightWeight + "*c1"
                + "|c1=" + leftWeight + "*c0+" + rightWeight + "*c1";
    }
}
