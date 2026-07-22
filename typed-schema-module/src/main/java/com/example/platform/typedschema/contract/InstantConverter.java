package com.example.platform.typedschema.contract;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Converts between {@link OffsetDateTime} (PostgreSQL TIMESTAMPTZ) and
 * {@link Instant} (domain model type).
 *
 * <p>PostgreSQL TIMESTAMPTZ stores timestamps with timezone information.
 * jOOQ's default mapping is {@code OffsetDateTime}. This converter
 * provides lossless conversion to {@code Instant} for domain models
 * that prefer the simpler type.</p>
 *
 * <p>Usage as jOOQ forced type converter:</p>
 * <pre>{@code
 * // In codegen config forcedType:
 * // <forcedType><userType>java.time.Instant</userType>
 * //   <converter>com.example.platform.typedschema.contract.InstantConverter</converter>
 * // </forcedType>
 * }</pre>
 *
 * <p>Null safety: {@code null} input returns {@code null} output.</p>
 */
public final class InstantConverter implements org.jooq.Converter<OffsetDateTime, Instant> {

    private static final long serialVersionUID = 1L;

    @Override
    public Instant from(OffsetDateTime databaseObject) {
        if (databaseObject == null) {
            return null;
        }
        return databaseObject.toInstant();
    }

    @Override
    public OffsetDateTime to(Instant userObject) {
        if (userObject == null) {
            return null;
        }
        return userObject.atOffset(ZoneOffset.UTC);
    }

    @Override
    public Class<OffsetDateTime> fromType() {
        return OffsetDateTime.class;
    }

    @Override
    public Class<Instant> toType() {
        return Instant.class;
    }
}
