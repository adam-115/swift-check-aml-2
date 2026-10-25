package com.example.app.core.normalizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateNormalizerTest {

    private final DateNormalizer normalizer = new DateNormalizer();

    @Test
    void testStandardIsoDate() {
        assertThat(normalizer.normalize("2003-07-07")).isEqualTo("2003-07-07");
        assertThat(normalizer.normalize("1937-04-28")).isEqualTo("1937-04-28");
    }

    @Test
    void testSlashFormats() {
        assertThat(normalizer.normalize("2003/07/07")).isEqualTo("2003-07-07");
        assertThat(normalizer.normalize("07/07/2003")).isEqualTo("2003-07-07");
        assertThat(normalizer.normalize("28/04/1937")).isEqualTo("1937-04-28");
    }

    @Test
    void testHyphenAlternativeFormat() {
        assertThat(normalizer.normalize("07-07-2003")).isEqualTo("2003-07-07");
    }

    @Test
    void testYearOnlyAndYearMonth() {
        assertThat(normalizer.normalize("1937")).isEqualTo("1937");
        assertThat(normalizer.normalize("2003-07")).isEqualTo("2003-07");
        assertThat(normalizer.normalize("07/2003")).isEqualTo("2003-07");
    }

    @Test
    void testTimestampWithTime() {
        assertThat(normalizer.normalize("2003-07-07 15:30:00")).isEqualTo("2003-07-07");
        assertThat(normalizer.normalize("2003-07-07T12:00:00Z")).isEqualTo("2003-07-07");
    }

    @Test
    void testEnglishTextualDate() {
        assertThat(normalizer.normalize("7 Jul 2003")).isEqualTo("2003-07-07");
        assertThat(normalizer.normalize("Jul 7, 2003")).isEqualTo("2003-07-07");
    }

    @Test
    void testNullAndEmpty() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("   ")).isNull();
    }
}
