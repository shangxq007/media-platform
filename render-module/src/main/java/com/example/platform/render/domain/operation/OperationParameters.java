package com.example.platform.render.domain.operation;

import com.example.platform.audio.domain.mix.AudioGain;
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
public sealed interface OperationParameters permits
        OperationParameters.NoParameters,
        OperationParameters.MoveParameters,
        OperationParameters.TrimParameters,
        OperationParameters.SetTemporalRateParameters,
        OperationParameters.SetTemporalDirectionParameters,
        OperationParameters.FreezeParameters,
        OperationParameters.AudioGainParameters,
        OperationParameters.AudioMuteParameters,
        OperationParameters.StereoBalanceParameters,
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
        OperationParameters.SetTextLayoutParameters {

    record NoParameters() implements OperationParameters {
    }

    /** MOVE (OE1): exact MediaTime delta OR absolute destination; non-ripple intent. */
    record MoveParameters(MediaTime delta, boolean absolute) implements OperationParameters {
        public MoveParameters {
            if (delta == null) {
                throw new IllegalArgumentException("delta required");
            }
        }
    }

    /** TRIM (OE3): bounded placement-edge trim, exactly one target, START or END edge. */
    record TrimParameters(TrimEdge edge, MediaTime delta) implements OperationParameters {
        public TrimParameters {
            if (edge == null || delta == null) {
                throw new IllegalArgumentException("edge and delta required");
            }
        }

        public enum TrimEdge {
            START,
            END
        }
    }

    /** SET_TEMPORAL_RATE (OIR2): positive exact Rational rate ONLY — no direction. */
    record SetTemporalRateParameters(com.example.platform.timeline.semantics.clip.MediaClip.Rational rate)
            implements OperationParameters {
        public SetTemporalRateParameters {
            if (rate == null) {
                throw new IllegalArgumentException("rate required");
            }
        }
    }

    /** SET_TEMPORAL_DIRECTION (OIR2): PlaybackDirection ONLY — no rate. */
    record SetTemporalDirectionParameters(PlaybackDirection direction) implements OperationParameters {
        public SetTemporalDirectionParameters {
            if (direction == null) {
                throw new IllegalArgumentException("direction required");
            }
        }
    }

    /** FREEZE (OIR2): exact sourcePosition ONLY — no rate/direction/duration. */
    record FreezeParameters(MediaTime sourcePosition) implements OperationParameters {
        public FreezeParameters {
            if (sourcePosition == null) {
                throw new IllegalArgumentException("sourcePosition required");
            }
        }
    }

    record AudioGainParameters(AudioGain gain) implements OperationParameters {
        public AudioGainParameters {
            if (gain == null) {
                throw new IllegalArgumentException("gain required");
            }
        }
    }

    record AudioMuteParameters(AudioMute mute) implements OperationParameters {
        public AudioMuteParameters {
            if (mute == null) {
                throw new IllegalArgumentException("mute required");
            }
        }
    }

    record StereoBalanceParameters(StereoBalance balance) implements OperationParameters {
        public StereoBalanceParameters {
            if (balance == null) {
                throw new IllegalArgumentException("balance required");
            }
        }
    }

    /** CREATE_GROUP: caller-supplied stable GroupId + >=2 typed members. */
    record CreateGroupParameters(GroupId groupId, List<TimelineClipId> members) implements OperationParameters {
        public CreateGroupParameters {
            if (groupId == null || members == null || members.size() < 2) {
                throw new IllegalArgumentException("groupId and >=2 members required");
            }
        }
    }

    /** UPDATE_GROUP_MEMBERSHIP: typed member delta (no generic patch collection). */
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
    record AddTextElementParameters(
            com.example.platform.fonttext.text.StyledText styledText,
            com.example.platform.fonttext.typography.TextFrame frame,
            com.example.platform.fonttext.resolution.FontFallbackPolicy fallbackPolicy,
            com.example.platform.fonttext.typography.FontRational start,
            com.example.platform.fonttext.typography.FontRational duration) implements OperationParameters {
    }

    /** REMOVE_TEXT_ELEMENT: exact TextElementId target. */
    record RemoveTextElementParameters(com.example.platform.timeline.canonical.TextElementId textElementId)
            implements OperationParameters {
    }

    /** REPLACE_TEXT_CONTENT: canonical authored Unicode content replacement (scalar-validated). */
    record ReplaceTextContentParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.text.TextContent content) implements OperationParameters {
    }

    /** SET_TEXT_STYLE_RANGE: scalar TextRange + canonical TextStyle (no lineHeight/fill by construction). */
    record SetTextStyleRangeParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.text.TextRange range,
            com.example.platform.fonttext.typography.TextStyle style) implements OperationParameters {
    }

    /** SET_PARAGRAPH_STYLE: sole lineHeight/baseDirection/alignment authority. */
    record SetParagraphStyleParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.typography.ParagraphStyle paragraphStyle) implements OperationParameters {
    }

    /** SET_FONT_SELECTION: updates FontSelectionIntent (sole selection authority) over a scalar range. */
    record SetFontSelectionParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.text.TextRange range,
            com.example.platform.fonttext.typography.FontSelectionIntent fontSelection) implements OperationParameters {
    }

    /** SET_FONT_FALLBACK_POLICY: explicit ordered canonical fallback intent. */
    record SetFontFallbackPolicyParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.resolution.FontFallbackPolicy fallbackPolicy) implements OperationParameters {
    }

    /** SET_VARIABLE_FONT_AXIS: updates FontSelectionIntent explicit axis overrides (exact Rational). */
    record SetVariableFontAxisParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.typography.VariationCoordinate coordinate) implements OperationParameters {
    }

    /** SET_TEXT_LAYOUT: authored layout intent (TextFrame) only; zero glyph geometry. */
    record SetTextLayoutParameters(
            com.example.platform.timeline.canonical.TextElementId textElementId,
            com.example.platform.fonttext.typography.TextFrame frame) implements OperationParameters {
    }
}
