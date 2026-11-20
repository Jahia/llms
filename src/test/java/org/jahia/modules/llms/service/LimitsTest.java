package org.jahia.modules.llms.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimitsTest {

    @Test
    void keepsValuesInRange() {
        Limits limits = Limits.of(3, 200, 20, 4, 2048, 4096, 300, "http://127.0.0.1:8080");
        assertThat(limits).isEqualTo(new Limits(3, 200, 20, 4, 2048 * 1024, 4096 * 1024, 300, "http://127.0.0.1:8080"));
    }

    @Test
    void aHugeSizeDoesNotOverflow() {
        Limits limits = Limits.of(3, 200, 20, 4, Integer.MAX_VALUE, 2_097_152, 300, "");
        assertThat(limits.maxPageBytes()).isEqualTo(64 * 1024 * 1024);
        assertThat(limits.maxFullBytes()).isEqualTo(64 * 1024 * 1024);
    }

    @Test
    void bringsBackValuesOutOfRange() {
        Limits limits = Limits.of(-1, 1_000_000, 0, 64, 0, -5, Integer.MAX_VALUE, null);
        assertThat(limits.maxDepth()).isEqualTo(1);
        assertThat(limits.maxPages()).isEqualTo(2000);
        assertThat(limits.fetchTimeoutSeconds()).isEqualTo(1);
        assertThat(limits.fetchThreads()).isEqualTo(8);
        assertThat(limits.maxPageBytes()).isEqualTo(16 * 1024);
        assertThat(limits.maxFullBytes()).isEqualTo(16 * 1024);
        assertThat(limits.maxTotalSeconds()).isEqualTo(3600);
        assertThat(limits.renderBaseUrl()).isNull();
    }

    @Test
    void ignoresARenderBaseUrlThatIsNotAnOrigin() {
        assertThat(Limits.of(3, 200, 20, 4, 2048, 4096, 300, "http://internal.example/admin").renderBaseUrl()).isNull();
        assertThat(Limits.of(3, 200, 20, 4, 2048, 4096, 300, "file:///etc/passwd").renderBaseUrl()).isNull();
        assertThat(Limits.isRejected("http://internal.example/admin")).isTrue();
        assertThat(Limits.isRejected(" ")).isFalse();
        assertThat(Limits.isRejected("http://127.0.0.1:8080")).isFalse();
    }
}
