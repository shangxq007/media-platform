package com.example.platform.entitlement.domain;

public record FeatureCheckCommand(String subjectId, String featureCode, String contextJson) {}
