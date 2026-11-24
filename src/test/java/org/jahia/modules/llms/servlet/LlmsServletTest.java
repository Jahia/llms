package org.jahia.modules.llms.servlet;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LlmsServletTest {

    private static final String ETAG = LlmsServlet.etag("# Site".getBytes(StandardCharsets.UTF_8));

    @Test
    void theValidatorFollowsTheContent() {
        assertThat(ETAG).matches("\"[0-9a-f]{32}\"")
                .isEqualTo(LlmsServlet.etag("# Site".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(LlmsServlet.etag("# Site 2".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void ifNoneMatchAcceptsListsWeakValidatorsAndTheWildcard() {
        assertThat(LlmsServlet.matches(ETAG, ETAG)).isTrue();
        assertThat(LlmsServlet.matches("W/" + ETAG, ETAG)).isTrue();
        assertThat(LlmsServlet.matches("\"other\", " + ETAG, ETAG)).isTrue();
        assertThat(LlmsServlet.matches("*", ETAG)).isTrue();
        assertThat(LlmsServlet.matches("\"other\"", ETAG)).isFalse();
        assertThat(LlmsServlet.matches(null, ETAG)).isFalse();
    }

    @Test
    void aValidatorHoldsForTheSameDatesAndForAMinute() {
        LlmsServlet.Validator validator = new LlmsServlet.Validator("2026-11-20T10:00|2026-11-20T10:01", ETAG, 0L);
        long soon = LlmsServlet.VALIDATOR_LIFETIME_NANOS - 1;
        assertThat(validator.isFor("2026-11-20T10:00|2026-11-20T10:01", soon)).isTrue();
        // Republished: the dates moved, the file is read again.
        assertThat(validator.isFor("2026-11-20T10:00|2026-11-20T10:05", soon)).isFalse();
        // Too old: read again even with the same dates.
        assertThat(validator.isFor("2026-11-20T10:00|2026-11-20T10:01", LlmsServlet.VALIDATOR_LIFETIME_NANOS)).isFalse();
        // A site node without dates is never trusted.
        assertThat(validator.isFor(null, soon)).isFalse();
    }

    @Test
    void theUrlAskedForNamesTheSiteAndTheFile() {
        assertThat(LlmsServlet.Target.of("/llms.txt", "", null, null)).isEqualTo(new LlmsServlet.Target(null, false));
        assertThat(LlmsServlet.Target.of("/llms-full.txt", "", null, null)).isEqualTo(new LlmsServlet.Target(null, true));
        assertThat(LlmsServlet.Target.of("/sites/a/llms.txt", "", null, null)).isEqualTo(new LlmsServlet.Target("a", false));
        assertThat(LlmsServlet.Target.of("/ctx/sites/a/llms-full.txt", "/ctx", null, null)).isEqualTo(new LlmsServlet.Target("a", true));
        // The forms Jahia's own rules produce, which the rewrite rules of the module accept too.
        assertThat(LlmsServlet.Target.of("/cms/render/live/en/sites/a/llms.txt", "", null, null)).isEqualTo(new LlmsServlet.Target("a", false));
        assertThat(LlmsServlet.Target.of("/cms/fr_FR/sites/a/llms-full.txt", "", null, null)).isEqualTo(new LlmsServlet.Target("a", true));
        // Every form of what Jahia's rules put in front, with and without a site.
        assertThat(LlmsServlet.Target.of("/cms/llms.txt", "", null, null)).isEqualTo(new LlmsServlet.Target(null, false));
        assertThat(LlmsServlet.Target.of("/cms/en/llms.txt", "", null, null)).isEqualTo(new LlmsServlet.Target(null, false));
        assertThat(LlmsServlet.Target.of("/cms/render/live/llms-full.txt", "", null, null)).isEqualTo(new LlmsServlet.Target(null, true));
        assertThat(LlmsServlet.Target.of("/cms/sites/a/llms.txt", "", null, null)).isEqualTo(new LlmsServlet.Target("a", false));
        // Nothing else is read as that prefix.
        assertThat(LlmsServlet.Target.of("/cmsx/llms.txt", "", null, null)).isNull();
        assertThat(LlmsServlet.Target.of("/cms/render/llms.txt", "", null, null)).isNull();
        assertThat(LlmsServlet.Target.of("/cms/en/fr/llms.txt", "", null, null)).isNull();
        assertThat(LlmsServlet.Target.of("/en/sites/a/llms.txt", "", null, null)).isNull();
    }

    @Test
    void aQueryStringCannotNameAnotherSiteOrFileThanThePath() {
        assertThat(LlmsServlet.Target.of("/sites/a/llms.txt", "", "b", "full")).isEqualTo(new LlmsServlet.Target("a", false));
        assertThat(LlmsServlet.Target.of("/llms.txt", "", "b", "full")).isEqualTo(new LlmsServlet.Target(null, false));
        assertThat(LlmsServlet.Target.of("/cms/render/live/en/sites/a/llms.txt", "", "b", "full")).isEqualTo(new LlmsServlet.Target("a", false));
    }

    @Test
    void aForwardFromAnyOtherUrlIsNotAnswered() {
        assertThat(LlmsServlet.Target.of("/modules/llms/llms", "", "a", null)).isNull();
        assertThat(LlmsServlet.Target.of("/files/live/sites/a/files/llms.txt", "", "a", null)).isNull();
        assertThat(LlmsServlet.Target.of("/other/sites/a/llms.txt", "", "a", null)).isNull();
        assertThat(LlmsServlet.Target.of("/sites/a/../b/llms.txt", "", "a", null)).isNull();
    }

    @Test
    void aDirectCallIsReadFromItsParameters() {
        assertThat(LlmsServlet.Target.of(null, "", "a", "full")).isEqualTo(new LlmsServlet.Target("a", true));
        assertThat(LlmsServlet.Target.of(null, "", null, null)).isEqualTo(new LlmsServlet.Target(null, false));
    }
}
