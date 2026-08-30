package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.Instant;

public record CreditReservation(
        String reservationId, String tenantId, String walletId, Money amount,
        String status, long version, String referenceType, String referenceId,
        Instant createdAt, Instant updatedAt) {}
