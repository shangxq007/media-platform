package com.example.platform.render.domain.renderplan;

import com.example.platform.fonttext.text.RangeDirectionOverride;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.FontSize;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.TextStyleRun;

import java.util.List;

/** Test fixture helper for building StyledText variants (R3-B2 collision tests). */
final class StyledTextHelper {

    private StyledTextHelper() {
    }

    record TextFixture(
            TextSemanticRun semanticRun,
            TextStyleRun styleRun,
            ParagraphStyle paragraphStyle) {
    }

    static TextFixture build(TextContent content, TextRange range) {
        TextSemanticRun semantic = new TextSemanticRun(
                range, null, ScriptTag.LATIN, RangeDirectionOverride.NONE);
        TextStyle style = new TextStyle(
                new FontSelectionIntent(List.of(new FontFamilyName("Inter")),
                        FontSelectionIntent.WeightIntent.NORMAL,
                        FontSelectionIntent.StretchIntent.NORMAL,
                        FontSelectionIntent.SlantIntent.NORMAL,
                        OpticalSizingIntent.disabled(), List.of()),
                new FontSize(FontRational.of(24, 1)),
                FontRational.of(0, 1), OpenTypeFeatureIntent.empty());
        TextStyleRun styleRun = new TextStyleRun(range, style);
        ParagraphStyle paragraph = new ParagraphStyle(
                ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                LineHeight.ratio(FontRational.of(12, 10)),
                ParagraphStyle.WrapPolicy.WRAP, ParagraphBaseDirection.AUTO,
                ParagraphStyle.LineBreakPolicy.STANDARD);
        return new TextFixture(semantic, styleRun, paragraph);
    }
}
