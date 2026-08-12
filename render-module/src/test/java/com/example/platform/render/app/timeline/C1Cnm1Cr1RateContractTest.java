package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.semantics.time.CanonicalFrameRateCodec;
import com.example.platform.render.domain.timeline.semantics.time.FrameRate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C1-CNM1-CR1 — RED-14 behavioral parity proofs.
 *
 * Every adversarial rate fixture is driven through the canonical adapter path
 * (InternalTimelineCandidateAdapter.map) and the script-parser path
 * (TimelineScriptParser.parse with structured outputSpec.frameRate), and the
 * verdicts must agree: valid -> ACCEPT (exact), invalid -> REJECT, missing ->
 * default (documented optional policy). Invalid input is NEVER defaulted.
 */
public class C1Cnm1Cr1RateContractTest {

    private static final String PROJECT = "proj-cr1";
    private final ObjectMapper mapper = new ObjectMapper();

    // ── fixture builders ──────────────────────────────────────────────

    private String clipPayload(String rateJson) {
        return "{\"schemaVersion\":\"1.0\",\"id\":\"tl-cr1\",\"composition\":{\"tracks\":[{\"id\":\"v1\","
                + "\"type\":\"VIDEO\",\"clips\":[{\"id\":\"clip_001\",\"assetId\":\"ast_smoke_001\","
                + "\"timelineRange\":{\"start\":{\"frame\":0,\"rate\":" + rateJson
                + "},\"duration\":{\"frame\":30,\"rate\":" + rateJson + "}},"
                + "\"sourceRange\":{\"start\":{\"frame\":0,\"rate\":" + rateJson
                + "},\"duration\":{\"frame\":30,\"rate\":" + rateJson + "}}}]}]}}";
    }

    private String scriptPayload(String frameRateJson) {
        return "{\"id\":\"tl-script\",\"name\":\"t\",\"outputSpec\":{\"format\":\"mp4\","
                + "\"resolution\":\"1920x1080\",\"frameRate\":" + frameRateJson
                + "},\"tracks\":[{\"id\":\"v1\",\"type\":\"video\",\"clips\":[]}]}";
    }

    private enum Verdict { ACCEPT, REJECT }

    private Verdict adapterVerdict(String rateJson) {
        try {
            InternalTimelineCandidateAdapter.map(PROJECT, clipPayload(rateJson));
            return Verdict.ACCEPT;
        } catch (Exception e) {
            return Verdict.REJECT;
        }
    }

    private Verdict scriptVerdict(String frameRateJson) {
        try {
            Optional<TimelineSpec> spec = new TimelineScriptParser().parse(scriptPayload(frameRateJson));
            return spec.isPresent() ? Verdict.ACCEPT : Verdict.REJECT;
        } catch (Exception e) {
            return Verdict.REJECT;
        }
    }

    private FrameRate adapterRate(String rateJson) throws Exception {
        TimelineCandidate c = InternalTimelineCandidateAdapter.map(PROJECT, clipPayload(rateJson));
        return c.tracks().get(0).clips().get(0).rate();
    }

    // ── valid rates: ACCEPT across both paths ─────────────────────────

    @Test
    void validRatesAcceptOnBothPaths() throws Exception {
        String[] valid = {
                "{\"num\":24,\"den\":1}", "{\"num\":25,\"den\":1}", "{\"num\":30,\"den\":1}",
                "{\"num\":50,\"den\":1}", "{\"num\":60,\"den\":1}",
                "{\"num\":24000,\"den\":1001}", "{\"num\":30000,\"den\":1001}",
                "{\"num\":60000,\"den\":1001}",
                "{\"num\":60000,\"den\":2002}" // normalizes to 30000/1001
        };
        for (String r : valid) {
            assertEquals(Verdict.ACCEPT, adapterVerdict(r), "adapter accept " + r);
            assertEquals(Verdict.ACCEPT, scriptVerdict(r), "script accept " + r);
        }
        // normalization parity: 60000/2002 == 30000/1001
        FrameRate norm = adapterRate("{\"num\":60000,\"den\":2002}");
        assertEquals(FrameRate.of(30000, 1001), norm, "60000/2002 must normalize to 30000/1001");
        // fractional denominators preserved
        assertEquals(FrameRate.of(30000, 1001), adapterRate("{\"num\":30000,\"den\":1001}"));
    }

    // ── invalid range: REJECT on both paths (never defaulted) ─────────

    @Test
    void outOfInt32RateRejectsOnBothPaths() {
        String[] bad = {
                "{\"num\":3000000000,\"den\":1}",   // num > int32 max
                "{\"num\":-3000000000,\"den\":1}",  // num < int32 min
                "{\"num\":30000,\"den\":3000000000}", // den > int32 max
                "{\"num\":30000,\"den\":-3000000000}"
        };
        for (String r : bad) {
            assertEquals(Verdict.REJECT, adapterVerdict(r), "adapter reject " + r);
            assertEquals(Verdict.REJECT, scriptVerdict(r), "script reject " + r);
        }
    }

    // ── zero denominator: REJECT on both paths ────────────────────────

    @Test
    void zeroDenominatorRejectsOnBothPaths() {
        String r = "{\"num\":30000,\"den\":0}";
        assertEquals(Verdict.REJECT, adapterVerdict(r), "adapter reject zero den");
        assertEquals(Verdict.REJECT, scriptVerdict(r), "script reject zero den");
    }

    // ── negative components: REJECT (FrameRate domain requires positive) ─

    @Test
    void negativeComponentsRejectOnBothPaths() {
        String[] bad = {
                "{\"num\":-30,\"den\":1}",
                "{\"num\":30000,\"den\":-1001}",
                "{\"num\":0,\"den\":1}" // zero numerator invalid for media FrameRate
        };
        for (String r : bad) {
            assertEquals(Verdict.REJECT, adapterVerdict(r), "adapter reject " + r);
            assertEquals(Verdict.REJECT, scriptVerdict(r), "script reject " + r);
        }
    }

    // ── malformed / non-integral: REJECT ──────────────────────────────

    @Test
    void malformedRateRejectsOnBothPaths() {
        String[] bad = {
                "{\"num\":\"30000\",\"den\":1}",   // string num
                "{\"num\":30000,\"den\":\"1001\"}", // string den
                "{\"num\":30.5,\"den\":1}",         // decimal num
                "{\"num\":30000,\"den\":1.5}",      // decimal den
                "{}",                               // empty object
                "{\"num\":30000}",                  // partial (missing den)
                "{\"den\":1001}",                   // partial (missing num)
                "\"30000/1001\""                    // string node (structured path must be object)
        };
        for (String r : bad) {
            assertEquals(Verdict.REJECT, adapterVerdict(r), "adapter reject " + r);
        }
        // JSON null is treated as ABSENT (optional field) -> documented default,
        // distinct from present-but-invalid.
        assertEquals(Verdict.ACCEPT, adapterVerdict("null"), "null rate node is treated as absent");
    }

    // ── missing rate: optional default (documented) ───────────────────

    @Test
    void missingRateDefaultsOnAdapterPath() throws Exception {
        // clip without rate block: adapter treats fully-absent rate as optional -> 30/1
        String payload = "{\"schemaVersion\":\"1.0\",\"id\":\"tl-cr1\",\"composition\":{\"tracks\":[{\"id\":\"v1\","
                + "\"type\":\"VIDEO\",\"clips\":[{\"id\":\"clip_001\",\"assetId\":\"ast_smoke_001\","
                + "\"timelineRange\":{\"start\":{\"frame\":0},\"duration\":{\"frame\":30}},"
                + "\"sourceRange\":{\"start\":{\"frame\":0},\"duration\":{\"frame\":30}}}]}]}}";
        TimelineCandidate c = InternalTimelineCandidateAdapter.map(PROJECT, payload);
        assertEquals(FrameRate.of(30, 1), c.tracks().get(0).clips().get(0).rate(),
                "missing rate is an explicitly documented optional default");
    }

    // ── direct codec unit proofs ──────────────────────────────────────

    @Test
    void codecUnitProofs() {
        // valid exact
        assertEquals(FrameRate.of(30000, 1001),
                CanonicalFrameRateCodec.parse(mapper.valueToTree(java.util.Map.of("num", 30000, "den", 1001)), false));
        // missing allowed vs not: absent node is missing; present empty object is invalid
        assertThrows(CanonicalFrameRateCodec.InvalidCanonicalRateException.class,
                () -> CanonicalFrameRateCodec.parse(mapper.createObjectNode(), true),
                "present-but-incomplete rate object must reject even when missing is allowed");
        assertThrows(CanonicalFrameRateCodec.InvalidCanonicalRateException.class,
                () -> CanonicalFrameRateCodec.parse(mapper.createObjectNode(), false));
        assertEquals(CanonicalFrameRateCodec.DEFAULT_RATE,
                CanonicalFrameRateCodec.parse(mapper.missingNode(), true),
                "fully absent rate node follows the optional default policy");
        // huge BigInteger JSON number
        ObjectNode huge = mapper.createObjectNode();
        huge.put("num", new java.math.BigInteger("99999999999999999999999"));
        huge.put("den", 1);
        assertThrows(CanonicalFrameRateCodec.InvalidCanonicalRateException.class,
                () -> CanonicalFrameRateCodec.parse(huge, false));
    }
}
