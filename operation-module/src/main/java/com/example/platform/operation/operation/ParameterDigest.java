package com.example.platform.operation.operation;

import com.example.platform.operation.operation.OperationDefinitionVersion;

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
        if (p instanceof OperationParameters.AddMediaClipParameters a) {
            var binding = a.sourceBinding();
            return "media-clip(" + a.trackId() + "," + a.clipId().value()
                    + ",sourceBinding="
                    + com.example.platform.timeline.semantics.clip
                            .TimelineSourceBindingCanonicalSemantics.encode(binding)
                    + ",placement=" + a.placement().start() + ".." + a.placement().end()
                    + ",mapping=" + temporalMapping(a.temporalMapping()) + ")";
        }
        if (p instanceof OperationParameters.NoParameters) {
            return "none";
        }
        if (p instanceof OperationParameters.MoveParameters m) {
            return "move(" + m.delta() + ",absolute=" + m.absolute() + ")";
        }
        if (p instanceof OperationParameters.TrimParameters t) {
            return "trim(" + t.edge() + "," + t.delta() + ")";
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
        // ROADMAP_19 / P2-5.5: nine typed Text operations. Value-based components
        // (records/classes with value equality) yield a deterministic parameter
        // serialization; the text plan digest itself is owned by TextOperationPlanner.
        if (p instanceof OperationParameters.AddTextElementParameters
                || p instanceof OperationParameters.RemoveTextElementParameters
                || p instanceof OperationParameters.ReplaceTextContentParameters
                || p instanceof OperationParameters.SetTextStyleRangeParameters
                || p instanceof OperationParameters.SetParagraphStyleParameters
                || p instanceof OperationParameters.SetFontSelectionParameters
                || p instanceof OperationParameters.SetFontFallbackPolicyParameters
                || p instanceof OperationParameters.SetVariableFontAxisParameters
                || p instanceof OperationParameters.SetTextLayoutParameters) {
            return "text(" + p.getClass().getSimpleName() + "," + Integer.toHexString(p.hashCode()) + ")";
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
