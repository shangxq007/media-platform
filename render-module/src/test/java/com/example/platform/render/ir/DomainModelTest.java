package com.example.platform.render.ir;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Domain model tests: structural and semantic validation.
 */
class DomainModelTest {

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
    void validSingleTrackSingleClipAccepted() {
        MediaProjectIr ir = valid().build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertTrue(errors.isEmpty(), "Expected valid IR to have no errors, got: " + errors);
    }

    @Test
    void missingProjectRejected() {
        MediaProjectIr ir = valid().project(null).build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).message().contains("project"));
    }

    @Test
    void missingTimelineRejected() {
        MediaProjectIr ir = valid().timeline(null).build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.path().contains("timeline")));
    }

    @Test
    void missingOutputRejected() {
        MediaProjectIr ir = valid().outputs(List.of()).build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.path().contains("outputs")));
    }

    @Test
    void duplicateOutputIdsRejected() {
        MediaProjectIr ir = valid().outputs(List.of(
            new OutputSpec("out-1", "mp4", "h264", 1920, 1080, RationalTime.of(30000, 1001), null),
            new OutputSpec("out-1", "mp4", "h265", 1280, 720, RationalTime.of(30000, 1001), null)
        )).build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.DUPLICATE_IDENTIFIER),
            "Expected DUPLICATE_IDENTIFIER, got: " + errors);
    }

    @Test
    void missingAssetVersionRefRejected() {
        // Clip references an asset not in the assets list
        MediaProjectIr ir = valid()
            .assets(List.of(new AssetVersionRef("other-asset", "v1")))
            .build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertTrue(errors.stream().anyMatch(e -> e.code() == IrErrorCode.MISSING_ASSET_REFERENCE),
            "Expected MISSING_ASSET_REFERENCE, got: " + errors);
    }

    @Test
    void nullProjectFieldsRejected() {
        MediaProjectIr ir = valid()
            .project(new Project("", ""))
            .build();
        List<IrValidationError> errors = IrValidator.validate(ir);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.path().contains("project.id")),
            "Expected project.id error");
    }
}
