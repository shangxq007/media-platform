package com.example.platform.shared.capability;

import java.util.Objects;

/**
 * #16 (C2/C12): bounded capability contract version range.
 *
 * <p>Represents the set of contract versions a consumer accepts. Compatibility
 * is explicit: major must match the requirement's min major and the version
 * must lie within [min, max] (numeric comparison). "Higher plugin version"
 * never implies contract compatibility.
 *
 * <p>CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b-1b: moved from
 * {@code extension.domain} to {@code shared.capability} (single authority).</p>
 */
public record ContractVersionRange(ContractVersion min, ContractVersion max) {

    public ContractVersionRange {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.major() != max.major()) {
            throw new IllegalArgumentException("range must stay within one contract major: "
                    + min + ".." + max);
        }
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("range min must not exceed max: " + min + ".." + max);
        }
    }

    public static ContractVersionRange exactly(ContractVersion version) {
        return new ContractVersionRange(version, version);
    }

    public static ContractVersionRange atLeast(ContractVersion min) {
        return new ContractVersionRange(min, min);
    }

    public static ContractVersionRange between(ContractVersion min, ContractVersion max) {
        return new ContractVersionRange(min, max);
    }

    public boolean contains(ContractVersion version) {
        return version.major() == min.major()
                && version.compareTo(min) >= 0
                && version.compareTo(max) <= 0;
    }

    @Override
    public String toString() {
        return min.equals(max) ? min.toString() : min + ".." + max;
    }
}
