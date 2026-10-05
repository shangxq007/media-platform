package com.example.platform.fontpipeline.manifest;

import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;

/** Parses font bytes into the LWJGL/FreeType-producible subset of the manifest. */
public interface LwjglFreeTypeManifestAdapter {

    /**
     * Parse {@code fontBytes} into a partial manifest.
     *
     * @param sourceDigest content digest of {@code fontBytes}
     * @param fontBytes raw font bytes (TTF / OTF / TTC)
     * @param faceIndex face index within a collection ({@code 0} for single-face fonts)
     * @return the partial manifest
     * @throws FontManifestParseException if the bytes are malformed or the face index is invalid
     */
    LwjglPartialManifest parse(FontContentDigest sourceDigest, byte[] fontBytes, FaceIndex faceIndex);
}
