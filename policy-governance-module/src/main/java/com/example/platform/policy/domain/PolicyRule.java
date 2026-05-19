package com.example.platform.policy.domain;

public record PolicyRule(
        String id,
        String name,
        PolicyEffect effect,
        String conditions,
        int priority,
        String status
) {}
