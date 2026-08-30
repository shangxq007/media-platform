package com.example.platform.billing.domain;
public record CreditWalletCommandResult(
        CreditWallet wallet, String reservationId, String reservationStatus) {}
