package com.example.platform.operation.operation;

import com.example.platform.timeline.canonical.TimedTextCanonicalSemantics;
import com.example.platform.timeline.canonical.TimelineClipId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OM15/§15): deterministic versioned parameter
 * digest. Domain-separated semantic input: format version + definition id +
 * OperationDefinitionVersion + deterministically serialized typed parameters.
 * Excludes base revision/hash, target/scope, invocationId, metadata, actor.
 * Same definition/version + semantically equal parameters => same digest
 * regardless of target/revision — intentional. Never Timeline content hash.
 */
@org.springframework.modulith.NamedInterface("operation")
public final class ParameterDigest {

    private static final String FORMAT_VERSION = "operation-parameter-format-v1";

    private ParameterDigest() {
    }

    public static String compute(OperationDefinitionId definitionId, OperationDefinitionVersion version,
                                 OperationParameters parameters) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(FORMAT_VERSION.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(definitionId.value().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update((version.major() + "." + version.minor()).getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(serialize(parameters).getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /** Deterministic canonical semantic serialization of typed parameters. */
    static String serialize(OperationParameters p) {
        if (p instanceof OperationParameters.AddTrackParameters track) {
            return "add-track(" + track.trackId() + "," + track.trackType() + ")";
        }
        if (p instanceof OperationParameters.InitializeTimelineParameters init) {
            return "initialize-timeline(video-track=" + init.trackId() + ")";
        }
        if (p instanceof OperationParameters.ImportTimelineParameters imported) {
            return "import-timeline(content="
                    + new com.example.platform.timeline.canonical.TimelineContentDigester()
                            .digest(imported.document()) + ")";
        }
        if (p instanceof OperationParameters.AddMediaClipParameters a) {
            var binding = a.sourceBinding();
            return "media-clip(" + a.trackId() + "," + a.clipId().value()
                    + ",sourceBinding="
                    + com.example.platform.timeline.semantics.clip
                            .TimelineSourceBindingCanonicalSemantics.encode(binding)
                    + ",placement=" + a.placement().start() + ".." + a.placement().end()
                    + ",mapping=" + temporalMapping(a.temporalMapping()) + ")";
        }
        if (p instanceof OperationParameters.PasteClipsParameters paste) {
            return framed("paste-clips-v1", paste.trackId(), paste.timelineStart().toString(),
                    paste.sourceClipIds().stream().map(TimelineClipId::value).collect(java.util.stream.Collectors.joining(",")),
                    paste.newClipIds().stream().map(TimelineClipId::value).collect(java.util.stream.Collectors.joining(",")));
        }
        if (p instanceof OperationParameters.RestoreRevisionParameters restore) {
            return framed("restore-revision-v1", restore.sourceRevisionId());
        }
        if (p instanceof OperationParameters.NoParameters) {
            return "none";
        }
        if (p instanceof OperationParameters.MoveParameters m) {
            return "move(" + (m.backward() ? "-" : "") + m.delta() + ",absolute=" + m.absolute() + ")";
        }
        if (p instanceof OperationParameters.RelocateClipParameters relocate) {
            return "relocate(" + relocate.delta() + ",absolute=" + relocate.absolute()
                    + ",track=" + relocate.destinationTrackId() + ")";
        }
        if (p instanceof OperationParameters.TrimParameters t) {
            return "trim(" + t.edge() + "," + t.delta() + ")";
        }
        if (p instanceof OperationParameters.SplitParameters split) {
            return "split(" + split.rightClipId().value() + "," + split.splitTimelineTime() + ")";
        }
        if (p instanceof OperationParameters.SetTemporalRateParameters r) {
            long n = r.rate().numerator();
            long d = r.rate().denominator();
            long g = gcd(n, d);
            return "rate(" + (n / g) + "/" + (d / g) + ")";
        }
        if (p instanceof OperationParameters.SetTemporalDirectionParameters d) {
            return "direction(" + d.direction() + ")";
        }
        if (p instanceof OperationParameters.FreezeParameters f) {
            return "freeze(" + f.sourcePosition() + ")";
        }
        if (p instanceof OperationParameters.VideoEffectParameters ve) {
            var e = ve.effect();
            return "video-effect(" + e.brightness() + "," + e.contrast() + "," + e.saturation()
                    + "," + e.grayscale() + "," + e.fadeInSeconds() + "," + e.fadeOutSeconds()
                    + "," + e.enabled() + ")";
        }
        if (p instanceof OperationParameters.TransitionCreateParameters tc) {
            return "transition-create(" + tc.transitionId() + "," + tc.incomingClipId() + "," + tc.duration() + ")";
        }
        if (p instanceof OperationParameters.TransitionUpdateParameters tu) {
            return "transition-update(" + tu.transitionId() + "," + tu.duration() + ")";
        }
        if (p instanceof OperationParameters.TransitionDeleteParameters td) {
            return "transition-delete(" + td.transitionId() + ")";
        }
        if (p instanceof OperationParameters.CompositionAspectParameters aspect) {
            if (aspect.input() != null) {
                var input = aspect.input();
                return "match-source-v1(" + input.artifactId().length() + ":" + input.artifactId()
                        + input.contentDigest() + input.streamId().length() + ":" + input.streamId()
                        + aspect.aspectRatio() + ")";
            }
            return "composition-aspect(" + aspect.aspectRatio() + ")";
        }
        if (p instanceof OperationParameters.VideoTransformParameters vt) {
            var t = vt.transform();
            return "video-transform(" + t.positionXMicros() + "," + t.positionYMicros() + ","
                    + t.scaleXMicros() + "," + t.scaleYMicros() + "," + t.rotationMicroDegrees() + ","
                    + t.cropLeftMicros() + "," + t.cropRightMicros() + "," + t.cropTopMicros() + "," + t.cropBottomMicros()
                    + "," + t.opacityMicros() + ")";
        }
        if (p instanceof OperationParameters.AudioGainParameters g) {
            return "gain(" + g.gain().linear() + ")";
        }
        if (p instanceof OperationParameters.AudioMuteParameters mu) {
            return "mute(" + mu.mute().muted() + ")";
        }
        if (p instanceof OperationParameters.StereoBalanceParameters b) {
            return "balance(" + b.balance().value() + ")";
        }
        if (p instanceof OperationParameters.CreateGroupParameters cg) {
            return "group(" + cg.groupId().value() + ","
                    + cg.members().stream().map(m -> m.value()).sorted().toList() + ")";
        }
        if (p instanceof OperationParameters.UpdateGroupMembershipParameters um) {
            return "membership("
                    + um.membersToAdd().stream().map(m -> m.value()).sorted().toList() + ","
                    + um.membersToRemove().stream().map(m -> m.value()).sorted().toList() + ")";
        }
        if (p instanceof OperationParameters.CreateSyncParameters cs) {
            String a = cs.endpointA().value();
            String b = cs.endpointB().value();
            String key = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
            return "sync(" + key + "," + cs.localAnchorA() + "," + cs.localAnchorB() + ")";
        }
        if (p instanceof OperationParameters.UpdateSyncAnchorParameters ua) {
            return "sync-anchor(" + ua.localAnchorA() + "," + ua.localAnchorB() + ")";
        }
        if (p instanceof OperationParameters.AddTextElementParameters || p instanceof OperationParameters.TextElementEdit) {
            return textFields(p);
        }
        if (p instanceof OperationParameters.CreateAlignmentParameters cap) {
            // L5 lineage: deterministic canonical serialization — endpoints sorted by value,
            // attributes sorted by tag (order-insensitive descriptor => stable digest).
            return "alignment(" + cap.alignmentId().value()
                    + ",sourceUnits=" + sortedUnits(cap.sourceUnits())
                    + ",derivedUnits=" + sortedUnits(cap.derivedUnits())
                    + ",attributes=" + sortedAttributes(cap.attributes()) + ")";
        }
        throw new IllegalArgumentException("unknown parameters: " + p.getClass());
    }

    /** Lengths in UTF-16 code units make adjacent arbitrary strings and absent values unambiguous. */
    static String framed(String... values) {
        var out = new StringBuilder();
        for (String value : values) {
            if (value == null) out.append("-1:");
            else out.append(value.length()).append(':').append(value);
        }
        return out.toString();
    }

    private static String textFields(OperationParameters parameters) {
        String fields = switch (parameters) {
            case OperationParameters.AddTextElementParameters p -> framed(
                    TimedTextCanonicalSemantics.styledText(p.styledText()).toString(),
                    TimedTextCanonicalSemantics.frame(p.frame()).toString(),
                    TimedTextCanonicalSemantics.fallbackPolicy(p.fallbackPolicy()).toString(),
                    TimedTextCanonicalSemantics.rational(p.start()).toString(),
                    TimedTextCanonicalSemantics.rational(p.duration()).toString());
            case OperationParameters.RemoveTextElementParameters ignored -> "";
            case OperationParameters.ReplaceTextContentParameters p -> p.content().value();
            case OperationParameters.SetTextStyleRangeParameters p -> framed(
                    TimedTextCanonicalSemantics.range(p.range()).toString(), TimedTextCanonicalSemantics.style(p.style()).toString());
            case OperationParameters.SetParagraphStyleParameters p -> TimedTextCanonicalSemantics.paragraphStyle(p.paragraphStyle()).toString();
            case OperationParameters.SetFontSelectionParameters p -> framed(
                    TimedTextCanonicalSemantics.range(p.range()).toString(), TimedTextCanonicalSemantics.fontSelection(p.fontSelection()).toString());
            case OperationParameters.SetFontFallbackPolicyParameters p -> TimedTextCanonicalSemantics.fallbackPolicy(p.fallbackPolicy()).toString();
            case OperationParameters.SetVariableFontAxisParameters p -> TimedTextCanonicalSemantics.variationCoordinate(p.coordinate()).toString();
            case OperationParameters.SetTextLayoutParameters p -> TimedTextCanonicalSemantics.frame(p.frame()).toString();
            default -> throw new IllegalArgumentException("Unknown Text parameters");
        };
        String id = parameters instanceof OperationParameters.TextElementEdit edit ? edit.textElementId().value() : null;
        return framed("text-parameters-v2", id, fields);
    }

    private static String sortedUnits(
            java.util.Set<com.example.platform.timeline.semantics.relationship.SourceUnitId> units) {
        return units.stream().map(u -> u.value()).sorted().toList().toString();
    }

    private static String sortedAttributes(
            java.util.List<com.example.platform.timeline.semantics.relationship.AlignmentAttribute> attributes) {
        return attributes.stream()
                .sorted(java.util.Comparator.comparing(
                        com.example.platform.timeline.semantics.relationship.AlignmentAttribute::tag))
                .map(a -> a.tag() + "=" + attributeValue(a.value()))
                .toList().toString();
    }

    private static String attributeValue(
            com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue value) {
        return switch (value) {
            case com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue.TextValue t ->
                    "text:" + t.value();
            case com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue.NumberValue n ->
                    "number:" + n.value();
            case com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue.BooleanValue b ->
                    "boolean:" + b.value();
        };
    }

    private static String temporalMapping(
            com.example.platform.timeline.semantics.temporal.TemporalMapping mapping) {
        return switch (mapping) {
            case com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping rate ->
                    "constant(" + rate.rate().numerator() + "/" + rate.rate().denominator()
                            + "," + rate.direction() + ")";
            case com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping freeze ->
                    "freeze(" + freeze.sourcePosition() + ")";
        };
    }
}
