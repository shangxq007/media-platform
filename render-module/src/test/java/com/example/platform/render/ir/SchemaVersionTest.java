package com.example.platform.render.ir;
import com.example.platform.shared.time.RationalTime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * Schema version validation tests for MediaProjectIr.
 */
class SchemaVersionTest {

    private static MediaProjectIrBuilder valid() {
        return new MediaProjectIrBuilder()
            .schemaVersion("media-project/v1")
            .project(new Project("proj-1", "Test Project"))
            .assets(List.of(new AssetVersionRef("asset-1", "v1")))
            .timeline(new Timeline("tl-1", List.of(
                new VideoTrack("track-1", List.of(
                    new Clip("clip-1",
                        new SourceRange(
                            new AssetVersionRef("asset-1", "v1"),
                            RationalTime.zero(30000),
                            RationalTime.of(90000, 30000)),
                        RationalTime.zero(30000))
                ))
            )))
            .outputs(List.of(new OutputSpec("out-1", "mp4", "h264", 1920, 1080,
                RationalTime.of(30000, 1001), null)))
            .artifacts(List.of(new ArtifactDeclaration("art-1", "out-1", "output.mp4")));
    }

    @Test
    void validSchemaVersionAccepted() {
        MediaProjectIr ir = valid().build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertTrue(errors.isEmpty(), "Expected no errors for valid IR, got: " + errors);
    }

    @Test
    void missingVersionRejected() {
        MediaProjectIr ir = valid().schemaVersion(null).build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertEquals(IrErrorCode.UNSUPPORTED_SCHEMA_VERSION, errors.get(0).code());
    }

    @Test
    void blankVersionRejected() {
        MediaProjectIr ir = valid().schemaVersion("").build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertEquals(IrErrorCode.UNSUPPORTED_SCHEMA_VERSION, errors.get(0).code());
    }

    @Test
    void unsupportedVersionRejected() {
        MediaProjectIr ir = valid().schemaVersion("media-project/v2").build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertEquals(IrErrorCode.UNSUPPORTED_SCHEMA_VERSION, errors.get(0).code());
    }

    @Test
    void unknownMajorRejected() {
        MediaProjectIr ir = valid().schemaVersion("unknown/v1").build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertEquals(IrErrorCode.UNSUPPORTED_SCHEMA_VERSION, errors.get(0).code());
    }

    @Test
    void malformedTopLevelRejected() {
        MediaProjectIr ir = new MediaProjectIr(
            null, null, null, null, null, null, null);
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
    }
}
