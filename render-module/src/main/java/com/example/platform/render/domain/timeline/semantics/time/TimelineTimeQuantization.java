package com.example.platform.render.domain.timeline.semantics.time;

/**
 * SINGLE canonical time-quantization policy authority for the Timeline merge
 * pipeline (C1-CRR2 frozen contract).
 *
 * <p>The canonical persisted Timeline revision payload is frame-based
 * (integer frames @ integer fps, {@code rate{num,den}} with den = 1 — the only
 * form the canonical save machinery emits and the E1b gate adapter accepts).
 * The semantic merge model ({@code CanonicalTimelineSnapshot}) is integer
 * milliseconds. This class defines the ONE paired, deterministic, lossless
 * quantization used on both boundaries:</p>
 *
 * <pre>
 *   frames --[framesToMillis]--> ms --[millisToFrame]--> frames
 * </pre>
 *
 * <p>Both conversions use round-half-up (nearest), NOT floor. The pair is
 * mathematically inverse over the supported domain (integer fps with
 * den = 1; non-negative values enforced by {@link MediaTime} and the gate):
 * {@code decode(encode(x)) == x} for all supported frames — proven
 * exhaustively (0..100000) for fps 24/25/30/50/60 and by property test for
 * the supported domain (see C1-CRR2 regression).</p>
 *
 * <p>Rounding policy (frozen):</p>
 * <ul>
 *   <li>mode: round-half-up (ties away from zero); ties do not occur in the
 *       supported domain (no supported integer fps yields x.5 ms), but the
 *       policy is explicit</li>
 *   <li>negatives: domain is non-negative ({@link MediaTime} rejects negative
 *       ticks; the gate rejects negative frames); behavior for negative inputs
 *       is defined as {@link Math#floorDiv} half-up — never relied upon by the
 *       canonical domain</li>
 *   <li>overflow: {@code Math.multiplyExact} propagates as the existing
 *       {@code MediaTime.ofFrames} contract; durations are bounded by
 *       {@code Long.MAX_VALUE / 1000}</li>
 *   <li>duration policy: duration quantized with the same half-up rule as
 *       start, so {@code end = start + duration} never regresses below the
 *       true frame boundary</li>
 * </ul>
 *
 * <p>There is intentionally only ONE quantization implementation in the merge
 * pipeline ({@code TIME_ROUNDING_AUTHORITIES = 1}). No other class may
 * implement frame/ms rounding for the canonical merge path.</p>
 */
public final class TimelineTimeQuantization {

    private TimelineTimeQuantization() {
    }

    /** 1000 ms per second. */
    static final long MILLIS_PER_SECOND = 1000L;

    /**
     * Exact rational {@link MediaTime} (ticks/timeScale seconds) -> nearest
     * millisecond, round-half-up.
     *
     * <p>Mathematically {@code roundHalfUp(ticks * 1000 / timeScale)}. The
     * half-up tie term is {@code timeScale / 2}; for odd timeScale this is
     * exactly the half-up definition and ties do not occur in the supported
     * domain.</p>
     */
    public static long mediaTimeToMillis(MediaTime time) {
        if (time == null) {
            return 0L;
        }
        long timeScale = time.timeScale();
        if (timeScale <= 0) {
            throw new IllegalArgumentException("timeScale must be > 0: " + timeScale);
        }
        long ticks = time.ticks();
        if (ticks < 0) {
            // Domain is non-negative; keep a well-defined half-up result anyway.
            return Math.floorDiv(ticks * MILLIS_PER_SECOND + timeScale / 2, timeScale);
        }
        // (ticks*1000 + timeScale/2) / timeScale — round half-up, no overflow
        // for the canonical domain (ticks bounded by MediaTime construction).
        return (ticks * MILLIS_PER_SECOND + timeScale / 2) / timeScale;
    }

    /**
     * Milliseconds -> nearest frame at the given integer fps, round-half-up.
     *
     * <p>Mathematically {@code roundHalfUp(ms * fps / 1000)}. This is the
     * exact inverse of {@link #mediaTimeToMillis(MediaTime)} over the
     * supported domain: for a MediaTime of {@code f/fps} seconds the pair
     * satisfies {@code millisToFrame(mediaTimeToMillis(mt), fps) == f}.</p>
     */
    public static long millisToFrame(long millis, int fps) {
        if (fps <= 0) {
            throw new IllegalArgumentException("fps must be > 0: " + fps);
        }
        if (millis < 0) {
            return Math.floorDiv(millis * fps + MILLIS_PER_SECOND / 2, MILLIS_PER_SECOND);
        }
        // (ms*fps + 500) / 1000 — round half-up.
        return (millis * fps + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND;
    }

    /**
     * Direct frames -> nearest milliseconds for a given integer fps
     * (used by tests and property proofs to build expected values).
     */
    public static long frameToMillis(long frame, int fps) {
        if (fps <= 0) {
            throw new IllegalArgumentException("fps must be > 0: " + fps);
        }
        if (frame < 0) {
            return Math.floorDiv(frame * MILLIS_PER_SECOND + fps / 2, fps);
        }
        return (frame * MILLIS_PER_SECOND + fps / 2) / fps;
    }
}
