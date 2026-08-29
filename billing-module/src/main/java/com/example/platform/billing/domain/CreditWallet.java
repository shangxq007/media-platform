package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record CreditWallet(
        String walletId, PrincipalRef principal, Money balance,
        String status, long version, Instant createdAt, Instant updatedAt) {
    public String tenantId() { return principal.tenantId(); }
    public String workspaceId() { return principal.workspaceId(); }
    public String userId() { return principal.principalId(); }
    public long balanceMinor() { return balance.amountMinor(); }
    public String currencyCode() { return balance.currency(); }
}
