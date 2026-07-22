package com.example.platform.typedschema.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TsvectorValue}.
 * Verifies the tsvector value wrapper behavior.
 */
class TsvectorValueTest {

    @Test
    void storesValue() {
        TsvectorValue tv = new TsvectorValue("'cat':1 'dog':4 'rat':2");
        assertThat(tv.value()).isEqualTo("'cat':1 'dog':4 'rat':2");
    }

    @Test
    void toStringReturnsValue() {
        TsvectorValue tv = new TsvectorValue("hello world");
        assertThat(tv.toString()).isEqualTo("hello world");
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new TsvectorValue(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tsvector value must not be null");
    }

    @Test
    void equalityByValue() {
        TsvectorValue a = new TsvectorValue("'cat':1 'dog':2");
        TsvectorValue b = new TsvectorValue("'cat':1 'dog':2");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void inequalityByValue() {
        TsvectorValue a = new TsvectorValue("'cat':1");
        TsvectorValue b = new TsvectorValue("'dog':1");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void emptyStringIsValid() {
        TsvectorValue tv = new TsvectorValue("");
        assertThat(tv.value()).isEmpty();
    }
}
