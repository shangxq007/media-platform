package com.example.platform.render.domain.storage.digest;
import java.io.Serializable;
public record ContentDigest(DigestAlgorithm algorithm, String value) implements Serializable {
    public ContentDigest {
        if (algorithm == null) throw new IllegalArgumentException("digest algorithm required");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("digest value required");
        validateFormat(algorithm, value);
    }
    private static void validateFormat(DigestAlgorithm alg, String val) {
        String normalized = val.toLowerCase().trim();
        switch (alg) {
            case SHA_256 -> {
                if (normalized.length() != 64) throw new IllegalArgumentException("SHA_256 digest must be 64 hex chars");
                if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA_256 digest must be valid hex");
            }
        }
    }
    public static ContentDigest sha256(String hex) {
        return new ContentDigest(DigestAlgorithm.SHA_256, hex.toLowerCase().trim());
    }
    public String canonicalValue() { return value.toLowerCase(); }
    public boolean matches(ContentDigest other) {
        if (other == null) return false;
        return this.algorithm == other.algorithm && this.canonicalValue().equals(other.canonicalValue());
    }
    @Override public String toString() { return algorithm.name() + ":" + canonicalValue(); }
    public enum DigestAlgorithm { SHA_256 }
}
