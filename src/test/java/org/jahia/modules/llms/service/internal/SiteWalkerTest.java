package org.jahia.modules.llms.service.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SiteWalkerTest {

    @Test
    void aDescriptionIsOneLineWithoutTags() {
        assertThat(SiteWalker.oneLine("  <p>Our <b>products</b>\n and\tservices</p> ")).isEqualTo("Our products and services");
        assertThat(SiteWalker.oneLine("<br>")).isNull();
        assertThat(SiteWalker.oneLine(null)).isNull();
    }

    @Test
    void aLongDescriptionIsCut() {
        String out = SiteWalker.oneLine("word ".repeat(200));
        assertThat(out).hasSizeLessThanOrEqualTo(301).endsWith("…");
    }

    @Test
    void aDescriptionMadeOfOpeningBracketsIsReadInLinearTime() {
        String hostile = "<".repeat(2_000_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThat(SiteWalker.oneLine(hostile)).isNotNull());
    }
}
