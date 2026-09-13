package com.example.platform.providerplugin.remote;

import com.example.platform.execution.domain.provider.*;
import com.example.platform.execution.taskgraph.*;
import com.example.platform.workerfabric.domain.*;
import com.example.platform.workerfabric.domain.providernative.*;
import com.example.platform.workerfabric.reuse.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HttpWorkerOutputStreamingTest {
    static final WorkerRuntimeId RUNTIME=WorkerRuntimeId.of("stream-runtime");
    static final WorkerRuntimeIncarnationId INCARNATION=WorkerRuntimeIncarnationId.of("stream-incarnation");
    static final ProviderBindingPin PIN=new ProviderBindingPin(ProviderId.of("test"),ProviderImplementationId.of("test.stream"),ProviderVersion.of("1.0.0"),
            ProviderExecutionContractVersion.of(1,0),ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1,0)),List.of());
    @TempDir Path root;
    static RuntimeExecutionBundle bundle(){var task=new ExecutableTaskId("b".repeat(64));var attempt=ExecutionAttemptId.of("stream-attempt");var generation=ExecutionOwnershipGeneration.first();
        return new RuntimeExecutionBundle(task,PIN,attempt,generation,List.of(new ExecutionCommand(task,PIN,attempt,generation,0,ProcessInvocationSpec.of("/bin/true",List.of()))));}
    @Test void stalledBodyHasABoundedReadAndLeavesNoStagedOutput() throws Exception {
        assertEquals(25,Runtime.version().feature());
        var release=new CountDownLatch(1);var received=new CountDownLatch(1);var output=new AtomicReference<ProviderExecutionOutput>();
        var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/api/remote-worker/executions",exchange->{try{
            var request=WorkerInvocationCodec.decode(exchange.getRequestBody().readAllBytes(),RemoteWorkerInvocation.class);
            exchange.getResponseHeaders().add("X-Execution-Context",WorkerInvocationCodec.correlation(WorkerInvocationCodec.context(request.bundle())));
            exchange.sendResponseHeaders(200,0);exchange.getResponseBody().flush();release.await(10,TimeUnit.SECONDS);
        }catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}});
        server.start();var pool=Executors.newSingleThreadExecutor();
        var executor=new HttpWorkerRuntimeCommandExecutor(URI.create("http://localhost:"+server.getAddress().getPort()),RUNTIME,INCARNATION,"task-key",WorkerHttpTimeouts.boundedBy(Duration.ofMillis(750)));
        try {
            var result=pool.submit(()->{try(var response=executor.execute(bundle(),List.of())){output.set(response);received.countDown();return new OutputStagingArea(root).stage(response.content());}});
            assertTrue(received.await(5,TimeUnit.SECONDS),"response headers must arrive before the body deadline probe");
            var failure=assertThrows(ExecutionException.class,()->result.get(2,TimeUnit.SECONDS));
            assertInstanceOf(IOException.class,failure.getCause());
            try(var files=Files.list(root)){assertEquals(0,files.count());}
        } finally {
            if(output.get()!=null)output.get().close();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));executor.close();if(output.get()!=null)assertTrue(((BoundedWorkerOutputStream)output.get().content()).awaitStopped(Duration.ofSeconds(1)));release.countDown();server.stop(0);
        }
    }
    enum Mode { NORMAL, PARTIAL_STALL, TRICKLE, SHORT }
    final class Peer implements AutoCloseable {
        final HttpServer server;final ExecutorService handlers=Executors.newCachedThreadPool();
        final ScheduledExecutorService ticker=Executors.newSingleThreadScheduledExecutor();
        final CountDownLatch release=new CountDownLatch(1),progress=new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger chunks=new java.util.concurrent.atomic.AtomicInteger();
        final byte[] bytes="real-http-output".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Peer(Mode mode) throws Exception {
            server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);server.setExecutor(handlers);
            server.createContext("/api/remote-worker/executions",exchange->{
                ScheduledFuture<?> ticking=null;
                try {
                    if(exchange.getRequestURI().getPath().endsWith("/cancel")){exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(202,-1);return;}
                    var request=WorkerInvocationCodec.decode(exchange.getRequestBody().readAllBytes(),RemoteWorkerInvocation.class);
                    assertEquals(RUNTIME.value(),exchange.getRequestHeaders().getFirst("X-Worker-Runtime-Id"));
                    assertEquals(INCARNATION.value(),exchange.getRequestHeaders().getFirst("X-Worker-Incarnation"));
                    exchange.getResponseHeaders().add("X-Execution-Context",WorkerInvocationCodec.correlation(WorkerInvocationCodec.context(request.bundle())));
                    exchange.sendResponseHeaders(200,mode==Mode.NORMAL?bytes.length:mode==Mode.SHORT?bytes.length+20:0);
                    if(mode==Mode.NORMAL || mode==Mode.SHORT){exchange.getResponseBody().write(bytes);return;}
                    if(mode==Mode.PARTIAL_STALL){exchange.getResponseBody().write(new byte[]{1,2,3});exchange.getResponseBody().flush();progress.countDown();}
                    else ticking=ticker.scheduleAtFixedRate(()->{try{exchange.getResponseBody().write(7);exchange.getResponseBody().flush();chunks.incrementAndGet();progress.countDown();}catch(IOException closed){release.countDown();}},0,75,TimeUnit.MILLISECONDS);
                    release.await(10,TimeUnit.SECONDS);
                } catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                catch(IOException expectedPeerClosure) { }
                finally{if(ticking!=null)ticking.cancel(false);exchange.close();}
            });server.start();
        }
        URI endpoint(){return URI.create("http://localhost:"+server.getAddress().getPort());}
        public void close() throws Exception {release.countDown();server.stop(0);ticker.shutdownNow();handlers.shutdownNow();assertTrue(ticker.awaitTermination(5,TimeUnit.SECONDS));assertTrue(handlers.awaitTermination(5,TimeUnit.SECONDS));}
    }
    final class ReadRun implements AutoCloseable {
        final HttpWorkerRuntimeCommandExecutor executor;final ExecutorService pool=Executors.newSingleThreadExecutor();
        final AtomicReference<ProviderExecutionOutput> output=new AtomicReference<>();final CountDownLatch headers=new CountDownLatch(1);
        final Future<StagedExecutionOutput> result;
        ReadRun(Peer peer,WorkerHttpTimeouts policy) {
            executor=new HttpWorkerRuntimeCommandExecutor(peer.endpoint(),RUNTIME,INCARNATION,"task-key",policy);
            result=pool.submit(()->{try(var response=executor.execute(bundle(),List.of())){output.set(response);headers.countDown();return new OutputStagingArea(root).stage(response.content());}});
        }
        public void close() throws Exception {
            if(output.get()!=null)output.get().close();result.cancel(true);pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));
            executor.close();if(output.get()!=null)assertTrue(((BoundedWorkerOutputStream)output.get().content()).awaitStopped(Duration.ofSeconds(1)),"deadline task must terminate");
        }
    }
    void emptyStaging() throws Exception {try(var files=Files.list(root)){assertEquals(0,files.count());}}
    @Test void normalBoundedOutputPreservesBytesDigestAndLength() throws Exception {
        try(var peer=new Peer(Mode.NORMAL);var read=new ReadRun(peer,WorkerHttpTimeouts.boundedBy(Duration.ofSeconds(2)))) {
            var staged=read.result.get(4,TimeUnit.SECONDS);assertArrayEquals(peer.bytes,Files.readAllBytes(staged.path()));assertEquals(peer.bytes.length,staged.byteLength());
            assertEquals(com.example.platform.shared.digest.ContentDigest.sha256(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(peer.bytes))),staged.contentDigest());
            Files.delete(staged.path());
        }emptyStaging();
    }
    @Test void partialBodyStallHitsIdleLimitAndDeletesPartialFile() throws Exception {
        var limits=new WorkerHttpTimeouts(Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofMillis(200),Duration.ofSeconds(2));
        try(var peer=new Peer(Mode.PARTIAL_STALL);var read=new ReadRun(peer,limits)) {
            assertTrue(read.headers.await(3,TimeUnit.SECONDS));assertTrue(peer.progress.await(3,TimeUnit.SECONDS));
            var failure=assertThrows(ExecutionException.class,()->read.result.get(3,TimeUnit.SECONDS));assertInstanceOf(SocketTimeoutException.class,failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("idle"));emptyStaging();
        }
    }
    @Test void slowTricklingCannotExtendOverallDeadline() throws Exception {
        var limits=new WorkerHttpTimeouts(Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofMillis(300),Duration.ofSeconds(1));
        try(var peer=new Peer(Mode.TRICKLE);var read=new ReadRun(peer,limits)) {
            assertTrue(read.headers.await(3,TimeUnit.SECONDS));assertTrue(peer.progress.await(3,TimeUnit.SECONDS));
            var failure=assertThrows(ExecutionException.class,()->read.result.get(3,TimeUnit.SECONDS));assertInstanceOf(SocketTimeoutException.class,failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("overall"));assertTrue(peer.chunks.get()>1);emptyStaging();
        }
    }
    @Test void cancellationClosesTheActualStagingStream() throws Exception {
        try(var peer=new Peer(Mode.PARTIAL_STALL);var read=new ReadRun(peer,WorkerHttpTimeouts.boundedBy(Duration.ofSeconds(3)))) {
            assertTrue(read.headers.await(3,TimeUnit.SECONDS));assertTrue(peer.progress.await(3,TimeUnit.SECONDS));
            read.executor.cancel(WorkerInvocationCodec.context(bundle()));
            var failure=assertThrows(ExecutionException.class,()->read.result.get(2,TimeUnit.SECONDS));assertInstanceOf(InterruptedIOException.class,failure.getCause());emptyStaging();
        }
    }
    @Test void prematureEofCannotCommitADeclaredLongerOutput() throws Exception {
        try(var peer=new Peer(Mode.SHORT);var read=new ReadRun(peer,WorkerHttpTimeouts.boundedBy(Duration.ofSeconds(2)))) {
            var failure=assertThrows(ExecutionException.class,()->read.result.get(3,TimeUnit.SECONDS));assertInstanceOf(IOException.class,failure.getCause());emptyStaging();
        }
    }
}
