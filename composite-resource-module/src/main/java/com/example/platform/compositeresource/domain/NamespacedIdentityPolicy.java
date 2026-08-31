package com.example.platform.compositeresource.domain;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NamespacedIdentityPolicy {
    private static final Pattern VALUE = Pattern.compile(
            "(?<namespace>[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)*):(?<local>[a-z][a-z0-9._-]*)");

    private NamespacedIdentityPolicy() {}

    static String require(String value, String type) {
        if (value == null) {
            throw new IllegalArgumentException(type + " is required");
        }
        Matcher matcher = VALUE.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(type + " must use canonical namespace:local syntax");
        }
        String namespace = matcher.group("namespace");
        if (namespace.equals("platform")) {
            return value;
        }
        if (!namespace.contains(".") || Arrays.asList(namespace.split("\\.")).contains("platform")) {
            throw new IllegalArgumentException(type + " vendor namespace must be reverse-DNS and must not squat platform");
        }
        return value;
    }
}
