package com.example.platform.shared.commercial;

import java.util.Currency;
import java.util.Locale;

/**
 * Canonical monetary value expressed only in integral minor units.
 *
 * @param amountMinor signed minor-unit amount; negative values represent adjustments
 * @param currency normalized ISO-4217 currency code
 */
public record Money(long amountMinor, String currency) {

    public Money {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be null/blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code", exception);
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money multiply(long multiplier) {
        return new Money(Math.multiplyExact(amountMinor, multiplier), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    private void requireSameCurrency(Money other) {
        if (other == null || !currency.equals(other.currency)) {
            throw new IllegalArgumentException("money arithmetic requires matching currencies");
        }
    }
}
