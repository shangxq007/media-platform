package com.example.platform.render.app.timeline;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.audio.domain.mix.StereoBalance;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.render.domain.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.version.ReleaseVersion;
import com.example.platform.storage.contract.ContentDigest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FIRST_REAL_MEDIA_CUT_V1 (FRMC-2/3/7): the golden edit is expressible in the
 * canonical model — MediaStreamSourceBinding immutable pins, exact MediaTime,
 * Audio V2 gain/mute/balance semantics, deterministic Timeline hash, and
 * Version Governance release identity. Actual H.264/AAC execution evidence is
 * produced and recorded by the FIRST_REAL_MEDIA_CUT CLI validation.
 */
class FirstRealMediaCutTest {

    private static final MediaAssetId ASSET = MediaAssetId.of("frmc-asset-1");
    private static final MediaStreamId VIDEO_STREAM = MediaStreamId.of("frmc-video-1");
    private static final ArtifactId ARTIFACT = new ArtifactId("frmc-artifact-1");
    private static final ContentDigest DIGEST = new ContentDigest(
            ContentDigest.DigestAlgorithm.SHA_256,
            "bc1059dc6029e4c51165b9806ff8b209cfcd774c5d2bcd99ce5d2e1bb7b4abb8");

    private static TimelineClip canonicalClip(String id, int startNum, int startDen,
                                              int endNum, int endDen,
                                              int trimStartNum, int trimStartDen,
                                              int trimEndNum, int trimEndDen) {
        return new TimelineClip(id, ASSET.value(), VIDEO_STREAM.value(), ARTIFACT.value(),
                DIGEST.value(),
                MediaTime.ofRational(startNum, startDen), MediaTime.ofRational(endNum, endDen),
                MediaTime.ofRational(trimStartNum, trimStartDen),
                MediaTime.ofRational(trimEndNum, trimEndDen), "MEDIA_STREAM");
    }

    private static TimelineClip canonicalClip(String id, int startNum, int startDen,
                                              int endNum, int endDen,
                                              int trimStartNum, int trimEndNum) {
        return canonicalClip(id, startNum, startDen, endNum, endDen,
                trimStartNum, 1, trimEndNum, 1);
    }

    /** Golden edit: A[0,2.5s] normal, C[6,8s] gain 0.4, B[3.5,5.5s] muted,
     *  D[9,11s] balance L. Source order A,B,C,D -> output order A,C,B,D. */
    private static TimelineDocument goldenTimeline() {
        TimelineClip clipA = canonicalClip("clip-a", 0, 1, 5, 2, 0, 1, 5, 2);
        TimelineClip clipC = canonicalClip("clip-c", 5, 2, 9, 2, 6, 8);
        TimelineClip clipB = canonicalClip("clip-b", 9, 2, 13, 2, 7, 2, 11, 2);
        TimelineClip clipD = canonicalClip("clip-d", 13, 2, 17, 2, 9, 11);
        TimelineTrack track = new TimelineTrack("t1", "main", TrackType.VIDEO,
                List.of(clipA, clipC, clipB, clipD));
        AudioRoute rA = new AudioRoute(new AudioMixInput("clip-a", "t1"),
                AudioGain.of(1.0), AudioMute.of(false), StereoBalance.neutral(), List.of());
        AudioRoute rC = new AudioRoute(new AudioMixInput("clip-c", "t1"),
                AudioGain.of(0.4), AudioMute.of(false), StereoBalance.neutral(), List.of());
        AudioRoute rB = new AudioRoute(new AudioMixInput("clip-b", "t1"),
                AudioGain.of(1.0), AudioMute.mutedState(), StereoBalance.neutral(), List.of());
        AudioRoute rD = new AudioRoute(new AudioMixInput("clip-d", "t1"),
                AudioGain.of(1.0), AudioMute.of(false), StereoBalance.of(-1.0), List.of());
        AudioMix mix = new AudioMix(AudioMasterBus.master(), List.of(rA, rC, rB, rD));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty(), mix);
    }

    @Test
    void goldenEditUsesExactCanonicalTime() {
        TimelineDocument doc = goldenTimeline();
        assertEquals(4, doc.getTracks().get(0).clips().size());
        assertEquals(MediaTime.ofRational(5, 2), doc.getTracks().get(0).clips().get(1).getStartTime());
    }

    @Test
    void goldenEditHashIsDeterministic() {
        TimelineContentDigester digester = new TimelineContentDigester();
        String h1 = digester.digest(goldenTimeline());
        String h2 = digester.digest(goldenTimeline());
        assertEquals(h1, h2);
        assertFalse(h1.isBlank());
    }

    @Test
    void sourceBindingsAreImmutablePins() {
        TimelineDocument doc = goldenTimeline();
        for (var clip : doc.getTracks().get(0).clips()) {
            assertEquals(ARTIFACT.value(), clip.getArtifactId());
            assertEquals(DIGEST.value(), clip.getContentDigest());
            assertEquals(TimelineSourceBinding.SourceKind.MEDIA_STREAM.name(), clip.getSourceKind());
        }
    }

    @Test
    void audioSemanticsDistinct() {
        AudioMix mix = goldenTimeline().getAudioMix();
        assertEquals(4, mix.routes().size());
        assertEquals(0.4, mix.routes().get(1).gain().linear());
        assertTrue(mix.routes().get(2).mute().muted());
        assertEquals(-1.0, mix.routes().get(3).balance().value());
        assertNotEquals(mix.routes().get(1).mute(), mix.routes().get(2).mute());
    }

    @Test
    void versionGovernanceIdentityPresent() {
        ReleaseVersion v = ReleaseVersion.parse("0.1.0");
        assertEquals(0, v.epoch());
        assertEquals(1, v.release());
        assertEquals(0, v.patch());
    }
}
