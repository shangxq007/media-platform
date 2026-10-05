package com.example.platform.fontpipeline;

import com.example.platform.fontpipeline.manifest.FontBoxManifestAdapter;
import com.example.platform.fontpipeline.manifest.FontFaceManifestAssembler;
import com.example.platform.fontpipeline.manifest.LwjglFreeTypeManifestAdapter;
import com.example.platform.fontpipeline.manifest.LwjglPartialManifest;
import com.example.platform.fontpipeline.manifest.PartialFontManifest;
import com.example.platform.fontpipeline.sanitize.FontSanitizeRequest;
import com.example.platform.fontpipeline.sanitize.FontSanitizer;
import com.example.platform.fontpipeline.sanitize.SanitizationOutcome;
import com.example.platform.fontpipeline.sanitize.SanitizedFontResult;
import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resource.FontContentDigest;
import java.util.Objects;

/**
 * Default {@link FontPipelineOrchestrator}: a pure function over its injected stage collaborators.
 *
 * <p>It reads no files, computes no storage access and performs no IO of its own; every stage is
 * supplied through the constructor (no Spring wiring — that is deliberately deferred). The
 * {@code sourceDigest} is SHA-256 of the raw request bytes, computed here; the {@code contentDigest}
 * is produced by the sanitizer as SHA-256 of the sanitized bytes and is passed unchanged to the
 * assembler, which stamps it onto the manifest.
 *
 * <p>The sanitized bytes never leave this method: only the digests and the assembled manifest are
 * returned.
 */
public final class DefaultFontPipelineOrchestrator implements FontPipelineOrchestrator {

    private final FontSanitizer sanitizer;
    private final FontBoxManifestAdapter fontBoxAdapter;
    private final LwjglFreeTypeManifestAdapter lwjglAdapter;
    private final FontFaceManifestAssembler assembler;

    public DefaultFontPipelineOrchestrator(
            FontSanitizer sanitizer,
            FontBoxManifestAdapter fontBoxAdapter,
            LwjglFreeTypeManifestAdapter lwjglAdapter,
            FontFaceManifestAssembler assembler) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
        this.fontBoxAdapter = Objects.requireNonNull(fontBoxAdapter, "fontBoxAdapter");
        this.lwjglAdapter = Objects.requireNonNull(lwjglAdapter, "lwjglAdapter");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
    }

    @Override
    public FontProcessingResult process(FontProcessingRequest request) {
        Objects.requireNonNull(request, "request");

        // 1. sourceDigest = SHA-256(rawBytes)
        FontContentDigest sourceDigest = FontContentDigest.ofBytes(request.rawBytes());

        // 2. sanitize
        FontSanitizeRequest sanitizeRequest = new FontSanitizeRequest(
                request.rawBytes(), sourceDigest, request.declaredMediaType());
        SanitizedFontResult sanitized = sanitizer.sanitize(sanitizeRequest);

        // 3. validate sanitization outcome
        if (sanitized.outcome() != SanitizationOutcome.SANITIZED) {
            throw new FontProcessingException(
                    "Sanitization failed: outcome=" + sanitized.outcome()
                            + " diagnostics=" + sanitized.diagnostics());
        }
        if (sanitized.sanitizedBytes() == null || sanitized.contentDigest() == null) {
            throw new FontProcessingException("Sanitizer returned SANITIZED but missing bytes/digest");
        }

        byte[] sanitizedBytes = sanitized.sanitizedBytes();
        FontContentDigest contentDigest = sanitized.contentDigest();

        // 4. parse (FontBox + LWJGL, both over the sanitized bytes, same source digest and face)
        PartialFontManifest fontBoxPartial =
                fontBoxAdapter.parse(sourceDigest, sanitizedBytes, request.faceIndex());
        LwjglPartialManifest lwjglPartial =
                lwjglAdapter.parse(sourceDigest, sanitizedBytes, request.faceIndex());

        // 5. assemble
        FontFaceManifest manifest = assembler.assemble(fontBoxPartial, lwjglPartial, contentDigest);

        // 6. assemble result
        return new FontProcessingResult(sourceDigest, contentDigest, manifest);
    }
}
