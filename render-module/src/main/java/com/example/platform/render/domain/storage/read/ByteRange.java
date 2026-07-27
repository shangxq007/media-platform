package com.example.platform.render.domain.storage.read;
import java.io.Serializable;
public record ByteRange(long startInclusive, long endInclusive) implements Serializable {
    public ByteRange {
        if (startInclusive < 0) throw new IllegalArgumentException("range start must be >= 0");
        if (endInclusive < startInclusive) throw new IllegalArgumentException("range end must be >= start");
    }
    public boolean isFullRange() { return startInclusive == 0 && endInclusive == Long.MAX_VALUE; }
}
