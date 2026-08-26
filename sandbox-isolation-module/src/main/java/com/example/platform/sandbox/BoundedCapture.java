package com.example.platform.sandbox;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@org.springframework.modulith.NamedInterface("API")
public record BoundedCapture(byte[] bytes, boolean truncated) {
    public BoundedCapture { bytes = Arrays.copyOf(bytes, bytes.length); }
    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    public String utf8() { return new String(bytes, StandardCharsets.UTF_8); }
}
