package com.example.platform.providerplugin.remote;

import java.io.*;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/** Owns and closes the actual HTTP body; timing out a waiting Future is insufficient. */
final class BoundedWorkerOutputStream extends InputStream {
    private final InputStream delegate;
    private final long started,overall,idle,expectedLength;
    private final Runnable retired;
    private final AtomicBoolean closed=new AtomicBoolean();
    private volatile long lastProgress=System.nanoTime();
    private volatile IOException failure;
    private volatile boolean eof;
    private long received;
    private final Thread watchdog;
    BoundedWorkerOutputStream(InputStream delegate,long started,WorkerHttpTimeouts limits,long expectedLength,Runnable retired) {
        this.delegate=java.util.Objects.requireNonNull(delegate);this.started=started;this.overall=limits.overall().toNanos();
        this.idle=limits.idleRead().toNanos();this.expectedLength=expectedLength;this.retired=retired;
        watchdog=Thread.ofVirtual().name("worker-output-deadline").unstarted(this::watch);
    }
    void start(){watchdog.start();}
    private void watch() {
        while(!closed.get()) {
            long now=System.nanoTime();long totalLeft=overall-(now-started),idleLeft=idle-(now-lastProgress);
            if(totalLeft<=0 || idleLeft<=0) {
                abort(new SocketTimeoutException(totalLeft<=0?"Worker output overall deadline exceeded; remote outcome unconfirmed":"Worker output idle-read deadline exceeded; remote outcome unconfirmed"));return;
            }
            LockSupport.parkNanos(this,Math.min(totalLeft,idleLeft));
        }
    }
    void abort(IOException reason) {
        if(failure==null)failure=reason;
        try{close();}catch(IOException closeFailure){if(closeFailure!=reason)reason.addSuppressed(closeFailure);}
    }
    private void check() throws IOException {
        if(failure!=null)throw failure;
        if(Thread.currentThread().isInterrupted()) {var interrupted=new InterruptedIOException("Worker output staging interrupted");abort(interrupted);throw interrupted;}
        if(System.nanoTime()-started>=overall) {var timeout=new SocketTimeoutException("Worker output overall deadline exceeded; remote outcome unconfirmed");abort(timeout);throw timeout;}
        if(closed.get()&&!eof)throw new IOException("Worker output stream closed before completion");
    }
    @Override public int read() throws IOException {byte[] one=new byte[1];int read=read(one,0,1);return read<0?-1:one[0]&255;}
    @Override public int read(byte[] bytes,int offset,int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset,length,bytes.length);if(length==0)return 0;
        if(eof)return -1;check();
        try {
            int read=delegate.read(bytes,offset,length);check();
            if(read<0) {
                if(expectedLength>=0 && received!=expectedLength)throw new EOFException("Worker output ended before declared Content-Length");
                eof=true;close();return -1;
            }
            if(read>0) {
                received=Math.addExact(received,read);
                if(expectedLength>=0 && received>expectedLength)throw new IOException("Worker output exceeded declared Content-Length");
                lastProgress=System.nanoTime();LockSupport.unpark(watchdog);
            }
            return read;
        } catch(IOException rejected) {
            IOException result=failure!=null?failure:rejected;
            abort(result);throw result;
        } catch(ArithmeticException overflow){var rejected=new IOException("Worker output length overflow",overflow);abort(rejected);throw rejected;}
    }
    @Override public void close() throws IOException {
        if(closed.compareAndSet(false,true)) {
            try{delegate.close();}finally{retired.run();LockSupport.unpark(watchdog);}
        }
    }
    boolean awaitStopped(Duration limit) throws InterruptedException {watchdog.join(limit.toMillis());return !watchdog.isAlive();}
}
