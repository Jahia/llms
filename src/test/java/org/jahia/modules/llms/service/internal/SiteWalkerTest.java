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

    @Test
    void readsTheConfiguredTypesAndDropsWhatIsNotATypeName() {
        assertThat(SiteWalker.typeNames("jseomix:noIndex, acme:internalOnly\tacmemix:draft_v2"))
                .containsExactly("jseomix:noIndex", "acme:internalOnly", "acmemix:draft_v2");
        assertThat(SiteWalker.typeNames("jseomix:noIndex,jseomix:noIndex")).containsExactly("jseomix:noIndex");
        assertThat(SiteWalker.typeNames("noprefix, :x, a:, a b:c', ../x:y, jnt:page;drop")).isEmpty();
        assertThat(SiteWalker.typeNames("")).isEmpty();
        assertThat(SiteWalker.typeNames(null)).isEmpty();
    }

    @Test
    void theDefaultsAreNoIndexAndTheHiddenMarkOfTheKnownTemplates() {
        assertThat(SiteWalker.typeNames(SiteWalker.DEFAULT_EXCLUDED))
                .containsExactly("jseomix:noIndex", "jcmix:hidePage", "jacademix:hidePage");
    }
}
