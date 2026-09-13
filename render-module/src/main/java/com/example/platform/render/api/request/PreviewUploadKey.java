package com.example.platform.render.api.request;
/** Optional HTTP Idempotency-Key. Omission creates a new upload, not a content identity. */
public record PreviewUploadKey(String value) {
    public PreviewUploadKey {
        if(value==null || !value.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new IllegalArgumentException("Invalid preview Idempotency-Key");
    }
}
