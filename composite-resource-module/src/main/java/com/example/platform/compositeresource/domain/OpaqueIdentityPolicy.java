package com.example.platform.compositeresource.domain;

import java.util.regex.Pattern;

final class OpaqueIdentityPolicy {
    private static final Pattern CANONICAL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private OpaqueIdentityPolicy() {}

    static String require(String value, String type) {
        if (value == null || !CANONICAL.matcher(value).matches()) {
            throw new IllegalArgumentException(type + " must be a non-empty canonical opaque identifier");
        }
        return value;
    }

    static String requireComponent(String value) {
        String canonical = require(value, "CompositeComponentId");
        if (canonical.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("CompositeComponentId must be stable identity, not a list index");
        }
        return canonical;
    }
}
