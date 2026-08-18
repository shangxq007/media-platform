package com.example.platform.timeline.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineAutomationKeyframe;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineAutomationSnapshot;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineTransitionSnapshot;
import com.example.platform.timeline.semantics.automation.AutomationCanonicalSemantics;
import com.example.platform.timeline.semantics.transition.TransitionCanonicalSemantics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 3): delimiter
 * collision regressions — authored strings resembling old separators must
 * survive canonical encode → decode losslessly.
 */
class ComponentLocalSemanticAuthorityCollisionTest {

    @Test
    void transitionParametersWithDelimiterLikeValuesSurvive() {
        CanonicalTimelineTransitionSnapshot t = new CanonicalTimelineTransitionSnapshot(
                "tr-1", "def-1", "1.0", "clip-a", "clip-b", "VIDEO",
                MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES",
                // old delimiter grammar would split on ',' and '='
                Map.of("a,b=c", "x=y,z", "plain", "value", "semi;colon", "colon:value"));
        // lossless round trip through the canonical JSON authority
        var decoded = TransitionCanonicalSemantics.fromCanonicalValue(
                "tr-1", TransitionCanonicalSemantics.canonicalValue(t));
        assertEquals(t, decoded, "delimiter-like parameter keys/values must survive losslessly");
        // fingerprint deterministic + distinct from a modified twin
        assertEquals(TransitionCanonicalSemantics.semanticFingerprint(t),
                TransitionCanonicalSemantics.semanticFingerprint(t));
        CanonicalTimelineTransitionSnapshot t2 = new CanonicalTimelineTransitionSnapshot(
                "tr-1", "def-1", "1.0", "clip-a", "clip-b", "VIDEO",
                MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES",
                Map.of("a,b=c", "x=y,z", "plain", "changed"));
        assertTrue(!TransitionCanonicalSemantics.semanticFingerprint(t)
                .equals(TransitionCanonicalSemantics.semanticFingerprint(t2)));
    }

    @Test
    void automationAuthoredStringsWithDelimiterLikeValuesSurvive() {
        CanonicalTimelineAutomationSnapshot a = new CanonicalTimelineAutomationSnapshot(
                "auto-1", "clip-a", "effects.blur.radius", "float", "HOLD",
                List.of(new CanonicalTimelineAutomationKeyframe(
                        "kf-1", MediaTime.ofTicks(5, 30), 0.5, "LINEAR")));
        var decoded = AutomationCanonicalSemantics.fromCanonicalValue(
                "auto-1", AutomationCanonicalSemantics.canonicalValue(a));
        assertEquals(a, decoded, "automation canonical round trip must be lossless");
        assertEquals(AutomationCanonicalSemantics.semanticFingerprint(a),
                AutomationCanonicalSemantics.semanticFingerprint(a));
    }
}
