package com.example.platform.providerplugin.remote;

import com.example.platform.workerfabric.domain.*;
import com.example.platform.workerfabric.domain.providernative.*;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Platform-side RuntimeCommandExecutor; output still flows through the existing Artifact/completion fence. */
public final class HttpWorkerRuntimeCommandExecutor implements RuntimeCommandExecutor {
    private final URI endpoint;
    private final HttpClient client;
    private final WorkerRuntimeId runtime;
    private final WorkerRuntimeIncarnationId incarnation;
    private final String apiKey;
    private final Duration timeout;
    public HttpWorkerRuntimeCommandExecutor(URI endpoint, WorkerRuntimeId runtime,
            WorkerRuntimeIncarnationId incarnation, String apiKey, Duration timeout) {
        this.endpoint = Objects.requireNonNull(endpoint);
        if (!"https".equals(endpoint.getScheme()) && !("http".equals(endpoint.getScheme())
                && List.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost()))) {
            throw new IllegalArgumentException("Remote execution requires HTTPS outside loopback");
        }
        if (endpoint.getUserInfo() != null || apiKey == null || apiKey.isBlank() || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Explicit worker credentials and bounded timeout required");
        }
        this.runtime = Objects.requireNonNull(runtime);
        this.incarnation = Objects.requireNonNull(incarnation);
        this.apiKey = apiKey;
        this.timeout = timeout;
        client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public ProviderExecutionOutput execute(RuntimeExecutionBundle bundle,
            List<MaterializedExecutionInput> inputs) throws IOException {
        var context = WorkerInvocationCodec.context(bundle);
        var request = request("executions", WorkerInvocationCodec.encode(new RemoteWorkerInvocation(bundle, inputs)));
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                ProviderNativeFailureCode code;
                try { code = ProviderNativeFailureCode.valueOf(response.headers().firstValue("X-Runtime-Failure").orElse("RUNTIME_EXECUTION_UNKNOWN")); }
                catch (IllegalArgumentException unknown) { code = ProviderNativeFailureCode.RUNTIME_EXECUTION_UNKNOWN; }
                throw new ProviderNativeExecutionFailure(code, "Worker rejected or failed execution");
            }
            if (!WorkerInvocationCodec.correlation(context).equals(
                    response.headers().firstValue("X-Execution-Context").orElse(""))) {
                response.body().close();
                throw new ProviderNativeExecutionFailure(ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                        "Worker response did not confirm this exact execution context");
            }
            return new ProviderExecutionOutput(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ProviderNativeExecutionFailure(ProviderNativeFailureCode.RUNTIME_EXECUTION_UNKNOWN,
                    "Worker request interrupted; remote outcome is not confirmed");
        }
    }
    public void cancel(RuntimeExecutionContext context) throws IOException {
        try {
            var response = client.send(request("executions/cancel", WorkerInvocationCodec.encode(context)), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 202) throw new IOException("Worker did not acknowledge cancellation request");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Cancellation interrupted", interrupted); }
    }
    private HttpRequest request(String path, byte[] body) {
        return HttpRequest.newBuilder(endpoint.resolve("/api/remote-worker/" + path)).timeout(timeout)
                .header("Content-Type", "application/json").header("X-Worker-Api-Key", apiKey)
                .header("X-Worker-Runtime-Id", runtime.value()).header("X-Worker-Incarnation", incarnation.value())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }
}
