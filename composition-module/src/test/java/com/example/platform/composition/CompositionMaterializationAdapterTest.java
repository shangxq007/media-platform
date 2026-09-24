package com.example.platform.composition;

import com.example.platform.composition.app.CompositionExecutionPort;
import com.example.platform.composition.app.CompositionCapabilityResolutionPort;
import com.example.platform.composition.app.CompositionExecutionRequest;
import com.example.platform.composition.app.CompositionMaterializationAdapter;
import com.example.platform.composition.app.CompositionMaterializationPort;
import com.example.platform.workerfabric.domain.providernative.ProviderExecutionOutput;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompositionMaterializationAdapterTest {
    private final CompositionCapabilityResolutionPort compatible = r -> java.util.Optional.of(
            new CompositionCapabilityResolutionPort.ResolvedCapability(
                    "media.transcode", "1.0", "ExecutableTask", "ProviderExecutionOutput"));
    private final CompositionExecutionRequest request = new CompositionExecutionRequest(
            "tenant-a", "workspace-a", "source-asset", "rev-1", "workflow", 3,
            "media.transcode", "1.0", "attempt-1", Map.of("profile", "default"), Map.of("composition.publish", "granted"));

    @Test void providerOutputMustPassStorageAndArtifactCommitInOrder() {
        var order = new StringBuilder();
        CompositionExecutionPort execution = r -> { order.append("execute,"); return new ProviderExecutionOutput(new ByteArrayInputStream(new byte[] {1, 2})); };
        CompositionMaterializationPort materialization = new CompositionMaterializationPort() {
            public IssuedOutput issue(CompositionExecutionRequest r, ProviderExecutionOutput o) { order.append("storage,"); return new IssuedOutput("tenant-a", "workspace-a", "placement", "sha", 2); }
            public CommittedArtifact commitArtifact(CompositionExecutionRequest r, IssuedOutput o) { order.append("artifact,"); return new CommittedArtifact("tenant-a", "workspace-a", "artifact", "sha"); }
            public void compensate(IssuedOutput o) { order.append("compensate,"); }
        };
        var result = new CompositionMaterializationAdapter(compatible, execution, materialization).execute(request);
        assertEquals("execute,storage,artifact,", order.toString());
        assertEquals("artifact", result.artifact().artifactId());
    }

    @Test void providerFailureCreatesNoStorageOrArtifact() {
        AtomicBoolean materialized = new AtomicBoolean();
        var execution = (CompositionExecutionPort) r -> { throw new IllegalStateException("provider failed"); };
        var materialization = new CompositionMaterializationPort() {
            public IssuedOutput issue(CompositionExecutionRequest r, ProviderExecutionOutput o) { materialized.set(true); throw new AssertionError("must not issue"); }
            public CommittedArtifact commitArtifact(CompositionExecutionRequest r, IssuedOutput o) { throw new AssertionError(); }
            public void compensate(IssuedOutput o) { throw new AssertionError(); }
        };
        assertThrows(IllegalStateException.class, () -> new CompositionMaterializationAdapter(compatible, execution, materialization).execute(request));
        assertFalse(materialized.get());
    }

    @Test void artifactFailureCompensatesIssuedStorageOutput() {
        AtomicBoolean compensated = new AtomicBoolean();
        var execution = (CompositionExecutionPort) r -> new ProviderExecutionOutput(new ByteArrayInputStream(new byte[] {1}));
        var materialization = new CompositionMaterializationPort() {
            public IssuedOutput issue(CompositionExecutionRequest r, ProviderExecutionOutput o) { return new IssuedOutput("tenant-a", "workspace-a", "placement", "sha", 1); }
            public CommittedArtifact commitArtifact(CompositionExecutionRequest r, IssuedOutput o) { throw new IllegalStateException("artifact failed"); }
            public void compensate(IssuedOutput o) { compensated.set(true); }
        };
        assertThrows(IllegalStateException.class, () -> new CompositionMaterializationAdapter(compatible, execution, materialization).execute(request));
        assertTrue(compensated.get());
    }

    @Test void forgedStorageScopeIsRejectedAndCompensated() {
        AtomicBoolean compensated = new AtomicBoolean();
        var execution = (CompositionExecutionPort) r -> new ProviderExecutionOutput(new ByteArrayInputStream(new byte[] {1}));
        var materialization = new CompositionMaterializationPort() {
            public IssuedOutput issue(CompositionExecutionRequest r, ProviderExecutionOutput o) { return new IssuedOutput("tenant-b", "workspace-b", "placement", "sha", 1); }
            public CommittedArtifact commitArtifact(CompositionExecutionRequest r, IssuedOutput o) { throw new AssertionError(); }
            public void compensate(IssuedOutput o) { compensated.set(true); }
        };
        assertThrows(IllegalArgumentException.class, () -> new CompositionMaterializationAdapter(compatible, execution, materialization).execute(request));
        assertTrue(compensated.get());
    }

    @Test void incompatibleProviderIsRejectedBeforeDispatch() {
        AtomicBoolean dispatched = new AtomicBoolean();
        var incompatible = (CompositionCapabilityResolutionPort) r -> java.util.Optional.of(
                new CompositionCapabilityResolutionPort.ResolvedCapability(
                        "media.transcode", "1.0", "MediaAsset", "MediaAsset"));
        var execution = (CompositionExecutionPort) r -> {
            dispatched.set(true);
            return new ProviderExecutionOutput(new ByteArrayInputStream(new byte[] {1}));
        };
        var materialization = new CompositionMaterializationPort() {
            public IssuedOutput issue(CompositionExecutionRequest r, ProviderExecutionOutput o) { throw new AssertionError(); }
            public CommittedArtifact commitArtifact(CompositionExecutionRequest r, IssuedOutput o) { throw new AssertionError(); }
            public void compensate(IssuedOutput o) { throw new AssertionError(); }
        };
        assertThrows(IllegalStateException.class,
                () -> new CompositionMaterializationAdapter(incompatible, execution, materialization).execute(request));
        assertFalse(dispatched.get());
    }

    @Test void completedAttemptIsReturnedWithoutRedispatch() {
        AtomicBoolean dispatched = new AtomicBoolean();
        var execution = (CompositionExecutionPort) r -> {
            dispatched.set(true);
            return new ProviderExecutionOutput(new ByteArrayInputStream(new byte[] {1}));
        };
        var existing = new CompositionMaterializationAdapter.Result(request,
                new CompositionMaterializationPort.IssuedOutput("tenant-a", "workspace-a", "placement", "sha", 1),
                new CompositionMaterializationPort.CommittedArtifact("tenant-a", "workspace-a", "artifact", "sha"));
        var materialization = new CompositionMaterializationPort() {
            public java.util.Optional<CompositionMaterializationAdapter.Result> findCommitted(CompositionExecutionRequest r) { return java.util.Optional.of(existing); }
            public IssuedOutput issue(CompositionExecutionRequest r, ProviderExecutionOutput o) { throw new AssertionError(); }
            public CommittedArtifact commitArtifact(CompositionExecutionRequest r, IssuedOutput o) { throw new AssertionError(); }
            public void compensate(IssuedOutput o) { throw new AssertionError(); }
        };
        assertSame(existing, new CompositionMaterializationAdapter(compatible, execution, materialization).execute(request));
        assertFalse(dispatched.get());
    }

    @Test void cancellationBeforeDispatchDoesNotInvokeProvider() {
        AtomicBoolean dispatched = new AtomicBoolean();
        var execution = (CompositionExecutionPort) r -> {
            dispatched.set(true);
            return new ProviderExecutionOutput(new ByteArrayInputStream(new byte[] {1}));
        };
        var materialization = new CompositionMaterializationPort() {
            public IssuedOutput issue(CompositionExecutionRequest r, ProviderExecutionOutput o) { throw new AssertionError(); }
            public CommittedArtifact commitArtifact(CompositionExecutionRequest r, IssuedOutput o) { throw new AssertionError(); }
            public void compensate(IssuedOutput o) { throw new AssertionError(); }
        };
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> new CompositionMaterializationAdapter(compatible, execution, materialization)
                        .execute(request, () -> true));
        assertFalse(dispatched.get());
    }

    @Test void cancellationAfterArtifactCommitDoesNotCompensateDurableArtifact() {
        AtomicBoolean compensated = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        var execution = (CompositionExecutionPort) r -> new ProviderExecutionOutput(new ByteArrayInputStream(new byte[] {1}));
        var materialization = new CompositionMaterializationPort() {
            public IssuedOutput issue(CompositionExecutionRequest r, ProviderExecutionOutput o) { return new IssuedOutput("tenant-a", "workspace-a", "placement", "sha", 1); }
            public CommittedArtifact commitArtifact(CompositionExecutionRequest r, IssuedOutput o) { cancelled.set(true); return new CommittedArtifact("tenant-a", "workspace-a", "artifact", "sha"); }
            public void compensate(IssuedOutput o) { compensated.set(true); }
        };
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> new CompositionMaterializationAdapter(compatible, execution, materialization).execute(request, cancelled::get));
        assertFalse(compensated.get(), "durable Artifact output must be reconciled, not implicitly deleted");
    }
}
