package com.example.platform.shared.time;
import com.example.platform.shared.time.MediaTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaTimeTest {

    @Test
    @DisplayName("ZERO is canonical with ticks=0, timeScale=1")
    void zeroIsCanonical() {
        assertEquals(0, MediaTime.ZERO.ticks());
        assertEquals(1, MediaTime.ZERO.timeScale());
    }

    @Test
    @DisplayName("Canonical reduction: 4/8 reduces to 1/2")
    void canonicalReduction() {
        MediaTime t = MediaTime.ofTicks(4, 8);
        assertEquals(1, t.ticks());
        assertEquals(2, t.timeScale());
    }

    @Test
    @DisplayName("Addition: 1/2 + 1/3 = 5/6")
    void addition() {
        MediaTime a = MediaTime.ofRational(1, 2);
        MediaTime b = MediaTime.ofRational(1, 3);
        MediaTime result = a.add(b);
        assertEquals(5, result.ticks());
        assertEquals(6, result.timeScale());
    }

    @Test
    @DisplayName("Subtraction: 3/4 - 1/4 = 1/2")
    void subtraction() {
        MediaTime a = MediaTime.ofRational(3, 4);
        MediaTime b = MediaTime.ofRational(1, 4);
        MediaTime result = a.subtract(b);
        assertEquals(1, result.ticks());
        assertEquals(2, result.timeScale());
    }

    @Test
    @DisplayName("Multiply by rational: 1/2 * 2/1 = 1/1")
    void multiplyRational() {
        MediaTime t = MediaTime.ofRational(1, 2);
        MediaTime result = t.multiplyRational(2, 1);
        assertEquals(1, result.ticks());
        assertEquals(1, result.timeScale());
    }

    @Test
    @DisplayName("Comparison: 1/2 < 3/4")
    void comparison() {
        MediaTime half = MediaTime.ofRational(1, 2);
        MediaTime threeFourths = MediaTime.ofRational(3, 4);
        assertTrue(half.isLessThan(threeFourths));
        assertTrue(threeFourths.isGreaterThan(half));
    }

    @Test
    @DisplayName("Equality: different representations of same value")
    void equality() {
        MediaTime a = MediaTime.ofRational(1, 2);
        MediaTime b = MediaTime.ofRational(2, 4);
        assertTrue(a.isEqualTo(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("Negative timeScale is rejected")
    void invalidTimeScale() {
        assertThrows(IllegalArgumentException.class, () -> MediaTime.ofTicks(1, 0));
        assertThrows(IllegalArgumentException.class, () -> MediaTime.ofTicks(1, -1));
    }

    @Test
    @DisplayName("Negative ticks are rejected")
    void invalidTicks() {
        assertThrows(IllegalArgumentException.class, () -> MediaTime.ofTicks(-1, 100));
    }

    @Test
    @DisplayName("ofNanos produces correct rational")
    void ofNanos() {
        MediaTime t = MediaTime.ofNanos(500_000_000);
        assertEquals(1, t.ticks());
        assertEquals(2, t.timeScale());
    }

    @Test
    @DisplayName("ofMicros produces correct rational")
    void ofMicros() {
        MediaTime t = MediaTime.ofMicros(250_000);
        assertEquals(1, t.ticks());
        assertEquals(4, t.timeScale());
    }

    @Test
    @DisplayName("ofFrames with 24fps produces correct time")
    void ofFrames() {
        // 12 frames at 24/1 fps = 12/24 = 1/2
        MediaTime t = MediaTime.ofFrames(12, 24, 1);
        assertEquals(1, t.ticks());
        assertEquals(2, t.timeScale());
    }

    @Test
    @DisplayName("min/max return correct values")
    void minMax() {
        MediaTime a = MediaTime.ofRational(1, 4);
        MediaTime b = MediaTime.ofRational(3, 4);
        assertEquals(a, a.min(b));
        assertEquals(b, a.max(b));
    }

    @Test
    @DisplayName("Deterministic serialization: same value, same toString")
    void deterministicToString() {
        MediaTime t1 = MediaTime.ofRational(3, 4);
        MediaTime t2 = MediaTime.ofRational(6, 8);
        assertEquals(t1.toString(), t2.toString());
    }
}
