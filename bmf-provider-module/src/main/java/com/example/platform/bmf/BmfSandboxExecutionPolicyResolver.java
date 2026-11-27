package com.example.platform.bmf;

import com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.BlockRasterRecipe;
import com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.FadeRaster;
import com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.DecorationRaster;
import com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.RasterRange;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderRaster;
import com.example.platform.sandbox.DeviceExposurePolicy;
import com.example.platform.sandbox.EnvironmentPolicy;
import com.example.platform.sandbox.FilesystemPolicy;
import com.example.platform.sandbox.NetworkPolicy;
import com.example.platform.sandbox.PrivilegePolicy;
import com.example.platform.sandbox.ProcessRequirement;
import com.example.platform.sandbox.ResourceEnforcementLimits;
import com.example.platform.sandbox.SandboxExecutionRequirement;
import com.example.platform.sandbox.SandboxExecutionResolver;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.SandboxResolution;
import com.example.platform.sandbox.SandboxRuntimeCapabilities;
import com.example.platform.sandbox.SecretExposure;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeOutputFiles;
import com.example.platform.workerfabric.domain.providernative.SandboxExecutionPolicyResolver;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Resolves the exact Artifact input and mounts the pinned BMF distribution read-only. */
public final class BmfSandboxExecutionPolicyResolver implements SandboxExecutionPolicyResolver {
    private final Path python;
    private final Path runtimeRoot;
    private final BmfSandboxWorkspace workspace;
    private final Duration timeout;
    private final long captureBytes;
    private final SandboxRuntimeCapabilities capabilities;

    public BmfSandboxExecutionPolicyResolver(Path python, Path runtimeRoot,
            BmfSandboxWorkspace workspace, Duration timeout, long captureBytes,
            SandboxRuntimeCapabilities capabilities) {
        this.python = exact(python, "python");
        this.runtimeRoot = exact(runtimeRoot, "runtimeRoot");
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
        this.timeout = java.util.Objects.requireNonNull(timeout, "timeout");
        this.capabilities = java.util.Objects.requireNonNull(capabilities, "capabilities");
        if (timeout.isZero() || timeout.isNegative() || captureBytes < 1) {
            throw new IllegalArgumentException("BMF runtime limits must be positive");
        }
        this.captureBytes = captureBytes;
    }

    @Override
    public SandboxResolution resolve(ExecutionCommand command,
            List<MaterializedExecutionInput> runtimeLocalInputs) {
        return resolve(command, runtimeLocalInputs, null);
    }

    @Override
    public PreparedResolution prepare(ExecutionCommand command, List<MaterializedExecutionInput> inputs) {
        var prepared = new SubtitleInputs();
        try {
            return new PreparedResolution(resolve(command, inputs, prepared), prepared);
        } catch (RuntimeException | Error failure) {
            try { prepared.close(); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private SandboxResolution resolve(ExecutionCommand command,
            List<MaterializedExecutionInput> runtimeLocalInputs, SubtitleInputs prepared) {
        if (!(command.invocationSpec() instanceof ProcessInvocationSpec invocation)
                || !invocation.executable().equals(python.toString())
                || invocation.arguments().size() < 4
                || !invocation.arguments().get(0).equals("-s")
                || !invocation.arguments().get(1).equals("-c")) {
            return rejected("BMF requires the exact graph program, materialized inputs and bounded timing");
        }
        var arguments = invocation.arguments();
        String program = arguments.get(2);
        boolean multitrack = BmfCpuRuntimeAdapter.MULTITRACK_GRAPH_PROGRAM.equals(program);
        boolean multiInput = multitrack || BmfCpuRuntimeAdapter.COMPOSITE_GRAPH_PROGRAM.equals(program);
        List<String> placements = List.of();
        List<SubtitleInput> subtitles = new ArrayList<>();
        List<String> inputTokens;
        String operation;
        String start;
        String duration;
        String rate = null;
        String outputDuration = null;
        RenderRaster raster;
        if (multiInput) {
            int count;
            try {
                count = Integer.parseInt(arguments.get(3));
            } catch (NumberFormatException malformed) {
                return rejected("BMF composite input count is invalid");
            }
            if (count < 1 || count > arguments.size() - 8) {
                return rejected("BMF composite input count is out of range");
            }
            int expected = 8 + count + (multitrack ? count : 0);
            if ((!multitrack && arguments.size() != expected)
                    || (multitrack && (arguments.size() < expected || arguments.size() > expected + 16))
                    || !arguments.get(4 + count).equals(ProviderNativeOutputFiles.RENDER_OUTPUT)
                    || !isNonNegativeSeconds(arguments.get(5 + count))
                    || !arguments.get(6 + count).matches("[1-9][0-9]*/[1-9][0-9]*")) {
                return rejected("BMF composite requires the exact program, inputs and timing");
            }
            inputTokens = List.copyOf(arguments.subList(4, 4 + count));
            operation = "composite";
            start = "0";
            duration = arguments.get(5 + count);
            rate = arguments.get(6 + count);
            raster = parseRaster(arguments.get(7 + count));
            if (raster == null) return rejected("BMF output raster is outside the bounded invocation contract");
            if (multitrack) {
                placements = List.copyOf(arguments.subList(8 + count, expected));
                if (prepared == null && arguments.size() > expected) {
                    return rejected("BMF subtitle rasters require scoped input preparation");
                }
                for (var block : arguments.subList(expected, arguments.size())) {
                    SubtitleInput subtitle = parseSubtitle(block);
                    if (subtitle == null) return rejected("BMF subtitle recipe is outside the bounded composition contract");
                    subtitles.add(subtitle);
                }
                if (placements.stream().anyMatch(placement -> !validPlacement(placement, duration, raster))) {
                    return rejected("BMF multitrack placement is invalid");
                }
            }
        } else if (BmfCpuRuntimeAdapter.GRAPH_PROGRAM.equals(program)) {
            if ((arguments.size() != 9 && arguments.size() != 11)
                    || !arguments.get(4).equals(ProviderNativeOutputFiles.RENDER_OUTPUT)
                    || !isKnownOperation(arguments.get(5))
                    || !isNonNegativeSeconds(arguments.get(6))
                    || !isNonNegativeSeconds(arguments.get(7))) {
                return rejected("BMF requires the exact graph program, one materialized input and a bounded operation");
            }
            inputTokens = List.of(arguments.get(3));
            operation = arguments.get(5);
            start = arguments.get(6);
            duration = arguments.get(7);
            if (arguments.size() == 11) {
                rate = arguments.get(9);
                outputDuration = arguments.get(10);
                if (!(operation.equals("encode") || operation.equals("encode-multitrack"))
                        || !rate.matches("[1-9][0-9]*/[1-9][0-9]*")
                        || !isNonNegativeSeconds(outputDuration)
                        || new java.math.BigDecimal(outputDuration).signum() <= 0) {
                    return rejected("BMF output timing requires a final encode, positive rate and duration");
                }
            }
            raster = parseRaster(arguments.get(8));
            if (raster == null) return rejected("BMF output raster is outside the bounded invocation contract");
        } else {
            return rejected("BMF requires the exact graph program");
        }
        if (runtimeLocalInputs.size() != inputTokens.size()) {
            return rejected("BMF input count does not match the materialized inputs");
        }
        Map<String, MaterializedExecutionInput> byToken = new java.util.LinkedHashMap<>();
        for (var input : runtimeLocalInputs) {
            byToken.put(BmfCpuRuntimeAdapter.materializedInputToken(input.inputId()), input);
        }
        if (byToken.size() != runtimeLocalInputs.size()
                || new java.util.HashSet<>(inputTokens).size() != inputTokens.size()
                || !byToken.keySet().containsAll(inputTokens)) {
            return rejected("BMF input identity does not match the materialized Artifact");
        }
        // The launcher numbers unique read-only paths. Multiple graph inputs can reuse one Artifact.
        var readOnlyInputs = new TreeSet<Path>(Comparator.comparing(Path::toString));
        readOnlyInputs.add(python);
        readOnlyInputs.add(runtimeRoot);
        for (var token : inputTokens) {
            readOnlyInputs.add(byToken.get(token).materializedArtifact().path().toAbsolutePath().normalize());
        }
        if (!Files.isRegularFile(python) || !Files.isExecutable(python)
                || !Files.isDirectory(runtimeRoot.resolve("output/bmf"))
                || !Files.isRegularFile(runtimeRoot.resolve("output/bmf/lib/libbuiltin_modules.so"))
                || !Files.isDirectory(runtimeRoot.resolve("host-bundle/usr/lib/python3.9"))
                || !Files.isDirectory(runtimeRoot.resolve("host-bundle/usr/lib/python3/dist-packages/numpy"))) {
            return rejected("BMF Python or pinned runtime distribution is unavailable");
        }
        try {
            List<Path> subtitlePaths = new ArrayList<>(subtitles.size());
            List<Path> selectionPaths = new ArrayList<>(subtitles.size());
            List<Path> decorationPaths = new ArrayList<>(subtitles.size());
            for (var subtitle : subtitles) {
                var selection = subtitle.fade() == null ? null : subtitle.fade().range();
                Path path = prepared.raster(subtitle.recipe(), raster.width(), selection, false);
                subtitlePaths.add(path);
                readOnlyInputs.add(path);
                Path selected = selection == null ? null : prepared.raster(subtitle.recipe(), raster.width(), selection, true);
                selectionPaths.add(selected);
                if (selected != null) readOnlyInputs.add(selected);
                Path decoration = subtitle.decoration() == null ? null : prepared.decoration(subtitle.decoration(), raster.width());
                decorationPaths.add(decoration);
                if (decoration != null) readOnlyInputs.add(decoration);
            }
            var mounts = List.copyOf(readOnlyInputs);
            int runtimeIndex = mounts.indexOf(runtimeRoot);
            Path sandboxRuntime = Path.of("/sandbox-inputs/input-" + runtimeIndex);
            Path working = workspace.outputStagingRoot().resolve("attempt-"
                    + UUID.nameUUIDFromBytes(command.platformExecutionAttemptId().value()
                            .getBytes(StandardCharsets.UTF_8)));
            List<String> args = new ArrayList<>(List.of("-s", "-c", program));
            if (multiInput) {
                args.add(Integer.toString(inputTokens.size()));
                for (var token : inputTokens) {
                    Path inputPath = byToken.get(token).materializedArtifact().path().toAbsolutePath().normalize();
                    args.add("/sandbox-inputs/input-" + mounts.indexOf(inputPath));
                }
                args.add(ProviderNativeOutputFiles.RENDER_OUTPUT);
                args.add(duration);
                args.add(rate);
                args.add(raster.width() + "x" + raster.height());
                args.addAll(placements);
                for (int index = 0; index < subtitles.size(); index++) {
                    int mount = mounts.indexOf(subtitlePaths.get(index));
                    var subtitle = subtitles.get(index);
                    String token = subtitle.timing() + ",/sandbox-inputs/input-" + mount;
                    if (subtitle.fade() != null) {
                        token += "," + subtitle.fade().time() + "," + subtitle.fade().duration()
                                + ",/sandbox-inputs/input-" + mounts.indexOf(selectionPaths.get(index));
                    }
                    if (subtitle.decoration() != null) {
                        if (subtitle.fade() == null) token += ",,,";
                        var decoration = subtitle.decoration();
                        token += "," + decoration.firstFrame() + "," + decoration.endFrame()
                                + "," + decoration.offset(subtitle.recipe().width(), raster.width())
                                + ",/sandbox-inputs/input-" + mounts.indexOf(decorationPaths.get(index));
                    }
                    args.add(token);
                }
            } else {
                Path inputPath = byToken.get(inputTokens.getFirst())
                        .materializedArtifact().path().toAbsolutePath().normalize();
                args.add("/sandbox-inputs/input-" + mounts.indexOf(inputPath));
                args.add(ProviderNativeOutputFiles.RENDER_OUTPUT);
                args.add(operation);
                args.add(start);
                args.add(duration);
                args.add(raster.width() + "x" + raster.height());
                if (outputDuration != null) {
                    args.add(rate);
                    args.add(outputDuration);
                }
            }
            var requirement = new SandboxExecutionRequirement(
                    ProcessRequirement.of(Set.of(python.toString()), python.toString(), args, timeout),
                    FilesystemPolicy.exact(readOnlyInputs, workspace.root(),
                            workspace.temporaryRoot(), workspace.outputStagingRoot(), working),
                    NetworkPolicy.none(),
                    EnvironmentPolicy.exact(Map.of(
                            "PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C",
                            "PYTHONHOME", sandboxRuntime.resolve("host-bundle/usr").toString(),
                            "PYTHONPATH", sandboxRuntime.resolve("output/bmf/lib") + ":"
                                    + sandboxRuntime.resolve("output") + ":"
                                    + sandboxRuntime.resolve("host-bundle/usr/lib/python3/dist-packages"),
                            "LD_LIBRARY_PATH", sandboxRuntime.resolve("host-bundle/selected-lib") + ":"
                                    + sandboxRuntime.resolve("output/bmf/lib"),
                            "BMF_MODULE_PATH", sandboxRuntime.resolve("output/bmf").toString(),
                            "PYTHONDONTWRITEBYTECODE", "1")),
                    SecretExposure.none(), PrivilegePolicy.unprivileged(),
                    ResourceEnforcementLimits.captureOnly(captureBytes), DeviceExposurePolicy.none());
            return SandboxExecutionResolver.resolve(requirement, capabilities);
        } catch (IOException | IllegalArgumentException failure) {
            return rejected("BMF sandbox input preparation or policy is invalid");
        }
    }

    private static Path exact(Path path, String label) {
        java.util.Objects.requireNonNull(path, label);
        if (!path.isAbsolute() || !path.equals(path.normalize())) {
            throw new IllegalArgumentException(label + " must be absolute and normalized");
        }
        return path;
    }

    private static boolean isKnownOperation(String operation) {
        try {
            BmfOperation.ofOperationKey(operation);
            return !operation.equals(BmfOperation.MULTITRACK_COMPOSITE.operationKey());
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    private static RenderRaster parseRaster(String value) {
        if (!value.matches("[1-9][0-9]{1,3}x[1-9][0-9]{1,3}")) return null;
        int separator = value.indexOf('x');
        try {
            return new RenderRaster(Integer.parseInt(value.substring(0, separator)),
                    Integer.parseInt(value.substring(separator + 1)));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean validPlacement(String placement, String duration, RenderRaster raster) {
        String[] parts = placement.split(",", -1);
        if ((parts.length < 17 || parts.length > 19) || !List.of("V", "A").contains(parts[0])
                || !isNonNegativeSeconds(parts[1]) || !isNonNegativeSeconds(parts[2])) {
            return false;
        }
        var start = new java.math.BigDecimal(parts[1]);
        var end = new java.math.BigDecimal(parts[2]);
        if (start.compareTo(end) >= 0 || end.compareTo(new java.math.BigDecimal(duration)) > 0) {
            return false;
        }
        try {
            var gain = new java.math.BigDecimal(parts[3]);
            var pan = new java.math.BigDecimal(parts[5]);
            if (gain.compareTo(java.math.BigDecimal.ZERO) < 0
                    || gain.compareTo(new java.math.BigDecimal("2")) > 0
                    || !(parts[4].equals("0") || parts[4].equals("1"))
                    || pan.compareTo(new java.math.BigDecimal("-1")) < 0
                    || pan.compareTo(java.math.BigDecimal.ONE) > 0) {
                return false;
            }
            var x = new BigDecimal(parts[6]);
            var y = new BigDecimal(parts[7]);
            var scaleX = new BigDecimal(parts[8]);
            var scaleY = new BigDecimal(parts[9]);
            var degrees = new BigDecimal(parts[10]);
            var opacity = new java.math.BigDecimal(parts[15]);
            var left = new java.math.BigDecimal(parts[11]);
            var right = new java.math.BigDecimal(parts[12]);
            var top = new java.math.BigDecimal(parts[13]);
            var bottom = new java.math.BigDecimal(parts[14]);
            String[] fx = parts[16].split(";", -1);
            if ((fx.length != 10 && fx.length != 14) || !isNonNegativeSeconds(fx[7])
                    || !isNonNegativeSeconds(fx[8]) || !isNonNegativeSeconds(fx[9])
                    || new java.math.BigDecimal(fx[8]).signum() <= 0) return false;
            if (fx.length == 14 && (!parts[0].equals("V") || !validOpacityRamp(fx))) return false;
            if (parts.length > 17 && (!parts[0].equals("V")
                    || !(parts.length == 19 && parts[17].isEmpty()) && !validPositionRamp(parts[17], fx[8]))) return false;
            if (parts.length == 19 && !validScaleRamp(parts[18], scaleX, scaleY, degrees, raster)) return false;
            var brightness = new java.math.BigDecimal(fx[0]);
            var contrast = new java.math.BigDecimal(fx[1]);
            var saturation = new java.math.BigDecimal(fx[2]);
            var fadeIn = new java.math.BigDecimal(fx[4]);
            var fadeOut = new java.math.BigDecimal(fx[5]);
            if (brightness.compareTo(java.math.BigDecimal.ONE.negate()) < 0
                    || brightness.compareTo(java.math.BigDecimal.ONE) > 0
                    || contrast.compareTo(java.math.BigDecimal.ZERO) < 0
                    || contrast.compareTo(new java.math.BigDecimal("2")) > 0
                    || saturation.compareTo(java.math.BigDecimal.ZERO) < 0
                    || saturation.compareTo(new java.math.BigDecimal("2")) > 0
                    || fadeIn.compareTo(java.math.BigDecimal.ZERO) < 0
                    || fadeOut.compareTo(java.math.BigDecimal.ZERO) < 0
                    || !(fx[3].equals("0") || fx[3].equals("1"))
                    || !(fx[6].equals("0") || fx[6].equals("1"))) return false;
            return x.abs().compareTo(BigDecimal.valueOf(16)) <= 0
                    && y.abs().compareTo(BigDecimal.valueOf(16)) <= 0
                    && degrees.compareTo(BigDecimal.valueOf(-180)) >= 0
                    && degrees.compareTo(BigDecimal.valueOf(180)) < 0
                    && scaleX.compareTo(java.math.BigDecimal.ZERO) > 0
                    && scaleX.compareTo(java.math.BigDecimal.TEN) <= 0
                    && scaleY.compareTo(java.math.BigDecimal.ZERO) > 0
                    && scaleY.compareTo(java.math.BigDecimal.TEN) <= 0
                    && opacity.compareTo(java.math.BigDecimal.ZERO) >= 0
                    && opacity.compareTo(java.math.BigDecimal.ONE) <= 0
                    && left.compareTo(java.math.BigDecimal.ZERO) >= 0
                    && right.compareTo(java.math.BigDecimal.ONE) <= 0
                    && top.compareTo(java.math.BigDecimal.ZERO) >= 0
                    && bottom.compareTo(java.math.BigDecimal.ONE) <= 0
                    && left.compareTo(right) < 0 && top.compareTo(bottom) < 0
                    && (parts[0].equals("A") || BmfCpuTranscodePlan.VideoPlacement.fitsIntermediateRaster(
                            raster, scaleX.doubleValue(), scaleY.doubleValue(), degrees.doubleValue()));
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private record SubtitleInput(String timing, BlockRasterRecipe recipe, FadeRaster fade, DecorationRaster decoration) {}

    private static SubtitleInput parseSubtitle(String block) {
        if (block.length() > 3_600 + 3_201 + 90 + 80) return null;
        String[] values = block.split(",", 12);
        String timeShape = "(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})";
        if ((values.length < 8 || values.length > 12) || !values[0].matches("[0-9]{1,10}") || !values[1].matches("[0-9]{1,10}")
                || Long.parseLong(values[0]) >= Long.parseLong(values[1])
                || !values[2].matches(timeShape) || !values[3].matches(timeShape)
                || !values[5].matches("[1-9][0-9]{1,2}") || !values[6].matches("[1-9][0-9]")
                || values[7].length() > 1_536 || (values.length >= 9 && !values[8].matches("[a-z0-9-]{1,40}"))) return null;
        String sample = timeShape + ";-?[0-9]{1,8};-?[0-9]{1,8};[0-9]{1,7}";
        if (!values[4].matches(sample + "(?:\\|" + sample + "){0,15}")) return null;
        try {
            com.example.platform.shared.time.MediaTime.parse(values[2]);
            com.example.platform.shared.time.MediaTime.parse(values[3]);
            com.example.platform.shared.time.MediaTime previous = null;
            for (String entry : values[4].split("\\|")) {
                var fields = entry.split(";");
                var time = com.example.platform.shared.time.MediaTime.parse(fields[0]);
                if (previous != null && !previous.isLessThan(time)) return null;
                if (Math.abs(Integer.parseInt(fields[1])) > 16_000_000 || Math.abs(Integer.parseInt(fields[2])) > 16_000_000
                        || Integer.parseInt(fields[3]) > 1_000_000) return null;
                previous = time;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(values[7]);
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(decoded)).toString();
            int width = Integer.parseInt(values[5]);
            int size = Integer.parseInt(values[6]);
            var decoration = DecorationRaster.parse(values.length == 12 ? values[11] : null);
            if (decoration != null && (decoration.firstFrame() < Long.parseLong(values[0])
                    || decoration.endFrame() > Long.parseLong(values[1]))) return null;
            return new SubtitleInput(String.join(",", java.util.Arrays.copyOf(values, 5)),
                    BlockRasterRecipe.fromEncodedRuns(text, width, size, values.length >= 9 ? values[8] : null,
                            values.length >= 10 && !values[9].isEmpty() ? values[9] : null),
                    FadeRaster.parse(values.length >= 11 && !values[10].isEmpty() ? values[10] : null), decoration);
        } catch (ArithmeticException | IllegalArgumentException | java.nio.charset.CharacterCodingException malformed) {
            return null;
        }
    }

    /** One private input set per launch. Names are generated here, never supplied by a cue. */
    private static final class SubtitleInputs implements Closeable {
        private record RasterKey(BlockRasterRecipe recipe, int width, RasterRange selection, boolean selected) {}
        private final Map<RasterKey, Path> files = new LinkedHashMap<>();
        private record DecorationKey(String id, int width) {}
        private final Map<DecorationKey, Path> decorations = new LinkedHashMap<>();
        private final List<Path> allocated = new ArrayList<>();
        private Path directory;

        Path raster(BlockRasterRecipe recipe, int destinationWidth, RasterRange selection, boolean selected) throws IOException {
            var key = new RasterKey(recipe, destinationWidth, selection, selected);
            Path existing = files.get(key);
            if (existing != null) return existing;
            byte[] png = recipe.png(destinationWidth, selection, selected);
            if (directory == null) directory = Files.createTempDirectory("bmf-subtitle-inputs-");
            Path target = directory.resolve("block-" + files.size() + ".png");
            allocated.add(target);
            Files.write(target, png, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            files.put(key, target);
            return target;
        }

        Path decoration(DecorationRaster recipe, int destinationWidth) throws IOException {
            var key = new DecorationKey(recipe.assetId(), destinationWidth);
            Path existing = decorations.get(key);
            if (existing != null) return existing;
            byte[] png = recipe.png(destinationWidth);
            if (directory == null) directory = Files.createTempDirectory("bmf-subtitle-inputs-");
            Path target = directory.resolve("decoration-" + decorations.size() + ".png");
            allocated.add(target);
            Files.write(target, png, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            decorations.put(key, target);
            return target;
        }

        @Override public void close() throws IOException {
            IOException failure = null;
            for (Path file : allocated) {
                try { Files.deleteIfExists(file); }
                catch (IOException cleanup) {
                    if (failure == null) failure = cleanup;
                    else failure.addSuppressed(cleanup);
                }
            }
            if (directory != null) {
                try { Files.deleteIfExists(directory); }
                catch (IOException cleanup) {
                    if (failure == null) failure = cleanup;
                    else failure.addSuppressed(cleanup);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private static boolean validScaleRamp(String token, BigDecimal sx, BigDecimal sy, BigDecimal degrees, RenderRaster raster) {
        String time = "(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})";
        String pair = time + ";[1-9][0-9]{0,13}";
        if (token.length() > 1240 || !token.matches(time + "~" + time + "~" + time + "~" + time + "~"
                + pair + "(?:\\|" + pair + "){1,15}")) return false;
        try {
            var fields = token.split("~");
            var start = com.example.platform.shared.time.MediaTime.parse(fields[0]);
            var end = com.example.platform.shared.time.MediaTime.parse(fields[1]);
            var elapsed = com.example.platform.shared.time.MediaTime.parse(fields[2]);
            var duration = com.example.platform.shared.time.MediaTime.parse(fields[3]);
            if (elapsed.compareTo(duration) >= 0 || start.compareTo(end) >= 0) return false;
            var ramp = RenderDependency.ScaleRamp.parseSamples(fields[4]);
            ramp.validateRelative(duration, sx.movePointRight(6).intValueExact(), sy.movePointRight(6).intValueExact());
            return !ramp.changesScale()
                    || com.example.platform.execution.compatibility.MediaLoweringEnvelope.fitsAnimatedScaleRaster(
                            raster, sx.doubleValue(), sy.doubleValue(), degrees.doubleValue());
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return false;
        }
    }

    private static boolean validPositionRamp(String token, String authoredDuration) {
        // Only exact nonnegative times and signed fixed-point integers may enter the graph compiler.
        String time = "(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})";
        String pair = time + ";-?[0-9]{1,8};-?[0-9]{1,8}";
        if (token.length() > 1024 || !token.matches(pair + "(?:\\|" + pair + "){1,15}")) return false;
        try {
            var ramp = RenderDependency.PositionRamp.parseSamples(token);
            return ramp.endsWithin(new BigDecimal(authoredDuration));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean validOpacityRamp(String[] fx) {
        String exactTime = "(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})";
        if (!fx[10].matches(exactTime) || !fx[12].matches(exactTime)) return false;
        try {
            var start = com.example.platform.shared.time.MediaTime.parse(fx[10]);
            var span = com.example.platform.shared.time.MediaTime.parse(fx[12]);
            var end = start.add(span);
            var endSeconds = BigDecimal.valueOf(end.ticks()).divide(BigDecimal.valueOf(end.timeScale()),
                    9, java.math.RoundingMode.HALF_UP);
            return span.ticks() > 0 && endSeconds.compareTo(new BigDecimal(fx[8])) <= 0
                    && validOpacityValue(fx[11]) && validOpacityValue(fx[13]);
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean validOpacityValue(String token) {
        if (token.length() > 24 || !token.matches("[0-9]+(?:\\.[0-9]+)?(?:E-?[0-9]{1,3})?")) return false;
        BigDecimal value = new BigDecimal(token);
        return value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0;
    }

    private static boolean isNonNegativeSeconds(String value) {
        return value.matches("[0-9]+(\\.[0-9]{1,9})?");
    }

    private static SandboxResolution rejected(String reason) {
        return new SandboxResolution.Rejected(SandboxFailure.of(
                SandboxFailureCode.SANDBOX_POLICY_UNSATISFIABLE, reason, Set.of()));
    }
}
