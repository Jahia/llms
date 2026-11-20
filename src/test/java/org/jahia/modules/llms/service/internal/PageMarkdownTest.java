package org.jahia.modules.llms.service.internal;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageMarkdownTest {

    @Test
    void readsTheSiteOfAPageInEveryFormJahiaWritesIt() {
        assertThat(PageMarkdown.siteKeyOf("/sites/a/home.html")).isEqualTo("a");
        assertThat(PageMarkdown.siteKeyOf("/en/sites/a/home/about.html")).isEqualTo("a");
        assertThat(PageMarkdown.siteKeyOf("/cms/fr_FR/sites/a/home.html")).isEqualTo("a");
        assertThat(PageMarkdown.siteKeyOf("/cms/render/live/en/sites/my-site_2/home/a%20b.html")).isEqualTo("my-site_2");
    }

    @Test
    void theSiteIsTheFirstOneNamedNotOneFurtherDownThePath() {
        assertThat(PageMarkdown.siteKeyOf("/sites/b/x/sites/a/home.html")).isEqualTo("b");
        assertThat(PageMarkdown.siteKeyOf("/modules/x/sites/a/home.html")).isNull();
    }

    @Test
    void aPathThatServersMayReadDifferentlyIsNotAPage() {
        for (String path : new String[]{null, "", "/sites/a/home", "/sites/a/home.html.md", "/sites/a/../b/home.html",
                "/sites/a/%2E%2E/b/home.html", "/sites/a/x%2Fy.html", "/sites/a/x%5Cy.html", "/sites/a/x%252e.html",
                "/sites/a/x;y/home.html", "/sites/a//home.html", "/sites/a/home.html#x", "/sites/a/home.html?x"}) {
            assertThat(PageMarkdown.siteKeyOf(path)).as(String.valueOf(path)).isNull();
        }
    }

    @Test
    void thePageFetchedMustEndWithThePageAskedFor() {
        assertThat(PageMarkdown.isSamePage("/home/about.html", "/cms/render/live/en/sites/a/home/about.html")).isTrue();
        assertThat(PageMarkdown.isSamePage("/sites/a/home.html", "/sites/a/home.html")).isTrue();
        assertThat(PageMarkdown.isSamePage("/home/about.html", "/cms/render/live/en/sites/a/home/other.html")).isFalse();
        assertThat(PageMarkdown.isSamePage("", "/sites/a/home.html")).isFalse();
        assertThat(PageMarkdown.isSamePage("home.html", "/sites/a/home.html")).isFalse();
    }

    @Test
    void theTitleIsTheFirstHeadingElseTheTitleOfTheDocument() {
        assertThat(PageMarkdown.titleOf(Jsoup.parse("<title>About | Site</title><body><h1> About\n us </h1></body>"))).isEqualTo("About us");
        assertThat(PageMarkdown.titleOf(Jsoup.parse("<title>About | Site</title><body><p>x</p></body>"))).isEqualTo("About | Site");
        assertThat(PageMarkdown.titleOf(Jsoup.parse("<body><p>x</p></body>"))).isEmpty();
    }

    @Test
    void aVanityUrlFollowedByMdIsReadAsThatVanityUrl() {
        assertThat(PageMarkdown.vanityOf("/news/product-launch.md", "")).isEqualTo("/news/product-launch");
        assertThat(PageMarkdown.vanityOf("/promo.md", "")).isEqualTo("/promo");
        assertThat(PageMarkdown.vanityOf("/ctx/news/product-launch.md", "/ctx")).isEqualTo("/news/product-launch");
        assertThat(PageMarkdown.vanityOf("/a/b_c/d~e/f.g.md", "")).isEqualTo("/a/b_c/d~e/f.g");
    }

    @Test
    void aPageAddressOrAPathOfJahiaIsNotAVanityUrl() {
        // A page has its own rule.
        assertThat(PageMarkdown.vanityOf("/home/about.html.md", "")).isNull();
        // Jahia's own paths, the media library, the modules: never looked up as a vanity URL.
        for (String reserved : new String[]{"/files/live/sites/a/files/readme.md", "/modules/llms/readme.md", "/cms/render/live/en/x.md",
                "/sites/a/news/launch.md", "/tools/x.md", "/repository/default/a.md"}) {
            assertThat(PageMarkdown.vanityOf(reserved, "")).as(reserved).isNull();
        }
        assertThat(PageMarkdown.vanityOf("/news/launch", "")).isNull();
        assertThat(PageMarkdown.vanityOf(null, "")).isNull();
        assertThat(PageMarkdown.vanityOf("/other/news/launch.md", "/ctx")).isNull();
    }

    @Test
    void aVanityUrlThatCouldBeReadTwoWaysIsRefused() {
        for (String odd : new String[]{"/news/../admin.md", "/news/.hidden.md", "/news//launch.md", "/news/%2e%2e/x.md", "/news/a b.md",
                "/news/a;x=1.md", "/news\\launch.md", "/.md", "/news/launch.md/", "/" + "a".repeat(300) + ".md",
                "/a/b/c/d/e/f/g/h/i/j/k/l/m.md"}) {
            assertThat(PageMarkdown.vanityOf(odd, "")).as(odd).isNull();
        }
    }
}
