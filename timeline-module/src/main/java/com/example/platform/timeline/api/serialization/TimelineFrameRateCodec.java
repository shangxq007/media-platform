package com.example.platform.timeline.api.serialization;
import com.example.platform.shared.time.FrameRate;

import com.fasterxml.jackson.databind.JsonNode;

/** Timeline canonical wire adapter. Missing-rate policy is explicitly chosen by the Timeline schema caller.
 * Both adapters construct the same shared exact FrameRate value; neither defines alternative value semantics.
 * Wire components retain the accepted signed-int32 JSON bounds.
 */
public final class TimelineFrameRateCodec {

    private TimelineFrameRateCodec() {
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
