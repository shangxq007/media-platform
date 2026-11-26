package com.example.platform.fonttext.typography;

import java.util.BitSet;
import java.util.regex.Pattern;

/** A half-open range in canonical Unicode scalars. */
public record SubtitleTextRange(int start, int end) {
    private static final Pattern CLUSTER = Pattern.compile("\\X");

    public SubtitleTextRange {
        if (start < 0 || start >= end || end > 384) {
            throw new IllegalArgumentException("Invalid subtitle text range");
        }
    }

    public void requireWholeGraphemes(String text) {
        var boundaries = boundaries(text);
        if (!boundaries.get(start) || !boundaries.get(end)) {
            throw new IllegalArgumentException("Subtitle selection must align to whole graphemes");
        }
    }

    static BitSet boundaries(String text) {
        var boundaries = new BitSet();
        boundaries.set(0);
        var clusters = CLUSTER.matcher(text);
        int scalar = 0;
        while (clusters.find()) {
            scalar += text.codePointCount(clusters.start(), clusters.end());
            boundaries.set(scalar);
        }
        return boundaries;
    }

    boolean contains(int scalar) {
        return start <= scalar && scalar < end;
    }
}
