package com.example.platform.entitlement.domain;

import java.math.BigDecimal;

public record QuotaDecision(String subjectId, String quotaCode, boolean allowed, BigDecimal limitValue, BigDecimal usedValue) {
}
