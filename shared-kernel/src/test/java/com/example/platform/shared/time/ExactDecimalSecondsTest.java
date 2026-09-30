package com.example.platform.shared.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExactDecimalSecondsTest {

    @Test
    void decodesExactRationalsWithoutDoubleRoundTrip() {
        assertEquals(MediaTime.ofRational(3, 2), ExactDecimalSeconds.toMediaTime("1.5"));
        assertEquals(MediaTime.ofRational(1, 1000), ExactDecimalSeconds.toMediaTime("0.001"));
        assertEquals(MediaTime.ofRational(12, 1), ExactDecimalSeconds.toMediaTime("12"));
        assertEquals(MediaTime.ofRational(1, 10), ExactDecimalSeconds.toMediaTime("0.1"));
        assertEquals(MediaTime.ZERO, ExactDecimalSeconds.toMediaTime("0"));
        assertEquals(MediaTime.ZERO, ExactDecimalSeconds.toMediaTime("0.0"));
        assertEquals(MediaTime.ofRational(12345, 1000), ExactDecimalSeconds.toMediaTime("12.345"));
    }

    @Test
    void exactFractionIsNotTheDoubleApproximation() {
        // 0.1 as a double is not exactly 1/10; the decoder must produce the exact rational.
        assertEquals(MediaTime.ofTicks(1, 10), ExactDecimalSeconds.toMediaTime("0.1"));
        assertEquals(MediaTime.ofTicks(1, 10).toString(), ExactDecimalSeconds.toMediaTime("0.1").toString());
    }

    @Test
    void acceptsSurroundingWhitespace() {
        assertEquals(MediaTime.ofRational(3, 2), ExactDecimalSeconds.toMediaTime("  1.5 "));
    }

    @Test
    void failsClosedOnInvalidInput() {
        assertThrows(NullPointerException.class, () -> ExactDecimalSeconds.toMediaTime(null));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime(""));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime("   "));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime("-1.5"));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime("1e3"));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime("1."));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime(".5"));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime("1.2.3"));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime("+1"));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime("NaN"));
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime("Infinity"));
    }

    @Test
    void failsClosedOnPrecisionOverflow() {
        String tooManyDigits = "0." + "1".repeat(40);
        assertThrows(IllegalArgumentException.class, () -> ExactDecimalSeconds.toMediaTime(tooManyDigits));
    }
}
