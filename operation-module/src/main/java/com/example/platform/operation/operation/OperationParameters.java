package com.example.platform.operation.operation;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.StereoBalance;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.shared.time.MediaTime;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OM14/OIR2): typed Operation parameters.
 * Only domain value objects; never Map<String,Object>/JsonNode/Object/
 * provider/FFmpeg strings. Temporal single-authority: SetRate has rate ONLY;
 * SetDirection has direction ONLY; Freeze has sourcePosition ONLY.
 */
@org.springframework.modulith.NamedInterface("invocation")
public sealed interface OperationParameters permits
        OperationParameters.RestoreRevisionParameters,
        OperationParameters.InitializeTimelineParameters,
        OperationParameters.AddTrackParameters,
        OperationParameters.ImportTimelineParameters,
        OperationParameters.AddMediaClipParameters,
        OperationParameters.NoParameters,
        OperationParameters.MoveParameters,
        OperationParameters.RelocateClipParameters,
        OperationParameters.TrimParameters,
        OperationParameters.SlideParameters,
        OperationParameters.SlipParameters,
        OperationParameters.RollCutParameters,
        OperationParameters.SplitParameters,
        OperationParameters.PasteClipsParameters,
        OperationParameters.SetTemporalRateParameters,
        OperationParameters.SetTemporalDirectionParameters,
        OperationParameters.FreezeParameters,
        OperationParameters.AudioGainParameters,
        OperationParameters.AudioMuteParameters,
        OperationParameters.StereoBalanceParameters,
        OperationParameters.OpacityParameters,
        OperationParameters.PositionParameters,
        OperationParameters.SubtitleCueParameters,
        OperationParameters.RemoveSubtitleCueParameters,
        OperationParameters.VideoTransformParameters,
        OperationParameters.CompositionAspectParameters,
        OperationParameters.VideoEffectParameters,
        OperationParameters.TransitionCreateParameters,
        OperationParameters.TransitionUpdateParameters,
        OperationParameters.TransitionDeleteParameters,
        OperationParameters.CreateGroupParameters,
        OperationParameters.UpdateGroupMembershipParameters,
        OperationParameters.CreateSyncParameters,
        OperationParameters.UpdateSyncAnchorParameters,
        OperationParameters.AddTextElementParameters,
        OperationParameters.RemoveTextElementParameters,
        OperationParameters.ReplaceTextContentParameters,
        OperationParameters.SetTextStyleRangeParameters,
        OperationParameters.SetParagraphStyleParameters,
        OperationParameters.SetFontSelectionParameters,
        OperationParameters.SetFontFallbackPolicyParameters,
        OperationParameters.SetVariableFontAxisParameters,
        OperationParameters.SetTextLayoutParameters,
        OperationParameters.CreateAlignmentParameters {

    @org.springframework.modulith.NamedInterface("invocation")
    record SubtitleCueParameters(String cueId, MediaTime start, MediaTime end, String text, int width, int fontSize,
            com.example.platform.timeline.canonical.SubtitleCueMotion motion, boolean useDefault,
            boolean legacyReceipt, boolean fontRunsSupplied, FadeIntent fadeIntent, boolean decorationSupplied) implements OperationParameters {
        @org.springframework.modulith.NamedInterface("invocation")
        public enum FadeIntent { PRESERVE, SET, CLEAR, REPLAY_OMISSION }

        public SubtitleCueParameters(String cueId, MediaTime start, MediaTime end, String text, int width, int fontSize,
                com.example.platform.timeline.canonical.SubtitleCueMotion motion, boolean useDefault) {
            this(cueId, start, end, text, width, fontSize, motion, useDefault, false, !motion.fontRuns().isEmpty(),
                    motion.selectedTextFade() == null ? FadeIntent.PRESERVE : FadeIntent.SET, motion.decoration() != null);
        }
        public SubtitleCueParameters(String cueId, MediaTime start, MediaTime end, String text, int width, int fontSize,
                com.example.platform.timeline.canonical.SubtitleCueMotion motion) {
            this(cueId, start, end, text, width, fontSize, motion, false);
        }

        public SubtitleCueParameters withResolvedFont(String profile) {
            return new SubtitleCueParameters(cueId, start, end, text, width, fontSize,
                    new com.example.platform.timeline.canonical.SubtitleCueMotion(profile, motion.keys(), motion.fontRuns(), motion.selectedTextFade(), motion.decoration()),
                    useDefault, false, fontRunsSupplied, fadeIntent, decorationSupplied);
        }

        /** Used only to reconstruct a pre-catalog command whose durable digest already matches. */
        public SubtitleCueParameters asLegacyReceipt() {
            return new SubtitleCueParameters(cueId, start, end, text, width, fontSize, motion, useDefault, true, fontRunsSupplied, fadeIntent, decorationSupplied);
        }

        /** Reproduce an earlier completed omission; the caller must verify its scoped receipt. */
        public SubtitleCueParameters asCompletedFadeOmission() {
            if (fadeIntent != FadeIntent.PRESERVE) throw new IllegalStateException("Expected an omitted fade");
            return new SubtitleCueParameters(cueId, start, end, text, width, fontSize, motion, useDefault, legacyReceipt,
                    fontRunsSupplied, FadeIntent.REPLAY_OMISSION, decorationSupplied);
        }

        public SubtitleCueParameters {
            java.util.Objects.requireNonNull(fadeIntent, "fadeIntent");
            if ((fadeIntent == FadeIntent.SET) != (motion.selectedTextFade() != null)) {
                throw new IllegalArgumentException("Subtitle fade intent differs from its value");
            }
            if (!decorationSupplied && motion.decoration() != null) {
                throw new IllegalArgumentException("Subtitle decoration must be supplied to configure it");
            }
            new RemoveSubtitleCueParameters(cueId);
            try {
                if (!start.isLessThan(end) || end.isGreaterThan(MediaTime.ofMillis(86_400_000))) {
                    throw new IllegalArgumentException("Subtitle needs an increasing interval within the first 24 hours");
                }
                var duration = end.subtract(start);
                for (var time : java.util.List.of(start, duration)) {
                    if (time.ticks() > 9_007_199_254_740_991L || time.timeScale() > 9_007_199_254_740_991L) {
                        throw new IllegalArgumentException("Subtitle time cannot be read exactly by the editor");
                    }
                }
                motion.requireInside(duration);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Subtitle timing exceeds the exact arithmetic range", overflow);
            }
            com.example.platform.fonttext.typography.SubtitleBlockLayout.validateShape(text, width, fontSize);
            com.example.platform.fonttext.typography.SubtitleFontRun.validate(text, motion.fontRuns());
            if (motion.selectedTextFade() != null) motion.selectedTextFade().range().requireWholeGraphemes(text);
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record RemoveSubtitleCueParameters(String cueId) implements OperationParameters {
        public RemoveSubtitleCueParameters {
            if (cueId == null || !cueId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid subtitle cue identity");
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record PositionParameters(com.example.platform.timeline.semantics.effect.PositionKeys keys) implements OperationParameters {
        public PositionParameters { java.util.Objects.requireNonNull(keys); }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record OpacityParameters(com.example.platform.timeline.semantics.effect.OpacityKeys keys) implements OperationParameters {
        public OpacityParameters { java.util.Objects.requireNonNull(keys); }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record SignedTimeOffset(MediaTime magnitude, boolean backward) {
        public SignedTimeOffset {
            java.util.Objects.requireNonNull(magnitude, "offset required");
            if (magnitude.equals(MediaTime.ZERO)) backward = false;
        }

        public static SignedTimeOffset parse(String value) {
            if (!value.matches("[+-]?[0-9]+(/[1-9][0-9]*)?")) {
                throw new IllegalArgumentException("Offset must be an exact signed integer or fraction");
            }
            boolean negative = value.startsWith("-");
            String unsigned = negative || value.startsWith("+") ? value.substring(1) : value;
            return new SignedTimeOffset(MediaTime.parse(unsigned.contains("/") ? unsigned : unsigned + "/1"), negative);
        }

        public MediaTime shift(MediaTime endpoint) {
            return backward ? endpoint.subtract(magnitude) : endpoint.add(magnitude);
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record SlideParameters(String trackId, TimelineClipId leftClipId, TimelineClipId middleClipId,
                           TimelineClipId rightClipId, SignedTimeOffset offset) implements OperationParameters {
        public SlideParameters {
            if (trackId == null || !trackId.matches("[A-Za-z0-9._:-]{1,64}") || offset == null
                    || leftClipId == null || middleClipId == null || rightClipId == null
                    || List.of(leftClipId, middleClipId, rightClipId).stream().distinct().count() != 3
                    || List.of(leftClipId, middleClipId, rightClipId).stream().anyMatch(id -> !id.value().matches("[A-Za-z0-9._:-]{1,64}"))) {
                throw new IllegalArgumentException("Slide requires a track, three distinct clip identities and an exact offset");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record SlipParameters(MediaTime magnitude, boolean backward) implements OperationParameters {
        public SlipParameters {
            java.util.Objects.requireNonNull(magnitude, "source offset required");
            if (magnitude.equals(MediaTime.ZERO)) backward = false;
        }

        public static SlipParameters parse(String value) {
            var offset = SignedTimeOffset.parse(value);
            return new SlipParameters(offset.magnitude(), offset.backward());
        }

        public MediaTime shift(MediaTime endpoint) {
            return backward ? endpoint.subtract(magnitude) : endpoint.add(magnitude);
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record RollCutParameters(String trackId, TimelineClipId leftClipId, TimelineClipId rightClipId,
                             MediaTime cutTime) implements OperationParameters {
        public RollCutParameters {
            if (trackId == null || !trackId.matches("[A-Za-z0-9._:-]{1,64}") || leftClipId == null || rightClipId == null
                    || !leftClipId.value().matches("[A-Za-z0-9._:-]{1,64}") || !rightClipId.value().matches("[A-Za-z0-9._:-]{1,64}")
                    || leftClipId.equals(rightClipId) || cutTime == null) {
                throw new IllegalArgumentException("Roll requires a track, two distinct clip IDs and an exact cut time");
            }
        }
    }

    /** Identities are paired in authored chronological source order. The planner computes all offsets. */
    @org.springframework.modulith.NamedInterface("invocation")
    record PasteClipsParameters(String trackId, List<TimelineClipId> sourceClipIds,
                                List<TimelineClipId> newClipIds, MediaTime timelineStart) implements OperationParameters {
        public static final int MAX_CLIPS = 64;

        public PasteClipsParameters {
            sourceClipIds = List.copyOf(sourceClipIds);
            newClipIds = List.copyOf(newClipIds);
            if (trackId == null || !trackId.matches("[A-Za-z0-9._:-]{1,64}") || timelineStart == null
                    || timelineStart.isLessThan(MediaTime.ZERO) || sourceClipIds.isEmpty()
                    || sourceClipIds.size() > MAX_CLIPS || sourceClipIds.size() != newClipIds.size()
                    || !validDistinctIds(sourceClipIds) || !validDistinctIds(newClipIds)) {
                throw new IllegalArgumentException("Paste requires one track, a nonnegative start, and 1–64 paired distinct clip identities");
            }
        }

        private static boolean validDistinctIds(List<TimelineClipId> ids) {
            return Set.copyOf(ids).size() == ids.size()
                    && ids.stream().allMatch(id -> id.value().matches("[A-Za-z0-9._:-]{1,64}"));
        }
    }

    /** Creates the first empty, versioned Timeline with one stable video track. */
    @org.springframework.modulith.NamedInterface("invocation")
    record AddTrackParameters(String trackId, com.example.platform.timeline.canonical.TrackType trackType)
            implements OperationParameters {
        public AddTrackParameters {
            if (trackId == null || !trackId.matches("[A-Za-z0-9._:-]{1,64}")
                    || (trackType != com.example.platform.timeline.canonical.TrackType.VIDEO
                            && trackType != com.example.platform.timeline.canonical.TrackType.AUDIO)) {
                throw new IllegalArgumentException("bounded track identity and video or audio type required");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record InitializeTimelineParameters(String trackId, String subtitleTrackLanguage) implements OperationParameters {
        public InitializeTimelineParameters(String trackId) { this(trackId, null); }
        public InitializeTimelineParameters {
            subtitleTrackLanguage = com.example.platform.timeline.canonical.TimelineMetadata.checkedSubtitleLanguage(subtitleTrackLanguage);
            if (trackId == null || !trackId.matches("[A-Za-z0-9._:-]{1,64}")) {
                throw new IllegalArgumentException("bounded trackId required");
            }
        }
    }

    /** Exact candidate for an authorized canonical Timeline import. */
    @org.springframework.modulith.NamedInterface("invocation")
    record ImportTimelineParameters(com.example.platform.timeline.canonical.TimelineDocument document)
            implements OperationParameters {
        public ImportTimelineParameters {
            java.util.Objects.requireNonNull(document, "canonical import document required");
        }
    }

    /**
     * ADD_MEDIA_CLIP_V1. The first supported variant adds exactly one
     * recorded-media clip to an existing Timeline track. The immutable source
     * pin owns the exact source range; placement owns the exact Timeline range;
     * TemporalMapping owns only the mapping between them.
     */
    @org.springframework.modulith.NamedInterface("invocation")
    record AddMediaClipParameters(
            String trackId,
            com.example.platform.timeline.canonical.TimelineClipId clipId,
            com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding sourceBinding,
            com.example.platform.timeline.semantics.clip.MediaClip.TimeRange placement,
            com.example.platform.timeline.semantics.temporal.TemporalMapping temporalMapping)
            implements OperationParameters {
        public AddMediaClipParameters {
            if (trackId == null || trackId.isBlank()) {
                throw new IllegalArgumentException("trackId required");
            }
            if (clipId == null || sourceBinding == null || placement == null || temporalMapping == null) {
                throw new IllegalArgumentException(
                        "clipId, sourceBinding, placement and temporalMapping required");
            }
            // Exact duration/mapping validity is a canonical MediaClip invariant.
            new com.example.platform.timeline.semantics.clip.MediaClip(
                    clipId.value(), trackId, placement, sourceBinding.sourceRange(),
                    temporalMapping, sourceBinding);
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record NoParameters() implements OperationParameters {
    }

    /**
     * SPLIT: split one clip at an exact timeline time. The left clip keeps the
     * original identity; the caller supplies the right clip identity so the plan
     * (and its replay) is deterministic.
     */
    @org.springframework.modulith.NamedInterface("invocation")
    record SplitParameters(
            com.example.platform.timeline.canonical.TimelineClipId rightClipId,
            MediaTime splitTimelineTime) implements OperationParameters {
        public SplitParameters {
            if (rightClipId == null || splitTimelineTime == null) {
                throw new IllegalArgumentException("rightClipId and splitTimelineTime required");
            }
        }
    }

    /** MOVE (OE1): exact MediaTime delta OR absolute destination; non-ripple intent. */
    @org.springframework.modulith.NamedInterface("invocation")
    record MoveParameters(MediaTime delta, boolean absolute, boolean backward) implements OperationParameters {
        public MoveParameters(MediaTime delta, boolean absolute) {
            this(delta, absolute, false);
        }

        public MoveParameters {
            if (delta == null || absolute && backward) {
                throw new IllegalArgumentException("Move requires a delta and an absolute destination cannot be negative");
            }
            if (delta.equals(MediaTime.ZERO)) backward = false;
        }
    }

    /** Repositions one clip on another track of the same media type. */
    @org.springframework.modulith.NamedInterface("invocation")
    record RelocateClipParameters(MediaTime delta, boolean absolute, String destinationTrackId)
            implements OperationParameters {
        public RelocateClipParameters {
            if (delta == null || destinationTrackId == null || !destinationTrackId.matches("[A-Za-z0-9._:-]{1,64}")) {
                throw new IllegalArgumentException("move time and bounded destination track ID required");
            }
        }
    }

    /** TRIM (OE3): bounded placement-edge trim, exactly one target, START or END edge. */
    @org.springframework.modulith.NamedInterface("invocation")
    record TrimParameters(TrimEdge edge, MediaTime delta) implements OperationParameters {
        public TrimParameters {
            if (edge == null || delta == null) {
                throw new IllegalArgumentException("edge and delta required");
            }
        }

        @org.springframework.modulith.NamedInterface("invocation")
        public enum TrimEdge {
            START,
            END
        }
    }

    /** SET_TEMPORAL_RATE (OIR2): positive exact Rational rate ONLY — no direction. */
    @org.springframework.modulith.NamedInterface("invocation")
    record SetTemporalRateParameters(com.example.platform.timeline.semantics.clip.MediaClip.Rational rate)
            implements OperationParameters {
        public SetTemporalRateParameters {
            if (rate == null) {
                throw new IllegalArgumentException("rate required");
            }
        }
    }

    /** SET_TEMPORAL_DIRECTION (OIR2): PlaybackDirection ONLY — no rate. */
    @org.springframework.modulith.NamedInterface("invocation")
    record SetTemporalDirectionParameters(PlaybackDirection direction) implements OperationParameters {
        public SetTemporalDirectionParameters {
            if (direction == null) {
                throw new IllegalArgumentException("direction required");
            }
        }
    }

    /** FREEZE (OIR2): exact sourcePosition ONLY — no rate/direction/duration. */
    @org.springframework.modulith.NamedInterface("invocation")
    record FreezeParameters(MediaTime sourcePosition) implements OperationParameters {
        public FreezeParameters {
            if (sourcePosition == null) {
                throw new IllegalArgumentException("sourcePosition required");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record VideoEffectParameters(BasicVideoEffect effect) implements OperationParameters {
        public VideoEffectParameters {
            if (effect == null) throw new IllegalArgumentException("video effect required");
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record TransitionCreateParameters(String transitionId, String incomingClipId,
                                      com.example.platform.shared.time.MediaTime duration) implements OperationParameters {
        public TransitionCreateParameters {
            if (transitionId == null || transitionId.isBlank()) throw new IllegalArgumentException("transitionId required");
            if (incomingClipId == null || incomingClipId.isBlank()) throw new IllegalArgumentException("incomingClipId required");
            if (duration == null) throw new IllegalArgumentException("duration required");
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record TransitionUpdateParameters(String transitionId,
                                      com.example.platform.shared.time.MediaTime duration) implements OperationParameters {
        public TransitionUpdateParameters {
            if (transitionId == null || transitionId.isBlank()) throw new IllegalArgumentException("transitionId required");
            if (duration == null) throw new IllegalArgumentException("duration required");
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record TransitionDeleteParameters(String transitionId) implements OperationParameters {
        public TransitionDeleteParameters {
            if (transitionId == null || transitionId.isBlank()) throw new IllegalArgumentException("transitionId required");
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record RestoreRevisionParameters(String sourceRevisionId) implements OperationParameters {
        public RestoreRevisionParameters {
            if (sourceRevisionId == null || !sourceRevisionId.matches(SOURCE_REVISION_ID_PATTERN)) {
                throw new IllegalArgumentException("Invalid historical revision identity");
            }
        }
        public static final String SOURCE_REVISION_ID_PATTERN = "[A-Za-z0-9._:-]{1,64}";
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record SourceStreamInput(String artifactId, String contentDigest, String streamId) {
        public static final int MAX_ID_LENGTH = 256;

        public SourceStreamInput {
            if (artifactId == null || artifactId.isBlank() || artifactId.codePointCount(0, artifactId.length()) > MAX_ID_LENGTH
                    || streamId == null || streamId.isBlank() || streamId.codePointCount(0, streamId.length()) > MAX_ID_LENGTH
                    || contentDigest == null || !contentDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid immutable source stream reference");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record CompositionAspectParameters(com.example.platform.timeline.canonical.CompositionAspectRatio aspectRatio,
                                       SourceStreamInput input)
            implements OperationParameters {
        public CompositionAspectParameters(com.example.platform.timeline.canonical.CompositionAspectRatio aspectRatio) {
            this(aspectRatio, null);
        }

        public CompositionAspectParameters {
            java.util.Objects.requireNonNull(aspectRatio, "composition aspect ratio");
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record VideoTransformParameters(VideoTransform transform) implements OperationParameters {
        public VideoTransformParameters {
            if (transform == null) {
                throw new IllegalArgumentException("video transform required");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record AudioGainParameters(AudioGain gain) implements OperationParameters {
        public AudioGainParameters {
            if (gain == null) {
                throw new IllegalArgumentException("gain required");
            }
            if (gain.linear() > 2.0) {
                throw new IllegalArgumentException("gain must be within [0.0, 2.0]");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record AudioMuteParameters(AudioMute mute) implements OperationParameters {
        public AudioMuteParameters {
            if (mute == null) {
                throw new IllegalArgumentException("mute required");
            }
        }
    }

    @org.springframework.modulith.NamedInterface("invocation")
    record StereoBalanceParameters(StereoBalance balance) implements OperationParameters {
        public StereoBalanceParameters {
            if (balance == null) {
                throw new IllegalArgumentException("balance required");
            }
        }
    }

    /** CREATE_GROUP: caller-supplied stable GroupId + >=2 typed members. */
    @org.springframework.modulith.NamedInterface("invocation")
    record CreateGroupParameters(GroupId groupId, List<TimelineClipId> members) implements OperationParameters {
        public CreateGroupParameters {
            if (groupId == null || members == null || members.size() < 2) {
                throw new IllegalArgumentException("groupId and >=2 members required");
            }
        }
    }

    /** UPDATE_GROUP_MEMBERSHIP: typed member delta (no generic patch collection). */
    @org.springframework.modulith.NamedInterface("invocation")
    record UpdateGroupMembershipParameters(Set<TimelineClipId> membersToAdd,
                                           Set<TimelineClipId> membersToRemove)
            implements OperationParameters {
        public UpdateGroupMembershipParameters {
            if (membersToAdd == null || membersToRemove == null) {
                throw new IllegalArgumentException("member deltas required");
            }
            var add = new TreeSet<>(membersToAdd);
            var remove = new TreeSet<>(membersToRemove);
            add.retainAll(remove);
            if (!add.isEmpty()) {
                throw new IllegalArgumentException("contradictory add/remove of same member");
            }
        }
    }

    /** CREATE_SYNC: two distinct clips + exact object-local anchors (no coincidence). */
    @org.springframework.modulith.NamedInterface("invocation")
    record CreateSyncParameters(TimelineClipId endpointA, MediaTime localAnchorA,
                                TimelineClipId endpointB, MediaTime localAnchorB)
            implements OperationParameters {
        public CreateSyncParameters {
            if (endpointA == null || endpointB == null || localAnchorA == null || localAnchorB == null) {
                throw new IllegalArgumentException("endpoints and anchors required");
            }
            if (endpointA.equals(endpointB)) {
                throw new IllegalArgumentException("sync endpoints must be distinct");
            }
        }
    }

    /** UPDATE_SYNC_ANCHOR: same normalized pair + new exact anchors. */
    @org.springframework.modulith.NamedInterface("invocation")
    record UpdateSyncAnchorParameters(MediaTime localAnchorA, MediaTime localAnchorB)
            implements OperationParameters {
        public UpdateSyncAnchorParameters {
            if (localAnchorA == null || localAnchorB == null) {
                throw new IllegalArgumentException("anchors required");
            }
        }
    }

    // ============ ROADMAP_19 TEXT OPERATIONS (C37/FTG19; typed domain semantics) ============

    /** ADD_TEXT_ELEMENT: authored text + layout intent; font resolution freezes in plan. */
    @org.springframework.modulith.NamedInterface("invocation")
    record AddTextElementParameters(
            com.example.platform.fonttext.text.StyledText styledText,
            com.example.platform.fonttext.typography.TextFrame frame,
            com.example.platform.fonttext.resolution.FontFallbackPolicy fallbackPolicy,
            com.example.platform.fonttext.typography.FontRational start,
            com.example.platform.fonttext.typography.FontRational duration) implements OperationParameters {
    }

    @org.springframework.modulith.NamedInterface("invocation")
    interface TextElementEdit {
        com.example.platform.timeline.canonical.TextElementId textElementId();
    }

    /** REMOVE_TEXT_ELEMENT: exact TextElementId target. */
    @org.springframework.modulith.NamedInterface("invocation")
    record RemoveTextElementParameters(com.example.platform.timeline.canonical.TextElementId textElementId)
            implements OperationParameters, TextElementEdit {
    }

    /** REPLACE_TEXT_CONTENT: canonical authored Unicode content replacement (scalar-validated). */
    @org.springframework.modulith.NamedInterface("invocation")
    record ReplaceTextContentParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.text.TextContent content) implements OperationParameters, TextElementEdit {
    }

    /** SET_TEXT_STYLE_RANGE: scalar TextRange + canonical TextStyle (no lineHeight/fill by construction). */
    @org.springframework.modulith.NamedInterface("invocation")
    record SetTextStyleRangeParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.text.TextRange range,
            com.example.platform.fonttext.typography.TextStyle style) implements OperationParameters, TextElementEdit {
    }

    /** SET_PARAGRAPH_STYLE: sole lineHeight/baseDirection/alignment authority. */
    @org.springframework.modulith.NamedInterface("invocation")
    record SetParagraphStyleParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.typography.ParagraphStyle paragraphStyle) implements OperationParameters, TextElementEdit {
    }

    /** SET_FONT_SELECTION: updates FontSelectionIntent (sole selection authority) over a scalar range. */
    @org.springframework.modulith.NamedInterface("invocation")
    record SetFontSelectionParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.text.TextRange range,
            com.example.platform.fonttext.typography.FontSelectionIntent fontSelection) implements OperationParameters, TextElementEdit {
    }

    /** SET_FONT_FALLBACK_POLICY: explicit ordered canonical fallback intent. */
    @org.springframework.modulith.NamedInterface("invocation")
    record SetFontFallbackPolicyParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.resolution.FontFallbackPolicy fallbackPolicy) implements OperationParameters, TextElementEdit {
    }

    /** SET_VARIABLE_FONT_AXIS: updates FontSelectionIntent explicit axis overrides (exact Rational). */
    @org.springframework.modulith.NamedInterface("invocation")
    record SetVariableFontAxisParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.typography.VariationCoordinate coordinate) implements OperationParameters, TextElementEdit {
    }

    /** SET_TEXT_LAYOUT: authored layout intent (TextFrame) only; zero glyph geometry. */
    @org.springframework.modulith.NamedInterface("invocation")
    record SetTextLayoutParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.typography.TextFrame frame) implements OperationParameters, TextElementEdit {
    }

    /**
     * CREATE_ALIGNMENT (L5 lineage): typed alignment descriptor. The caller mints the stable
     * {@link com.example.platform.timeline.semantics.relationship.AlignmentId}; both endpoint sets
     * are typed {@code SourceUnitId}s and must be non-empty; attributes are typed values (never a
     * Map/String). {@code sourceBaseline} is provenance materialised by the owner layer, so it is
     * deliberately NOT part of this intent parameter.
     */
    @org.springframework.modulith.NamedInterface("invocation")
    record CreateAlignmentParameters(
            com.example.platform.timeline.semantics.relationship.AlignmentId alignmentId,
            java.util.Set<com.example.platform.timeline.semantics.relationship.SourceUnitId> sourceUnits,
            java.util.Set<com.example.platform.timeline.semantics.relationship.SourceUnitId> derivedUnits,
            java.util.List<com.example.platform.timeline.semantics.relationship.AlignmentAttribute> attributes)
            implements OperationParameters {
        public CreateAlignmentParameters {
            if (alignmentId == null || sourceUnits == null || derivedUnits == null) {
                throw new IllegalArgumentException("alignmentId, sourceUnits and derivedUnits required");
            }
            if (sourceUnits.isEmpty() || derivedUnits.isEmpty()) {
                throw new IllegalArgumentException("sourceUnits and derivedUnits must be non-empty");
            }
            sourceUnits = new java.util.TreeSet<>(sourceUnits);
            derivedUnits = new java.util.TreeSet<>(derivedUnits);
            attributes = attributes == null ? java.util.List.of() : java.util.List.copyOf(attributes);
        }
    }
}
