package com.example.platform.remoterender.api;

import com.example.platform.remoterender.app.RemoteRenderService;
import com.example.platform.providerplugin.remote.*;
import com.example.platform.workerfabric.domain.providernative.*;
import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Authenticated fixed-command transport. No registration, scheduling, job status or completion authority. */
@RestController
@RequestMapping("/api/remote-worker")
public class RemoteWorkerController {
    private final RemoteRenderService execution;
    public RemoteWorkerController(RemoteRenderService execution) { this.execution = execution; }

    @PostMapping("/executions")
    public ResponseEntity<StreamingResponseBody> execute(HttpServletRequest request) throws IOException {
        execution.requireRuntime(request.getHeader("X-Worker-Runtime-Id"), request.getHeader("X-Worker-Incarnation"));
        var invocation = WorkerInvocationCodec.decode(body(request), RemoteWorkerInvocation.class);
        var output = execution.execute(request.getHeader("X-Worker-Runtime-Id"),
                request.getHeader("X-Worker-Incarnation"), invocation);
        StreamingResponseBody stream = target -> { try (output) { output.content().transferTo(target); } };
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Execution-Context", WorkerInvocationCodec.correlation(WorkerInvocationCodec.context(invocation.bundle())))
                .body(stream);
    }

    @PostMapping("/executions/cancel")
    public ResponseEntity<Map<String, String>> cancel(HttpServletRequest request) throws IOException {
        execution.requireRuntime(request.getHeader("X-Worker-Runtime-Id"), request.getHeader("X-Worker-Incarnation"));
        var context = WorkerInvocationCodec.decode(body(request), RuntimeExecutionContext.class);
        if (!execution.cancel(request.getHeader("X-Worker-Runtime-Id"), request.getHeader("X-Worker-Incarnation"), context)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.accepted().body(Map.of("observation", "CANCELLATION_REQUESTED"));
    }

    private static byte[] body(HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readNBytes(4 * 1024 * 1024 + 1);
        if (bytes.length > 4 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        return bytes;
    }
    @ExceptionHandler(ProviderNativeExecutionFailure.class)
    ResponseEntity<Map<String, String>> failure(ProviderNativeExecutionFailure failure) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).header("X-Runtime-Failure", failure.code().name())
                .body(Map.of("code", failure.code().name()));
    }
    @ExceptionHandler({IllegalArgumentException.class, com.fasterxml.jackson.core.JsonProcessingException.class})
    ResponseEntity<Map<String, String>> malformed(Exception failure) {
        return ResponseEntity.badRequest().body(Map.of("code", "MALFORMED_RUNTIME_INVOCATION"));
    }
}
