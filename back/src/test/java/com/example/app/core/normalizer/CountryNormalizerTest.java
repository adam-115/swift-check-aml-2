package com.example.app.core.normalizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CountryNormalizerTest {

    private final CountryNormalizer normalizer = new CountryNormalizer();

    @Test
    void testAlpha2Passthrough() {
        assertThat(normalizer.normalize("IQ")).isEqualTo("IQ");
        assertThat(normalizer.normalize("ru")).isEqualTo("RU");
        assertThat(normalizer.normalize("US")).isEqualTo("US");
        assertThat(normalizer.normalize("cy")).isEqualTo("CY");
    }

    @Test
    void testAlpha3ToAlpha2() {
        assertThat(normalizer.normalize("IRQ")).isEqualTo("IQ");
        assertThat(normalizer.normalize("RUS")).isEqualTo("RU");
        assertThat(normalizer.normalize("USA")).isEqualTo("US");
        assertThat(normalizer.normalize("CYP")).isEqualTo("CY");
        assertThat(normalizer.normalize("IRN")).isEqualTo("IR");
        assertThat(normalizer.normalize("GBR")).isEqualTo("GB");
        assertThat(normalizer.normalize("FRA")).isEqualTo("FR");
    }

    @Test
    void testCountryNamesToAlpha2() {
        assertThat(normalizer.normalize("Iraq")).isEqualTo("IQ");
        assertThat(normalizer.normalize("Russian Federation")).isEqualTo("RU");
        assertThat(normalizer.normalize("Russia")).isEqualTo("RU");
        assertThat(normalizer.normalize("United States")).isEqualTo("US");
        assertThat(normalizer.normalize("United States of America")).isEqualTo("US");
        assertThat(normalizer.normalize("Cyprus")).isEqualTo("CY");
        assertThat(normalizer.normalize("Iran")).isEqualTo("IR");
        assertThat(normalizer.normalize("United Kingdom")).isEqualTo("GB");
        assertThat(normalizer.normalize("North Korea")).isEqualTo("KP");
    }

    @Test
    void testNullAndEmpty() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("  ")).isNull();
    }
}
