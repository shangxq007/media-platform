#!/usr/bin/env python3
"""Deterministic mutation tests for the render-module zero-awareness guard."""

from __future__ import annotations

from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts/phase19-render-zero-awareness-guard.py"
CATEGORY_FIELDS = (
    "RENDER_MODULE_FFMPEG_PRODUCTION_REFERENCE_COUNT",
    "RENDER_MODULE_FFMPEG_TEST_REFERENCE_COUNT",
    "RENDER_MODULE_FFMPEG_BUILD_TASK_COUNT",
    "RENDER_MODULE_FFMPEG_BINARY_DISCOVERY_COUNT",
    "RENDER_MODULE_FFMPEG_COMMAND_OR_ARGV_COUNT",
    "RENDER_MODULE_FFMPEG_PROCESS_INVOCATION_COUNT",
    "RENDER_MODULE_FFMPEG_CONFIG_COUNT",
    "RENDER_MODULE_FFMPEG_FIXTURE_SCRIPT_COUNT",
    "RENDER_MODULE_TO_FFMPEG_PROVIDER_DEPENDENCY_COUNT",
    "RENDER_MODULE_FFMPEG_COMPATIBILITY_SURFACE_COUNT",
)
AGGREGATE_FIELD = "RENDER_MODULE_CONCRETE_FFMPEG_AWARENESS_COUNT"

# One isolated RED mutation for every category, plus an invalid-UTF-8 tracked
# generated/bin artifact whose concrete awareness exists only in its path.
LEGACY_RED_MUTATIONS: tuple[tuple[str, str, str | bytes, str], ...] = (
    (
        "production-reference-comment",
        "render-module/src/main/java/example/NeutralRenderer.java",
        "package example; // concrete FFmpeg implementation detail\n",
        CATEGORY_FIELDS[0],
    ),
    (
        "test-reference-comment",
        "render-module/src/test/java/example/NeutralRendererTest.java",
        "package example; // this test requires FFPROBE\n",
        CATEGORY_FIELDS[1],
    ),
    (
        "build-task",
        "render-module/build.gradle.kts",
        'tasks.register("nativeMediaTest")\n',
        CATEGORY_FIELDS[2],
    ),
    (
        "binary-discovery",
        "render-module/src/main/java/example/BinaryDiscovery.java",
        'String binary = System.getenv("FFMPEG_PATH");\n',
        CATEGORY_FIELDS[3],
    ),
    (
        "command-or-argv",
        "render-module/src/main/java/example/CommandFactory.java",
        'var argv = List.of("ffmpeg", "-filter_complex", "drawtext=text=owned");\n',
        CATEGORY_FIELDS[4],
    ),
    (
        "process-invocation",
        "render-module/src/main/java/example/ProcessInvoker.java",
        'new ProcessBuilder("ffprobe", "-version").start();\n',
        CATEGORY_FIELDS[5],
    ),
    (
        "configuration",
        "render-module/src/main/resources/application.yml",
        "ffmpeg.path: /usr/bin/ffmpeg\n",
        CATEGORY_FIELDS[6],
    ),
    (
        "fixture-script",
        "render-module/src/test/resources/fixtures/generate-media.sh",
        "#!/bin/sh\nffmpeg -f lavfi -i testsrc fixture.mp4\n",
        CATEGORY_FIELDS[7],
    ),
    (
        "provider-dependency",
        "render-module/build.gradle.kts",
        'implementation(project(":ffmpeg-provider-module"))\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "compatibility-surface",
        "render-module/src/main/java/example/LegacyAdapter.java",
        "final class LegacyFFmpegCompatibilityAdapter {}\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "tracked-generated-bin-path",
        "render-module/bin/main/example/FfPrObEGenerated.class",
        b"\xca\xfe\xba\xbe\xff\x00",
        CATEGORY_FIELDS[0],
    ),
)

# Each correction mutation names one concrete family in isolation and identifies
# the category that must independently become non-zero.
CORRECTION_RED_MUTATIONS: tuple[tuple[str, str, str | bytes, str], ...] = (
    (
        "javacv-runtime-identity",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "final class JavaCVRuntime {}\n",
        CATEGORY_FIELDS[0],
    ),
    (
        "bytedeco-provider-identity",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "final class BytedecoProvider {}\n",
        CATEGORY_FIELDS[0],
    ),
    (
        "javacpp-wrapper-identity",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "final class JavaCppWrapper {}\n",
        CATEGORY_FIELDS[0],
    ),
    (
        "ffmpeg-frame-grabber-wrapper",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "FFmpegFrameGrabber grabber;\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "ffmpeg-frame-recorder-wrapper",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "FFmpegFrameRecorder recorder;\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "frame-grabber-in-javacv-context",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "FrameGrabber grabber; // JavaCV runtime\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "frame-recorder-in-bytedeco-context",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "FrameRecorder recorder; // Bytedeco runtime\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "javacv-frame-grabber-identity",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "JavaCVFrameGrabber grabber;\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "bytedeco-frame-recorder-identity",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "BytedecoFrameRecorder recorder;\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "bytedeco-import-dependency",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "import org.bytedeco.javacv.FFmpegFrameGrabber;\n",
        CATEGORY_FIELDS[8],
    ),
    (
        "javacv-build-dependency",
        "render-module/build.gradle.kts",
        'implementation("org.bytedeco:javacv-platform")\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "javacpp-build-dependency",
        "render-module/build.gradle.kts",
        'implementation("org.bytedeco:javacpp-platform")\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "filtergraph-lowercase-api",
        "render-module/src/main/java/example/NeutralRuntime.java",
        'String filtergraph = "[0:v]scale=1280:720";\n',
        CATEGORY_FIELDS[4],
    ),
    (
        "filtergraph-type-case-api",
        "render-module/src/main/java/example/NeutralRuntime.java",
        'String FilterGraph = "[0:v]scale=1280:720";\n',
        CATEGORY_FIELDS[4],
    ),
    (
        "filter-complex-camel-api",
        "render-module/src/main/java/example/NeutralRuntime.java",
        'String filterComplex = "[0:v]scale=1280:720";\n',
        CATEGORY_FIELDS[4],
    ),
    (
        "filtergraph-upper-api",
        "render-module/src/main/java/example/NeutralRuntime.java",
        'String FILTERGRAPH = "[0:v]scale=1280:720";\n',
        CATEGORY_FIELDS[4],
    ),
    (
        "raw-filtergraph-escape-hatch",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "String rawFilterGraph;\n",
        CATEGORY_FIELDS[4],
    ),
    (
        "custom-filtergraph-escape-hatch",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "String custom_filtergraph;\n",
        CATEGORY_FIELDS[4],
    ),
    (
        "arbitrary-filtergraph-escape-hatch",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "String arbitraryFiltergraph;\n",
        CATEGORY_FIELDS[4],
    ),
    (
        "quoted-subtitles-filter-fragment",
        "render-module/src/main/java/example/NeutralRuntime.java",
        'String filter = "subtitles=filename=/tmp/a.srt";\n',
        CATEGORY_FIELDS[4],
    ),
    (
        "argv-vf-subtitles-pair",
        "render-module/src/main/java/example/NeutralRuntime.java",
        'var argv = java.util.List.of("-vf", "subtitles=/tmp/a.srt");\n',
        CATEGORY_FIELDS[4],
    ),
    (
        "filter-append-subtitles-fragment",
        "render-module/src/main/java/example/NeutralRuntime.java",
        'filter.append("subtitles=");\n',
        CATEGORY_FIELDS[4],
    ),
    (
        "arbitrary-ffmpeg-filtergraph-escape-hatch",
        "render-module/src/main/java/example/NeutralRuntime.java",
        "String ARBITRARY_FFMPEG_FILTERGRAPH;\n",
        CATEGORY_FIELDS[0],
    ),
    *(
        (
            f"quoted-cli-flag-{flag.removeprefix('-').replace(':', '-')}",
            "render-module/src/main/java/example/NeutralRuntime.java",
            f'var argv = java.util.List.of("{flag}");\n',
            CATEGORY_FIELDS[4],
        )
        for flag in (
            "-vf", "-filter_complex", "-c:v", "-c:a", "-pix_fmt",
            "-preset", "-shortest", "-b:a",
        )
    ),
    *(
        (
            f"implementation-token-{token.replace(':', '-')}",
            "render-module/src/main/java/example/NeutralRuntime.java",
            f'var argv = java.util.List.of("{token}");\n',
            CATEGORY_FIELDS[4],
        )
        for token in (
            "libx264", "libx265", "libvpx-vp9", "libaom-av1", "yuv420p",
            "x264-params",
        )
    ),
    (
        "avformat-protocol-prefix",
        "render-module/src/main/java/example/NeutralRuntime.java",
        'String input = "avformat:rtsp";\n',
        CATEGORY_FIELDS[4],
    ),
    *(
        (
            f"config-symbol-{symbol.replace('.', '-')}",
            "render-module/src/main/java/example/RenderSettings.java",
            f'String concreteSetting = "{symbol}";\n',
            CATEGORY_FIELDS[6],
        )
        for symbol in (
            "render.providers.ffmpeg",
            "render.providers.libass",
            "render.providers.javacv",
            "render.subtitle.libass",
            "ffmpegBinary",
            "ffprobeBinary",
            "libassEnabled",
        )
    ),
    *(
        (
            f"concrete-path-{name}",
            f"render-module/src/main/java/example/{name}.java",
            "package example;\n",
            CATEGORY_FIELDS[0],
        )
        for name in (
            "JavaCVRuntime",
            "BytedecoRuntime",
            "JavaCppWrapper",
            "RawFilterGraph",
            "AvformatDemuxer",
            "Libx265Encoder",
            "LibvpxVp9Encoder",
            "LibaomAv1Encoder",
            "Yuv420pPixelFormat",
            "X264Params",
        )
    ),
)

# Regression mutations for the rejected hidden-alias candidate.  These use the
# historical affected paths where path context is part of the ownership rule.
HIDDEN_ALIAS_RED_MUTATIONS: tuple[tuple[str, str, str | bytes, str], ...] = (
    *(
        (
            f"hidden-libass-variant-{index}",
            "render-module/src/main/java/example/NleLayerCatalogService.java",
            f'String layer = "{token}";\n',
            CATEGORY_FIELDS[0],
        )
        for index, token in enumerate((
            "L6_libass", "lib_ass", "lib-ass", "Libass",
        ), start=1)
    ),
    (
        "hidden-subtitle-renderer-alias",
        "render-module/src/main/java/example/TimelineExecutorService.java",
        'String executionOwner = "subtitle renderer";\n',
        CATEGORY_FIELDS[0],
    ),
    (
        "hidden-provider-subtitle-renderer-alias",
        "render-module/src/main/java/example/TimelineRevisionRenderService.java",
        'String executionOwner = "provider-subtitle renderer";\n',
        CATEGORY_FIELDS[0],
    ),
    *(
        (
            f"hidden-subtitle-renderer-casing-or-separator-{index}",
            "render-module/src/main/java/example/TimelineExecutorService.java",
            f'String executionOwner = "{token}";\n',
            CATEGORY_FIELDS[0],
        )
        for index, token in enumerate((
            "SubtitleRenderer", "subtitleRenderer", "subtitle_renderer", "subtitle-renderer",
        ), start=1)
    ),
    (
        "synthetic-subtitle-provider-identity",
        "render-module/src/main/java/example/EffectToProviderMapper.java",
        'String provider = "SubtitleRendererSubtitleProvider";\n',
        CATEGORY_FIELDS[9],
    ),
    (
        "synthetic-overlay-provider-identity",
        "render-module/src/main/java/example/OverlayProvider.java",
        '/** Suitable for: SubtitleRendererOverlayProvider */\n',
        CATEGORY_FIELDS[9],
    ),
    (
        "final-composer-qualified-provider-member",
        "render-module/src/main/java/example/RenderPlannerService.java",
        "var composer = FinalComposerHint.PROVIDER;\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "final-composer-provider-enum-member",
        "render-module/src/main/java/com/example/platform/render/domain/planning/FinalComposerHint.java",
        "enum FinalComposerHint { AUTO, MLT, PROVIDER }\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "final-composer-collapsed-provider-parser",
        "render-module/src/main/java/com/example/platform/render/domain/planning/FinalComposerHint.java",
        'case "provider" -> PROVIDER;\n',
        CATEGORY_FIELDS[9],
    ),
    (
        "opencue-concrete-backend-allowlist",
        "render-module/src/main/java/example/OpenCueSubmissionRequest.java",
        'static final Set<String> CANONICAL_BACKENDS = Set.of("provider", "remotion");\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "opencue-multiline-concrete-backend-allowlist",
        "render-module/src/main/java/com/example/platform/render/domain/environment/OpenCueSubmissionRequest.java",
        'static final Set<String> CANONICAL_BACKENDS = Set.of(\n'
        '        "provider", "remotion");\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "pipeline-task-collapsed-provider-backend",
        "render-module/src/main/java/example/RenderPlannerService.java",
        'var task = PipelineTask.of("id", "transcode", PipelineTaskType.TRANSCODE, "provider", List.of(), Map.of());\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "pipeline-task-multiline-collapsed-provider-backend",
        "render-module/src/main/java/com/example/platform/render/app/planner/RenderPlannerService.java",
        'var task = PipelineTask.of("id", "transcode", PipelineTaskType.TRANSCODE,\n'
        '        "provider", List.of(), Map.of());\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "policy-collapsed-provider-backend",
        "render-module/src/main/java/example/SimpleRenderPolicyEngine.java",
        'var decision = new RenderPolicyDecision("provider", "NORMAL");\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "java2d-collapsed-provider-engine",
        "render-module/src/main/java/example/RenderPlannerService.java",
        'String engine = "java2d+provider";\n',
        CATEGORY_FIELDS[9],
    ),
    (
        "invented-provider-javadoc-example",
        "render-module/src/main/java/example/ProviderStatus.java",
        "/** Example: Provider */\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "deleted-remote-provider-javadoc-example",
        "render-module/src/main/java/example/ProviderStatus.java",
        "/** Example: RemoteRenderProvider */\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "hidden-remote-provider-bean",
        "render-module/src/main/java/com/example/platform/render/infrastructure/remote/RemoteRenderProvider.java",
        '@org.springframework.stereotype.Component("remote-provider")\n'
        'final class RemoteRenderProvider {}\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "hidden-remote-provider-lookup",
        "render-module/src/main/java/com/example/platform/render/app/MultiProviderPipelineService.java",
        'registry.getProvider("remote-provider");\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "collapsed-provider-compatibility-list",
        "render-module/src/main/java/com/example/platform/render/infrastructure/effects/EffectProviderRouter.java",
        'var compatibility = java.util.List.of("provider", "provider");\n',
        CATEGORY_FIELDS[9],
    ),
    (
        "fake-provider-executable-availability",
        "render-module/src/main/java/example/PlanBasedTimelineRevisionRenderService.java",
        'boolean available = toolInventory.isToolAvailable("provider");\n',
        CATEGORY_FIELDS[3],
    ),
    (
        "hidden-native-provider-enum-alias",
        "render-module/src/main/java/example/EffectBackendKind.java",
        "enum EffectBackendKind { NATIVE_PROVIDER }\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "hidden-native-subtitle-renderer-enum-alias",
        "render-module/src/main/java/example/EffectBackendKind.java",
        "enum EffectBackendKind { NATIVE_SUBTITLE_RENDERER }\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "mangled-non-provider-provider-identifier",
        "render-module/src/main/java/example/ProviderStatus.java",
        "static final String NON_PROVIDER_PROVIDER = \"forbidden\";\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "mangled-arbitrary-provider-provider-expression",
        "render-module/src/main/java/example/TemplateOperation.java",
        "static final String ARBITRARY_PROVIDER_PROVIDER_EXPRESSION = \"forbidden\";\n",
        CATEGORY_FIELDS[9],
    ),
    (
        "concrete-pix-fmt-probe-field",
        "render-module/src/main/java/com/example/platform/render/infrastructure/ColorProbeMetadataExtractor.java",
        'String pixelFormat = fields.get("pix_fmt");\n',
        CATEGORY_FIELDS[0],
    ),
    (
        "hidden-provider-command-plan-how-alias",
        "render-module/src/main/java/example/ProviderExecutionDocumentDraftType.java",
        "enum ProviderExecutionDocumentDraftType { PROVIDER_COMMAND_PLAN } // args/filter graph command HOW\n",
        CATEGORY_FIELDS[4],
    ),
    (
        "former-step-executor-process-runner-ownership",
        "render-module/src/main/java/com/example/platform/render/app/timeline/compile/RenderExecutionStepExecutor.java",
        "import com.example.platform.shared.process.ProcessToolRunner;\n",
        CATEGORY_FIELDS[5],
    ),
    (
        "former-step-executor-tool-request-ownership",
        "render-module/src/main/java/com/example/platform/render/app/timeline/compile/RenderExecutionStepExecutor.java",
        "import com.example.platform.shared.process.ToolExecutionRequest;\n",
        CATEGORY_FIELDS[5],
    ),
    (
        "former-timeline-service-process-runner-ownership",
        "render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRevisionRenderService.java",
        "import com.example.platform.shared.process.ProcessToolRunner;\n",
        CATEGORY_FIELDS[5],
    ),
    (
        "former-timeline-service-tool-request-ownership",
        "render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRevisionRenderService.java",
        "import com.example.platform.shared.process.ToolExecutionRequest;\n",
        CATEGORY_FIELDS[5],
    ),
    (
        "local-runner-provider-name-identity-equality",
        "render-module/src/main/java/com/example/platform/render/app/timeline/compile/LocalExecutionPlanRunner.java",
        'if ("provider".equals(step.providerName())) { return false; }\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "plan-compiler-provider-name-identity-equality",
        "render-module/src/main/java/com/example/platform/render/app/timeline/compile/RenderExecutionPlanCompiler.java",
        'if ("provider".equalsIgnoreCase(providerRef.providerName())) { return LOCAL; }\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "export-preset-hardcoded-provider-identity",
        "render-module/src/main/java/com/example/platform/render/infrastructure/ExportPolicyService.java",
        'var preset = new ExportPreset("free", "Free", "1280x720", 30, "mp4", "h264", "aac", true, "FREE", "provider");\n',
        CATEGORY_FIELDS[8],
    ),
    (
        "export-policy-provider-fallback",
        "render-module/src/main/java/com/example/platform/render/infrastructure/ExportPolicyService.java",
        'return tier.level() >= 2 ? "ofx" : "provider";\n',
        CATEGORY_FIELDS[8],
    ),
)

RED_MUTATIONS = (
    LEGACY_RED_MUTATIONS
    + CORRECTION_RED_MUTATIONS
    + HIDDEN_ALIAS_RED_MUTATIONS
)

NEUTRAL_SOURCE = """\
package example;

interface RenderProvider {}
record ProviderId(String value) {}
record ProviderImplementationId(String value) {}
final class ProviderPluginRuntime {}
final class BinaryLocator { String locate(String executableName) { return executableName; } }
record CommandSpec(java.util.List<String> argv) {}
interface ProcessInvoker { int invoke(CommandSpec command); }
interface FilterGraph {}
record RenderConfiguration(String executablePath) {}
final class FixtureScript {}
final class CompatibilityAdapter {}
record SubtitleTrack(String text) {}
"""

NEUTRAL_MEDIA_STANDARDS_SOURCE = """\
package example;

record SemanticMediaSpec(String videoCodec, String audioCodec, String container) {}
final class SemanticRequest {
    final SemanticMediaSpec media = new SemanticMediaSpec("H264", "AAC", "MP4");
}
"""

NEUTRAL_FALSE_POSITIVE_FIXTURES: tuple[tuple[str, str], ...] = (
    (
        "timeline-text-overlay-subtitle-variable",
        "TimelineTextOverlay subtitle = timelineTextOverlay;\n",
    ),
    (
        "timeline-track-subtitle-variable",
        "TimelineTrack subtitle = timelineTrack;\n",
    ),
    (
        "subtitles-log-placeholder",
        'logger.info("subtitles={}", subtitles);\n',
    ),
    (
        "generic-filter-graph-capability",
        'String capability = "filter-graph";\n',
    ),
    (
        "generic-filter-graph-type",
        "FilterGraph graph = typedGraph;\n",
    ),
)


class SyntheticGitRepository:
    def __init__(self, root: Path) -> None:
        self.root = root
        subprocess.run(
            ["git", "init", "-q", str(root)], check=True,
            text=True, capture_output=True)

    def track(self, path: str, content: str | bytes) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, bytes):
            target.write_bytes(content)
        else:
            target.write_text(content, encoding="utf-8")
        subprocess.run(
            ["git", "-C", str(self.root), "add", "--", path], check=True,
            text=True, capture_output=True)

    def write_untracked(self, path: str, content: str) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")


class Phase19RenderZeroAwarenessGuardTest(unittest.TestCase):
    maxDiff = None

    def run_guard(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(GUARD), "--root", str(root)],
            cwd=root, text=True, capture_output=True, check=False)

    def counts(self, result: subprocess.CompletedProcess[str]) -> dict[str, int]:
        parsed: dict[str, int] = {}
        for field in (*CATEGORY_FIELDS, AGGREGATE_FIELD):
            occurrences = re.findall(
                rf"(?m)^{re.escape(field)}=(\d+)$", result.stdout)
            self.assertEqual(
                1, len(occurrences),
                f"expected exactly one {field} line\n{result.stdout}\n{result.stderr}")
            parsed[field] = int(occurrences[0])
        return parsed

    def make_clean_repository(self, directory: str) -> SyntheticGitRepository:
        repository = SyntheticGitRepository(Path(directory))
        repository.track(
            "render-module/src/main/java/example/NeutralRuntime.java",
            NEUTRAL_SOURCE)
        return repository

    def assert_clean_pass(self, result: subprocess.CompletedProcess[str]) -> None:
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        counts = self.counts(result)
        self.assertTrue(all(counts[field] == 0 for field in CATEGORY_FIELDS))
        self.assertEqual(0, counts[AGGREGATE_FIELD])
        self.assertIn(
            "RENDER_MODULE_CONCRETE_FFMPEG_ZERO_AWARENESS=PASS",
            result.stdout)

    def test_clean_neutral_provider_fixture_passes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="phase19-render-zero-clean-") as directory:
            repository = self.make_clean_repository(directory)
            self.assert_clean_pass(self.run_guard(repository.root))

    def test_provider_neutral_media_standards_pass(self) -> None:
        with tempfile.TemporaryDirectory(prefix="phase19-render-zero-standards-") as directory:
            repository = SyntheticGitRepository(Path(directory))
            repository.track(
                "render-module/src/main/java/example/SemanticRequest.java",
                NEUTRAL_MEDIA_STANDARDS_SOURCE)
            self.assert_clean_pass(self.run_guard(repository.root))

    def test_provider_neutral_false_positive_fixtures_pass(self) -> None:
        for name, content in NEUTRAL_FALSE_POSITIVE_FIXTURES:
            with self.subTest(fixture=name):
                with tempfile.TemporaryDirectory(
                        prefix=f"phase19-render-zero-neutral-{name}-") as directory:
                    repository = self.make_clean_repository(directory)
                    repository.track(
                        "render-module/src/main/java/example/NeutralSemantics.java",
                        content)
                    self.assert_clean_pass(self.run_guard(repository.root))

    def test_each_category_and_tracked_generated_path_mutates_red(self) -> None:
        for name, path, content, target_field in RED_MUTATIONS:
            with self.subTest(mutation=name):
                with tempfile.TemporaryDirectory(
                        prefix=f"phase19-render-zero-{name}-") as directory:
                    repository = self.make_clean_repository(directory)
                    repository.track(path, content)

                    result = self.run_guard(repository.root)

                    self.assertNotEqual(0, result.returncode)
                    counts = self.counts(result)
                    self.assertGreater(counts[target_field], 0)
                    self.assertEqual(
                        sum(counts[field] for field in CATEGORY_FIELDS),
                        counts[AGGREGATE_FIELD])
                    self.assertNotIn(
                        "RENDER_MODULE_CONCRETE_FFMPEG_ZERO_AWARENESS=PASS",
                        result.stdout)

    def test_untracked_concrete_file_is_not_repository_authority(self) -> None:
        with tempfile.TemporaryDirectory(prefix="phase19-render-zero-untracked-") as directory:
            repository = self.make_clean_repository(directory)
            repository.write_untracked(
                "render-module/src/main/java/example/UntrackedFfmpegLeak.java",
                "// FFmpeg is intentionally untracked\n")
            self.assert_clean_pass(self.run_guard(repository.root))

    def test_output_field_order_is_deterministic(self) -> None:
        with tempfile.TemporaryDirectory(prefix="phase19-render-zero-order-") as directory:
            repository = self.make_clean_repository(directory)
            first = self.run_guard(repository.root)
            second = self.run_guard(repository.root)
            self.assertEqual(0, first.returncode, first.stdout + first.stderr)
            self.assertEqual(first.stdout, second.stdout)
            self.assertEqual(first.stderr, second.stderr)


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(
        Phase19RenderZeroAwarenessGuardTest)
    outcome = unittest.TextTestRunner(verbosity=2).run(suite)
    if outcome.wasSuccessful():
        print(f"PHASE19_RENDER_ZERO_AWARENESS_MUTATION_COUNT={len(RED_MUTATIONS)}")
        print("PHASE19_RENDER_ZERO_AWARENESS_MUTATION_TESTS=PASS")
    raise SystemExit(0 if outcome.wasSuccessful() else 1)
