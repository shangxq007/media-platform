package com.example.platform.render.infrastructure.remotion;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic render skeleton for Remotion.
 *
 * <p>Verifies that the same input props produce the same normalized command payload,
 * that sourceUrl is excluded, that templateId/templateVersion are stable and validated,
 * that invalid inputs are rejected before render, and that the command builder output
 * is deterministic for equivalent inputs.
 *
 * <p>Full pixel-perfect golden render validation requires a real Remotion binary and
 * Chrome headless — deferred to a later phase.
 */
class RemotionRenderDeterminismTest {

    @Test
    void sameInputProducesIdenticalCommand() {
        RemotionInputProps props = validInputProps();
        List<String> cmd1 = buildCommand(props);
        List<String> cmd2 = buildCommand(props);
        assertEquals(cmd1, cmd2, "same input must produce identical command");
    }

    @Test
    void equivalentInputsProduceIdenticalCommand() {
        RemotionInputProps props1 = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        "s3://fonts/test.ttf", "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", Map.of()
        );
        RemotionInputProps props2 = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        "s3://fonts/test.ttf", "s3://fonts/subset.woff2", "abc123", true)),
                null, "mp4", Map.of()
        );
        assertEquals(buildCommand(props1), buildCommand(props2),
                "equivalent inputs must produce identical commands");
    }

    @Test
    void sourceUrlNeverAppearsInCommandPayload() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        "s3://fonts/original-font.ttf", "s3://fonts/subset-v2.woff2", "abc123", true)),
                null, "mp4", Map.of()
        );
        List<String> cmd = buildCommand(props);
        String serialized = String.join(" ", cmd);
        assertFalse(serialized.contains("original-font.ttf"),
                "sourceUrl must never leak into command payload");
        assertTrue(serialized.contains("subset-v2.woff2"),
                "effectiveUrl (subsetUrl) must be in the command payload");
        assertFalse(serialized.contains("\"sourceUrl\""),
                "sourceUrl key must never appear in serialized props");
        assertTrue(serialized.contains("\"effectiveUrl\""),
                "effectiveUrl key must appear in serialized props");
    }

    @Test
    void sourceUrlNotPresentWhenOnlySubsetUrlProvided() {
        RemotionInputProps props = new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        null, "s3://fonts/pure-subset.woff2", "hash999", true)),
                null, "mp4", Map.of()
        );
        List<String> cmd = buildCommand(props);
        String serialized = String.join(" ", cmd);
        assertTrue(serialized.contains("pure-subset.woff2"));
        assertFalse(serialized.contains("\"sourceUrl\""));
    }

    @Test
    void templateIdAndVersionAreStable() {
        RemotionTemplateSpec template1 = new RemotionTemplateSpec("social-captions", "1.2.0", Map.of(), "SocialComposition");
        RemotionTemplateSpec template2 = new RemotionTemplateSpec("social-captions", "1.2.0", Map.of(), "SocialComposition");

        RemotionInputProps props1 = new RemotionInputProps(
                1080, 1920, 30, 300,
                List.of(), List.of(), template1, "mp4", Map.of()
        );
        RemotionInputProps props2 = new RemotionInputProps(
                1080, 1920, 30, 300,
                List.of(), List.of(), template2, "mp4", Map.of()
        );

        List<String> cmd1 = buildCommand(props1);
        List<String> cmd2 = buildCommand(props2);
        assertEquals(cmd1, cmd2, "same template inputs must produce identical commands");
    }

    @Test
    void invalidDimensionsRejectedBeforeRender() {
        RemotionInputProps zeroWidth = new RemotionInputProps(
                0, 1080, 30, 900, List.of(), List.of(), null, "mp4", Map.of()
        );
        assertThrows(IllegalArgumentException.class, () -> buildCommand(zeroWidth));

        RemotionInputProps zeroHeight = new RemotionInputProps(
                1920, 0, 30, 900, List.of(), List.of(), null, "mp4", Map.of()
        );
        assertThrows(IllegalArgumentException.class, () -> buildCommand(zeroHeight));

        RemotionInputProps zeroFps = new RemotionInputProps(
                1920, 1080, 0, 900, List.of(), List.of(), null, "mp4", Map.of()
        );
        assertThrows(IllegalArgumentException.class, () -> buildCommand(zeroFps));

        RemotionInputProps zeroDuration = new RemotionInputProps(
                1920, 1080, 30, 0, List.of(), List.of(), null, "mp4", Map.of()
        );
        assertThrows(IllegalArgumentException.class, () -> buildCommand(zeroDuration));
    }

    @Test
    void unsafeTemplateReferencesRejectedBeforeRender() {
        RemotionTemplateSpec pathTraversal = new RemotionTemplateSpec("../etc/passwd", "1.0", Map.of(), null);
        List<String> errors = RemotionTemplateGuard.validate(pathTraversal);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("..")));

        RemotionTemplateSpec urlTemplate = new RemotionTemplateSpec("https://evil.com/template.js", "1.0", Map.of(), null);
        errors = RemotionTemplateGuard.validate(urlTemplate);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("URL")));

        RemotionTemplateSpec jsInjection = new RemotionTemplateSpec("template-exec-bad", "1.0", Map.of(), null);
        errors = RemotionTemplateGuard.validate(jsInjection);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("exec")));
    }

    @Test
    void commandBuilderOutputIsDeterministic() {
        RemotionInputProps props = validInputProps();
        List<String> cmd1 = buildCommand(props);
        List<String> cmd2 = buildCommand(props);
        List<String> cmd3 = buildCommand(props);

        assertEquals(cmd1.size(), cmd2.size());
        assertEquals(cmd2.size(), cmd3.size());
        for (int i = 0; i < cmd1.size(); i++) {
            assertEquals(cmd1.get(i), cmd2.get(i),
                    "command entries must be identical at index " + i);
            assertEquals(cmd2.get(i), cmd3.get(i),
                    "command entries must be identical at index " + i);
        }
    }

    @Test
    void sameFontManifestSubsetUrlProducesStableRenderInput() {
        RemotionFontSpec font1 = new RemotionFontSpec("BrandFont", 700, "normal",
                null, "s3://fonts/brand-subset.woff2", "hash-brand", true);
        RemotionFontSpec font2 = new RemotionFontSpec("BrandFont", 700, "normal",
                null, "s3://fonts/brand-subset.woff2", "hash-brand", true);

        RemotionInputProps props1 = new RemotionInputProps(
                1920, 1080, 30, 600,
                List.of(),
                List.of(font1),
                null, "mp4", Map.of()
        );
        RemotionInputProps props2 = new RemotionInputProps(
                1920, 1080, 30, 600,
                List.of(),
                List.of(font2),
                null, "mp4", Map.of()
        );

        assertEquals(buildCommand(props1), buildCommand(props2),
                "same FontManifest inputs must produce identical commands");
    }

    private static RemotionInputProps validInputProps() {
        return new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(new RemotionFontSpec("TestFont", 400, "normal",
                        "s3://fonts/test.ttf", "s3://fonts/test-subset.woff2", "abc123", true)),
                null, "mp4", Map.of()
        );
    }

    private static List<String> buildCommand(RemotionInputProps props) {
        return new RemotionRenderCommandBuilder()
                .compositionId("DeterminismTest")
                .outputPath(Path.of("/tmp/test-output.mp4"))
                .inputProps(props)
                .build();
    }
}
