package com.example.platform.remoterender.app;

import com.example.platform.providerplugin.*;
import com.example.platform.providerplugin.remote.*;
import com.example.platform.workerfabric.domain.*;
import com.example.platform.workerfabric.domain.providernative.*;
import com.example.platform.sandbox.SandboxCancellation;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Worker-local execution handles only. Task, attempt, generation and completion belong to the platform. */
public final class RemoteRenderService {
    @FunctionalInterface public interface RuntimeContextFactory {
        ProviderPluginRuntimeContext create(ProviderPluginContribution contribution, SandboxCancellation cancellation);
    }
    private final WorkerRuntimeDescriptor runtime;
    private final WorkerRuntimeIncarnationId incarnation;
    private final WorkerRuntimeSupportAdvertisement advertisement;
    private final ProviderPluginCatalog catalog;
    private final RuntimeContextFactory contexts;
    private final Path inputRoot;
    private final ConcurrentHashMap<RuntimeExecutionContext, AtomicBoolean> active = new ConcurrentHashMap<>();

    public RemoteRenderService(WorkerRuntimeDescriptor runtime, WorkerRuntimeIncarnationId incarnation,
            WorkerRuntimeSupportAdvertisement advertisement, ProviderPluginCatalog catalog,
            RuntimeContextFactory contexts, Path inputRoot) {
        this.runtime = Objects.requireNonNull(runtime);
        this.incarnation = Objects.requireNonNull(incarnation);
        this.advertisement = Objects.requireNonNull(advertisement);
        this.catalog = Objects.requireNonNull(catalog);
        this.contexts = Objects.requireNonNull(contexts);
        this.inputRoot = inputRoot.toAbsolutePath().normalize();
    }

    public void requireRuntime(String runtimeId, String incarnationId) {
        if (!runtime.id().value().equals(runtimeId) || !incarnation.value().equals(incarnationId)) {
            throw failure(ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH, "Worker runtime identity mismatch");
        }
    }

    public ProviderExecutionOutput execute(String runtimeId, String incarnationId, RemoteWorkerInvocation invocation) throws IOException {
        requireRuntime(runtimeId, incarnationId);
        var bundle = invocation.bundle();
        var context = WorkerInvocationCodec.context(bundle);
        var contribution = catalog.find(bundle.providerBindingPin()).orElseThrow(() ->
                failure(ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH, "Exact provider binding unavailable"));
        var requirement = contribution.workerRuntimeSupportRequirement();
        if (requirement == null || !requirement.providerBindingPin().equals(bundle.providerBindingPin())
                || !RuntimeSupportAdvertisementEvaluator.evaluate(runtime, Optional.of(advertisement), Optional.of(requirement))
                        .acceptedAsCandidateEvidence()) {
            throw failure(ProviderNativeFailureCode.SANDBOX_POLICY_REJECTED, "Installed runtime support does not match requirement");
        }
        // Inputs are already staged by the platform materialization path, never fetched from a caller URL.
        for (var input : invocation.inputs()) {
            Path path = input.materializedArtifact().path();
            if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(inputRoot.toRealPath())
                    || Files.size(path) != input.materializedArtifact().byteLength()) {
                throw failure(ProviderNativeFailureCode.INVALID_MATERIALIZED_INPUT_BINDING, "Input outside worker materialization scope");
            }
            try (var bytes = Files.newInputStream(path)) {
                var hash = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = bytes.read(buffer)) != -1) hash.update(buffer, 0, count);
                if (!HexFormat.of().formatHex(hash.digest()).equals(input.artifactPin().contentDigest().canonicalValue())) {
                    throw failure(ProviderNativeFailureCode.INVALID_MATERIALIZED_INPUT_BINDING, "Materialized input digest mismatch");
                }
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        var cancellation = new AtomicBoolean();
        if (active.putIfAbsent(context, cancellation) != null) {
            throw failure(ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH, "Execution context already active");
        }
        Path scratch = null;
        try {
            var hostContext = contexts.create(contribution, cancellation::get);
            scratch = hostContext.workspaceRoot();
            final Path ownedScratch = scratch;
            var binding = contribution.createRuntimeBinding(hostContext);
            var output = binding.executePrepared(bundle, invocation.inputs());
            if (cancellation.get()) {
                output.close();
                throw failure(ProviderNativeFailureCode.PROCESS_CANCELLED, "Execution cancellation requested");
            }
            return new ProviderExecutionOutput(new FilterInputStream(output.content()) {
                @Override public void close() throws IOException {
                    Exception failure = null;
                    try { super.close(); } catch (IOException | RuntimeException error) { failure = error; }
                    try { cleanupScratch(ownedScratch); }
                    catch (IOException | RuntimeException cleanup) {
                        if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
                    } finally { active.remove(context, cancellation); }
                    if (failure instanceof IOException io) throw io;
                    if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
                }
            });
        } catch (IOException | RuntimeException failure) {
            active.remove(context, cancellation);
            try { cleanupScratch(scratch); } catch (IOException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public boolean cancel(String runtimeId, String incarnationId, RuntimeExecutionContext context) {
        requireRuntime(runtimeId, incarnationId);
        var handle = active.get(context);
        if (handle == null) return false;
        handle.set(true);
        return true; // request acknowledgement, never canonical task cancellation
    }

    private static ProviderNativeExecutionFailure failure(ProviderNativeFailureCode code, String message) {
        return new ProviderNativeExecutionFailure(code, message);
    }

    private void cleanupScratch(Path directory) throws IOException {
        // Only the deployment factory's fresh, private execution directories belong to this handle.
        // The materialization root and caller-owned input files are never cleanup targets.
        if (directory == null || !inputRoot.equals(directory.getParent())
                || !directory.getFileName().toString().startsWith("execution-")) return;
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
