package com.example.platform.render.domain.asset.semantic;

import java.util.List;

/**
 * OCR result produced by an OCR provider (Tesseract).
 */
public record OcrResult(
        String provider,
        String model,
        String language,
        double confidence,
        double processingTimeSec,
        String fullText,
        List<OcrBlock> blocks) {

    public record OcrBlock(
            int page,
            int blockNum,
            String text,
            double confidence) {}

    public boolean isValid() {
        return fullText != null && !fullText.isBlank();
    }
}
