package com.example.platform.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Rootless Podman/Docker mechanics behind the technology-neutral bounded launcher port. */
@org.springframework.modulith.NamedInterface("API")
public final class ContainerSandboxProcessLauncher implements BoundedProcessLauncher {
    private static final String CONTAINER_WORKSPACE = "/sandbox/workspace";
    private static final String LABEL = "com.example.platform.sandbox.instance";
    private static final byte[] REDACTED = "[REDACTED]".getBytes(StandardCharsets.UTF_8);
    private static final Duration CONTAINER_REMOVAL_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REMOVE_OPERATION_MIN_TIMEOUT = Duration.ofMillis(100);
    private static final Duration REMOVE_OPERATION_MAX_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration INSPECT_OPERATION_MIN_TIMEOUT = Duration.ofMillis(100);
    private static final Duration INSPECT_OPERATION_MAX_TIMEOUT = Duration.ofMillis(500);
    private static final Duration CONTAINER_REMOVAL_RETRY = Duration.ofMillis(50);
    private static final Duration ENGINE_EXIT_GRACE = Duration.ofSeconds(1);
    private static final Duration ENGINE_TERMINATION_GRACE = Duration.ofSeconds(2);

    private final ContainerEngineConfiguration engine;
    private final SandboxRuntimeCapabilities capabilities;
    private final Optional<SandboxSecretResolver> secretResolver;
    private final String instance = UUID.randomUUID().toString();

    ContainerSandboxProcessLauncher(
            ContainerEngineConfiguration engine,
            SandboxRuntimeCapabilities capabilities,
            Optional<SandboxSecretResolver> secretResolver) {
        this.engine = engine;
        this.capabilities = capabilities;
        this.secretResolver = secretResolver;
    }

    public SandboxRuntimeCapabilities capabilities() { return capabilities; }

    /** Resolves with this adapter's probe-created evidence, preventing caller-fabricated launch claims. */
    public SandboxExecutionResult launchResolved(
            SandboxExecutionRequirement requirement, SandboxCancellation cancellation) throws IOException {
        SandboxResolution resolution = SandboxExecutionResolver.resolve(requirement, capabilities);
        if (resolution instanceof SandboxResolution.Rejected rejected) {
            return setupFailure(requirement.filesystem().workingDirectory(), rejected.failure());
        }
        return launch(((SandboxResolution.Resolved) resolution).specification(), cancellation);
    }

    @Override
    public SandboxExecutionResult launch(
            EffectiveSandboxExecutionSpecification spec, SandboxCancellation cancellation)
            throws IOException {
        if (!capabilities.equals(spec.runtimeCapabilities())) {
            throw new IllegalArgumentException(
                    "container launcher capability evidence differs from resolved specification");
        }
        Optional<SandboxFailure> pathFailure = prepareFilesystem(spec.filesystem());
        if (pathFailure.isPresent()) {
            return setupFailure(spec.filesystem().workingDirectory(), pathFailure.orElseThrow());
        }

        List<ScopedSecretValue> scopedSecrets = new ArrayList<>();
        List<byte[]> redactions = new ArrayList<>();
        try {
            Optional<SandboxFailure> resolutionFailure = resolveSecrets(spec, scopedSecrets, redactions);
            if (resolutionFailure.isPresent()) {
                return setupFailure(spec.filesystem().workingDirectory(), resolutionFailure.orElseThrow());
            }
            return launchContainer(spec, cancellation, scopedSecrets, redactions);
        } finally {
            scopedSecrets.forEach(ScopedSecretValue::close);
            redactions.forEach(value -> Arrays.fill(value, (byte) 0));
        }
    }

    boolean hasRunningSandboxContainers() {
        ContainerEngineProcess.EngineResult result = ContainerEngineProcess.execute(
                List.of(engine.executable().toString(), "ps", "--quiet", "--filter",
                        "label=" + LABEL + "=" + instance),
                engine.engineEnvironment(), Duration.ofSeconds(5));
        return result.succeeded() && !result.stdout().isBlank();
    }

    private SandboxExecutionResult launchContainer(
            EffectiveSandboxExecutionSpecification spec,
            SandboxCancellation cancellation,
            List<ScopedSecretValue> secrets,
            List<byte[]> redactions) throws IOException {
        String name = "media-platform-sandbox-" + instance.substring(0, 8) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        LinkedHashMap<String, String> processEnvironment = new LinkedHashMap<>(engine.engineEnvironment());
        List<String> command = buildCommand(
                engine, spec, name, instance, processEnvironment, secrets);
        Instant launchedAt = Instant.now();
        Process process;
        try {
            process = ContainerEngineProcess.start(command, processEnvironment);
        } catch (IOException failure) {
            return setupFailure(spec.filesystem().workingDirectory(), SandboxFailure.of(
                    SandboxFailureCode.PROCESS_LAUNCH_FAILED,
                    "container engine process launch failed", Set.of()));
        } finally {
            processEnvironment.clear();
        }

        SandboxExecutionHandle handle = new SandboxExecutionHandle(process.pid(), launchedAt);
        CaptureReader stdout = new CaptureReader(
                process.getInputStream(), spec.resources().captureBytes(), redactions);
        CaptureReader stderr = new CaptureReader(
                process.getErrorStream(), spec.resources().captureBytes(), redactions);
        Thread outThread = Thread.ofVirtual().start(stdout);
        Thread errThread = Thread.ofVirtual().start(stderr);
        SandboxFailure processFailure = null;
        long deadline = System.nanoTime() + spec.process().timeout().toNanos();
        while (process.isAlive()) {
            if (cancellation.isCancellationRequested()) {
                processFailure = SandboxFailure.of(
                        SandboxFailureCode.PROCESS_TERMINATED_BY_LIMIT, "process cancelled", Set.of());
                break;
            }
            if (System.nanoTime() >= deadline) {
                processFailure = SandboxFailure.of(
                        SandboxFailureCode.PROCESS_TIMEOUT,
                        "process exceeded wall-clock timeout", Set.of());
                break;
            }
            try {
                process.waitFor(20, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                processFailure = SandboxFailure.of(
                        SandboxFailureCode.SANDBOX_RUNTIME_LOST, "launcher interrupted", Set.of());
                break;
            }
        }

        // Ask the engine to stop the container while the attached `run` client can still
        // coordinate a normal shutdown. Then stop that client so it cannot create/recreate the
        // named container after a not-found readback, and only then make the final removal
        // observation authoritative.
        ContainerRemovalDeadline removalDeadline = ContainerRemovalDeadline.start(
                CONTAINER_REMOVAL_TIMEOUT);
        RemovalCommandObservation initialRemoval = requestContainerRemoval(
                engine, name, removalDeadline, false);
        waitForExit(process, removalDeadline.remainingRetryTime(ENGINE_EXIT_GRACE));
        Duration preliminaryGrace = removalDeadline
                .remainingRetryTime(ENGINE_TERMINATION_GRACE.multipliedBy(2))
                .dividedBy(2);
        LocalBoundedProcessLauncher.TreeTermination preliminaryTermination =
                LocalBoundedProcessLauncher.terminateTreeWithHandles(
                        process, preliminaryGrace, List.of());
        ContainerRemovalObservation removal = removeContainer(
                engine, name, removalDeadline, CONTAINER_REMOVAL_RETRY, initialRemoval);
        LocalBoundedProcessLauncher.TreeTermination finalTermination =
                LocalBoundedProcessLauncher.terminateTreeWithHandles(
                        process, ENGINE_TERMINATION_GRACE,
                        preliminaryTermination.descendants());
        boolean capturesComplete = closeAndJoinCaptures(
                stdout, outThread, stderr, errThread);
        SandboxCleanupObservation cleanup = cleanupObservation(
                name, process.pid(), removal, finalTermination.observation(), capturesComplete);
        OptionalInt exit = OptionalInt.empty();
        if (!process.isAlive()) {
            try { exit = OptionalInt.of(process.exitValue()); }
            catch (IllegalThreadStateException ignored) { }
        }
        if (processFailure == null && exit.isPresent() && exit.getAsInt() != 0) {
            SandboxFailureCode code = exit.getAsInt() == 125
                    ? SandboxFailureCode.SANDBOX_SETUP_FAILED
                    : SandboxFailureCode.PROCESS_CRASHED;
            processFailure = SandboxFailure.of(code,
                    code == SandboxFailureCode.SANDBOX_SETUP_FAILED
                            ? "container engine rejected sandbox setup"
                            : "process exited non-zero",
                    Set.of());
        }
        return new SandboxExecutionResult(exit, stdout.capture(), stderr.capture(),
                selectResultFailure(processFailure, cleanup),
                new SandboxExecutionObservation(handle,
                        spec.filesystem().workingDirectory().toRealPath(),
                        Duration.between(launchedAt, Instant.now()), cleanup));
    }

    static List<String> buildCommand(
            ContainerEngineConfiguration engine,
            EffectiveSandboxExecutionSpecification spec,
            String name,
            String instance,
            Map<String, String> processEnvironment,
            List<ScopedSecretValue> secrets) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(engine.executable().toString());
        command.addAll(List.of("run", "--rm", "--name", name,
                "--label", LABEL + "=" + instance,
                "--pull=missing", "--network=none", "--read-only", "--cap-drop=all",
                "--security-opt=no-new-privileges"));
        UnixIdentity identity = identity(spec.filesystem().workspaceRoot());
        addUser(engine, identity, command);
        command.addAll(resourceArguments(spec.resources()));
        command.addAll(List.of("--mount", bindMount(
                spec.filesystem().workspaceRoot(), CONTAINER_WORKSPACE, false)));

        List<Path> inputs = spec.filesystem().readOnlyInputs().stream()
                .sorted(Comparator.comparing(Path::toString)).toList();
        for (int index = 0; index < inputs.size(); index++) {
            command.add("--mount");
            command.add(bindMount(inputs.get(index), "/sandbox/inputs/input-" + index, true));
        }
        String containerTemporary = translateWorkspacePath(
                spec.filesystem().workspaceRoot(), spec.filesystem().temporaryRoot());
        long temporaryBytes = spec.resources().temporaryBytes().orElse(16L << 20);
        command.add(tmpfsOption(
                engine.kind(), containerTemporary, temporaryBytes, identity.uid(), identity.gid()));
        command.add("--workdir=" + translateWorkspacePath(
                spec.filesystem().workspaceRoot(), spec.filesystem().workingDirectory()));

        command.addAll(List.of("--env=PATH=/usr/bin:/bin", "--env=HOME=" + CONTAINER_WORKSPACE,
                "--env=TMPDIR=" + containerTemporary, "--env=LANG=C.UTF-8"));
        spec.environment().values().forEach((nameValue, value) -> {
            if (Set.of("PATH", "HOME", "TMPDIR", "LANG").contains(nameValue)) {
                command.add("--env=" + nameValue + "=" + value);
            } else {
                processEnvironment.put(nameValue, value);
                command.add("--env");
                command.add(nameValue);
            }
        });
        for (ScopedSecretValue secret : secrets) {
            char[] value = secret.copyValue();
            try {
                processEnvironment.put(secret.environmentName(), new String(value));
            } finally {
                Arrays.fill(value, '\0');
            }
            command.add("--env");
            command.add(secret.environmentName());
        }

        command.add(engine.image());
        command.add(spec.process().executable());
        for (String argument : spec.process().arguments()) {
            command.add(translateArgument(spec.filesystem(), inputs, argument));
        }
        return List.copyOf(command);
    }

    private static void addUser(
            ContainerEngineConfiguration engine, UnixIdentity identity, List<String> command) {
        if (engine.kind() == ContainerEngineConfiguration.Kind.PODMAN) {
            command.add("--userns=keep-id");
        }
        command.add("--user=" + identity.uid() + ":" + identity.gid());
    }

    static String tmpfsOption(
            ContainerEngineConfiguration.Kind kind,
            String destination,
            long sizeBytes,
            String uid,
            String gid) {
        String options = "rw,noexec,nosuid,nodev,size=" + sizeBytes;
        if (kind == ContainerEngineConfiguration.Kind.PODMAN) {
            // Podman's --tmpfs parser rejects uid/gid. 1777 keeps the isolated mount writable
            // by the explicitly selected non-root container user without weakening mount flags.
            return "--tmpfs=" + destination + ":" + options + ",mode=1777";
        }
        return "--tmpfs=" + destination + ":" + options
                + ",mode=0700,uid=" + uid + ",gid=" + gid;
    }

    private static UnixIdentity identity(Path workspace) throws IOException {
        return new UnixIdentity(Files.getAttribute(workspace, "unix:uid").toString(),
                Files.getAttribute(workspace, "unix:gid").toString());
    }

    static List<String> resourceArguments(ResourceEnforcementLimits limits) {
        List<String> arguments = new ArrayList<>();
        limits.processCount().ifPresent(value -> arguments.add("--pids-limit=" + value));
        // Do not add --memory-swap: swap is not a requested resource dimension and rootless
        // cgroup delegations frequently expose memory.max without exposing memory.swap.max.
        limits.memoryBytes().ifPresent(value -> arguments.add("--memory=" + value));
        limits.cpuCount().ifPresent(value -> arguments.add("--cpus=" + value));
        limits.openFileCount().ifPresent(value ->
                arguments.add("--ulimit=nofile=" + value + ":" + value));
        return List.copyOf(arguments);
    }

    private Optional<SandboxFailure> resolveSecrets(
            EffectiveSandboxExecutionSpecification spec,
            List<ScopedSecretValue> resolved,
            List<byte[]> redactions) {
        if (spec.secrets().references().isEmpty()) return Optional.empty();
        if (secretResolver.isEmpty()) return Optional.of(SandboxFailure.of(
                SandboxFailureCode.SECRET_INJECTION_FAILED,
                "secret resolver is unavailable", Set.of()));
        Set<String> environmentNames = new java.util.HashSet<>(spec.environment().values().keySet());
        try {
            for (OpaqueSecretReference reference : spec.secrets().references().stream()
                    .sorted(Comparator.comparing(OpaqueSecretReference::value)).toList()) {
                ScopedSecretValue value = secretResolver.orElseThrow().resolve(reference);
                if (value == null || !value.reference().equals(reference)
                        || !environmentNames.add(value.environmentName())) {
                    if (value != null) value.close();
                    return Optional.of(SandboxFailure.of(
                            SandboxFailureCode.SECRET_INJECTION_FAILED,
                            "secret resolver returned an invalid or colliding scoped value", Set.of()));
                }
                resolved.add(value);
                char[] copy = value.copyValue();
                try {
                    ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(copy));
                    byte[] bytes = new byte[encoded.remaining()];
                    encoded.get(bytes);
                    if (bytes.length == 0) return Optional.of(SandboxFailure.of(
                            SandboxFailureCode.SECRET_INJECTION_FAILED,
                            "secret resolver returned an empty scoped value", Set.of()));
                    redactions.add(bytes);
                } finally {
                    Arrays.fill(copy, '\0');
                }
            }
            return Optional.empty();
        } catch (SandboxSecretResolutionException | RuntimeException failure) {
            return Optional.of(SandboxFailure.of(
                    SandboxFailureCode.SECRET_INJECTION_FAILED,
                    "secret resolution failed", Set.of()));
        }
    }

    private Optional<SandboxFailure> prepareFilesystem(FilesystemPolicy filesystem) {
        try {
            for (Path path : List.of(filesystem.workspaceRoot(), filesystem.temporaryRoot(),
                    filesystem.outputStagingRoot(), filesystem.workingDirectory())) {
                if (path.toString().contains(",") || path.toString().contains("\n")) {
                    return filesystemFailure();
                }
            }
            Files.createDirectories(filesystem.workspaceRoot());
            Files.createDirectories(filesystem.temporaryRoot());
            Files.createDirectories(filesystem.outputStagingRoot());
            Files.createDirectories(filesystem.workingDirectory());
            if (FilesystemPathValidator.validateWorkingDirectory(
                    filesystem.workspaceRoot(), filesystem.workingDirectory()).isPresent()
                    || FilesystemPathValidator.validateWithin(
                            filesystem.workspaceRoot(), filesystem.temporaryRoot()).isPresent()
                    || FilesystemPathValidator.validateOutput(
                            filesystem.workspaceRoot(), filesystem.outputStagingRoot()).isPresent()) {
                return filesystemFailure();
            }
            for (Path input : filesystem.readOnlyInputs()) {
                if (input.toString().contains(",") || input.toString().contains("\n")
                        || FilesystemPathValidator.validateExactNoSymlink(input).isPresent()) {
                    return filesystemFailure();
                }
            }
            return Optional.empty();
        } catch (IOException | RuntimeException failure) {
            return filesystemFailure();
        }
    }

    private static Optional<SandboxFailure> filesystemFailure() {
        return Optional.of(SandboxFailure.of(
                SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION,
                "container mount path policy is unsatisfied", Set.of()));
    }

    static ContainerRemovalObservation removeContainer(
            ContainerEngineConfiguration engine,
            String name,
            Duration timeout,
            Duration retryDelay) {
        ContainerRemovalDeadline deadline = ContainerRemovalDeadline.start(timeout);
        RemovalCommandObservation initialRemoval = requestContainerRemoval(
                engine, name, deadline, true);
        return removeContainer(engine, name, deadline, retryDelay, initialRemoval);
    }

    private static ContainerRemovalObservation removeContainer(
            ContainerEngineConfiguration engine,
            String name,
            ContainerRemovalDeadline deadline,
            Duration retryDelay,
            RemovalCommandObservation initialRemoval) {
        ContainerInspection lastInspection = ContainerInspection.unknown("not inspected");
        int attempts = 0;
        boolean removalCommandSucceeded = initialRemoval.succeeded();
        boolean successfulPostReapRemoval = initialRemoval.succeeded()
                && initialRemoval.issuedAfterClientReap();
        while (deadline.hasRetryBudget(INSPECT_OPERATION_MIN_TIMEOUT)) {
            attempts++;
            lastInspection = inspectContainer(
                    engine, name, deadline.retryBudget(INSPECT_OPERATION_MAX_TIMEOUT));
            if (lastInspection.presence() == ContainerPresence.REMOVED) {
                return new ContainerRemovalObservation(
                        true, removalCommandSucceeded, attempts, lastInspection.status(), "");
            }
            if (lastInspection.presence() == ContainerPresence.PRESENT
                    && !successfulPostReapRemoval) {
                RemovalCommandObservation retryRemoval = requestContainerRemoval(
                        engine, name, deadline, true);
                removalCommandSucceeded |= retryRemoval.succeeded();
                successfulPostReapRemoval = retryRemoval.succeeded();
            }
            if (!deadline.hasRetryBudget(INSPECT_OPERATION_MIN_TIMEOUT)) break;
            try {
                long sleepMillis = deadline.retrySleepMillis(retryDelay);
                if (sleepMillis == 0L) break;
                Thread.sleep(sleepMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new ContainerRemovalObservation(
                        false, removalCommandSucceeded, attempts, lastInspection.status(),
                        "container removal observation was interrupted");
            }
        }

        Duration finalInspectionTimeout = deadline.finalInspectionTimeout();
        if (!finalInspectionTimeout.isZero()) {
            attempts++;
            lastInspection = inspectContainer(engine, name, finalInspectionTimeout);
            if (lastInspection.presence() == ContainerPresence.REMOVED) {
                return new ContainerRemovalObservation(
                        true, removalCommandSucceeded, attempts, lastInspection.status(), "");
            }
        }
        String message = lastInspection.presence() == ContainerPresence.PRESENT
                ? "container remains after bounded forced-removal retries (status="
                        + lastInspection.status() + ")"
                : "container removal could not be verified after bounded retries";
        return new ContainerRemovalObservation(
                false, removalCommandSucceeded, attempts, lastInspection.status(), message);
    }

    private static RemovalCommandObservation requestContainerRemoval(
            ContainerEngineConfiguration engine,
            String name,
            ContainerRemovalDeadline deadline,
            boolean issuedAfterClientReap) {
        if (!deadline.hasRetryBudget(REMOVE_OPERATION_MIN_TIMEOUT)) {
            return new RemovalCommandObservation(false, issuedAfterClientReap);
        }
        ContainerEngineProcess.EngineResult result = ContainerEngineProcess.execute(
                engine.immediateRemovalCommand(name),
                engine.engineEnvironment(), deadline.retryBudget(REMOVE_OPERATION_MAX_TIMEOUT));
        return new RemovalCommandObservation(result.succeeded(), issuedAfterClientReap);
    }

    static ContainerInspection inspectContainer(
            ContainerEngineConfiguration engine, String name, Duration timeout) {
        ContainerEngineProcess.EngineResult result = ContainerEngineProcess.execute(
                List.of(engine.executable().toString(), "container", "inspect",
                        "--format={{.State.Status}}", name),
                engine.engineEnvironment(), timeout);
        if (result.succeeded()) {
            String status = result.stdout().lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .findFirst()
                    .map(ContainerSandboxProcessLauncher::normalizeStatus)
                    .orElse("present");
            return new ContainerInspection(ContainerPresence.PRESENT, status, "");
        }
        String diagnostic = result.diagnostic().toLowerCase(Locale.ROOT);
        if (isContainerNotFound(diagnostic)) {
            return new ContainerInspection(ContainerPresence.REMOVED, "removed", "");
        }
        String failure = result.completed()
                ? "container inspect exited " + result.exitCode()
                : "container inspect did not complete";
        return ContainerInspection.unknown(failure);
    }

    private static boolean isContainerNotFound(String diagnostic) {
        return diagnostic.contains("no such container")
                || diagnostic.contains("no such object")
                || diagnostic.contains("no container with name or id")
                || diagnostic.contains("no container with id or name");
    }

    private static String normalizeStatus(String status) {
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() >= 2 && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized.isEmpty() ? "present" : normalized;
    }

    private static void waitForExit(Process process, Duration duration) {
        if (!process.isAlive()) return;
        try {
            process.waitFor(duration.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static SandboxCleanupObservation cleanupObservation(
            String containerName,
            long engineClientPid,
            ContainerRemovalObservation removal,
            SandboxCleanupObservation localCleanup,
            boolean capturesComplete) {
        boolean engineClientReaped = !localCleanup.survivors().contains(engineClientPid);
        boolean workloadContained = removal.completed();
        List<Long> detachedHelpers = localCleanup.survivors().stream()
                .filter(pid -> pid != engineClientPid)
                .toList();
        List<Long> blockingSurvivors = new ArrayList<>();
        if (!engineClientReaped) blockingSurvivors.add(engineClientPid);
        if (!workloadContained) blockingSurvivors.addAll(detachedHelpers);
        boolean completed = removal.completed() && engineClientReaped
                && workloadContained && capturesComplete;
        List<String> failures = new ArrayList<>();
        if (!removal.completed()) failures.add(removal.failureMessage());
        if (!engineClientReaped) failures.add("container engine client remains alive");
        if (!workloadContained) failures.add("sandbox workload containment is not proven");
        if (!capturesComplete) failures.add("container capture streams remain open");
        Optional<SandboxFailure> failure = completed
                ? Optional.empty()
                : Optional.of(SandboxFailure.of(SandboxFailureCode.SANDBOX_CLEANUP_FAILED,
                        String.join("; ", failures), Set.of()));
        return new SandboxCleanupObservation(
                completed, removal.completed(), engineClientReaped, workloadContained,
                capturesComplete, localCleanup.descendantsObserved(), blockingSurvivors,
                removal.completed() ? detachedHelpers : List.of(),
                OptionalLong.of(engineClientPid), containerName, removal.lastStatus(), failure);
    }

    static Optional<SandboxFailure> selectResultFailure(
            SandboxFailure primaryFailure, SandboxCleanupObservation cleanup) {
        return primaryFailure == null ? cleanup.failure() : Optional.of(primaryFailure);
    }

    private static String bindMount(Path source, String destination, boolean readOnly)
            throws IOException {
        return "type=bind,src=" + source.toRealPath() + ",destination=" + destination + ","
                + (readOnly ? "ro" : "rw");
    }

    private static String translateArgument(
            FilesystemPolicy filesystem, List<Path> inputs, String argument) {
        Path candidate;
        try { candidate = Path.of(argument); }
        catch (RuntimeException invalid) { return argument; }
        if (!candidate.isAbsolute()) return argument;
        Path normalized = candidate.normalize();
        for (int index = 0; index < inputs.size(); index++) {
            if (normalized.equals(inputs.get(index))) return "/sandbox/inputs/input-" + index;
        }
        if (normalized.startsWith(filesystem.workspaceRoot())) {
            return translateWorkspacePath(filesystem.workspaceRoot(), normalized);
        }
        return argument;
    }

    private static String translateWorkspacePath(Path workspace, Path path) {
        Path relative = workspace.relativize(path);
        return relative.getNameCount() == 0
                ? CONTAINER_WORKSPACE
                : CONTAINER_WORKSPACE + "/" + relative.toString().replace('\\', '/');
    }

    private static SandboxExecutionResult setupFailure(Path working, SandboxFailure failure) {
        Instant now = Instant.now();
        return new SandboxExecutionResult(OptionalInt.empty(),
                new BoundedCapture(new byte[0], false), new BoundedCapture(new byte[0], false),
                Optional.of(failure),
                new SandboxExecutionObservation(new SandboxExecutionHandle(-1, now), working,
                        Duration.ZERO, new SandboxCleanupObservation(true, 0, List.of(), "")));
    }

    private static boolean closeAndJoinCaptures(
            CaptureReader stdout, Thread outThread, CaptureReader stderr, Thread errThread) {
        boolean outComplete = joinCapture(outThread, 250);
        boolean errComplete = joinCapture(errThread, 250);
        stdout.close();
        stderr.close();
        return (outComplete || joinCapture(outThread, 750))
                & (errComplete || joinCapture(errThread, 750));
    }

    private static boolean joinCapture(Thread thread, long timeoutMillis) {
        try { thread.join(timeoutMillis); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        return !thread.isAlive();
    }

    private record UnixIdentity(String uid, String gid) {}

    enum ContainerPresence { REMOVED, PRESENT, UNKNOWN }

    record ContainerInspection(ContainerPresence presence, String status, String failureMessage) {
        private static ContainerInspection unknown(String failureMessage) {
            return new ContainerInspection(ContainerPresence.UNKNOWN, "unknown", failureMessage);
        }
    }

    record ContainerRemovalObservation(
            boolean completed,
            boolean removalCommandSucceeded,
            int attempts,
            String lastStatus,
            String failureMessage) {}

    private record RemovalCommandObservation(boolean succeeded, boolean issuedAfterClientReap) {}

    private record ContainerRemovalDeadline(long retryDeadline, long finalDeadline) {
        private static ContainerRemovalDeadline start(Duration totalTimeout) {
            long minimumTotal = REMOVE_OPERATION_MIN_TIMEOUT.toNanos()
                    + INSPECT_OPERATION_MIN_TIMEOUT.toNanos();
            long totalNanos = totalTimeout.toNanos();
            if (totalNanos < minimumTotal) {
                throw new IllegalArgumentException(
                        "container removal timeout must reserve meaningful rm and inspect budgets");
            }
            long verificationReserve = Math.min(
                    INSPECT_OPERATION_MAX_TIMEOUT.toNanos(),
                    Math.max(INSPECT_OPERATION_MIN_TIMEOUT.toNanos(), totalNanos / 4));
            long now = System.nanoTime();
            long finalDeadline = now + totalNanos;
            return new ContainerRemovalDeadline(
                    finalDeadline - verificationReserve, finalDeadline);
        }

        private boolean hasRetryBudget(Duration minimum) {
            return retryDeadline - System.nanoTime() >= minimum.toNanos();
        }

        private Duration retryBudget(Duration maximum) {
            return remainingTime(retryDeadline, maximum);
        }

        private Duration remainingRetryTime(Duration maximum) {
            return remainingTime(retryDeadline, maximum);
        }

        private long retrySleepMillis(Duration requested) {
            long available = retryDeadline - System.nanoTime()
                    - INSPECT_OPERATION_MIN_TIMEOUT.toNanos();
            if (available <= 0L) return 0L;
            return Math.max(1L, Math.min(
                    requested.toMillis(), TimeUnit.NANOSECONDS.toMillis(available)));
        }

        private Duration finalInspectionTimeout() {
            return remainingTime(finalDeadline, INSPECT_OPERATION_MAX_TIMEOUT);
        }

        private static Duration remainingTime(long deadline, Duration maximum) {
            long remaining = Math.max(0L, deadline - System.nanoTime());
            return Duration.ofNanos(Math.min(maximum.toNanos(), remaining));
        }
    }

    private static final class CaptureReader implements Runnable, AutoCloseable {
        private final InputStream input;
        private final int limit;
        private final List<byte[]> secrets;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final ArrayList<Byte> pending = new ArrayList<>();
        private volatile boolean truncated;

        private CaptureReader(InputStream input, long limit, List<byte[]> secrets) {
            this.input = input;
            this.limit = Math.toIntExact(Math.min(limit, Integer.MAX_VALUE));
            this.secrets = secrets.stream().map(byte[]::clone).toList();
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8192];
            try (input) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    for (int index = 0; index < read; index++) accept(buffer[index]);
                }
            } catch (IOException ignored) { }
            finally {
                finishPending();
                secrets.forEach(value -> Arrays.fill(value, (byte) 0));
            }
        }

        @Override
        public void close() {
            try { input.close(); }
            catch (IOException ignored) { }
        }

        private void accept(byte value) {
            if (secrets.isEmpty()) {
                write(value);
                return;
            }
            pending.add(value);
            while (!pending.isEmpty()) {
                byte[] exact = secrets.stream().filter(this::equalsPending).findFirst().orElse(null);
                if (exact != null) {
                    write(REDACTED);
                    pending.clear();
                    return;
                }
                if (secrets.stream().anyMatch(this::startsWithPending)) return;
                write(pending.removeFirst());
            }
        }

        private void finishPending() {
            if (pending.isEmpty()) return;
            if (secrets.stream().anyMatch(this::startsWithPending)) write(REDACTED);
            else while (!pending.isEmpty()) write(pending.removeFirst());
            pending.clear();
        }

        private boolean equalsPending(byte[] secret) {
            if (secret.length != pending.size()) return false;
            for (int index = 0; index < secret.length; index++) {
                if (secret[index] != pending.get(index)) return false;
            }
            return true;
        }

        private boolean startsWithPending(byte[] secret) {
            if (secret.length < pending.size()) return false;
            for (int index = 0; index < pending.size(); index++) {
                if (secret[index] != pending.get(index)) return false;
            }
            return true;
        }

        private void write(byte value) {
            if (bytes.size() < limit) bytes.write(value);
            else truncated = true;
        }

        private void write(byte[] value) {
            for (byte item : value) write(item);
        }

        private BoundedCapture capture() {
            return new BoundedCapture(bytes.toByteArray(), truncated);
        }
    }
}
