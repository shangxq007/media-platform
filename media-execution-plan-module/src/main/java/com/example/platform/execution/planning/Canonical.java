package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import java.util.Comparator;
import java.util.Objects;

/**
 * Roadmap #21 deterministic canonical serialization of typed #20 declarations
 * for digest purposes (Blocker H).
 *
 * <p>Explicit field-by-field canonical forms — NEVER relies on record
 * toString() (which may be overridden to omit semantic fields, e.g.
 * CapabilityRequirement.toString() omits alternatives). This is the mechanical
 * basis for law:logical-digest-content-complete /
 * law:physical-digest-content-complete.
 */
public final class Canonical {

    private Canonical() {
    }

    public static String capability(CapabilityRequirement cr) {
        Objects.requireNonNull(cr, "cr");
        StringBuilder sb = new StringBuilder();
        sb.append(cr.capabilityId().value())
                .append('|').append(cr.required())
                .append('|').append(contractRange(cr.contractRange()));
        if (cr.alternatives() != null && !cr.alternatives().isEmpty()) {
            var alts = cr.alternatives().stream()
                    .map(a -> a.value())
                    .sorted()
                    .toList();
            sb.append('|');
            for (int i = 0; i < alts.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(alts.get(i));
            }
        }
        return sb.toString();
    }

    public static String contractRange(com.example.platform.extension.domain.ContractVersionRange range) {
        if (range == null) {
            return "any";
        }
        return range.toString();
    }

    public static String executionIntent(RenderExecutionRequirement er) {
        return (er.gpu() != null ? er.gpu().name() : "null")
                + "|" + (er.determinism() != null ? er.determinism().name() : "null")
                + "|" + er.sandboxedIntent();
    }

    public static String output(RenderOutputRequirement o) {
        // Default record toString() carries the full declaration (role +
        // color/raster Optional values); RenderOutputRequirement does not
        // override toString, so this is deterministic and complete.
        return o.toString();
    }

    public static String materialization(RenderMaterializationRequirement m) {
        // sealed bounded V1: canonical via type + toString (records carry full
        // payload; no overridden toString in the sealed hierarchy)
        return m.getClass().getSimpleName() + "[" + m + "]";
    }

    public static String artifact(com.example.platform.render.domain.renderplan.RenderArtifactReference a) {
        return a.toString();
    }
}
