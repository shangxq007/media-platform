package com.example.platform.fonttext.typography;

import java.util.BitSet;
import java.util.List;
import java.util.regex.Pattern;

/** Explicit immutable profile pin on a half-open Unicode scalar range in the cue text. */
public record SubtitleFontRun(int start, int end, String fontProfile) {
    public static final int MAX_RUNS = 64;
    private static final Pattern CLUSTER = Pattern.compile("\\X");

    public SubtitleFontRun {
        if (start < 0 || start >= end || end > 384) throw new IllegalArgumentException("Invalid subtitle font range");
        if (fontProfile == null) throw new IllegalArgumentException("Missing inline subtitle font profile");
        SubtitleBlockLayout.resolveProfile(fontProfile);
    }

    public static void validate(String text, List<SubtitleFontRun> runs) {
        if (runs.isEmpty()) return;
        if (runs.size() > MAX_RUNS) throw new IllegalArgumentException("Too many subtitle font ranges");
        var boundaries = new BitSet();
        boundaries.set(0);
        var clusters = CLUSTER.matcher(text);
        int scalar = 0;
        while (clusters.find()) {
            scalar += text.codePointCount(clusters.start(), clusters.end());
            boundaries.set(scalar);
        }
        int last = 0;
        for (var run : runs) {
            if (run == null || run.start < last || !boundaries.get(run.start) || !boundaries.get(run.end)) {
                throw new IllegalArgumentException("Font ranges must be ordered, disjoint and aligned to whole graphemes");
            }
            last = run.end;
        }
    }

    public String canonical() {
        return start + ":" + end + ":" + fontProfile;
    }
}
