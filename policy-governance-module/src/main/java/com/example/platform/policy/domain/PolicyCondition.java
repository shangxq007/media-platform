package com.example.platform.policy.domain;

public record PolicyCondition(
        String attribute,
        String operator,
        String value
) {}
