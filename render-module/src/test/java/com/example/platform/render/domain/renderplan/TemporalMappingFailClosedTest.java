package com.example.platform.render.domain.renderplan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 correction F5: TemporalMapping must FAIL CLOSED for unsupported
 * (future) subtypes.
 *
 * <p>The sealed hierarchy currently permits only ConstantRate + Freeze, so a
 * future subtype cannot be authored (or proxied — sealed interfaces reject
 * dynamic proxies) in a runtime test today. Per the correction scope — "add a
 * regression/architecture test protecting this expectation as far as the sealed
 * hierarchy allows" — this is a source-level architecture guard: the
 * materializer must contain the fail-closed diagnostic path and must NOT
 * contain the silent full-source-range fallback.
 */
class TemporalMappingFailClosedTest {

    private static String materializerSource() throws IOException {
        // Gradle test working dir = render-module/
        Path path = Paths.get("src/main/java/com/example/platform/render/domain/renderplan/DefaultRenderMaterializer.java");
        return Files.readString(path);
    }

    @Test
    void materializerFailsClosedForUnknownMapping() throws IOException {
        String source = materializerSource();
        // The fail-closed diagnostic must be present...
        assertTrue(source.contains("PLANNING_UNSUPPORTED"),
                "materializer emits PLANNING_UNSUPPORTED for unsupported mapping");
        assertTrue(source.contains("Unsupported TemporalMapping kind"),
                "materializer records an explicit unsupported-mapping diagnostic");
        // ...and the silent full-source-range fallback must be GONE.
        assertFalse(source.contains("unknown mapping kind: fall back to full source range"),
                "silent full-source-range fallback removed (F5)");
        assertFalse(source.contains("fall back to full source range"),
                "no silent fallback wording remains (F5)");
    }

    @Test
    void sealedHierarchyHasExactlyTwoV1Subtypes() throws IOException {
        Path mapping = Paths.get("../timeline-module/src/main/java/com/example/platform/timeline/semantics/temporal/TemporalMapping.java");
        String source = Files.readString(mapping);
        assertTrue(source.contains("sealed interface TemporalMapping permits"),
                "TemporalMapping remains sealed");
        assertTrue(source.contains("ConstantRateTemporalMapping"),
                "V1 subtype ConstantRateTemporalMapping present");
        assertTrue(source.contains("FreezeTemporalMapping"),
                "V1 subtype FreezeTemporalMapping present");
    }
}
