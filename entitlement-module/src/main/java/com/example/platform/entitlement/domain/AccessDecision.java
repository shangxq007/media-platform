package com.example.platform.entitlement.domain;

public record AccessDecision(String subjectId, String featureCode, boolean granted, String reason) {}
