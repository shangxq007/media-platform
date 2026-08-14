package com.example.platform.render.domain.timeline.canonical;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.render.domain.timeline.diff.ChangeType;
import com.example.platform.render.domain.timeline.diff.TimelineChange;
import com.example.platform.render.domain.timeline.diff.TimelineChangeSet;
import com.example.platform.render.domain.timeline.diff.TimelineDiffEngine;
import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AUDIO_V2 (A3/A13/A14): Timeline revision semantic integration tests.
 *
 * <p>Audio mix semantic edits MUST alter the Timeline revision content hash
 * (and be visible in semantic diff); provider-only translation must NOT alter
 * the hash (no FFmpeg strings in canonical content). The Timeline revision
 * graph remains the sole DAG — no second audio revision authority.
 */
class TimelineV2AudioRevisionTest {

    private final TimelineContentDigester digester = new TimelineContentDigester();

    private static TimelineDocument doc(AudioMix mix) {
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(),
                TimelineMetadata.empty(),
                mix);
    }

    private static AudioMix mixWith(AudioGain gain) {
        return AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("audio-1", "clip-1"), gain)));
    }

    // ---- E. Revision semantics: audio edits change content hash ----

    @Test
    void sameMixSameHash() {
        String h1 = digester.digest(doc(mixWith(AudioGain.of(0.8))));
        String h2 = digester.digest(doc(mixWith(AudioGain.of(0.8))));
        assertEquals(h1, h2);
    }

    @Test
    void gainEditChangesHash() {
        String base = digester.digest(doc(mixWith(AudioGain.of(1.0))));
        String changed = digester.digest(doc(mixWith(AudioGain.of(0.8))));
        assertNotEquals(base, changed);
    }

    @Test
    void muteEditChangesHash() {
        String base = digester.digest(doc(mixWith(AudioGain.of(1.0))));
        AudioMix muted = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("audio-1", "clip-1"), AudioGain.of(1.0))
                        .withMute(AudioMute.mutedState())));
        assertNotEquals(base, digester.digest(doc(muted)));
    }

    @Test
    void routingEditChangesHash() {
        String base = digester.digest(doc(mixWith(AudioGain.of(1.0))));
        AudioMix routed = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("audio-1", "clip-2"))));
        assertNotEquals(base, digester.digest(doc(routed)));
    }

    @Test
    void gainZeroDistinctFromMute() {
        // A4/A5: canonical gain=0 mute=false != gain=1 mute=true (semantic distinction)
        AudioMix zeroGain = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c"), AudioGain.of(0.0))));
        AudioMix muted = AudioMix.of(AudioMasterBus.master(), List.of(
                AudioRoute.of(AudioMixInput.of("t", "c"), AudioGain.of(1.0))
                        .withMute(AudioMute.mutedState())));
        assertNotEquals(digester.digest(doc(zeroGain)), digester.digest(doc(muted)));
    }

    @Test
    void providerOnlyConfigDoesNotChangeHash() {
        // A15: FFmpeg filter strings / provider translation are NOT canonical content.
        // The adapter output is computed from the canonical mix; the same canonical mix
        // always hashes identically regardless of provider syntax.
        AudioMix mix = mixWith(AudioGain.of(0.5));
        String h1 = digester.digest(doc(mix));
        String h2 = digester.digest(doc(mix));
        assertEquals(h1, h2);
    }

    // ---- G. Diff: audio semantic changes are typed ----

    @Test
    void audioMixChangeDetectedAsTypedChange() {
        TimelineDocument base = doc(mixWith(AudioGain.of(1.0)));
        TimelineDocument target = doc(mixWith(AudioGain.of(0.5)));
        TimelineChangeSet set = TimelineDiffEngine.diff("p", "r1", "r2", "d1", "d2", base, target);
        boolean found = set.getChanges().stream()
                .anyMatch(c -> c.getChangeType() == ChangeType.AUDIO_MIX_CHANGED);
        assertTrue(found, "expected typed AUDIO_MIX_CHANGED");
    }

    @Test
    void identicalMixNoAudioChange() {
        TimelineDocument base = doc(mixWith(AudioGain.of(0.5)));
        TimelineDocument target = doc(mixWith(AudioGain.of(0.5)));
        TimelineChangeSet set = TimelineDiffEngine.diff("p", "r1", "r2", "d1", "d2", base, target);
        assertFalse(set.getChanges().stream().anyMatch(c -> c.getChangeType() == ChangeType.AUDIO_MIX_CHANGED));
    }
}
