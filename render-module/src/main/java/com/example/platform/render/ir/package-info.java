/**
 * Media Platform Declarative IR V1 — Canonical Intermediate Representation.
 *
 * <h2>Semantic Authority</h2>
 * The IR is the single semantic authority for media project declarations.
 * All downstream interpretation, execution, and rendering MUST derive from
 * the normalized IR, never from raw user input.
 *
 * <h2>Normalization Boundary</h2>
 * Normalization produces a deterministic, idempotent form that is independent
 * of input field ordering, collection ordering, identifier casing, enum casing,
 * default-value presence, null handling, numeric formatting, locale, timezone,
 * and Map implementation.
 *
 * <h2>Canonical Serialization Boundary</h2>
 * Canonical serialization produces byte-for-byte identical UTF-8 JSON bytes
 * regardless of input JSON property order, whitespace, or formatting.
 *
 * <h2>Digest Domain</h2>
 * The stable digest (SHA-256, base64url, no padding) is computed over the
 * domain-separated canonical serialized bytes. Domain prefix: {@code MEDIA_PROJECT_IR_V1}.
 * The digest is designed to be stable across JVM runs, locales, and timezones.
 *
 * <h2>Time Model</h2>
 * Time is represented as {@link RationalTime} (integer numerator / positive
 * integer denominator). Floating-point seconds are NEVER the semantic authority.
 * Zero denominator is rejection. Negative time where prohibited is rejection.
 * Arithmetic includes overflow detection.
 *
 * <h2>Extension Policy</h2>
 * Extensions use a reserved namespace with deterministic key ordering.
 * Unknown extensions are rejected by default. Prohibited: shell commands,
 * FFmpeg argv, absolute paths, env vars, credentials, serialized classes.
 *
 * @see MediaProjectIr
 * @see RationalTime
 * @see IrValidator
 * @see IrNormalizer
 * @see CanonicalSerializer
 * @see IrDigest
 */
package com.example.platform.render.ir;
