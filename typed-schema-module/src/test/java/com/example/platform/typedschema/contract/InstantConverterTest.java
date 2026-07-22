package com.example.platform.typedschema.contract;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InstantConverter}.
 * Verifies OffsetDateTime ↔ Instant conversion including null handling.
 */
class InstantConverterTest {

    private final InstantConverter converter = new InstantConverter();

    @Test
    void fromReturnsNullForNullInput() {
        assertThat(converter.from(null)).isNull();
    }

    @Test
    void toReturnsNullForNullInput() {
        assertThat(converter.to(null)).isNull();
    }

    @Test
    void fromConvertsOffsetDateTimeToInstant() {
        OffsetDateTime odt = OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC);
        Instant result = converter.from(odt);
        assertThat(result).isEqualTo(odt.toInstant());
    }

    @Test
    void toConvertsInstantToOffsetDateTimeUTC() {
        Instant instant = Instant.parse("2024-06-15T10:30:00Z");
        OffsetDateTime result = converter.to(instant);
        assertThat(result).isEqualTo(instant.atOffset(ZoneOffset.UTC));
    }

    @Test
    void roundTripFromToPreservesInstant() {
        Instant original = Instant.parse("2024-01-01T00:00:00Z");
        OffsetDateTime odt = converter.to(original);
        Instant roundTripped = converter.from(odt);
        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void roundTripToFromPreservesOffsetDateTime() {
        OffsetDateTime original = OffsetDateTime.of(2024, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
        Instant instant = converter.from(original);
        OffsetDateTime roundTripped = converter.to(instant);
        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void fromTypeReturnsOffsetDateTimeClass() {
        assertThat(converter.fromType()).isEqualTo(OffsetDateTime.class);
    }

    @Test
    void toTypeReturnsInstantClass() {
        assertThat(converter.toType()).isEqualTo(Instant.class);
    }

    @Test
    void handlesNonUtcOffset() {
        OffsetDateTime odt = OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.ofHours(5));
        Instant result = converter.from(odt);
        // Instant should represent the same moment in time
        assertThat(result).isEqualTo(odt.toInstant());
    }

    @Test
    void handlesEpochInstant() {
        Instant epoch = Instant.EPOCH;
        OffsetDateTime result = converter.to(epoch);
        assertThat(result.toInstant()).isEqualTo(epoch);
    }
}
