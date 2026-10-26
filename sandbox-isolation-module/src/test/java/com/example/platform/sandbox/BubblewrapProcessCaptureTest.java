package com.example.platform.sandbox;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BubblewrapProcessCaptureTest {
    @Test
    void naturalExitPreservesUnreadOutputAndStillClosesOwnedProcess() {
        var process = new ExitedProcessWithDeferredPipe();
        var result = BubblewrapProcess.captureProbe(process, Duration.ofSeconds(1));
        assertTrue(result.succeeded());
        assertEquals("LANG=C\nPATH=/usr/bin:/bin\nPWD=/workspace\n", result.stdout());
        assertTrue(process.destroyed.get(), "owned process cleanup remains mandatory");
        assertTrue(process.closed.get(), "all capture resources must close");
    }

    /** Models JDK Process.destroy() closing pipes even after waitFor reports exit. */
    private static final class ExitedProcessWithDeferredPipe extends Process {
        final AtomicBoolean destroyed = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();
        final CountDownLatch exited = new CountDownLatch(1);
        final InputStream pipe = new InputStream() {
            final ByteArrayInputStream bytes = new ByteArrayInputStream(
                "LANG=C\nPATH=/usr/bin:/bin\nPWD=/workspace\n".getBytes(StandardCharsets.UTF_8));
            boolean deferred;
            @Override public int read() throws IOException { byte[] b=new byte[1];int n=read(b,0,1);return n<0?-1:b[0]&255; }
            @Override public int read(byte[] b,int off,int len) throws IOException {
                if(!deferred) {deferred=true;try {exited.await();Thread.sleep(50);} catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
                return closed.get()?-1:bytes.read(b,off,len);
            }
            @Override public void close(){closed.set(true);}
        };
        @Override public InputStream getInputStream(){return pipe;}
        @Override public InputStream getErrorStream(){return InputStream.nullInputStream();}
        @Override public OutputStream getOutputStream(){return OutputStream.nullOutputStream();}
        @Override public int waitFor(){exited.countDown();return 0;}
        @Override public boolean waitFor(long n,TimeUnit unit){exited.countDown();return true;}
        @Override public int exitValue(){return 0;}
        @Override public boolean isAlive(){return false;}
        @Override public long pid(){return -1;}
        @Override public Stream<ProcessHandle> descendants(){return Stream.empty();}
        @Override public void destroy(){destroyed.set(true);closed.set(true);}
        @Override public Process destroyForcibly(){destroy();return this;}
    }
}
