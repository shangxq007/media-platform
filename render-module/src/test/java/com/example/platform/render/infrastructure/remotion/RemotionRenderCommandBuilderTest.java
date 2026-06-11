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
}
