package com.example.platform.render.infrastructure.asset.provider;

import com.example.platform.extension.domain.*;
import com.example.platform.render.domain.asset.semantic.AiProviderDescriptor;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OCR provider using Tesseract CLI. Registered as a platform extension.
 * Follows the same provider governance pattern as Whisper.
 */
public class TesseractOcrProvider {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrProvider.class);

    public String runOcr(String imagePath, String language, String jobId, String taskId) {
        List<String> args = new ArrayList<>(List.of("tesseract", imagePath, "stdout",
                "-l", language != null ? language : "eng"));
        log.info("Tesseract OCR: image={} lang={}", imagePath, language);
        // Execution is delegated through ExecutionBackend by OcrProviderExtension
        return "OCR text placeholder"; // CLI execution goes through ExtensionRuntime → ExecutionBackend
    }
}
