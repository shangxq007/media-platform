package com.example.platform.render.ir;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Integer rational time model tests: construction, comparison, arithmetic, invariants.
 */
class TimeModelTest {

    @Test
    void zeroStartAccepted() {
        RationalTime t = RationalTime.of(0, 30000);
        assertTrue(t.isZero());
        assertFalse(t.isNegative());
    }

    @Test
    void negativeStartCreated() {
        // RationalTime does NOT prohibit negative values — validation does
        RationalTime t = RationalTime.of(-1, 30000);
        assertTrue(t.isNegative());
    }

    @Test
    void positiveDurationAccepted() {
        RationalTime t = RationalTime.of(90000, 30000);
        assertFalse(t.isZero());
        assertFalse(t.isNegative());
    }

    @Test
    void zeroDurationCreated() {
        RationalTime t = RationalTime.zero(30000);
        assertTrue(t.isZero());
    }

    @Test
    void negativeDurationCreated() {
        RationalTime t = RationalTime.of(-90000, 30000);
        assertTrue(t.isNegative());
    }

    @Test
    void zeroDenominatorRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            RationalTime.of(1, 0));
    }

    @Test
    void negativeDenominatorRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            RationalTime.of(1, -1));
    }

    @Test
    void overflowDetectedInDenominatorMultiply() {
        RationalTime t1 = RationalTime.of(1, Long.MAX_VALUE);
        RationalTime t2 = RationalTime.of(1, 2);
        // Multiply denominators: Long.MAX_VALUE * 2 overflows
        assertThrows(ArithmeticException.class, () -> t1.add(t2));
    }

    @Test
    void exactComparison() {
        RationalTime a = RationalTime.of(1, 2);
        RationalTime b = RationalTime.of(2, 4);
        assertEquals(0, a.compareTo(b));

        RationalTime c = RationalTime.of(3, 4);
        assertTrue(a.isLessThan(c));
        assertTrue(c.isGreaterThan(a));
    }

    @Test
    void exactAddition() {
        RationalTime a = RationalTime.of(1, 30000);
        RationalTime b = RationalTime.of(2, 30000);
        RationalTime sum = a.add(b);
        assertEquals(RationalTime.of(3, 30000), sum);
    }

    @Test
    void normalizationStability() {
        // 2/4 should normalize to 1/2
        RationalTime t = RationalTime.of(2, 4);
        assertEquals(RationalTime.of(1, 2), t);
        // Normalizing again should be stable
        assertEquals(t, RationalTime.of(1, 2));
    }

    @Test
    void endCalculation() {
        RationalTime start = RationalTime.of(0, 30000);
        RationalTime duration = RationalTime.of(90000, 30000);
        RationalTime end = start.end(duration);
        assertEquals(RationalTime.of(90000, 30000), end);
    }

    @Test
    void differentDenominatorComparison() {
        RationalTime a = RationalTime.of(1, 2);   // 0.5
        RationalTime b = RationalTime.of(15000, 30000); // 0.5
        assertEquals(0, a.compareTo(b));
        assertEquals(a, b);
    }

    @Test
    void hashCodeConsistency() {
        RationalTime a = RationalTime.of(2, 4);
        RationalTime b = RationalTime.of(1, 2);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
