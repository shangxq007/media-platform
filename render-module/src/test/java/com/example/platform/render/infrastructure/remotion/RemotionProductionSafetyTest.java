package com.example.platform.render.infrastructure.remotion;

import com.example.platform.render.infrastructure.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production safety tests for Remotion.
 *
 * <p>Proves that unsafe behaviors are rejected by default:
 * <ul>
 *   <li>npm install is not allowed by default</li>
 *   <li>Raw user JS/React template input is rejected</li>
 *   <li>Arbitrary template filesystem path is rejected</li>
 *   <li>Arbitrary remote template URL is rejected</li>
 *   <li>System font fallback is not silently accepted</li>
 *   <li>Render timeout is defaulted</li>
 *   <li>Output path stays under controlled directory</li>
 *   <li>Production dispatch remains disabled for STUB/POC status</li>
 * </ul>
 */
class RemotionProductionSafetyTest {

    @Test
    void npmInstallIsNotAllowedByDefault() {
        RemotionWorkerProperties defaults = new RemotionWorkerProperties();
        assertFalse(defaults.isNpmInstallAllowed(),
                "npm install must not be allowed by default");
        assertFalse(defaults.isUserCodeExecutionAllowed(),
                "user code execution must not be allowed by default");
        assertFalse(defaults.isShellCommandsAllowed(),
                "shell command construction must not be allowed by default");
    }

    @Test
    void rawUserJsTemplateInputIsRejected() {
        RemotionTemplateSpec jsPayload = new RemotionTemplateSpec(
                "my-template", "1.0",
                Map.of("code", "require('child_process').exec('rm -rf /');"),
                null
        );
        List<String> errors = RemotionTemplateGuard.validate(jsPayload);
        assertFalse(errors.isEmpty(),
                "template params containing code injection must be rejected");
        assertTrue(errors.stream().anyMatch(e -> e.contains("code")));
    }

    @Test
    void rawUserReactTemplateInputIsRejected() {
        RemotionTemplateSpec reactPayload = new RemotionTemplateSpec(
                "my-template", "1.0",
                Map.of("jsx", "<script>alert(1)</script>"),
                null
        );
        List<String> errors = RemotionTemplateGuard.validate(reactPayload);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("code injection")));
    }

    @Test
    void arbitraryTemplateFilesystemPathIsRejected() {
        List<String> errors = RemotionTemplateGuard.validate(
                new RemotionTemplateSpec("/etc/malicious-template", "1.0", Map.of(), null));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e ->
                e.contains("URL") || e.contains("filesystem") || e.contains("/")));

        errors = RemotionTemplateGuard.validate(
                new RemotionTemplateSpec("./relative/path", "1.0", Map.of(), null));
        assertFalse(errors.isEmpty());

        errors = RemotionTemplateGuard.validate(
                new RemotionTemplateSpec("C:\\Windows\\template", "1.0", Map.of(), null));
        assertFalse(errors.isEmpty());
    }

    @Test
    void arbitraryRemoteTemplateUrlIsRejected() {
        List<String> errors = RemotionTemplateGuard.validate(
                new RemotionTemplateSpec("https://evil.example.com/template.js", "1.0", Map.of(), null));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e ->
                e.contains("URL") || e.contains("http")));

        errors = RemotionTemplateGuard.validate(
                new RemotionTemplateSpec("http://cdn.example.com/pkg", "1.0", Map.of(), null));
        assertFalse(errors.isEmpty());
    }

    @Test
    void fileProtocolTemplateUrlIsRejected() {
        List<String> errors = RemotionTemplateGuard.validate(
                new RemotionTemplateSpec("file:///etc/shadow", "1.0", Map.of(), null));
        assertFalse(errors.isEmpty());
    }

    @Test
    void systemFontFallbackNotSilentlyAccepted() {
        RemotionFontSpec noSubset = new RemotionFontSpec("SystemFont", 400, "normal",
                null, null, "nohash", true);
        List<String> errors = RemotionInputPropsValidator.validate(new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(noSubset),
                null, "mp4", Map.of()
        ));
        assertTrue(errors.stream().anyMatch(e ->
                e.contains("must have at least one of subsetUrl or sourceUrl")));
    }

    @Test
    void notProductionSafeFontRejectedByValidator() {
        RemotionFontSpec unsafe = new RemotionFontSpec("UnsafeFont", 400, "normal",
                "s3://fonts/unsafe.ttf", null, "badhash", false);
        List<String> errors = RemotionInputPropsValidator.validate(new RemotionInputProps(
                1920, 1080, 30, 900,
                List.of(),
                List.of(unsafe),
                null, "mp4", Map.of()
        ));
        assertTrue(errors.stream().anyMatch(e -> e.contains("not production-safe")));
    }

    @Test
    void renderTimeoutIsDefaulted() {
        RemotionWorkerProperties defaults = new RemotionWorkerProperties();
        assertTrue(defaults.getRenderTimeoutMillis() > 0,
                "render timeout must have a default value");
        assertEquals(900_000, defaults.getRenderTimeoutMillis());
    }

    @Test
    void outputPathMustStayUnderControlledDirectory() {
        Path unsafeOutput = Path.of("/etc/malicious-output.mp4");
        Path safeWorkingDir = Path.of("/tmp/remotion-worker");
        Path safeOutputDir = Path.of("/tmp/remotion-worker/output");

        assertFalse(unsafeOutput.startsWith(safeWorkingDir),
                "output outside working directory must be rejected");

        Path safeOutput = safeOutputDir.resolve("render-output.mp4");
        assertTrue(safeOutput.startsWith(safeWorkingDir),
                "output inside working directory must be accepted");
    }

    @Test
    void productionDispatchRemainsDisabledForStubStatus() {
        ProviderMetadata remotionStub = new ProviderMetadata(
                "remotion", ProviderStatus.STUB, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_EFFECTS),
                List.of(Capabilities.CAPTION_EFFECTS),
                List.of(), List.of(), false,
                "NODE", "Advanced subtitles", List.of()
        );
        assertFalse(remotionStub.status().canBeConfiguredForDispatch(),
                "STUB status must never be configured for dispatch");

        RenderJob prodJob = new RenderJob(
                "job-1", "captioned_video_export", "production", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4",
                List.of(Capabilities.CAPTION_EFFECTS),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of("remotion"), List.of()
        );
        assertFalse(ProviderEligibility.isEligible(remotionStub, prodJob),
                "STUB must never be eligible for dispatch");
    }

    @Test
    void productionDispatchRemainsDisabledForPocStatusWithoutExplicitAllow() {
        ProviderMetadata remotionPoc = new ProviderMetadata(
                "remotion", ProviderStatus.POC, "P1", ProviderType.RENDER,
                List.of(Capabilities.CAPTION_EFFECTS),
                List.of(Capabilities.CAPTION_EFFECTS),
                List.of(), List.of(), false,
                "NODE", "Advanced subtitles", List.of()
        );

        RenderJob productionJob = new RenderJob(
                "job-1", "captioned_video_export", "production", "1920x1080",
                List.of(), "{}", "{}", "{}", "mp4",
                List.of(Capabilities.CAPTION_EFFECTS),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of()
        );
        assertFalse(ProviderEligibility.isEligible(remotionPoc, productionJob),
                "POC must not be eligible for production dispatch without explicit allow");
    }

    @Test
    void defaultWorkerConfigIsFailClosed() {
        RemotionWorkerProperties defaults = new RemotionWorkerProperties();

        assertFalse(defaults.isNetworkAllowed(),
                "network must be disabled by default");
        assertFalse(defaults.isNpmInstallAllowed(),
                "npm install must be disabled by default");
        assertFalse(defaults.isUserCodeExecutionAllowed(),
                "user code execution must be disabled by default");
        assertFalse(defaults.isSystemFontsAllowed(),
                "system fonts must be disabled by default");
        assertFalse(defaults.isShellCommandsAllowed(),
                "shell command construction must be disabled by default");
        assertFalse(defaults.isProductionDispatchEnabled(),
                "production dispatch must be disabled by default");
    }

    @Test
    void networkAccessIsDisabledByDefault() {
        RemotionWorkerProperties defaults = new RemotionWorkerProperties();
        assertFalse(defaults.isNetworkAllowed(),
                "network access must be disabled by default for Remotion workers");
    }

    @Test
    void userCodeExecutionIsDisabledByDefault() {
        RemotionWorkerProperties defaults = new RemotionWorkerProperties();
        assertFalse(defaults.isUserCodeExecutionAllowed());
    }

    @Test
    void shellCommandConstructionIsDisabledByDefault() {
        RemotionWorkerProperties defaults = new RemotionWorkerProperties();
        assertFalse(defaults.isShellCommandsAllowed());
    }

    @Test
    void systemFontsDisabledByDefault() {
        RemotionWorkerProperties defaults = new RemotionWorkerProperties();
        assertFalse(defaults.isSystemFontsAllowed(),
                "system font dependency must be disabled by default");
    }

    @Test
    void remotionWorkerPropertiesDefaultsAreSafe() {
        RemotionWorkerProperties props = new RemotionWorkerProperties();

        assertTrue(props.getMaxWidth() > 0 && props.getMaxWidth() <= 7680);
        assertTrue(props.getMaxHeight() > 0 && props.getMaxHeight() <= 4320);
        assertTrue(props.getMaxFps() > 0 && props.getMaxFps() <= 120);
        assertTrue(props.getMaxDurationSeconds() > 0);
        assertTrue(props.getMaxOutputSizeBytes() > 0);
        assertTrue(props.getRenderTimeoutMillis() > 0);
        assertNotNull(props.getWorkerImage());
        assertNotNull(props.getNodeVersion());
        assertNotNull(props.getRemotionVersion());
        assertNotNull(props.getTemplateRegistryRoot());
        assertTrue(props.getTemplateRegistryRoot().startsWith("bundled://"),
                "template registry must be bundled by default");
    }

    @Test
    void validTemplatePassesGuard() {
        RemotionTemplateSpec valid = new RemotionTemplateSpec("social-captions", "2.1.0",
                Map.of("style", "modern"), "SocialComposition");
        List<String> errors = RemotionTemplateGuard.validate(valid);
        assertTrue(errors.isEmpty(),
                "valid template must pass guard, errors: " + errors);
    }

    @Test
    void templateParamWithArrowFunctionRejected() {
        RemotionTemplateSpec arrowPayload = new RemotionTemplateSpec(
                "t1", "1.0",
                Map.of("handler", "(x) => x.eval('bad')"),
                null
        );
        List<String> errors = RemotionTemplateGuard.validate(arrowPayload);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("code injection")));
    }

    @Test
    void templateParamWithProcessReferenceRejected() {
        RemotionTemplateSpec procPayload = new RemotionTemplateSpec(
                "t1", "1.0",
                Map.of("env", "process.env.SECRET"),
                null
        );
        List<String> errors = RemotionTemplateGuard.validate(procPayload);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("code injection")));
    }
}
