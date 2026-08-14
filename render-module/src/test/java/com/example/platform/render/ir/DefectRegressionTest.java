package com.example.platform.render.ir;
import com.example.platform.shared.time.RationalTime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for H.48X defect fixes.
 */
class DefectRegressionTest {

    // ---- F002: Extensions Map defensive copy ----

    @Test
    void mediaProjectIrDefensiveCopiesExtensions() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("com.example.platform.extension.foo", "bar");

        MediaProjectIr ir = new MediaProjectIr(
            "media-project/v1",
            new Project("p", "Project"),
            List.of(new AssetVersionRef("a", "v1")),
            new Timeline("tl", List.of(
                new VideoTrack("t", List.of(
                    new Clip("c",
                        new SourceRange(new AssetVersionRef("a", "v1"),
                            RationalTime.zero(1), RationalTime.of(100, 1)),
                        RationalTime.zero(1))
                ))
            )),
            List.of(new OutputSpec("o", "mp4", "h264", 1920, 1080,
                RationalTime.of(30, 1), null)),
            List.of(new ArtifactDeclaration("art", "o", "out.mp4")),
            mutable
        );

        mutable.put("com.example.platform.extension.evil", "mutated");

        assertFalse(ir.extensions().containsKey("com.example.platform.extension.evil"),
            "Extensions must be defensively copied, mutation after construction must not affect IR");
        assertEquals(1, ir.extensions().size());
    }

    @Test
    void outputSpecDefensiveCopiesExtensions() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("com.example.platform.extension.foo", "bar");

        OutputSpec spec = new OutputSpec("o", "mp4", "h264", 1920, 1080,
            RationalTime.of(30, 1), mutable);

        mutable.put("com.example.platform.extension.evil", "mutated");

        assertNull(spec.extensions().get("com.example.platform.extension.evil"),
            "OutputSpec extensions must be defensively copied");
        assertEquals(1, spec.extensions().size());
    }

    // ---- F003: Zero-valued rational time canonicalization ----

    @Test
    void zeroRationalTimeNormalizesDenominatorToOne() {
        RationalTime t1 = RationalTime.of(0, 30000);
        RationalTime t2 = RationalTime.of(0, 1001);
        RationalTime t3 = RationalTime.of(0, 2);
        RationalTime t4 = RationalTime.zero(24000);

        assertEquals(1L, t1.denominator(), "0/30000 must normalize to 0/1");
        assertEquals(1L, t2.denominator(), "0/1001 must normalize to 0/1");
        assertEquals(1L, t3.denominator(), "0/2 must normalize to 0/1");
        assertEquals(1L, t4.denominator(), "0/24000 must normalize to 0/1");
    }

    @Test
    void zeroRationalTimesWithDifferentDenominatorsAreEqual() {
        RationalTime a = RationalTime.of(0, 2);
        RationalTime b = RationalTime.of(0, 3);
        RationalTime c = RationalTime.of(0, 1);

        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(b.hashCode(), c.hashCode());
    }

    @Test
    void zeroRationalTimeSerializesToCanonicalForm() {
        MediaProjectIr ir = new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("p", "Project"))
            .assets(List.of(new AssetVersionRef("a", "v1")))
            .timeline(new Timeline("tl", List.of(
                new VideoTrack("t", List.of(
                    new Clip("c",
                        new SourceRange(new AssetVersionRef("a", "v1"),
                            RationalTime.of(0, 30000), RationalTime.of(100, 1)),
                        RationalTime.of(0, 24000))
                ))
            )))
            .outputs(List.of(new OutputSpec("o", "mp4", "h264", 1920, 1080,
                RationalTime.of(30, 1), null)))
            .artifacts(List.of(new ArtifactDeclaration("art", "o", "out.mp4")))
            .build();

        MediaProjectIr n = IrNormalizer.normalize(ir);
        byte[] bytes = CanonicalSerializer.serialize(n);
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertFalse(json.contains("\"0/30000\""), "Zero time must serialize as 0/1, not 0/30000");
        assertTrue(json.contains("\"0/1\""), "Zero time must serialize as canonical 0/1");
    }

    // ---- F004: Null extension keys cause NPE ----

    @Test
    void nullExtensionKeyDoesNotCauseNullPointerException() {
        Map<String, Object> extWithNull = new HashMap<>();
        extWithNull.put(null, "value");
        extWithNull.put("com.example.platform.extension.valid", "ok");

        MediaProjectIr ir = new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("p", "Project"))
            .assets(List.of(new AssetVersionRef("a", "v1")))
            .timeline(new Timeline("tl", List.of(
                new VideoTrack("t", List.of(
                    new Clip("c",
                        new SourceRange(new AssetVersionRef("a", "v1"),
                            RationalTime.zero(1), RationalTime.of(100, 1)),
                        RationalTime.zero(1))
                ))
            )))
            .outputs(List.of(new OutputSpec("o", "mp4", "h264", 1920, 1080,
                RationalTime.of(30, 1), null)))
            .artifacts(List.of(new ArtifactDeclaration("art", "o", "out.mp4")))
            .extensions(extWithNull)
            .build();

        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty(), "Must produce validation error for null extension key");
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.INVALID_EXTENSION),
            "Must use INVALID_EXTENSION error code for null key");
        assertTrue(errors.stream().anyMatch(
            e -> e.message().toLowerCase().contains("null")),
            "Error message must mention null key");
    }

    @Test
    void nullExtensionKeyOnOutputSpecDoesNotCauseNPE() {
        Map<String, Object> extWithNull = new HashMap<>();
        extWithNull.put(null, "value");

        OutputSpec spec = new OutputSpec("o", "mp4", "h264", 1920, 1080,
            RationalTime.of(30, 1), extWithNull);

        List<IrValidationError> errors = IrValidator.validate(
            new MediaProjectIrBuilder()
                .schemaVersion("media-project/v1")
                .project(new Project("p", "Project"))
                .assets(List.of(new AssetVersionRef("a", "v1")))
                .timeline(new Timeline("tl", List.of(
                    new VideoTrack("t", List.of(
                        new Clip("c",
                            new SourceRange(new AssetVersionRef("a", "v1"),
                                RationalTime.zero(1), RationalTime.of(100, 1)),
                            RationalTime.zero(1))
                    ))
                )))
                .outputs(List.of(spec))
                .artifacts(List.of(new ArtifactDeclaration("art", "o", "out.mp4")))
                .build()
        );
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.INVALID_EXTENSION),
            "Null key in OutputSpec extensions must produce INVALID_EXTENSION error");
    }

    // ---- F007: Wrong error code for artifact->output reference ----

    @Test
    void artifactMissingOutputReferenceUsesCorrectErrorCode() {
        MediaProjectIr ir = new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("p", "Project"))
            .assets(List.of(new AssetVersionRef("a", "v1")))
            .timeline(new Timeline("tl", List.of(
                new VideoTrack("t", List.of(
                    new Clip("c",
                        new SourceRange(new AssetVersionRef("a", "v1"),
                            RationalTime.zero(1), RationalTime.of(100, 1)),
                        RationalTime.zero(1))
                ))
            )))
            .outputs(List.of(new OutputSpec("out-1", "mp4", "h264", 1920, 1080,
                RationalTime.of(30, 1), null)))
            .artifacts(List.of(new ArtifactDeclaration("art-1", "non-existent-output", "out.mp4")))
            .build();

        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.path().contains("outputSpecId")),
            "Must flag artifact with missing output reference");

        boolean hasMissingOutputRef = errors.stream()
            .filter(e -> e.path().contains("outputSpecId"))
            .anyMatch(e -> e.code() == IrErrorCode.MISSING_OUTPUT_REFERENCE);
        assertTrue(hasMissingOutputRef,
            "Artifact->output reference failure must use MISSING_OUTPUT_REFERENCE, not MISSING_ASSET_REFERENCE");
    }

    @Test
    void assetReferenceStillUsesMissingAssetReference() {
        MediaProjectIr ir = new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("p", "Project"))
            .assets(List.of(new AssetVersionRef("declared", "v1")))
            .timeline(new Timeline("tl", List.of(
                new VideoTrack("t", List.of(
                    new Clip("c",
                        new SourceRange(new AssetVersionRef("undeclared", "v1"),
                            RationalTime.zero(1), RationalTime.of(100, 1)),
                        RationalTime.zero(1))
                ))
            )))
            .outputs(List.of(new OutputSpec("o", "mp4", "h264", 1920, 1080,
                RationalTime.of(30, 1), null)))
            .artifacts(List.of(new ArtifactDeclaration("art", "o", "out.mp4")))
            .build();

        List<IrValidationError> errors = IrValidator.validate(ir);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.MISSING_ASSET_REFERENCE),
            "Clip->asset reference failure must still use MISSING_ASSET_REFERENCE");
    }

    // ---- F008: Extension content validation ----

    @Test
    void extensionValuesMustBeSimpleTypes() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("com.example.platform.extension.ok", "string-value");
        ext.put("com.example.platform.extension.number", 42);
        ext.put("com.example.platform.extension.bool", true);
        ext.put("com.example.platform.extension.list", List.of("a", "b"));
        ext.put("com.example.platform.extension.map", Map.of("k", "v"));

        List<IrValidationError> errors = validateWithExtensions(ext);
        assertTrue(errors.isEmpty(), "Simple types must be accepted: " + errors);
    }

    @Test
    void extensionValueStringMustNotContainPaths() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("com.example.platform.extension.path", "/etc/passwd");
        List<IrValidationError> errors = validateWithExtensions(ext);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.INVALID_EXTENSION),
            "Path separator in extension value must be rejected");
    }

    @Test
    void extensionValueStringMustNotContainBackslashPath() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("com.example.platform.extension.path", "C:\\windows\\system32");
        List<IrValidationError> errors = validateWithExtensions(ext);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.INVALID_EXTENSION),
            "Backslash path in extension value must be rejected");
    }

    @Test
    void extensionValueStringMustNotContainCommandInjection() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("com.example.platform.extension.cmd", "value; rm -rf /");
        List<IrValidationError> errors = validateWithExtensions(ext);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.INVALID_EXTENSION),
            "Command injection character in extension value must be rejected");
    }

    @Test
    void extensionValueStringMustNotContainCredentials() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("com.example.platform.extension.secret", "my-password-is-secret");
        List<IrValidationError> errors = validateWithExtensions(ext);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.INVALID_EXTENSION),
            "Credential-like content in extension value must be rejected");
    }

    @Test
    void extensionValueNestedRejection() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("key", "/path/injection");
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("com.example.platform.extension.nested", inner);

        List<IrValidationError> errors = validateWithExtensions(ext);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.INVALID_EXTENSION),
            "Nested path separator must be rejected");
    }

    @Test
    void unsupportedExtensionValueTypeRejected() {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("com.example.platform.extension.obj", new Object());

        List<IrValidationError> errors = validateWithExtensions(ext);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.INVALID_EXTENSION),
            "Unsupported extension value type must be rejected");
    }

    private static List<IrValidationError> validateWithExtensions(Map<String, Object> extensions) {
        MediaProjectIr ir = new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("p", "Project"))
            .assets(List.of(new AssetVersionRef("a", "v1")))
            .timeline(new Timeline("tl", List.of(
                new VideoTrack("t", List.of(
                    new Clip("c",
                        new SourceRange(new AssetVersionRef("a", "v1"),
                            RationalTime.zero(1), RationalTime.of(100, 1)),
                        RationalTime.zero(1))
                ))
            )))
            .outputs(List.of(new OutputSpec("o", "mp4", "h264", 1920, 1080,
                RationalTime.of(30, 1), null)))
            .artifacts(List.of(new ArtifactDeclaration("art", "o", "out.mp4")))
            .extensions(extensions)
            .build();
        return IrValidator.validate(ir);
    }
}
