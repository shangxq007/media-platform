package com.example.platform.render.infrastructure;

public interface MediaProbeAdapter {
    MediaProbeResult probe(String jobId, String filePath);
    boolean isAvailable();
}
