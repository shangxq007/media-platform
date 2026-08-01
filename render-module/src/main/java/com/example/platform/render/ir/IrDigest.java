package com.example.platform.render.ir;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Computes a stable, domain-separated SHA-256 digest of a {@link MediaProjectIr}.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Algorithm: SHA-256</li>
 *   <li>Encoding: base64url (no padding)</li>
 *   <li>Domain separation prefix: {@code MEDIA_PROJECT_IR_V1}</li>
 *   <li>Input: domain prefix + digest version (byte) + canonical serialized bytes</li>
 *   <li>Typed result distinguishing algorithm, version, and encoded digest</li>
 * </ul>
 *
 * <p>The digest is stable across JVM runs, locales, timezones, and Map implementations
 * when computed on a normalized IR.
 *
 * @param algorithm  the digest algorithm name (e.g., "SHA-256")
 * @param version    the digest format version
 * @param encoded    the base64url-encoded digest (no padding)
 */
public record IrDigest(String algorithm, int version, String encoded) {

    /** Domain separation prefix. */
    public static final String DOMAIN_PREFIX = "MEDIA_PROJECT_IR_V1";

    /** Current digest format version. */
    public static final int DIGEST_VERSION = 1;

    /** Digest algorithm. */
    public static final String ALGORITHM = "SHA-256";

    public IrDigest {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(encoded, "encoded must not be null");
    }

    /**
     * Computes a stable digest for the given normalized IR.
     *
     * <p>The input to the hash is: domain prefix bytes + version byte + canonical serialized bytes.
     *
     * @param ir the normalized IR
     * @return a typed digest result
     * @throws IrValidationException if digest generation fails
     */
    public static IrDigest compute(MediaProjectIr ir) {
        Objects.requireNonNull(ir, "ir must not be null");
        try {
            byte[] canonical = CanonicalSerializer.serialize(ir);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Domain separation prefix
            md.update(DOMAIN_PREFIX.getBytes(StandardCharsets.UTF_8));
            // Digest version
            md.update((byte) DIGEST_VERSION);
            // Canonical serialized bytes
            md.update(canonical);
            byte[] hash = md.digest();
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return new IrDigest(ALGORITHM, DIGEST_VERSION, encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IrValidationException(List.of(
                IrValidationError.of(IrErrorCode.DIGEST_GENERATION_FAILED, "$",
                    "SHA-256 not available: " + e.getMessage())
            ));
        } catch (IrValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new IrValidationException(List.of(
                IrValidationError.of(IrErrorCode.DIGEST_GENERATION_FAILED, "$",
                    "Digest generation failed: " + e.getMessage())
            ));
        }
    }

    /**
     * Convenience: normalize then compute digest.
     */
    public static IrDigest normalizeAndCompute(MediaProjectIr ir) {
        return compute(IrNormalizer.normalize(ir));
    }

    @Override
    public String toString() {
        return algorithm + ":" + version + ":" + encoded;
    }
}
