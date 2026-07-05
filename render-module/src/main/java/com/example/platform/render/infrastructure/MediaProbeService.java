package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class MediaProbeService {
    private static final Logger log = LoggerFactory.getLogger(MediaProbeService.class);

    private final MediaProbeAdapter probeAdapter;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public MediaProbeService(MediaProbeAdapter probeAdapter) {
        this.probeAdapter = probeAdapter;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public MediaProbeResult probe(String jobId, String relativePath) {
        Path filePath = Path.of(storageRoot, relativePath);
        return probeAdapter.probe(jobId, filePath.toString());
    }

    public MediaProbeResult probeAbsolute(String jobId, String absolutePath) {
        return probeAdapter.probe(jobId, absolutePath);
    }

}
