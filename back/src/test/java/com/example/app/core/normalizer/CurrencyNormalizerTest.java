package com.example.app.core.normalizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyNormalizerTest {

    private final CurrencyNormalizer normalizer = new CurrencyNormalizer();

    @Test
    void testSymbolsToIso() {
        assertThat(normalizer.normalize("$")).isEqualTo("USD");
        assertThat(normalizer.normalize("US$")).isEqualTo("USD");
        assertThat(normalizer.normalize("€")).isEqualTo("EUR");
        assertThat(normalizer.normalize("£")).isEqualTo("GBP");
        assertThat(normalizer.normalize("¥")).isEqualTo("JPY");
    }

    @Test
    void testIso3Codes() {
        assertThat(normalizer.normalize("usd")).isEqualTo("USD");
        assertThat(normalizer.normalize("EUR")).isEqualTo("EUR");
        assertThat(normalizer.normalize("CHF")).isEqualTo("CHF");
        assertThat(normalizer.normalize("RUB")).isEqualTo("RUB");
    }

    @Test
    void testNullAndEmpty() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("  ")).isNull();
    }
}
