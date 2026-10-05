package com.example.platform.fontpipeline;

/** Isolated font processing pipeline: {@code bytes -> sanitize -> parse -> assemble -> result}. */
public interface FontPipelineOrchestrator {

    /**
     * Run the processing pipeline for {@code request}.
     *
     * @param request raw bytes, declared media type and face index
     * @return the digests and complete manifest
     * @throws FontProcessingException if sanitization fails or any stage cannot produce a manifest
     */
    FontProcessingResult process(FontProcessingRequest request);
}
