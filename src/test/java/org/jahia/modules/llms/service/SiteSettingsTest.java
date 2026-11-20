package org.jahia.modules.llms.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SiteSettingsTest {

    private static final SiteSettings MODULE = new SiteSettings(3, 200, 100, Set.of("jseomix:noIndex"), Set.of(), Set.of(), false, false);

    @Test
    void aSiteWithoutValuesKeepsTheOnesOfTheModule() {
        assertThat(MODULE.with(Map.of("siteKey", "academy"))).isEqualTo(MODULE);
        assertThat(MODULE.with(Map.of("maxDepth", " ", "includedNodeTypes", "", "linkToMarkdown", ""))).isEqualTo(MODULE);
    }

    @Test
    void aSiteChangesOnlyWhatItNames() {
        SiteSettings site = MODULE.with(Map.of("maxDepth", "8", "includedNodeTypes", "jmix:llmsListed", "linkToMarkdown", "true"));
        assertThat(site.maxDepth()).isEqualTo(8);
        assertThat(site.included()).containsExactly("jmix:llmsListed");
        assertThat(site.linkToMarkdown()).isTrue();
        assertThat(site.maxPages()).isEqualTo(200);
        assertThat(site.excluded()).containsExactly("jseomix:noIndex");
        assertThat(site.keepImages()).isFalse();
    }

    @Test
    void numbersAreBroughtBackIntoRangeAndWhatIsNotANumberIsIgnored() {
        SiteSettings site = MODULE.with(Map.of("maxDepth", "99", "maxPages", "-4", "maxMainResources", "many"));
        assertThat(site.maxDepth()).isEqualTo(10);
        assertThat(site.maxPages()).isEqualTo(1);
        assertThat(site.maxMainResources()).isEqualTo(100);
        assertThat(MODULE.with(Map.of("maxMainResources", 0)).maxMainResources()).isZero();
    }

    @Test
    void noneDropsAListOfTypesForTheSite() {
        assertThat(MODULE.with(Map.of("excludedNodeTypes", "none")).excluded()).isEmpty();
    }

    @Test
    void aFlagIsTrueOrFalseAndNothingElse() {
        SiteSettings on = MODULE.with(Map.of("keepImages", "TRUE"));
        assertThat(on.keepImages()).isTrue();
        assertThat(on.with(Map.of("keepImages", "maybe")).keepImages()).isTrue();
        assertThat(on.with(Map.of("keepImages", false)).keepImages()).isFalse();
    }
}
