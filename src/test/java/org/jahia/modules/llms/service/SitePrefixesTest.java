package org.jahia.modules.llms.service;

import org.jahia.modules.llms.service.internal.SiteWalker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SitePrefixesTest {

    private static SiteWalker.Page page(String renderPath) {
        return new SiteWalker.Page("t", "https://www.example.com/x", null, renderPath);
    }

    @Test
    void theAllowedRedirectPathsStopAtTheSiteKey() {
        assertThat(LlmsService.sitePrefixes(List.of(
                page("/cms/render/live/en/sites/a/home.html"),
                page("/cms/render/live/en/sites/a/home/about.html"),
                page("/ctx/cms/render/live/fr/sites/a/home.html")), "a"))
                .containsExactlyInAnyOrder("/cms/render/live/en/sites/a/", "/ctx/cms/render/live/fr/sites/a/");
    }

    @Test
    void aPageOfAnotherSiteAddsNothing() {
        assertThat(LlmsService.sitePrefixes(List.of(page("/cms/render/live/en/sites/ab/home.html")), "a")).isEmpty();
    }
}
