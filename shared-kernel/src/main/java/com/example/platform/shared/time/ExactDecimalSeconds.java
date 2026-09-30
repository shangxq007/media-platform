package com.example.platform.shared.time;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exact decimal-seconds -> {@link MediaTime} decoder
 * (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b, F-Q1).
 *
 * <p>Transport time values (e.g. a JSON {@code timestampSeconds} number) must
 * never become a floating-point authority. This helper decodes the exact
 * decimal <em>string</em> representation into an exact rational
 * {@link MediaTime}, mirroring the repository precedent
 * ({@code FfprobeMediaProbeNormalizer.decimalToRational}). Callers must obtain
 * the decimal string from a decimal-preserving transport decode (e.g. Jackson
 * {@code BigDecimal}/{@code DecimalNode}), never from {@code doubleValue()}.</p>
 *
 * <p>Grammar: {@code -?\d+(\.\d+)?}. Non-finite, negative, blank, exponent or
 * otherwise unparsable input fails closed. No {@code double} is ever used.</p>
 */
public final class ExactDecimalSeconds {

    private static final Pattern DECIMAL = Pattern.compile("^(-?)(\\d+)(?:\\.(\\d+))?$");
    private static final BigInteger TEN = BigInteger.TEN;

    private ExactDecimalSeconds() {
    }

    /**
     * Decodes an exact non-negative decimal-seconds string into a canonical
     * {@link MediaTime}.
     *
     * @throws IllegalArgumentException when the value is null/blank, negative,
     *         malformed, or not representable as an exact long-rational time
     */
    public static MediaTime toMediaTime(String decimalSeconds) {
        Objects.requireNonNull(decimalSeconds, "decimalSeconds");
        String trimmed = decimalSeconds.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("decimal seconds must not be blank");
        }
        Matcher matcher = DECIMAL.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "invalid exact decimal seconds (expected -?\\d+(\\.\\d+)?): " + decimalSeconds);
        }
        boolean negative = "-".equals(matcher.group(1));
        if (negative) {
            throw new IllegalArgumentException(
                    "decimal seconds must be non-negative: " + decimalSeconds);
        }
        String integerPart = matcher.group(2);
        String fractionPart = matcher.group(3);

        BigInteger denominator = BigInteger.ONE;
        BigInteger numerator = new BigInteger(integerPart);
        if (fractionPart != null && !fractionPart.isEmpty()) {
            denominator = TEN.pow(fractionPart.length());
            numerator = numerator.multiply(denominator).add(new BigInteger(fractionPart));
        }
        BigInteger gcd = numerator.gcd(denominator);
        BigInteger reducedNumerator = numerator.divide(gcd);
        BigInteger reducedDenominator = denominator.divide(gcd);
        final long num;
        final long den;
        try {
            num = reducedNumerator.longValueExact();
            den = reducedDenominator.longValueExact();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "decimal seconds not representable as an exact rational: " + decimalSeconds,
                    overflow);
        }
        return MediaTime.ofRational(num, den);
    }
}
