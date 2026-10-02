package com.example.platform.contract.media;

/** Result of one frame extraction: either bytes + content type, or a failure code. */
public record FrameExtractResult(byte[] bytes, String contentType, String failureCode) {

    public static FrameExtractResult success(byte[] bytes, String contentType) {
        return new FrameExtractResult(bytes, contentType, null);
    }

    public static FrameExtractResult failure(String failureCode) {
        return new FrameExtractResult(null, null, failureCode);
    }

    public boolean succeeded() {
        return bytes != null && failureCode == null;
    }
}
