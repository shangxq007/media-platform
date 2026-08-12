package com.example.platform.render.domain.timeline.semantics.time;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * C1-CNM1-CR1: canonical cross-language FrameRate wire codec.
 *
 * <p>Single authoritative wire-domain parser for the canonical
 * {@code rate{num,den}} JSON contract (Option A — bounded numeric).
 * All production consumers of the same canonical rate contract
 * (InternalTimelineCandidateAdapter, TimelineScriptParser, legacy int-fps
 * projection readers) must parse through this codec so that:
 *
 * <ul>
 *   <li>INVALID canonical rate input is REJECTED, never defaulted;</li>
 *   <li>MISSING (entire rate node absent) follows the caller's explicit
 *       optional/default policy — it is never conflated with invalid;</li>
 *   <li>validation precedes every bounded narrowing (no {@code asInt} of a
 *       value that may exceed int32 before range check);</li>
 *   <li>all parsers of the same contract accept the same value domain.</li>
 * </ul>
 *
 * <p>Wire domain (frozen): numerator and denominator are exact JSON
 * integers within signed int32, subject to FrameRate canonical rules
 * (numerator &gt; 0, denominator &gt; 0, gcd normalization). Because int32
 * ⊂ JS safe-integer range, the cross-language contract is automatically
 * JS-safe. FrameRate's internal BigInteger capability does NOT expand the
 * wire domain.</p>
 */
public final class CanonicalFrameRateCodec {

    private CanonicalFrameRateCodec() {
    }

    /**
     * Parses a canonical rate node.
     *
     * @param rateNode     the rate JSON node (may be missing/null)
     * @param allowMissing when true, a fully absent rate node yields
     *                     {@link #DEFAULT_RATE}; when false, absence is
     *                     treated as invalid
     * @return the exact FrameRate
     * @throws InvalidCanonicalRateException when the rate is present but
     *         malformed, non-integral, out of the int32 wire domain, or has
     *         an invalid denominator — NEVER silently defaulted
     */
    public static FrameRate parse(JsonNode rateNode, boolean allowMissing) {
        if (rateNode == null || rateNode.isMissingNode() || rateNode.isNull()) {
            if (allowMissing) {
                return DEFAULT_RATE;
            }
            throw invalid("rate is absent");
        }
        if (!rateNode.isObject()) {
            throw invalid("rate must be a JSON object, got " + rateNode.getNodeType());
        }
        JsonNode numNode = rateNode.get("num");
        JsonNode denNode = rateNode.get("den");
        if (numNode == null || denNode == null) {
            throw invalid("rate object must contain both num and den");
        }
        long num = readBoundedLong(numNode, "num");
        long den = readBoundedLong(denNode, "den");
        if (den == 0) {
            throw invalid("denominator must not be zero");
        }
        try {
            return FrameRate.of(num, den);
        } catch (IllegalArgumentException e) {
            // FrameRate requires positive num and den after domain checks;
            // zero/negative values are invalid at the wire boundary.
            throw invalid(e.getMessage());
        }
    }

    /**
     * Reads an exact JSON integer, verifies it is integral and within signed
     * int32, and only then narrows to long. Rejects decimal/exponent/string/
     * boolean/huge values.
     */
    private static long readBoundedLong(JsonNode node, String field) {
        if (!node.isIntegralNumber()) {
            throw invalid(field + " must be an exact integer, got " + node.getNodeType());
        }
        // BigInteger JSON numbers (>= 2^63) are rejected before narrowing.
        if (node.isBigInteger()) {
            java.math.BigInteger v = node.bigIntegerValue();
            if (v.compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                    || v.compareTo(java.math.BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                throw invalid(field + " out of int32 wire domain: " + v);
            }
        }
        long v = node.longValue(); // safe: integral number within long after BigInteger check
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw invalid(field + " out of int32 wire domain: " + v);
        }
        return v;
    }

    private static InvalidCanonicalRateException invalid(String message) {
        return new InvalidCanonicalRateException(message);
    }

    /** Default rate used only for explicitly-optional missing rate state. */
    public static final FrameRate DEFAULT_RATE = FrameRate.of(30, 1);

    /**
     * Thrown for present-but-invalid canonical rate input. Distinct from
     * absence; callers must never catch this and fall back to a default.
     */
    public static final class InvalidCanonicalRateException extends RuntimeException {
        public InvalidCanonicalRateException(String message) {
            super("Invalid canonical rate: " + message);
        }
    }
}
