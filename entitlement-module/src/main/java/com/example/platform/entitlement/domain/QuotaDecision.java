package com.example.platform.entitlement.domain;

public record QuotaDecision(String subjectId, String quotaCode, boolean allowed, double limitValue, double usedValue) {}
