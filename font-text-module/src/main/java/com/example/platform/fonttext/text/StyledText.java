package com.example.platform.fonttext.text;

import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextStyleRun;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP_19 (C17/C36): immutable canonical StyledText — non-overlapping
 * ordered runs validated against scalar TextRange bounds.
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class StyledText {

    private final TextContent content;
    private final List<TextSemanticRun> semanticRuns;
    private final List<TextStyleRun> styleRuns;
    private final ParagraphStyle paragraphStyle;

    public StyledText(TextContent content, List<TextSemanticRun> semanticRuns,
                      List<TextStyleRun> styleRuns, ParagraphStyle paragraphStyle) {
        this.content = Objects.requireNonNull(content, "content");
        this.paragraphStyle = Objects.requireNonNull(paragraphStyle, "paragraphStyle");
        int max = content.scalarCount();
        List<TextSemanticRun> sr = new ArrayList<>(semanticRuns);
        List<TextStyleRun> st = new ArrayList<>(styleRuns);
        for (TextSemanticRun run : sr) {
            run.range().withBound(max);
        }
        for (TextStyleRun run : st) {
            run.range().withBound(max);
        }
        sr.sort((a, b) -> Integer.compare(a.range().start(), b.range().start()));
        st.sort((a, b) -> Integer.compare(a.range().start(), b.range().start()));
        for (int i = 1; i < st.size(); i++) {
            if (st.get(i - 1).range().overlaps(st.get(i).range())) {
                throw new IllegalArgumentException("overlapping style runs forbidden in canonical StyledText");
            }
        }
        this.semanticRuns = Collections.unmodifiableList(sr);
        this.styleRuns = Collections.unmodifiableList(st);
    }

    public TextContent content() { return content; }
    public List<TextSemanticRun> semanticRuns() { return semanticRuns; }
    public List<TextStyleRun> styleRuns() { return styleRuns; }
    public ParagraphStyle paragraphStyle() { return paragraphStyle; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StyledText s)) return false;
        return content.equals(s.content) && semanticRuns.equals(s.semanticRuns)
                && styleRuns.equals(s.styleRuns) && paragraphStyle.equals(s.paragraphStyle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, semanticRuns, styleRuns, paragraphStyle);
    }
}
