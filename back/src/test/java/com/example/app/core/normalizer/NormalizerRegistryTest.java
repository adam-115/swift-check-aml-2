package com.example.app.core.normalizer;

import com.example.app.core.HashUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizerRegistryTest {

    private final NormalizerRegistry registry = new NormalizerRegistry();

    @Test
    void testNormalizeDate() {
        assertThat(registry.normalize("date", "07/07/2003")).isEqualTo("2003-07-07");
    }

    @Test
    void testNormalizeCountry() {
        assertThat(registry.normalize("country", "Russian Federation")).isEqualTo("RU");
        assertThat(registry.normalize("country", "IRQ")).isEqualTo("IQ");
    }

    @Test
    void testNormalizeCurrency() {
        assertThat(registry.normalize("currency", "$")).isEqualTo("USD");
        assertThat(registry.normalize("currency", "eur")).isEqualTo("EUR");
    }

    @Test
    void testNormalizeEntity() {
        assertThat(registry.normalize("entity", "88")).isEqualTo(HashUtils.sha1("88"));
    }

    @Test
    void testUnknownTypeReturnsTrimmed() {
        assertThat(registry.normalize("unknown", "  Hello World  ")).isEqualTo("Hello World");
        assertThat(registry.normalize(null, "  Hello World  ")).isEqualTo("Hello World");
    }
}
