package com.example.platform.render.infrastructure.remotion;

import com.example.platform.render.infrastructure.RenderJob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemotionRenderCommandBuilderTest {

    @Test
    void buildsBasicCommand() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        "s3://fonts/test.ttf", "s3://fonts/test-subset.woff2", "abc123", true)),
                null, "mp4", java.util.Map.of()
        );

        List<String> cmd = new RemotionRenderCommandBuilder()
                .compositionId("CaptionComposition")
                .outputPath(java.nio.file.Path.of("/tmp/output.mp4"))
                .inputProps(props)
                .build();

        assertTrue(cmd.contains("npx"));
        assertTrue(cmd.contains("remotion"));
        assertTrue(cmd.contains("render"));
        assertTrue(cmd.contains("CaptionComposition"));
        assertTrue(cmd.contains("--format=mp4"));
        assertTrue(cmd.contains("--width=1920"));
        assertTrue(cmd.contains("--height=1080"));
    }

    @Test
    void commandContainsFontSpecs() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("NotoSansCJK", 700, "bold",
                        null, "s3://fonts/subset.woff2", "def456", true)),
                null, "mp4", java.util.Map.of()
        );

        List<String> cmd = new RemotionRenderCommandBuilder()
                .compositionId("TestComp")
                .outputPath(java.nio.file.Path.of("/tmp/out.mp4"))
                .inputProps(props)
                .build();

        String propsArg = cmd.stream().filter(s -> s.startsWith("--props=")).findFirst().orElse("");
        assertTrue(propsArg.contains("NotoSansCJK"));
        assertTrue(propsArg.contains("700"));
        assertTrue(propsArg.contains("subset.woff2"));
    }

    @Test
    void commandDoesNotContainSourceUrl() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        "s3://fonts/original.ttf", "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", java.util.Map.of()
        );

        List<String> cmd = new RemotionRenderCommandBuilder()
                .compositionId("TestComp")
                .outputPath(java.nio.file.Path.of("/tmp/out.mp4"))
                .inputProps(props)
                .build();

        String propsArg = cmd.stream().filter(s -> s.startsWith("--props=")).findFirst().orElse("");
        assertTrue(propsArg.contains("subset.woff2"));
        assertFalse(propsArg.contains("original.ttf"));
    }

    @Test
    void buildWithoutOutputPathThrows() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(), List.of(), null, "mp4", java.util.Map.of()
        );
        assertThrows(IllegalStateException.class, () ->
                new RemotionRenderCommandBuilder()
                        .compositionId("TestComp")
                        .inputProps(props)
                        .build()
        );
    }

    @Test
    void buildWithInvalidPropsThrows() {
        RemotionInputProps props = new RemotionInputProps(
                0, 0, 0, 0,
                List.of(), List.of(), null, "avi", java.util.Map.of()
        );
        assertThrows(IllegalArgumentException.class, () ->
                new RemotionRenderCommandBuilder()
                        .compositionId("TestComp")
                        .outputPath(java.nio.file.Path.of("/tmp/out.mp4"))
                        .inputProps(props)
                        .build()
        );
    }

    @Test
    void fontWithOnlySubsetUrlUsesEffectiveUrl() {
        RemotionFontSpec font = new RemotionFontSpec("TestFont", 400, "normal",
                null, "s3://fonts/subset.woff2", "abc123", true);
        assertEquals("s3://fonts/subset.woff2", font.effectiveUrl());
        assertNull(font.sourceUrl());
        assertNotNull(font.subsetUrl());
    }

    @Test
    void fontWithOnlySourceUrlFallsBackToSourceUrl() {
        RemotionFontSpec font = new RemotionFontSpec("TestFont", 400, "normal",
                "s3://fonts/test.ttf", null, "abc123", true);
        assertEquals("s3://fonts/test.ttf", font.effectiveUrl());
        assertNotNull(font.sourceUrl());
        assertNull(font.subsetUrl());
    }

    @Test
    void fontWithBothUrlsPrefersSubsetUrl() {
        RemotionFontSpec font = new RemotionFontSpec("TestFont", 400, "normal",
                "s3://fonts/original.ttf", "s3://fonts/subset.woff2", "abc123", true);
        assertEquals("s3://fonts/subset.woff2", font.effectiveUrl());
        assertNotNull(font.sourceUrl());
        assertNotNull(font.subsetUrl());
    }

    @Test
    void serializedPropsNeverContainsSourceUrlKey() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        "s3://fonts/original.ttf", "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", java.util.Map.of()
        );

        List<String> cmd = new RemotionRenderCommandBuilder()
                .compositionId("TestComp")
                .outputPath(java.nio.file.Path.of("/tmp/out.mp4"))
                .inputProps(props)
                .build();

        String propsArg = cmd.stream().filter(s -> s.startsWith("--props=")).findFirst().orElse("");
        assertFalse(propsArg.contains("\"sourceUrl\":"),
                "sourceUrl key must never appear in serialized props");
        assertTrue(propsArg.contains("\"effectiveUrl\":"),
                "effectiveUrl must appear in serialized props");
        assertTrue(propsArg.contains("subset.woff2"),
                "subset URL must be the effective URL");
    }

    @Test
    void multipleFontsAllUseEffectiveUrl() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(
                        new RemotionFontSpec("Font1", 400, "normal",
                                "s3://fonts/font1.ttf", "s3://fonts/font1-subset.woff2", "aaa", true),
                        new RemotionFontSpec("Font2", 700, "bold",
                                null, "s3://fonts/font2-subset.woff2", "bbb", true),
                        new RemotionFontSpec("Font3", 400, "italic",
                                "s3://fonts/font3.ttf", null, "ccc", true)
                ),
                null, "mp4", java.util.Map.of()
        );

        List<String> cmd = new RemotionRenderCommandBuilder()
                .compositionId("TestComp")
                .outputPath(java.nio.file.Path.of("/tmp/out.mp4"))
                .inputProps(props)
                .build();

        String propsArg = cmd.stream().filter(s -> s.startsWith("--props=")).findFirst().orElse("");
        assertTrue(propsArg.contains("font1-subset.woff2"));
        assertTrue(propsArg.contains("font2-subset.woff2"));
        assertTrue(propsArg.contains("font3.ttf"));
        assertFalse(propsArg.contains("font1.ttf"),
                "font1's sourceUrl must not appear (subsetUrl used instead)");
    }

    @Test
    void commandIncludesConcurrencyAndOverwrite() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        null, "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", java.util.Map.of()
        );

        List<String> cmd = new RemotionRenderCommandBuilder()
                .compositionId("TestComp")
                .outputPath(java.nio.file.Path.of("/tmp/out.mp4"))
                .inputProps(props)
                .concurrency(4)
                .overwrite(true)
                .build();

        assertTrue(cmd.contains("--concurrency=4"));
        assertTrue(cmd.contains("--overwrite"));
    }

    @Test
    void buildWithNullInputPropsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new RemotionRenderCommandBuilder()
                        .compositionId("TestComp")
                        .outputPath(java.nio.file.Path.of("/tmp/out.mp4"))
                        .inputProps(null)
                        .build()
        );
    }
}
