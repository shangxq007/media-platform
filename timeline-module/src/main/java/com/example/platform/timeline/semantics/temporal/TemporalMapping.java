package com.example.platform.timeline.semantics.temporal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * TEMPORAL_MAPPING_FOUNDATION_V1 (TM7/TM30): canonical Timeline traversal
 * semantics — HOW object-local playback time maps to source time.
 *
 * <p>Authorities (TM2): Timeline placement owns WHEN/WHERE an object occupies
 * canonical Timeline time; MediaStreamSourceBinding owns WHAT immutable source
 * semantics are referenced (including the exact source window); TemporalMapping
 * owns HOW local time traverses that source window. Never collapsed.
 *
 * <p>V1 production variants: {@link ConstantRateTemporalMapping} (identity is
 * normalized as rate 1/1 + FORWARD, R1) and {@link FreezeTemporalMapping}.
 * No third production subtype in V1 (R1: no IdentityTemporalMapping).
 *
 * <p>Exact rational time only; no floating point, no provider/filter syntax
 * (TM3, TM20).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConstantRateTemporalMapping.class, name = "CONSTANT_RATE"),
        @JsonSubTypes.Type(value = FreezeTemporalMapping.class, name = "FREEZE")
})
public sealed interface TemporalMapping permits
        ConstantRateTemporalMapping,
        FreezeTemporalMapping {

    /** Deterministic canonical type discriminator. NEVER "IDENTITY" (R1). */
    Kind kind();

    enum Kind {
        CONSTANT_RATE,
        FREEZE
    }
}
