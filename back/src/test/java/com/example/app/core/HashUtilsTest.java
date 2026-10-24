package com.example.app.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilsTest {

    @Test
    void testSha1Signature() {
        String input = "test-string";
        // SHA-1 de "test-string" = 4f49d69613b186e71104c7ca1b26c1e5b78c9193
        String expectedHash = "4f49d69613b186e71104c7ca1b26c1e5b78c9193";

        assertThat(HashUtils.sha1(input)).isEqualTo(expectedHash);
        assertThat(HashUtils.computeSha1(input)).isEqualTo(expectedHash);
        assertThat(HashUtils.sign(input)).isEqualTo(expectedHash);
    }

    @Test
    void testNullInputReturnsNull() {
        assertThat(HashUtils.sha1(null)).isNull();
        assertThat(HashUtils.sign(null)).isNull();
    }

    @Test
    void testDeterministicOutput() {
        String input = "EU-SANCTION:13";
        String hash1 = HashUtils.sha1(input);
        String hash2 = HashUtils.sha1(input);

        assertThat(hash1).isNotNull().hasSize(40).isEqualTo(hash2);
    }
}
