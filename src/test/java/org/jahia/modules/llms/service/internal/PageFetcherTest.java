package org.jahia.modules.llms.service.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageFetcherTest {

    private static final URI FROM = URI.create("http://127.0.0.1:8080/cms/render/live/en/sites/a/home.html");
    private static final Predicate<String> SITE_A = path -> path.contains("/sites/a/");

    @Test
    void followsARedirectInsideTheSite() throws IOException {
        assertThat(PageFetcher.redirect(FROM, "/cms/render/live/en/sites/a/home/about.html", SITE_A))
                .isEqualTo(URI.create("http://127.0.0.1:8080/cms/render/live/en/sites/a/home/about.html"));
        assertThat(PageFetcher.redirect(FROM, "about.html", SITE_A).getPath())
                .isEqualTo("/cms/render/live/en/sites/a/about.html");
    }

    @Test
    void refusesAnotherOrigin() {
        for (String location : new String[]{"//evil.test/sites/a/x", "http://evil.test/sites/a/x",
                "http://127.0.0.1:8080@evil.test/sites/a/x", "http://127.0.0.1:22/sites/a/x", "https://127.0.0.1:8080/sites/a/x"}) {
            assertThatThrownBy(() -> PageFetcher.redirect(FROM, location, SITE_A))
                    .as(location).isInstanceOf(IOException.class).hasMessage("redirect off this server");
        }
    }

    @Test
    void refusesAnotherSiteAndServlets() {
        for (String location : new String[]{"/cms/render/live/en/sites/b/home.html", "/modules/graphql",
                "/cms/render/live/en/sites/a/../b/home.html"}) {
            assertThatThrownBy(() -> PageFetcher.redirect(FROM, location, SITE_A))
                    .as(location).isInstanceOf(IOException.class).hasMessage("redirect off this site");
        }
    }

    @Test
    void refusesAMissingLocation() {
        assertThatThrownBy(() -> PageFetcher.redirect(FROM, null, SITE_A)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> PageFetcher.redirect(FROM, "", SITE_A)).isInstanceOf(IOException.class);
    }
}
