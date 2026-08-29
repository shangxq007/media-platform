package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.billing.domain.CreditWallet;
import com.example.platform.billing.domain.CreditWalletCommand;
import com.example.platform.billing.infrastructure.CreditWalletJdbcRepository;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CreditWalletPrincipalScopeTest {
    @Test
    void walletPrincipalScopeIncludesWorkspace() {
        CreditWalletJdbcRepository repository = mock(CreditWalletJdbcRepository.class);
        CreditWalletService service = new CreditWalletService(repository);
        Instant now = Instant.parse("2026-08-29T10:00:00Z");
        PrincipalRef owner = new PrincipalRef(
                "tenant-a", PrincipalType.USER, "user-a", "workspace-a", null);
        PrincipalRef otherWorkspace = new PrincipalRef(
                "tenant-a", PrincipalType.USER, "user-a", "workspace-b", null);
        when(repository.findWalletForUpdate("tenant-a", "wallet-a")).thenReturn(Optional.of(
                new CreditWallet("wallet-a", owner, new Money(100, "USD"),
                        "ACTIVE", 1, now, now)));
        CreditWalletCommand command = CreditWalletCommand.credit(otherWorkspace, "wallet-a",
                new Money(1, "USD"), 1, "TOPUP", "ref", "credit",
                "credit-key", "actor", "credit", "trace", now);

        assertThrows(IllegalStateException.class, () -> service.execute(command));
    }
}
