package com.example.platform.timeline.canonicalmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SIXTH CORRECTION — S1 (whole-Effect fingerprint collision) and
 * S2 (single deep canonical Effect representation) closure tests.
 */
class EffectCanonicalSemanticsSixthCorrectionTest {

    private static TimelineClipEffect effect(String id, String key, Map<String, Object> params) {
        return new TimelineClipEffect(id, key, params);
    }

    // ── S1-T1: adversarial id/effectKey delimiter collision → different ──
    @Test
    void s1t1AdversarialIdKeyCollision() {
        TimelineClipEffect a = effect("a;key=b", "c", Map.of());
        TimelineClipEffect b = effect("a", "b;key=c", Map.of());
        String fa = a.semanticFingerprint();
        String fb = b.semanticFingerprint();
        assertNotEquals(fa, fb,
                "S1-T1: id=\"a;key=b\",key=\"c\" vs id=\"a\",key=\"b;key=c\" must NOT collide");
    }

    // ── S1-T2: id containing delimiters/quotes/backslash → deterministic, no collision ──
    @Test
    void s1t2IdDelimiterCharsSafe() {
        TimelineClipEffect a = effect("x;y=z,w\"q\\b", "k", Map.of("p", 1));
        TimelineClipEffect b = effect("x", "k", Map.of("p", 1));
        // Deterministic: repeated encoding identical; distinct from plain id.
        assertEquals(a.semanticFingerprint(), a.semanticFingerprint());
        assertNotEquals(a.semanticFingerprint(), b.semanticFingerprint());
        // JSON framing escapes quotes/backslash while preserving the content:
        // the id bytes must be recoverable (decode round-trip preserves them).
        TimelineClipEffect roundTripped = EffectCanonicalSemantics.decodeEffects(
                EffectCanonicalSemantics.encodeEffects(List.of(a))).get(0);
        assertEquals("x;y=z,w\"q\\b", roundTripped.id(),
                "S1-T2: id must survive verbatim through canonical encode/decode");
    }

    // ── S1-T3: effectKey containing delimiters/quotes/backslash → safe ──
    @Test
    void s1t3EffectKeyDelimiterCharsSafe() {
        TimelineClipEffect a = effect("id", "k;x=y,z\"q\\b", Map.of("p", 1));
        TimelineClipEffect b = effect("id", "k", Map.of("p", 1));
        assertEquals(a.semanticFingerprint(), a.semanticFingerprint());
        assertNotEquals(a.semanticFingerprint(), b.semanticFingerprint());
    }

    // ── S1-T4: same state → same fingerprint ──
    @Test
    void s1t4SameStateSameFingerprint() {
        assertEquals(effect("fx1", "blur", Map.of("radius", 3)).semanticFingerprint(),
                effect("fx1", "blur", Map.of("radius", 3)).semanticFingerprint());
    }

    // ── S1-T5/T6/T7: id-only / key-only / parameter-only changes → different ──
    @Test
    void s1t5IdOnlyChangeDiffers() {
        assertNotEquals(effect("fx1", "blur", Map.of("r", 1)).semanticFingerprint(),
                effect("fx2", "blur", Map.of("r", 1)).semanticFingerprint());
    }

    @Test
    void s1t6KeyOnlyChangeDiffers() {
        assertNotEquals(effect("fx1", "blur", Map.of("r", 1)).semanticFingerprint(),
                effect("fx1", "wipe", Map.of("r", 1)).semanticFingerprint());
    }

    @Test
    void s1t7ParameterOnlyChangeDiffers() {
        assertNotEquals(effect("fx1", "blur", Map.of("r", 1)).semanticFingerprint(),
                effect("fx1", "blur", Map.of("r", 2)).semanticFingerprint());
    }

    // ── S1-T8: nested map insertion order → same fingerprint ──
    @Test
    void s1t8NestedOrderSameFingerprint() {
        Map<String, Object> n1 = new LinkedHashMap<>();
        n1.put("z", 2); n1.put("a", 1);
        Map<String, Object> n2 = new LinkedHashMap<>();
        n2.put("a", 1); n2.put("z", 2);
        assertEquals(effect("fx1", "blur", Map.of("nested", n1)).semanticFingerprint(),
                effect("fx1", "blur", Map.of("nested", n2)).semanticFingerprint());
    }

    // ── S2-T1: top-level parameter map order → byte-identical encodeEffects ──
    @Test
    void s2t1TopLevelOrderByteIdentical() {
        Map<String, Object> m1 = new LinkedHashMap<>();
        m1.put("z", 2); m1.put("a", 1);
        Map<String, Object> m2 = new LinkedHashMap<>();
        m2.put("a", 1); m2.put("z", 2);
        assertEquals(EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", m1))),
                EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", m2))));
    }

    // ── S2-T2: nested map order → byte-identical encodeEffects ──
    @Test
    void s2t2NestedOrderByteIdentical() {
        Map<String, Object> n1 = new LinkedHashMap<>();
        n1.put("outer", new LinkedHashMap<>(Map.of("z", 2, "a", 1)));
        Map<String, Object> n2 = new LinkedHashMap<>();
        n2.put("outer", new LinkedHashMap<>(Map.of("a", 1, "z", 2)));
        assertEquals(EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", n1))),
                EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", n2))));
    }

    // ── S2-T3: deeply nested maps inside Lists → deterministic ──
    @Test
    void s2t3MapInsideListDeterministic() {
        Map<String, Object> p1 = Map.of("l", List.of(Map.of("z", 2, "a", 1)));
        Map<String, Object> p2 = Map.of("l", List.of(Map.of("a", 1, "z", 2)));
        assertEquals(EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", p1))),
                EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", p2))));
    }

    // ── S2-T4: list order change → encoding differs ──
    @Test
    void s2t4ListOrderSemantic() {
        assertNotEquals(EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", Map.of("l", List.of(1, 2))))),
                EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", Map.of("l", List.of(2, 1))))));
    }

    // ── S2-T5: integer 9 vs string "9" → encoding differs ──
    @Test
    void s2t5IntegerVsStringDistinct() {
        assertNotEquals(EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", Map.of("n", 9)))),
                EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", Map.of("n", "9")))));
        assertNotEquals(EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", Map.of("b", true)))),
                EffectCanonicalSemantics.encodeEffects(List.of(effect("fx1", "k", Map.of("b", "true")))));
    }

    // ── S2-T6: canonicalEffectValue drives BOTH fingerprint and encodeEffects ──
    @Test
    void s2t6SingleCanonicalValuePath() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("z", 2); params.put("a", 1);
        TimelineClipEffect e = effect("fx1", "blur", params);
        // fingerprint embeds the same canonical value as encodeEffects elements
        Map<String, Object> canonical = EffectCanonicalSemantics.canonicalEffectValue(e);
        assertEquals("fx1", canonical.get("id"));
        assertEquals("blur", canonical.get("effectKey"));
        // deepSorted: the canonical value's parameters are key-sorted TreeMap
        assertTrue(canonical.get("parameters") instanceof java.util.TreeMap,
                "S2-T6: canonical parameters must be deep-sorted TreeMap");
        // Behavior proof: manual re-serialization of the canonical value equals fingerprint
        try {
            String direct = com.example.platform.timeline.app.InternalTimelineJson.mapper()
                    .writeValueAsString(canonical);
            assertEquals(e.semanticFingerprint(), direct,
                    "S2-T6: fingerprint must be exactly the canonical Effect value encoding");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    // ── S2 extra: encode→decode→encode byte-stable ──
    @Test
    void s2EncodeDecodeEncodeStable() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("radius", 9);
        params.put("label", "9");
        params.put("comma", "a,b");
        params.put("equals", "a=b");
        params.put("nested", Map.of("z", 2, "a", 1));
        params.put("list", List.of(1, "2", true));
        TimelineClipEffect e = effect("fx1", "blur", params);
        String enc1 = EffectCanonicalSemantics.encodeEffects(List.of(e));
        List<TimelineClipEffect> decoded = EffectCanonicalSemantics.decodeEffects(enc1);
        String enc2 = EffectCanonicalSemantics.encodeEffects(decoded);
        assertEquals(enc1, enc2, "S2: encode→decode→encode must be byte-stable");
        assertEquals(Integer.valueOf(9), decoded.get(0).parameters().get("radius"));
        assertEquals("9", decoded.get(0).parameters().get("label"));
    }

    // ── S1 extra: fingerprint byte-stable under full round trip ──
    @Test
    void s1FingerprintStableAcrossEncodeDecode() {
        TimelineClipEffect e = effect("a;key=b", "c;x=y", Map.of("p", 1));
        List<TimelineClipEffect> decoded = EffectCanonicalSemantics.decodeEffects(
                EffectCanonicalSemantics.encodeEffects(List.of(e)));
        assertEquals(e.semanticFingerprint(), decoded.get(0).semanticFingerprint(),
                "S1: fingerprint must survive encode→decode with delimiter-bearing id/key");
    }
}
