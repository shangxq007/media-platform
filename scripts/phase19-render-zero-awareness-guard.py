#!/usr/bin/env python3
"""Enforce concrete-runtime-provider neutrality in tracked render-module surfaces.

Each finding is a unique ``(category, path, line, pattern-id)`` tuple.  Path-name
findings use line zero.  The authoritative awareness count is the deterministic
sum of the ten category counts, so one line may deliberately contribute to more
than one category when it represents more than one kind of concrete awareness.
"""

from __future__ import annotations

import argparse
from collections.abc import Iterable
from pathlib import Path
import re
import subprocess
import sys


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

PRODUCTION = CATEGORY_FIELDS[0]
TEST = CATEGORY_FIELDS[1]
BUILD = CATEGORY_FIELDS[2]
BINARY = CATEGORY_FIELDS[3]
COMMAND = CATEGORY_FIELDS[4]
PROCESS = CATEGORY_FIELDS[5]
CONFIG = CATEGORY_FIELDS[6]
FIXTURE = CATEGORY_FIELDS[7]
DEPENDENCY = CATEGORY_FIELDS[8]
COMPATIBILITY = CATEGORY_FIELDS[9]

Pattern = tuple[str, re.Pattern[str]]
Finding = tuple[str, str, int, str]


def patterns(definitions: Iterable[tuple[str, str]]) -> tuple[Pattern, ...]:
    return tuple(
        (name, re.compile(expression, re.IGNORECASE))
        for name, expression in definitions
    )


RAW_FILTERGRAPH_ESCAPE_PATTERN = (
    r"(?:\b(?:raw|custom|arbitrary)(?:[_-]?ffmpeg)?[_-]?filter[_-]?graph\b"
    r"|\bffmpeg[_-]?(?:raw|custom|arbitrary)[_-]?filter[_-]?graph\b)"
)
CONCRETE_FILTERGRAPH_API_PATTERN = (
    rf"(?:{RAW_FILTERGRAPH_ESCAPE_PATTERN}"
    r"|\bfilter[_-]?(?:graph|complex)\b\s*[:=]\s*[\"']\s*"
    r"\[[0-9]+:[avsd]\])"
)
CONCRETE_SUBTITLES_FILTER_PATTERN = (
    r"(?:(?:[\"']|[,;])\s*subtitles\s*=\s*"
    r"(?:[\"']|(?:filename\s*=\s*)?"
    r"(?:[/~.]|[A-Za-z]:[\\/]|[^\"'\s={}]+\.(?:srt|ass|ssa)\b))"
    r"|[\"']-vf[\"']\s*,\s*[\"']subtitles\s*="
    r"|(?:^|\s)-vf\s+[\"']?subtitles\s*=)"
)


# These are concrete mechanics, not neutral abstractions such as ProviderId,
# CommandSpec, ProcessInvoker, BinaryLocator, or SubtitleTrack.
AWARENESS_PATTERNS = patterns((
    ("ffmpeg-or-ffprobe", r"ff[._-]?(?:mpeg|probe)"),
    ("libass", r"(?<![A-Za-z0-9])lib[_-]?ass(?![A-Za-z0-9])"),
    ("hidden-subtitle-renderer-alias", r"\bsubtitle[\s_-]*renderer"),
    ("concrete-probe-pix-fmt-field", r"[\"']pix_fmt[\"']"),
    ("x264", r"\b(?:lib)?x264\b"),
    ("javacv-runtime", r"\b(?:java[_-]?cv|bytedeco|java[_-]?cpp)(?:runtime|provider|wrapper|plugin|adapter|frame(?:grabber|recorder))?\b"),
    ("raw-filtergraph", RAW_FILTERGRAPH_ESCAPE_PATTERN),
    ("libx265", r"\blibx265(?:encoder|codec|implementation)?\b"),
    ("libvpx", r"\blibvpx[A-Za-z0-9_-]*\b"),
    ("libaom", r"\blibaom[A-Za-z0-9_-]*\b"),
    ("yuv420p", r"\byuv420p(?:pixelformat)?\b"),
    ("x264-params", r"\bx264[-_]?params\b"),
    ("avformat", r"\bavformat(?::|(?:demuxer|protocol|input|source)\b)"),
    ("filter-complex", r"(?:-filter_complex|filter[_-]?complex)\b"),
    ("drawtext-filter", r"\bdrawtext\b"),
    ("subtitles-filter", CONCRETE_SUBTITLES_FILTER_PATTERN),
    ("ass-filter", r"(?:\bass\s*=|\bass\s+filter\b|\bfilter[^\n]*\bass\b)"),
    ("native-media-test", r"\bnative[_-]?media[_-]?test\b"),
    ("native-media-owner", r"\bnative-media\b"),
))

BUILD_PATTERNS = patterns((
    ("concrete-build-task", r"\b(?:tasks?\s*\.|register\s*\(|named\s*\(|dependsOn\b)[^\n]*(?:ff[._-]?(?:mpeg|probe)|native[_-]?media)"),
    ("native-media-test-task", r"\bnative[_-]?media[_-]?test\b"),
    ("native-media-task-owner", r"\bnative-media\b"),
))

BINARY_PATTERNS = patterns((
    ("concrete-binary-name", r"\bff[._-]?(?:mpeg|probe)(?:Binary|Executable|Path|Home|Locator|Resolver|Discovery|Available)\b"),
    ("concrete-binary-env", r"\bFF(?:MPEG|PROBE)_(?:BIN(?:ARY)?|PATH|HOME|EXECUTABLE)\b"),
    ("concrete-binary-property", r"\bff[._-]?(?:mpeg|probe)[._-](?:bin(?:ary)?|path|home|executable)\b"),
    ("concrete-executable-path", r"(?:^|[\s\"'=])(?:/[^\s\"']*/|[A-Za-z]:[\\/][^\s\"']*[\\/])ff(?:mpeg|probe)(?:\.exe)?\b"),
    ("concrete-binary-discovery-command", r"\b(?:command\s+-v|which|where|where\.exe)\s+ff(?:mpeg|probe)(?:\.exe)?\b"),
    ("concrete-executable-check", r"\b(?:isExecutable|isRegularFile|exists|findExecutable|resolveExecutable)[^\n]*ff[._-]?(?:mpeg|probe)\b"),
    ("concrete-availability-check", r"\b(?:assume|check|detect|discover|locate|resolve|find)[A-Za-z0-9_]*(?:Ffmpeg|Ffprobe)[A-Za-z0-9_]*(?:Available|Binary|Executable|Path)?\b"),
    ("fake-provider-tool-availability", r"\bisToolAvailable\s*\(\s*[\"']provider[\"']\s*\)"),
))

BINARY_CONTEXT_PATTERNS = patterns((
    ("executable-check-in-concrete-file", r"\b(?:Files\s*\.\s*)?isExecutable\s*\("),
    ("binary-locator-in-concrete-file", r"\b(?:Binary|Executable)(?:Locator|Resolver|Discovery)\b"),
    ("path-lookup-in-concrete-file", r"\b(?:find|locate|resolve|discover)[A-Za-z0-9_]*(?:Binary|Executable|Path)\s*\("),
))

COMMAND_PATTERNS = patterns((
    ("concrete-command-container", r"\b(?:cmd|command|commandLine|argv|args|arguments)\b[^\n]*ff[._-]?(?:mpeg|probe)\b"),
    ("concrete-command-factory", r"\b(?:FFmpeg|Ffmpeg|Ffprobe)[A-Za-z0-9_]*(?:Command|Args|Argv|Options|Invocation)\b"),
    ("filtergraph-api", CONCRETE_FILTERGRAPH_API_PATTERN),
    ("filter-complex-argument", r"(?:-filter_complex|filter[_-]?complex)\b"),
    ("quoted-cli-flag", r"[\"']-(?:vf|filter_complex|c:v|c:a|pix_fmt|preset|shortest|b:a)[\"']"),
    ("concrete-cli-implementation", r"\b(?:libx265|libvpx[A-Za-z0-9_-]*|libaom[A-Za-z0-9_-]*|yuv420p)\b"),
    ("avformat-protocol", r"\bavformat:"),
    ("drawtext-argument", r"\bdrawtext\s*="),
    ("subtitles-argument", CONCRETE_SUBTITLES_FILTER_PATTERN),
    ("ass-argument", r"\bass\s*="),
    ("x264-argument", r"\b(?:lib)?x264\b"),
    ("hidden-provider-command-plan", r"\bPROVIDER_COMMAND_PLAN\b"),
))

PROCESS_PATTERNS = patterns((
    ("direct-concrete-process", r"\b(?:ProcessBuilder|Runtime\s*\.\s*getRuntime\s*\(\)\s*\.\s*exec|exec|spawn|popen)[^\n]*ff[._-]?(?:mpeg|probe)\b"),
    ("concrete-process-runner", r"\b(?:FFmpeg|Ffmpeg|Ffprobe)[A-Za-z0-9_]*(?:Process|Runner|Executor|Invoker)\b"),
    ("concrete-process-invocation", r"\b(?:run|execute|invoke|start)[A-Za-z0-9_]*(?:Ffmpeg|Ffprobe)[A-Za-z0-9_]*\s*\("),
))

PROCESS_CONTEXT_PATTERNS = patterns((
    ("process-builder-in-concrete-file", r"\bnew\s+ProcessBuilder\b"),
    ("runtime-exec-in-concrete-file", r"\bRuntime\s*\.\s*getRuntime\s*\(\)\s*\.\s*exec\b"),
    ("tool-run-in-concrete-file", r"\b(?:tool|process)[A-Za-z0-9_]*(?:Runner|Invoker|Executor)?\s*\.\s*(?:run|execute|invoke)\s*\("),
))

COMMAND_CONTEXT_PATTERNS = patterns((
    ("argv-list-in-concrete-file", r"\b(?:List\s*\.\s*of|Arrays\s*\.\s*asList)\s*\("),
    ("argv-add-in-concrete-file", r"\b(?:cmd|command|argv|args|arguments)\s*\.\s*add(?:All)?\s*\("),
    ("command-request-in-concrete-file", r"\b(?:CommandSpec|ToolExecutionRequest|ProcessInvocationSpec)\b"),
))

CONFIG_PATTERNS = patterns((
    ("concrete-config-key", r"\bff[._-]?(?:mpeg|probe)[._-](?:enabled|timeout|path|binary|executable|home|options?|args?|command)\b"),
    ("concrete-config-symbol", r"\b(?:FFmpeg|Ffmpeg|Ffprobe)[A-Za-z0-9_]*(?:Config|Configuration|Properties|Settings)\b"),
    ("concrete-config-access", r"\b(?:getenv|getProperty|@Value|configurationProperties)[^\n]*ff[._-]?(?:mpeg|probe)\b"),
    ("concrete-render-provider-config", r"\brender\.providers\.(?:ffmpeg|libass|javacv)\b"),
    ("concrete-render-subtitle-config", r"\brender\.subtitle\.libass\b"),
    ("concrete-config-owner-symbol", r"\b(?:ffmpegBinary|ffprobeBinary|libassEnabled)\b"),
))

CONFIG_CONTEXT_PATTERNS = patterns((
    ("value-in-concrete-file", r"@Value\s*\("),
    ("environment-in-concrete-file", r"\b(?:System\s*\.\s*)?(?:getenv|getProperty)\s*\("),
    ("configuration-properties-in-concrete-file", r"@ConfigurationProperties\b"),
))

FIXTURE_PATTERNS = patterns((
    ("concrete-fixture", r"\b(?:fixture|sample|smoke|testsrc|lavfi|generator)[A-Za-z0-9_. -]*ff[._-]?(?:mpeg|probe)\b"),
    ("concrete-fixture-reverse", r"\bff[._-]?(?:mpeg|probe)[A-Za-z0-9_. -]*(?:fixture|sample|smoke|testsrc|lavfi|generator)\b"),
    ("concrete-lavfi-command", r"\b(?:testsrc|lavfi)\b"),
))

DEPENDENCY_PATTERNS = patterns((
    ("concrete-provider-module", r"\bffmpeg-provider-module\b"),
    ("concrete-provider-package", r"\bcom\.example\.platform\.ffmpeg(?:\.|\b)"),
    ("concrete-provider-id", r"\bProviderId\s*\.\s*of\s*\(\s*[\"']ffmpeg[\"']"),
    ("concrete-implementation-id", r"\bProviderImplementationId\s*\.\s*of\s*\(\s*[\"'][^\"']*ffmpeg[^\"']*[\"']"),
    ("concrete-plugin-id", r"\b(?:plugin|provider|implementation)[._-]?(?:id|module)?[^\n]*[\"'][^\"']*ffmpeg[^\"']*[\"']"),
    ("concrete-dependency-declaration", r"\b(?:implementation|api|compileOnly|runtimeOnly|testImplementation|dependency|dependsOn)[^\n]*ff[._-]?(?:mpeg|probe)"),
    ("bytedeco-package", r"\borg\.bytedeco\.(?:javacv|javacpp)(?:\.|\b)"),
    ("javacv-dependency-declaration", r"\b(?:implementation|api|compileOnly|runtimeOnly|testImplementation|dependency|dependsOn)[^\n]*(?:bytedeco|javacv|javacpp)\b"),
    ("hardcoded-provider-registry-lookup", r"\bgetProvider\s*\(\s*[\"']provider[\"']\s*\)"),
    ("collapsed-provider-canonical-backend-list", r"\bCANONICAL_BACKENDS\b[^\n]*\bSet\s*\.\s*of\s*\([^\n]*[\"']provider[\"']"),
    ("collapsed-provider-pipeline-task-backend", r"\b(?:PipelineTask\s*\.\s*of|new\s+PipelineTask)\s*\([^\n]*[\"']provider[\"']"),
    ("collapsed-provider-policy-decision", r"\bnew\s+RenderPolicyDecision\s*\(\s*[\"']provider[\"']"),
))

COMPATIBILITY_PATTERNS = patterns((
    ("concrete-fallback", r"\b(?:fallback|fallBack)[A-Za-z0-9_. -]*ff[._-]?(?:mpeg|probe)\b"),
    ("concrete-compatibility", r"\b(?:legacy|compat(?:ibility)?|alias|wrapper)[A-Za-z0-9_. -]*ff[._-]?(?:mpeg|probe)\b"),
    ("concrete-compatibility-reverse", r"\bff[._-]?(?:mpeg|probe)[A-Za-z0-9_. -]*(?:legacy|compat(?:ibility)?|alias|wrapper)\b"),
    ("concrete-compatibility-symbol", r"\b(?:Legacy)?(?:FFmpeg|Ffmpeg|Ffprobe)[A-Za-z0-9_]*(?:Adapter|Bridge|Builder|Compatibility|Fallback|Interface|Legacy|Provider|Runner|Tool|Validator|Wrapper)\b"),
    ("ffmpeg-frame-wrapper", r"\bFFmpegFrame(?:Grabber|Recorder)\b"),
    ("javacv-frame-wrapper", r"(?:\b(?:javacv|bytedeco|javacpp)Frame(?:Grabber|Recorder)\b|\b(?:javacv|bytedeco|javacpp)\b[^\n]*\bFrame(?:Grabber|Recorder)\b|\bFrame(?:Grabber|Recorder)\b[^\n]*\b(?:javacv|bytedeco|javacpp)\b)"),
    ("collapsed-provider-list", r"\bList\s*\.\s*of\s*\(\s*[\"']provider[\"']\s*,\s*[\"']provider[\"']"),
    ("hidden-native-provider-alias", r"\bNATIVE_PROVIDER\b"),
    ("hidden-native-subtitle-renderer-alias", r"\bNATIVE_SUBTITLE_RENDERER\b"),
    ("mangled-non-provider-provider", r"\bNON_PROVIDER_PROVIDER\b"),
    ("mangled-arbitrary-provider-provider-expression", r"\bARBITRARY_PROVIDER_PROVIDER_EXPRESSION\b"),
    ("synthetic-subtitle-provider-name", r"\bSubtitleRenderer(?:Subtitle|Overlay)Provider\b"),
    ("final-composer-provider-member", r"\bFinalComposerHint\s*\.\s*PROVIDER\b"),
    ("final-composer-provider-enum-member", r"\bFinalComposerHint\s*\{[^\n}]*\bPROVIDER\b"),
    ("final-composer-provider-parser-collapse", r"\bcase\s+[\"']provider[\"']\s*->\s*PROVIDER\b"),
    ("java2d-provider-composite-identity", r"\bjava2d\+provider\b"),
    ("invented-provider-javadoc-example", r"\bExample:\s*Provider\b"),
    ("deleted-remote-provider-javadoc-example", r"\bExample:\s*RemoteRenderProvider\b"),
))

# These aliases/types are forbidden only in the historical concrete execution
# paths that owned the removed runtime.  Keeping the path qualifier avoids
# rejecting legitimate process ownership in named provider implementations.
CONTEXTUAL_PATH_RULES: tuple[tuple[re.Pattern[str], str, tuple[Pattern, ...]], ...] = (
    (
        re.compile(
            r"^render-module/src/main/java/com/example/platform/render/domain/planning/"
            r"FinalComposerHint\.java$"),
        COMPATIBILITY,
        patterns((
            ("final-composer-provider-enum-member", r"^\s*PROVIDER\s*[,;]"),
            ("final-composer-provider-parser-collapse", r"\bcase\s+[\"']provider[\"']\s*->\s*PROVIDER\b"),
        )),
    ),
    (
        re.compile(
            r"^render-module/src/main/java/com/example/platform/render/(?:domain/environment/"
            r"OpenCueSubmissionRequest|infrastructure/environment/OpenCueExecutionEnvironment|"
            r"app/timeline/RenderJobRevisionPinningService)\.java$"),
        DEPENDENCY,
        patterns((("render-owned-concrete-backend-allowlist", r"\bCANONICAL_BACKENDS\b"),)),
    ),
    (
        re.compile(
            r"^render-module/src/main/java/com/example/platform/render/app/planner/"
            r"RenderPlannerService\.java$"),
        DEPENDENCY,
        patterns((("collapsed-provider-planner-backend", r"[\"']provider[\"']"),)),
    ),
    (
        re.compile(r"^render-module/src/main/java/com/example/platform/render/infrastructure/remote/RemoteRenderProvider\.java$"),
        DEPENDENCY,
        patterns((("hidden-remote-concrete-provider-file", r".*"),)),
    ),
    (
        re.compile(r"^render-module/src/main/java/com/example/platform/render/app/MultiProviderPipelineService\.java$"),
        DEPENDENCY,
        patterns((
            ("hidden-remote-provider-selection", r"[\"']remote-provider[\"']"),
            ("hardcoded-provider-registry-lookup", r"getProvider\s*\(\s*[\"']provider[\"']\s*\)"),
        )),
    ),
    (
        re.compile(
            r"^render-module/src/main/java/com/example/platform/render/app/timeline/compile/"
            r"(?:LocalExecutionPlanRunner|RenderExecutionPlanCompiler)\.java$"),
        DEPENDENCY,
        patterns((
            (
                "provider-name-literal-identity-equality",
                r"[\"']provider[\"']\s*\.\s*equals(?:IgnoreCase)?\s*\("
                r"[^)]*providerName\s*\(\s*\)\s*\)",
            ),
        )),
    ),
    (
        re.compile(
            r"^render-module/src/main/java/com/example/platform/render/infrastructure/"
            r"ExportPolicyService\.java$"),
        DEPENDENCY,
        patterns((
            (
                "export-preset-hardcoded-provider-identity",
                r"(?:new\s+ExportPreset\s*\([^\n]*[\"']provider[\"']\s*\)"
                r"|[\"'](?:FREE|PRO|TEAM|ENTERPRISE|EXPERIMENTAL)[\"']\s*,\s*"
                r"[\"']provider[\"']\s*\)\s*\))",
            ),
            (
                "export-policy-provider-fallback",
                r"\breturn\b[^\n;]*[\"']provider[\"'][^\n;]*;",
            ),
        )),
    ),
    (
        re.compile(r"^render-module/src/main/java/com/example/platform/render/app/timeline/(?:compile/RenderExecutionStepExecutor|TimelineRevisionRenderService)\.java$"),
        PROCESS,
        patterns((
            ("former-concrete-process-runner-ownership", r"\bProcessToolRunner\b"),
            ("former-concrete-tool-request-ownership", r"\bToolExecutionRequest\b"),
            ("former-concrete-tool-result-ownership", r"\bToolExecutionResult\b"),
        )),
    ),
)


def matches(value: str, candidates: tuple[Pattern, ...]) -> list[str]:
    return [name for name, expression in candidates if expression.search(value)]


def is_test_surface(path: str) -> bool:
    lowered = path.lower()
    name = Path(path).name.lower()
    return (
        "/src/test/" in f"/{lowered}/"
        or "/testfixtures/" in f"/{lowered}/"
        or "/test-fixtures/" in f"/{lowered}/"
        or name.startswith("test_")
        or bool(re.search(r"(?:test|tests|spec)\.[^.]+$", name))
    )


def is_build_surface(path: str) -> bool:
    lowered = path.lower()
    name = Path(path).name.lower()
    return (
        name in {"pom.xml", "build.xml", "settings.gradle", "settings.gradle.kts"}
        or name.startswith("build.gradle")
        or name in {"makefile", "cmakelists.txt"}
        or "/buildsrc/" in f"/{lowered}/"
        or "/gradle/" in f"/{lowered}/"
    )


def is_config_surface(path: str) -> bool:
    lowered = path.lower()
    suffix = Path(path).suffix.lower()
    return (
        suffix in {".conf", ".config", ".ini", ".json", ".properties", ".toml", ".yaml", ".yml"}
        or "/config/" in f"/{lowered}/"
        or "/configuration/" in f"/{lowered}/"
    )


def is_fixture_script_surface(path: str) -> bool:
    lowered = path.lower()
    suffix = Path(path).suffix.lower()
    return (
        suffix in {".bat", ".cmd", ".ps1", ".sh"}
        or any(token in lowered for token in ("fixture", "testdata", "test-data", "sample-media", "smoke"))
        or "/src/test/resources/" in f"/{lowered}/"
    )


def add_findings(
        findings: set[Finding], category: str, path: str, line: int,
        pattern_names: Iterable[str]) -> None:
    findings.update((category, path, line, pattern_name) for pattern_name in pattern_names)


def tracked_render_paths(root: Path) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--", "render-module"],
        check=True, capture_output=True)
    paths = {
        raw.decode("utf-8", errors="surrogateescape")
        for raw in result.stdout.split(b"\0") if raw
    }
    return sorted(paths)


def read_tracked_worktree_bytes(root: Path, path: str) -> bytes | None:
    worktree_path = root / path
    try:
        return worktree_path.read_bytes()
    except (FileNotFoundError, IsADirectoryError, OSError):
        result = subprocess.run(
            ["git", "-C", str(root), "show", f":{path}"],
            check=False, capture_output=True)
        return result.stdout if result.returncode == 0 else None


def scan(root: Path) -> dict[str, int]:
    findings: set[Finding] = set()
    for path in tracked_render_paths(root):
        test_surface = is_test_surface(path)
        build_surface = is_build_surface(path)
        config_surface = is_config_surface(path)
        fixture_script_surface = is_fixture_script_surface(path)
        path_awareness = matches(path, AWARENESS_PATTERNS)
        if path_awareness:
            reference_category = TEST if test_surface else PRODUCTION
            add_findings(findings, reference_category, path, 0, path_awareness)

        path_categories = (
            (BUILD, BUILD_PATTERNS),
            (BINARY, BINARY_PATTERNS),
            (COMMAND, COMMAND_PATTERNS),
            (PROCESS, PROCESS_PATTERNS),
            (CONFIG, CONFIG_PATTERNS),
            (FIXTURE, FIXTURE_PATTERNS),
            (DEPENDENCY, DEPENDENCY_PATTERNS),
            (COMPATIBILITY, COMPATIBILITY_PATTERNS),
        )
        for category, candidates in path_categories:
            add_findings(findings, category, path, 0, matches(path, candidates))
        contextual_rules = tuple(
            (category, candidates)
            for path_pattern, category, candidates in CONTEXTUAL_PATH_RULES
            if path_pattern.search(path)
        )
        for category, candidates in contextual_rules:
            add_findings(findings, category, path, 0, matches(path, candidates))

        raw = read_tracked_worktree_bytes(root, path)
        if raw is None:
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            # Binary/generated authority is still covered by its tracked path.
            continue

        lines = text.splitlines()
        file_is_aware = bool(path_awareness) or any(
            matches(line, AWARENESS_PATTERNS) for line in lines)
        for line_number, line in enumerate(lines, start=1):
            awareness = matches(line, AWARENESS_PATTERNS)
            if awareness:
                reference_category = TEST if (
                    test_surface
                    or any(name.startswith("native-media") for name in awareness)
                ) else PRODUCTION
                add_findings(findings, reference_category, path, line_number, awareness)

            specialized = (
                (BUILD, BUILD_PATTERNS),
                (BINARY, BINARY_PATTERNS),
                (COMMAND, COMMAND_PATTERNS),
                (PROCESS, PROCESS_PATTERNS),
                (CONFIG, CONFIG_PATTERNS),
                (FIXTURE, FIXTURE_PATTERNS),
                (DEPENDENCY, DEPENDENCY_PATTERNS),
                (COMPATIBILITY, COMPATIBILITY_PATTERNS),
            )
            for category, candidates in specialized:
                add_findings(findings, category, path, line_number, matches(line, candidates))
            for category, candidates in contextual_rules:
                add_findings(findings, category, path, line_number, matches(line, candidates))

            if build_surface and awareness:
                add_findings(findings, BUILD, path, line_number,
                             (f"build-awareness:{name}" for name in awareness))
            if config_surface and awareness:
                add_findings(findings, CONFIG, path, line_number,
                             (f"config-awareness:{name}" for name in awareness))
            if fixture_script_surface and awareness:
                add_findings(findings, FIXTURE, path, line_number,
                             (f"fixture-script-awareness:{name}" for name in awareness))
            if file_is_aware:
                add_findings(findings, BINARY, path, line_number,
                             matches(line, BINARY_CONTEXT_PATTERNS))
                add_findings(findings, COMMAND, path, line_number,
                             matches(line, COMMAND_CONTEXT_PATTERNS))
                add_findings(findings, PROCESS, path, line_number,
                             matches(line, PROCESS_CONTEXT_PATTERNS))
                add_findings(findings, CONFIG, path, line_number,
                             matches(line, CONFIG_CONTEXT_PATTERNS))

    return {
        category: sum(finding[0] == category for finding in findings)
        for category in CATEGORY_FIELDS
    }


def emit(counts: dict[str, int], *, scan_succeeded: bool = True) -> int:
    for field in CATEGORY_FIELDS:
        print(f"{field}={counts[field]}")
    aggregate = sum(counts[field] for field in CATEGORY_FIELDS)
    print(f"{AGGREGATE_FIELD}={aggregate}")
    if aggregate or not scan_succeeded:
        print("RENDER_MODULE_CONCRETE_FFMPEG_ZERO_AWARENESS=FAIL")
        return 1 if aggregate else 2
    print("RENDER_MODULE_CONCRETE_FFMPEG_ZERO_AWARENESS=PASS")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root", type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Git worktree root (defaults to the guard's repository root)")
    arguments = parser.parse_args()
    counts = {field: 0 for field in CATEGORY_FIELDS}
    try:
        counts = scan(arguments.root.resolve())
    except (OSError, subprocess.SubprocessError) as error:
        result = emit(counts, scan_succeeded=False)
        print(f"RENDER_MODULE_CONCRETE_FFMPEG_GUARD_ERROR={error}", file=sys.stderr)
        return result
    return emit(counts)


if __name__ == "__main__":
    raise SystemExit(main())
