package com.example.platform.delivery.app;

import org.springframework.http.HttpStatus;

/** Stable product rejection; never includes storage coordinates or another scope's identity. */
public final class DeliveryFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public DeliveryFailure(HttpStatus status, String code) { super(code); this.status=status; this.code=code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
