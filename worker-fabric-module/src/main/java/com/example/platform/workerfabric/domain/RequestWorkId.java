package com.example.platform.workerfabric.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable idempotency identity for one logical Native Pull work request. */
public record RequestWorkId(String value) implements Comparable<RequestWorkId> {

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public RequestWorkId {
        Objects.requireNonNull(value, "value");
        if (!SAFE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "RequestWorkId must be 1-128 safe identity characters");
        }
    }

    public static RequestWorkId of(String value) {
        return new RequestWorkId(value);
    }

    @Override
    public int compareTo(RequestWorkId other) {
        return value.compareTo(other.value);
    }
}
