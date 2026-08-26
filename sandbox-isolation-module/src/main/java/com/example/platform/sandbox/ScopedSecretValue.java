package com.example.platform.sandbox;

import java.util.Arrays;
import java.util.Objects;

/** Scoped resolved secret whose bytes never participate in equality, text, or semantic projection. */
@org.springframework.modulith.NamedInterface("API")
public final class ScopedSecretValue implements AutoCloseable {
    private final OpaqueSecretReference reference;
    private final String environmentName;
    private final char[] value;

    private ScopedSecretValue(OpaqueSecretReference reference, String environmentName, char[] value) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.environmentName = Objects.requireNonNull(environmentName, "environmentName");
        this.value = Objects.requireNonNull(value, "value").clone();
        if (!environmentName.matches("[A-Za-z_][A-Za-z0-9_]*") || value.length == 0) {
            Arrays.fill(this.value, '\0');
            throw new IllegalArgumentException("secret environment name and value must be valid");
        }
    }

    public static ScopedSecretValue resolved(
            OpaqueSecretReference reference, String environmentName, char[] value) {
        return new ScopedSecretValue(reference, environmentName, value);
    }

    public OpaqueSecretReference reference() { return reference; }
    public String environmentName() { return environmentName; }
    public char[] copyValue() { return value.clone(); }
    public String semanticProjection() { return reference.value() + "@" + environmentName; }

    @Override public boolean equals(Object other) {
        return other instanceof ScopedSecretValue that
                && reference.equals(that.reference) && environmentName.equals(that.environmentName);
    }
    @Override public int hashCode() { return Objects.hash(reference, environmentName); }
    @Override public String toString() { return "ScopedSecretValue[" + semanticProjection() + "=[REDACTED]]"; }
    @Override public void close() { Arrays.fill(value, '\0'); }
}
