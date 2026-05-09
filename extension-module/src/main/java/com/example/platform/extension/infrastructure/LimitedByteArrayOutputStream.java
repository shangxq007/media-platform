package com.example.platform.extension.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Grows like {@link ByteArrayOutputStream} until {@code maxCapacityBytes}, then discards further writes.
 */
final class LimitedByteArrayOutputStream extends OutputStream {

    private final ByteArrayOutputStream contentBuffer = new ByteArrayOutputStream();
    private final int maxCapacityBytes;
    private boolean truncationExceeded;

    LimitedByteArrayOutputStream(int maxCapacityBytes) {
        this.maxCapacityBytes = maxCapacityBytes;
    }

    @Override
    public void write(int b) {
        if (truncationExceeded || contentBuffer.size() >= maxCapacityBytes) {
            truncationExceeded = true;
            return;
        }
        contentBuffer.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if (truncationExceeded) {
            return;
        }
        int remainingCapacity = maxCapacityBytes - contentBuffer.size();
        if (remainingCapacity <= 0) {
            truncationExceeded = true;
            return;
        }
        int acceptedLength = Math.min(len, remainingCapacity);
        contentBuffer.write(b, off, acceptedLength);
        if (acceptedLength < len) {
            truncationExceeded = true;
        }
    }

    @Override
    public void flush() throws IOException {
        contentBuffer.flush();
    }

    byte[] toByteArray() {
        return contentBuffer.toByteArray();
    }

    boolean isTruncated() {
        return truncationExceeded;
    }
}
