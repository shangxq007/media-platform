package com.example.platform.remoterender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.platform.remoterender.api.*;
import com.example.platform.remoterender.app.RemoteRenderService;
import com.example.platform.providerplugin.*;
import com.example.platform.providerplugin.remote.*;
import com.example.platform.execution.domain.provider.*;
import com.example.platform.execution.taskgraph.*;
import com.example.platform.workerfabric.domain.*;
import com.example.platform.workerfabric.domain.providernative.*;
import com.example.platform.workerfabric.reuse.*;
import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.sun.net.httpserver.HttpServer;

class RemoteRenderWorkerTest {
    static final WorkerRuntimeId RUNTIME = WorkerRuntimeId.of("runtime-a");
    static final WorkerRuntimeIncarnationId INCARNATION = WorkerRuntimeIncarnationId.of("incarnation-a");
    static final RuntimeSupportIdentifier SUPPORT = RuntimeSupportIdentifier.of("test.process.v1");
    static final ProviderBindingPin PIN = new ProviderBindingPin(ProviderId.of("test"), ProviderImplementationId.of("test.process"),
            ProviderVersion.of("1.0.0"), ProviderExecutionContractVersion.of(1, 0),
            ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0)), List.of());
    @TempDir Path root;
    RemoteRenderService service;
    ProviderPluginCatalog catalog;
    AtomicInteger executions;
    AtomicBoolean block;
    CountDownLatch started;
    AtomicBoolean cancellationObserved;
    RuntimeExecutionBundle bundle;

    @BeforeEach void setup() {
        executions = new AtomicInteger();
        block = new AtomicBoolean();
        started = new CountDownLatch(1);
        cancellationObserved = new AtomicBoolean();
        // Worker consumes catalog queries; PF4J host tests own registration/retirement coverage.
        catalog = mock(ProviderPluginCatalog.class);
        var contribution = mock(ProviderPluginContribution.class);
        when(contribution.pluginId()).thenReturn("test.process");
        when(contribution.pluginVersion()).thenReturn("1.0.0");
        when(contribution.providerBindingPin()).thenReturn(PIN);
        when(contribution.workerRuntimeSupportRequirement()).thenReturn(new WorkerRuntimeSupportRequirement(
                PIN, RuntimeLifecycleKind.EPHEMERAL_TASK, SUPPORT));
        when(contribution.createRuntimeBinding(any())).thenAnswer(call -> {
            ProviderPluginRuntimeContext context = call.getArgument(0);
            return new ProviderNativeRuntimeBinding<TestPlan>(
                    (task, ignored) -> { throw new AssertionError("worker must not lower"); },
                    (plan, ignored) -> { throw new AssertionError("worker must not adapt canonical semantics"); },
                    (commands, inputs) -> {
                        executions.incrementAndGet();
                        started.countDown();
                        if (block.get()) {
                            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                            while (!context.cancellation().isCancellationRequested() && System.nanoTime() < deadline) {
                                try { Thread.sleep(5); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                            }
                            cancellationObserved.set(context.cancellation().isCancellationRequested());
                            throw new ProviderNativeExecutionFailure(ProviderNativeFailureCode.PROCESS_CANCELLED, "bounded test cancellation");
                        }
                        return new ProviderExecutionOutput(new ByteArrayInputStream(new byte[] {1, 2, 3}));
                    });
        });
        when(catalog.find(PIN)).thenReturn(Optional.of(contribution));
        service = worker(new WorkerRuntimeSupportAdvertisement(RUNTIME, RuntimeLifecycleKind.EPHEMERAL_TASK,
                Map.of(SUPPORT, new RuntimeSupportEvidence("provider-plugin", "test.process@1.0.0"))));
        bundle = bundle(ExecutionOwnershipGeneration.first());
    }
    RemoteRenderService worker(WorkerRuntimeSupportAdvertisement advertisement) {
        return new RemoteRenderService(WorkerRuntimeDescriptor.local(RUNTIME, RuntimeLifecycleKind.EPHEMERAL_TASK,
                PhysicalHostId.of("host-a")), INCARNATION, advertisement, catalog,
                (contribution, cancel) -> new ProviderPluginRuntimeContext(Path.of("/bin/true"), root, Duration.ofSeconds(5), 1024, cancel), root);
    }
    RuntimeExecutionBundle bundle(ExecutionOwnershipGeneration generation) {
        var task = new ExecutableTaskId("a".repeat(64));
        var attempt = ExecutionAttemptId.of("attempt-a");
        return new RuntimeExecutionBundle(task, PIN, attempt, generation, List.of(new ExecutionCommand(task, PIN,
                attempt, generation, 0, ProcessInvocationSpec.of("/bin/true", List.of()))));
    }
    RemoteWorkerInvocation invocation() { return new RemoteWorkerInvocation(bundle, List.of()); }
    ProviderExecutionOutput execute() throws IOException { return service.execute(RUNTIME.value(), INCARNATION.value(), invocation()); }
    record TestPlan(ExecutableTaskId executableTaskId, ProviderBindingPin providerBindingPin) implements ProviderNativeExecutionPlan {}

    @Test void typedPreparedSubmissionUsesExistingRuntimeExecutorAndReturnsOnlyOutput() throws Exception {
        try (var output = execute()) { assertArrayEquals(new byte[]{1, 2, 3}, output.content().readAllBytes()); }
        assertEquals(1, executions.get());
    }
    @Test void exactWireRoundTripPreservesTaskBindingAttemptGeneration() throws Exception {
        var decoded = WorkerInvocationCodec.decode(WorkerInvocationCodec.encode(invocation()), RemoteWorkerInvocation.class);
        assertEquals(invocation(), decoded);
    }
    @Test void missingOrMismatchedRuntimeIdentityRejectsBeforeExecution() {
        for (String id : Arrays.asList(null, "", "other")) {
            assertThrows(ProviderNativeExecutionFailure.class, () -> service.execute(id, INCARNATION.value(), invocation()));
        }
        assertThrows(ProviderNativeExecutionFailure.class, () -> service.execute(RUNTIME.value(), "old-incarnation", invocation()));
        assertEquals(0, executions.get());
    }
    @Test void unsupportedRuntimeAdvertisementDoesNotExecute() {
        service = worker(new WorkerRuntimeSupportAdvertisement(RUNTIME, RuntimeLifecycleKind.EPHEMERAL_TASK, Map.of()));
        assertEquals(ProviderNativeFailureCode.SANDBOX_POLICY_REJECTED, assertThrows(ProviderNativeExecutionFailure.class, this::execute).code());
    }
    @Test void missingProviderDoesNotFallBackToAnotherProvider() {
        when(catalog.find(PIN)).thenReturn(Optional.empty());
        assertEquals(ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH, assertThrows(ProviderNativeExecutionFailure.class, this::execute).code());
        assertEquals(0, executions.get());
    }
    @Test void duplicateActiveInvocationIsRejectedUntilOutputIsClosed() throws Exception {
        try (var output = execute()) {
            assertThrows(ProviderNativeExecutionFailure.class, this::execute);
            assertEquals(1, executions.get());
        }
    }
    @Test void malformedCommandBindingAndDuplicateSequencesAreRejected() {
        assertThrows(ProviderNativeExecutionFailure.class, () -> new RuntimeExecutionBundle(bundle.executableTaskId(), PIN,
                ExecutionAttemptId.of("other"), bundle.platformOwnershipGeneration(), bundle.commands()));
        assertThrows(IllegalArgumentException.class, () -> new RemoteWorkerInvocation(new RuntimeExecutionBundle(
                bundle.executableTaskId(), PIN, bundle.platformExecutionAttemptId(), bundle.platformOwnershipGeneration(),
                List.of(bundle.commands().getFirst(), bundle.commands().getFirst())), List.of()));
    }
    @Test void cancellationReachesExistingProviderContextAndStaleGenerationCannotCancel() throws Exception {
        block.set(true);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> { try (var output = execute()) { return output.content().readAllBytes(); } });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertFalse(service.cancel(RUNTIME.value(), INCARNATION.value(), WorkerInvocationCodec.context(bundle(new ExecutionOwnershipGeneration(2)))));
            assertTrue(service.cancel(RUNTIME.value(), INCARNATION.value(), WorkerInvocationCodec.context(bundle)));
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertTrue(cancellationObserved.get());
        }
    }
    @Test void workerInputMustMatchImmutableDigest() throws Exception {
        Path file = Files.write(root.resolve("input.bin"), new byte[]{9});
        var pin = new ArtifactPin(new ArtifactId("artifact-test"), ContentDigest.sha256("0".repeat(64)));
        var input = new MaterializedExecutionInput(new com.example.platform.execution.domain.ExecutionInputId("input-a"),
                pin, new MaterializedArtifact(pin, file, 1));
        var failure = assertThrows(ProviderNativeExecutionFailure.class, () -> service.execute(RUNTIME.value(), INCARNATION.value(),
                new RemoteWorkerInvocation(bundle, List.of(input))));
        assertEquals(ProviderNativeFailureCode.INVALID_MATERIALIZED_INPUT_BINDING, failure.code());
        assertEquals(0, executions.get());
    }
    @Test void oldJobAndCallbackRoutesAreGoneAndUnconfiguredKeyFailsClosed() throws Exception {
        var filter = new WorkerApiKeyFilter();
        var mvc = MockMvcBuilders.standaloneSetup(new RemoteWorkerController(service)).addFilters(filter).build();
        mvc.perform(post("/api/remote-worker/executions")).andExpect(status().isServiceUnavailable());
        ReflectionTestUtils.setField(filter, "configuredApiKey", "test-key");
        mvc.perform(post("/api/remote-worker/executions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/remote-worker/jobs/old/callback").header("X-Worker-Api-Key", "test-key")).andExpect(status().isNotFound());
        mvc.perform(post("/api/remote-worker/workers/old/jobs").header("X-Worker-Api-Key", "test-key")).andExpect(status().isNotFound());
        var pending = mvc.perform(post("/api/remote-worker/executions").header("X-Worker-Api-Key", "test-key")
                .header("X-Worker-Runtime-Id", RUNTIME.value()).header("X-Worker-Incarnation", INCARNATION.value())
                .contentType("application/json").content(WorkerInvocationCodec.encode(invocation())))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
    @Test void httpRuntimeExecutorPreservesPlatformAdaptationAndRejectsStaleResponse() throws Exception {
        AtomicBoolean staleResponse = new AtomicBoolean();
        AtomicBoolean failedResponse = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/remote-worker/executions", exchange -> {
            try {
                assertEquals("test-key", exchange.getRequestHeaders().getFirst("X-Worker-Api-Key"));
                if (failedResponse.get()) {
                    exchange.getResponseHeaders().add("X-Runtime-Failure", "PROCESS_TIMEOUT");
                    exchange.sendResponseHeaders(422, -1);
                    return;
                }
                var invocation = WorkerInvocationCodec.decode(exchange.getRequestBody().readAllBytes(), RemoteWorkerInvocation.class);
                try (var output = service.execute(exchange.getRequestHeaders().getFirst("X-Worker-Runtime-Id"),
                        exchange.getRequestHeaders().getFirst("X-Worker-Incarnation"), invocation)) {
                    exchange.getResponseHeaders().add("X-Execution-Context", staleResponse.get() ? "old" :
                            WorkerInvocationCodec.correlation(WorkerInvocationCodec.context(invocation.bundle())));
                    exchange.sendResponseHeaders(200, 3);
                    output.content().transferTo(exchange.getResponseBody());
                }
            } finally { exchange.close(); }
        });
        server.start();
        try (var http = new HttpWorkerRuntimeCommandExecutor(URI.create("http://localhost:" + server.getAddress().getPort()),
                    RUNTIME, INCARNATION, "test-key", WorkerHttpTimeouts.boundedBy(Duration.ofSeconds(5)))) {
            AtomicInteger adapted = new AtomicInteger();
            var binding = new ProviderNativeRuntimeBinding<TestPlan>(
                    (task, ignored) -> new TestPlan(task.id(), task.providerBindingPin()),
                    (plan, context) -> { adapted.incrementAndGet(); return bundle; },
                    (ignored, inputs) -> { throw new AssertionError("local fallback forbidden"); }).withCommandExecutor(http);
            var task = mock(ExecutableTask.class);
            when(task.id()).thenReturn(bundle.executableTaskId());
            when(task.providerBindingPin()).thenReturn(PIN);
            when(task.requiredRuntimeInputs()).thenReturn(List.of());
            try (var output = binding.execute(task, WorkerInvocationCodec.context(bundle), List.of())) {
                assertArrayEquals(new byte[]{1, 2, 3}, output.content().readAllBytes());
            }
            assertEquals(1, adapted.get());
            staleResponse.set(true);
            assertThrows(ProviderNativeExecutionFailure.class, () -> http.execute(bundle, List.of()));
            failedResponse.set(true);
            assertEquals(ProviderNativeFailureCode.PROCESS_TIMEOUT,
                    assertThrows(ProviderNativeExecutionFailure.class, () -> http.execute(bundle, List.of())).code());
        } finally { server.stop(0); }
    }
    @Test void remoteClientRequiresAuthenticatedEncryptedNonLoopbackTransport() {
        assertThrows(IllegalArgumentException.class, () -> new HttpWorkerRuntimeCommandExecutor(URI.create("http://example.com"),
                RUNTIME, INCARNATION, "test-key", WorkerHttpTimeouts.boundedBy(Duration.ofSeconds(5))));
        assertThrows(IllegalArgumentException.class, () -> new HttpWorkerRuntimeCommandExecutor(URI.create("https://example.com"),
                RUNTIME, INCARNATION, "", WorkerHttpTimeouts.boundedBy(Duration.ofSeconds(5))));
    }

    @Test void platformAdapterCannotRebindGenerationBeforeDispatch() {
        var task = mock(ExecutableTask.class);
        when(task.id()).thenReturn(bundle.executableTaskId());
        when(task.providerBindingPin()).thenReturn(PIN);
        when(task.requiredRuntimeInputs()).thenReturn(List.of());
        var binding = new ProviderNativeRuntimeBinding<TestPlan>((input, ignored) -> new TestPlan(input.id(), PIN),
                (plan, context) -> bundle(new ExecutionOwnershipGeneration(2)),
                (commands, inputs) -> { throw new AssertionError("mismatched adapter must never execute"); });
        assertEquals(ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH, assertThrows(ProviderNativeExecutionFailure.class,
                () -> binding.execute(task, WorkerInvocationCodec.context(bundle), List.of())).code());
    }

    @Test void hostCannotReadInputOutsideMaterializationRoot(@TempDir Path other) throws Exception {
        Path file = Files.write(other.resolve("foreign.bin"), new byte[]{9});
        var pin = new ArtifactPin(new ArtifactId("artifact-test"), ContentDigest.sha256("0".repeat(64)));
        var input = new MaterializedExecutionInput(new com.example.platform.execution.domain.ExecutionInputId("input-a"),
                pin, new MaterializedArtifact(pin, file, 1));
        assertThrows(ProviderNativeExecutionFailure.class, () -> service.execute(RUNTIME.value(), INCARNATION.value(),
                new RemoteWorkerInvocation(bundle, List.of(input))));
        assertEquals(0, executions.get());
    }

    @Test void canonicalTimelineIsNotAWorkerExecutionInput() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new RemoteWorkerController(service)).build();
        mvc.perform(post("/api/remote-worker/executions")
                .header("X-Worker-Runtime-Id", RUNTIME.value()).header("X-Worker-Incarnation", INCARNATION.value())
                .contentType("application/json").content("{\"profile\":\"default\",\"timelineJson\":\"{}\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, executions.get());
    }

    @Test void actualWorkerApplicationComposesWithoutCanonicalDatabaseOrRenderScan() throws Exception {
        Path plugins = Files.createDirectory(root.resolve("plugins"));
        var application = new org.springframework.boot.SpringApplication(RemoteRenderWorkerApplication.class);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("app.remote-worker.runtime-id", RUNTIME.value(),
                "app.remote-worker.incarnation-id", INCARNATION.value(), "app.remote-worker.runtime-kind", "EPHEMERAL_TASK",
                "app.remote-worker.physical-host-id", "host-a", "app.remote-worker.plugins-directory", plugins.toString(),
                "app.remote-worker.workspace-root", root.toString(), "spring.main.banner-mode", "off"));
        try (var context = application.run()) {
            assertEquals(1, context.getBeansOfType(RemoteRenderService.class).size());
            assertEquals(0, context.getBeansOfType(javax.sql.DataSource.class).size());
            context.getBean(RemoteRenderService.class).requireRuntime(RUNTIME.value(), INCARNATION.value());
            assertTrue(Arrays.stream(context.getBeanDefinitionNames()).noneMatch(name -> name.contains("renderProviderRouter")));
        }
    }

    @Test void outputCloseRetiresOnlyPrivateScratchAndKeepsMaterializedInputs() throws Exception {
        Path input = Files.write(root.resolve("retained-input"), new byte[]{7});
        Path scratch = Files.createTempDirectory(root, "execution-");
        service = new RemoteRenderService(WorkerRuntimeDescriptor.local(RUNTIME, RuntimeLifecycleKind.EPHEMERAL_TASK,
                PhysicalHostId.of("host-a")), INCARNATION, new WorkerRuntimeSupportAdvertisement(RUNTIME,
                RuntimeLifecycleKind.EPHEMERAL_TASK, Map.of(SUPPORT, new RuntimeSupportEvidence("provider-plugin", "test"))),
                catalog, (contribution, cancel) -> new ProviderPluginRuntimeContext(Path.of("/bin/true"), scratch,
                        Duration.ofSeconds(5), 1024, cancel), root);
        try (var output = execute()) { assertEquals(3, output.content().readAllBytes().length); }
        assertFalse(Files.exists(scratch));
        assertTrue(Files.exists(input));
    }
}
