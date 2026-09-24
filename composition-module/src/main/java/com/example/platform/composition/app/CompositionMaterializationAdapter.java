package com.example.platform.composition.app;

import com.example.platform.composition.app.CompositionMaterializationPort.CommittedArtifact;
import com.example.platform.composition.app.CompositionMaterializationPort.IssuedOutput;
import com.example.platform.workerfabric.domain.providernative.ProviderExecutionOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Explicit ProviderExecutionOutput to the platform Artifact boundary.
 *
 * <p>The adapter never creates a second platform identity from provider bytes. Storage
 * issuance and Artifact commitment must succeed first. Disposable Storage output
 * is compensated only before Artifact commitment; after Artifact commitment a
 * publication failure is fenced for reconciliation rather than deleting durable
 * Artifact state implicitly.</p>
 */
public final class CompositionMaterializationAdapter {
    private final CompositionExecutionPort execution;
    private final CompositionCapabilityResolutionPort capabilities;
    private final CompositionMaterializationPort materialization;

    public CompositionMaterializationAdapter(CompositionCapabilityResolutionPort capabilities,
                                             CompositionExecutionPort execution,
                                             CompositionMaterializationPort materialization) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.execution = Objects.requireNonNull(execution, "execution");
        this.materialization = Objects.requireNonNull(materialization, "materialization");
    }

    public Result execute(CompositionExecutionRequest request) {
        return execute(request, () -> false);
    }

    /** Executes one admission attempt; cancellation fences publication at each durable boundary. */
    public Result execute(CompositionExecutionRequest request, BooleanSupplier cancelled) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancelled, "cancelled");
        if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("composition execution cancelled before dispatch");
        var resolved = capabilities.resolve(request)
                .orElseThrow(() -> new IllegalStateException("CAPABILITY_UNAVAILABLE"));
        if (!request.capabilityId().equals(resolved.capabilityId())
                || !request.capabilityVersion().equals(resolved.capabilityVersion())) {
            throw new IllegalArgumentException("resolved capability does not match request");
        }
        if (!"ExecutableTask".equals(resolved.inputType())
                || !"ProviderExecutionOutput".equals(resolved.outputType())) {
            throw new IllegalStateException("INCOMPATIBLE_PROVIDER_CONTRACT");
        }
        var committed = materialization.findCommitted(request);
        if (committed.isPresent()) return committed.get();
        ProviderExecutionOutput providerOutput = execution.execute(request);
        if (providerOutput == null) throw new IllegalStateException("provider execution returned no output");
        IssuedOutput issued = null;
        boolean artifactCommitted = false;
        try (providerOutput) {
            issued = materialization.issue(request, providerOutput);
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("composition execution cancelled after storage issuance");
            if (!request.tenantId().equals(issued.tenantId()) || !request.workspaceId().equals(issued.workspaceId()))
                throw new IllegalArgumentException("storage output scope does not match authenticated request");
            CommittedArtifact artifact = materialization.commitArtifact(request, issued);
            if (!request.tenantId().equals(artifact.tenantId()) || !request.workspaceId().equals(artifact.workspaceId()))
                throw new IllegalArgumentException("Artifact scope does not match authenticated request");
            artifactCommitted = true;
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("composition execution cancelled after Artifact commitment");
            materialization.recordCommitted(request, issued, artifact);
            return new Result(request, issued, artifact);
        } catch (RuntimeException failure) {
            if (issued != null && !artifactCommitted) {
                try { materialization.compensate(issued); } catch (RuntimeException compensation) { failure.addSuppressed(compensation); }
            }
            throw failure;
        } catch (IOException failure) {
            RuntimeException wrapped = new IllegalStateException("provider output could not be closed", failure);
            if (issued != null && !artifactCommitted) {
                try { materialization.compensate(issued); } catch (RuntimeException compensation) { wrapped.addSuppressed(compensation); }
            }
            throw wrapped;
        }
    }

    public record Result(CompositionExecutionRequest request, IssuedOutput issuedOutput, CommittedArtifact artifact) {
        public Result { Objects.requireNonNull(request); Objects.requireNonNull(issuedOutput); Objects.requireNonNull(artifact); }
    }
}
