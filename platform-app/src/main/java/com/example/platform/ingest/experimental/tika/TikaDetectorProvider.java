package com.example.platform.ingest.experimental.tika;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Tika experimental detector for MIME detection and light metadata.
 * Disabled by default. Not for production use.
 */
@Component
@ConditionalOnProperty(prefix = "ingest.experimental.tika", name = "enabled", havingValue = "true")
public class TikaDetectorProvider {

    private static final Logger log = LoggerFactory.getLogger(TikaDetectorProvider.class);
    private final TikaExperimentalProperties properties;
    private final Detector detector;

    public TikaDetectorProvider(TikaExperimentalProperties properties) {
        this.properties = properties;
        this.detector = new DefaultDetector();
        log.info("TikaDetectorProvider initialized: mode={} maxDetectBytes={} (EXPERIMENTAL)",
                properties.getMaxDetectBytes(), properties.getMaxDetectBytes());
    }

    public TikaDetectionResult detect(byte[] data, String filename, String declaredContentType) {
        if (data == null || data.length == 0) {
            return TikaDetectionResult.unknown(filename, declaredContentType);
        }

        int detectLength = Math.min(data.length, properties.getMaxDetectBytes());
        byte[] detectBytes = new byte[detectLength];
        System.arraycopy(data, 0, detectBytes, 0, detectLength);

        Metadata metadata = new Metadata();
        if (filename != null) {
            metadata.set("resourceName", filename);
        }

        MediaType mediaType;
        try (InputStream input = new ByteArrayInputStream(detectBytes)) {
            mediaType = detector.detect(input, metadata);
        } catch (IOException e) {
            log.warn("Tika detection failed: {}", e.getMessage());
            return TikaDetectionResult.unknown(filename, declaredContentType);
        }

        String detectedType = mediaType.toString();
        String extension = extractExtension(filename);

        boolean extensionMatches = true;
        boolean declaredMatches = declaredContentType == null || declaredContentType.equals(detectedType);

        List<String> warnings = new ArrayList<>();
        if (!declaredMatches) {
            warnings.add("CONTENT_TYPE_MISMATCH");
        }

        return new TikaDetectionResult(
            detectedType, declaredContentType, filename, extension,
            extensionMatches, declaredMatches, "tika-detector", warnings
        );
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
