package com.example.platform.shared.commercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesIsoCurrencyAndSupportsNegativeAdjustments() {
        Money amount = new Money(-125L, " usd ");

        assertEquals(-125L, amount.amountMinor());
        assertEquals("USD", amount.currency());
    }

    @Test
    void rejectsMissingOrUnknownCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new Money(1L, null));
        assertThrows(IllegalArgumentException.class, () -> new Money(1L, " "));
        assertThrows(IllegalArgumentException.class, () -> new Money(1L, "ZZZ"));
    }

    @Test
    void performsCheckedArithmetic() {
        Money usd = new Money(25L, "USD");

        assertEquals(new Money(35L, "USD"), usd.add(new Money(10L, "usd")));
        assertEquals(new Money(15L, "USD"), usd.subtract(new Money(10L, "USD")));
        assertEquals(new Money(75L, "USD"), usd.multiply(3L));
        assertEquals(new Money(-25L, "USD"), usd.negate());

        assertThrows(ArithmeticException.class,
                () -> new Money(Long.MAX_VALUE, "USD").add(new Money(1L, "USD")));
        assertThrows(ArithmeticException.class,
                () -> new Money(Long.MIN_VALUE, "USD").subtract(new Money(1L, "USD")));
        assertThrows(ArithmeticException.class,
                () -> new Money(Long.MAX_VALUE, "USD").multiply(2L));
        assertThrows(ArithmeticException.class,
                () -> new Money(Long.MIN_VALUE, "USD").negate());
    }

    @Test
    void rejectsMixedCurrencyArithmetic() {
        Money usd = new Money(100L, "USD");
        Money eur = new Money(100L, "EUR");

        assertThrows(IllegalArgumentException.class, () -> usd.add(eur));
        assertThrows(IllegalArgumentException.class, () -> usd.subtract(eur));
    }

    @Test
    void exposesNoBinaryFloatingPointApi() {
        Stream<Class<?>> parameterTypes = Stream.concat(
                Arrays.stream(Money.class.getDeclaredConstructors())
                        .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())),
                Arrays.stream(Money.class.getDeclaredMethods())
                        .flatMap(method -> Stream.concat(
                                Stream.of(method.getReturnType()),
                                Arrays.stream(method.getParameterTypes()))));

        assertFalse(parameterTypes.anyMatch(type -> type == Double.TYPE
                || type == Double.class
                || type == Float.TYPE
                || type == Float.class));
    }
}
