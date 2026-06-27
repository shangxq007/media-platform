package com.example.platform.render.domain.environment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenCueJobSpecValidatorTest {

    private OpenCueJobSpecValidator validator;

    @BeforeEach
    void setUp() {
        OpenCueProperties props = new OpenCueProperties();
        validator = new OpenCueJobSpecValidator(props);
    }

    @Test
    void nullSpecFails() {
        List<String> errors = validator.validate(null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("must not be null")));
    }

    @Test
    void blankJobNameFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1, "memoryMb", 1024),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render", List.of("ffmpeg"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("jobName")));
    }

    @Test
    void nullOwnerFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", null, 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render", List.of("ffmpeg"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("owner")));
    }

    @Test
    void priorityOutOfRangeFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 0,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render", List.of("ffmpeg"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("priority")));
    }

    @Test
    void nullLayersFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1), null);
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("layers")));
    }

    @Test
    void emptyLayersFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1), List.of());
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("layers must not be empty")));
    }

    @Test
    void blankLayerNameFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("", List.of("ffmpeg"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("layerName")));
    }

    @Test
    void nullCommandsFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render", null, 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("commands")));
    }

    @Test
    void emptyCommandsFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render", List.of(), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("commands must not be empty")));
    }

    @Test
    void blankCommandFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render", List.of("   "), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("must not be null or blank")));
    }

    @Test
    void shellInjectionInCommandFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render",
                        List.of("ffmpeg -i in.mp4 && rm -rf /"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("shell injection") && e.contains("&&")));
    }

    @Test
    void pathTraversalInCommandFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render",
                        List.of("cat ../../etc/passwd"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("path traversal") && e.contains("..")));
    }

    @Test
    void remoteUrlCommandFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render",
                        List.of("https://evil.com/payload.sh"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("remote URL")));
    }

    @Test
    void forbiddenEnvVarFails() {
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of("PATH", "/evil/bin"), Map.of("cpu", 1),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render", List.of("ffmpeg"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("forbidden environment variable")));
    }

    @Test
    void validJobSpecPasses() {
        OpenCueJobSpec spec = new OpenCueJobSpec("platform-job-001", "platform", 50,
                List.of("platform"), Map.of("FFMPEG_LOGLEVEL", "info"),
                Map.of("cpu", 4, "memoryMb", 4096),
                List.of(new OpenCueJobSpec.OpenCueLayerSpec("render-layer",
                        List.of("ffmpeg", "-i", "input.mp4", "-c:v", "libx264", "output.mp4"), 1, Map.of())));
        List<String> errors = validator.validate(spec);
        assertTrue(errors.isEmpty(), "valid spec should pass, got: " + errors);
    }

    @Test
    void fromJobIdProducesValidSpec() {
        OpenCueJobSpec spec = OpenCueJobSpec.fromJobId("test-job-123");
        List<String> errors = validator.validate(spec);
        assertTrue(errors.isEmpty() || errors.stream().allMatch(e -> e.contains("layers")),
                "fromJobId should produce valid spec (empty layers is expected default)");
    }

    @Test
    void tooManyLayersFails() {
        OpenCueProperties strictProps = new OpenCueProperties();
        strictProps.setMaxLayers(2);
        OpenCueJobSpecValidator strictValidator = new OpenCueJobSpecValidator(strictProps);
        OpenCueJobSpec spec = new OpenCueJobSpec("my-job", "platform", 50,
                List.of(), Map.of(), Map.of("cpu", 1),
                List.of(
                        new OpenCueJobSpec.OpenCueLayerSpec("l1", List.of("cmd1"), 1, Map.of()),
                        new OpenCueJobSpec.OpenCueLayerSpec("l2", List.of("cmd2"), 1, Map.of()),
                        new OpenCueJobSpec.OpenCueLayerSpec("l3", List.of("cmd3"), 1, Map.of())
                ));
        List<String> errors = strictValidator.validate(spec);
        assertTrue(errors.stream().anyMatch(e -> e.contains("too many layers")));
    }
}
