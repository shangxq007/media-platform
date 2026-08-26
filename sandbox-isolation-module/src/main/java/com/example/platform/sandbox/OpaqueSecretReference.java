package com.example.platform.sandbox;

@org.springframework.modulith.NamedInterface("API")
public record OpaqueSecretReference(String value) {
    public OpaqueSecretReference {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("secret reference must not be blank");
    }
    public static OpaqueSecretReference of(String value) { return new OpaqueSecretReference(value); }
}
