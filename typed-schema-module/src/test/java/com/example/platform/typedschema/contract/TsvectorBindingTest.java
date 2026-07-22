package com.example.platform.typedschema.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TsvectorBinding}.
 * Verifies converter behavior (null handling, from/to types).
 * Full JDBC integration tests require a running PostgreSQL instance.
 */
class TsvectorBindingTest {

    private final TsvectorBinding binding = new TsvectorBinding();

    @Test
    void converterFromReturnsNullForNull() {
        var converter = binding.converter();
        assertThat(converter.from(null)).isNull();
    }

    @Test
    void converterToReturnsNullForNull() {
        var converter = binding.converter();
        assertThat(converter.to(null)).isNull();
    }

    @Test
    void converterFromWrapsStringInTsvectorValue() {
        var converter = binding.converter();
        TsvectorValue result = converter.from("'cat':1 'dog':2");
        assertThat(result).isNotNull();
        assertThat(result.value()).isEqualTo("'cat':1 'dog':2");
    }

    @Test
    void converterToUnwrapsTsvectorValueToString() {
        var converter = binding.converter();
        TsvectorValue tv = new TsvectorValue("'hello':1");
        Object result = converter.to(tv);
        assertThat(result).isEqualTo("'hello':1");
    }

    @Test
    void converterFromTypeIsObject() {
        assertThat(binding.converter().fromType()).isEqualTo(Object.class);
    }

    @Test
    void converterToTypeIsTsvectorValue() {
        assertThat(binding.converter().toType()).isEqualTo(TsvectorValue.class);
    }

    @Test
    void converterRoundTrip() {
        var converter = binding.converter();
        TsvectorValue original = new TsvectorValue("'search':1 'term':3");
        Object dbObject = converter.to(original);
        TsvectorValue roundTripped = converter.from(dbObject);
        assertThat(roundTripped).isEqualTo(original);
    }
}
