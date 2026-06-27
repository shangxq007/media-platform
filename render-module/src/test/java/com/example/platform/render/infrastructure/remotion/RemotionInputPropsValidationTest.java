package com.example.platform.render.infrastructure.remotion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RemotionInputPropsValidationTest {

    @Test
    void validMinimalPropsPassesValidation() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        null, "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
    }

    @Test
    void nullPropsReturnsError() {
        List<String> errors = RemotionInputPropsValidator.validate(null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("must not be null")));
    }

    @Test
    void invalidWidthRejected() {
        RemotionInputProps props = new RemotionInputProps(
                0, 1080, 30, 900,
                List.of(), List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("compositionWidth")));
    }

    @Test
    void widthExceedsMaximumRejected() {
        RemotionInputProps props = new RemotionInputProps(
                8000, 1080, 30, 900,
                List.of(), List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("compositionWidth")));
    }

    @Test
    void invalidFpsRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 0, 900,
                List.of(), List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("fps")));
    }

    @Test
    void fpsExceedsMaximumRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 121, 900,
                List.of(), List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("fps")));
    }

    @Test
    void negativeDurationRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 0,
                List.of(), List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("durationInFrames")));
    }

    @Test
    void invalidOutputFormatRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(), List.of(), null, "avi", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("outputFormat")));
    }

    @Test
    void captionWithBlankIdRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(new RemotionCaption("", "test", 0, 1,
                        RemotionCaptionStyle.defaultStyle(), List.of())),
                List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains(".id")));
    }

    @Test
    void captionWithNullTextRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(new RemotionCaption("c1", null, 0, 1,
                        RemotionCaptionStyle.defaultStyle(), List.of())),
                List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains(".text must not be null")));
    }

    @Test
    void captionWithNegativeStartTimeRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(new RemotionCaption("c1", "test", -1, 1,
                        RemotionCaptionStyle.defaultStyle(), List.of())),
                List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("startTime must not be negative")));
    }

    @Test
    void captionEndTimeNotAfterStartTimeRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(new RemotionCaption("c1", "test", 5, 3,
                        RemotionCaptionStyle.defaultStyle(), List.of())),
                List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("endTime must be greater than startTime")));
    }

    @Test
    void tooManyCaptionsRejected() {
        List<RemotionCaption> captions = new java.util.ArrayList<>();
        for (int i = 0; i < 5001; i++) {
            captions.add(new RemotionCaption("c" + i, "test", 0, 1,
                    RemotionCaptionStyle.defaultStyle(), List.of()));
        }
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                captions, List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("too many captions")));
    }

    @Test
    void fontSpecWithBlankFamilyRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("", 400, "normal",
                        null, "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("fontFamily must not be blank")));
    }

    @Test
    void fontSpecWithInvalidWeightRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("Test", 0, "normal",
                        null, "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("weight")));
    }

    @Test
    void fontSpecWithNullFontFamilyRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec(null, 400, "normal",
                        null, "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("fontFamily must not be blank")));
    }

    @Test
    void fontSpecNoUrlRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("Test", 400, "normal",
                        null, null, "abc123", true)),
                null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("must have at least one of subsetUrl or sourceUrl")));
    }

    @Test
    void notProductionSafeFontRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("Test", 400, "normal",
                        "s3://fonts/test.ttf", null, "abc123", false)),
                null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("not production-safe")));
    }

    @Test
    void templateWithBlankIdRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(), List.of(),
                new RemotionTemplateSpec("", "1.0", Map.of(), null),
                "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("template.templateId must not be blank")));
    }

    @Test
    void templateWithBlankVersionRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(), List.of(),
                new RemotionTemplateSpec("t1", "", Map.of(), null),
                "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("template.templateVersion must not be blank")));
    }

    @Test
    void captionWordWithBlankTextRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(new RemotionCaption("c1", "test", 0, 1,
                        RemotionCaptionStyle.defaultStyle(),
                        List.of(new RemotionCaptionWord("", 0, 0.5, false)))),
                List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("text must not be blank")));
    }

    @Test
    void captionStyleInvalidOpacityRejected() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(new RemotionCaption("c1", "test", 0, 1,
                        new RemotionCaptionStyle("Arial", 24, "#fff", "#000", "#000", 1.0, "center", null, false, false, 1.5),
                        List.of())),
                List.of(), null, "mp4", Map.of()
        );
        List<String> errors = RemotionInputPropsValidator.validate(props);
        assertTrue(errors.stream().anyMatch(e -> e.contains("opacity")));
    }
}
