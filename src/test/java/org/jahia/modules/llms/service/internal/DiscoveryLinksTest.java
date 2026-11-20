package org.jahia.modules.llms.service.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryLinksTest {

    private static final String PAGE = "<!DOCTYPE html><html lang=\"en\"><head><title>About</title></head><body><p>x</p></body></html>";

    @Test
    void writesBothLinksAtTheTopOfTheHead() {
        assertThat(DiscoveryLinks.addTo(PAGE, "/home/about.html.md", "/sites/a/llms.txt")).isEqualTo(
                "<!DOCTYPE html><html lang=\"en\"><head>"
                        + "<link rel=\"alternate\" type=\"text/markdown\" href=\"/home/about.html.md\"/>"
                        + "<link rel=\"describedby\" href=\"/sites/a/llms.txt\"/>"
                        + "<title>About</title></head><body><p>x</p></body></html>");
    }

    @Test
    void writesOnlyTheLinkThereIs() {
        assertThat(DiscoveryLinks.addTo(PAGE, null, "/sites/a/llms.txt"))
                .contains("rel=\"describedby\"").doesNotContain("text/markdown");
        assertThat(DiscoveryLinks.addTo(PAGE, "/home/about.html.md", null))
                .contains("text/markdown").doesNotContain("describedby");
        assertThat(DiscoveryLinks.addTo(PAGE, null, null)).isSameAs(PAGE);
    }

    @Test
    void findsAHeadWithAttributesWhateverItsCase() {
        assertThat(DiscoveryLinks.addTo("<HTML><HEAD profile=\"x\"><title>t</title></HEAD></HTML>", "/a.html.md", null))
                .startsWith("<HTML><HEAD profile=\"x\"><link rel=\"alternate\"");
        // <header> is not <head>.
        assertThat(DiscoveryLinks.addTo("<body><header>Site</header></body>", "/a.html.md", null)).isEqualTo("<body><header>Site</header></body>");
    }

    @Test
    void leavesAloneAPageThatAlreadyCarriesTheLinks() {
        String linked = DiscoveryLinks.addTo(PAGE, "/home/about.html.md", "/sites/a/llms.txt");
        assertThat(DiscoveryLinks.addTo(linked, "/home/about.html.md", "/sites/a/llms.txt")).isEqualTo(linked);
    }

    @Test
    void anAddressCannotEndItsAttribute() {
        assertThat(DiscoveryLinks.addTo(PAGE, "/a\"><script>x</script>.html.md", null))
                .contains("href=\"/a&quot;&gt;&lt;script&gt;x&lt;/script&gt;.html.md\"").doesNotContain("<script>");
    }

    @Test
    void saysTheSameInALinkHeaderForAResourceWithoutAHead() {
        assertThat(DiscoveryLinks.header("https://www.example.com/home/about.html", "/sites/a/llms.txt"))
                .isEqualTo("<https://www.example.com/home/about.html>; rel=\"canonical\", </sites/a/llms.txt>; rel=\"describedby\"");
        assertThat(DiscoveryLinks.header("https://www.example.com/a.html", null))
                .isEqualTo("<https://www.example.com/a.html>; rel=\"canonical\"");
        assertThat(DiscoveryLinks.header(null, null)).isNull();
        assertThat(DiscoveryLinks.header("/a>\r\nSet-Cookie: x=1", null)).isEqualTo("</a%3ESet-Cookie: x=1>; rel=\"canonical\"");
    }

    @Test
    void theMarkdownAddressComesFromThePageInItsShortFormForTheDefaultLanguage() {
        assertThat(DiscoveryLinks.markdownHref("/cms/render/live/en/sites/a/home/about.html", "", "en", "en"))
                .isEqualTo("/sites/a/home/about.html.md");
        // Another language keeps the address that names it.
        assertThat(DiscoveryLinks.markdownHref("/cms/render/live/fr/sites/a/home/about.html", "", "fr", "en"))
                .isEqualTo("/cms/render/live/fr/sites/a/home/about.html.md");
        assertThat(DiscoveryLinks.markdownHref("/jahia/cms/render/live/en/sites/a/home.html", "/jahia", "en", "en"))
                .isEqualTo("/jahia/sites/a/home.html.md");
    }

    @Test
    void noMarkdownAddressForWhatIsNotAPageOfASite() {
        assertThat(DiscoveryLinks.markdownHref("/cms/render/live/en/sites/a/home/about.pdf", "", "en", "en")).isNull();
        assertThat(DiscoveryLinks.markdownHref("/cms/render/live/en/users/root/x.html", "", "en", "en")).isNull();
        assertThat(DiscoveryLinks.markdownHref("//evil.test/sites/a/home.html", "", "en", "en")).isNull();
        assertThat(DiscoveryLinks.markdownHref("/other/cms/render/live/en/sites/a/home.html", "/jahia", "en", "en")).isNull();
        assertThat(DiscoveryLinks.markdownHref(null, "", "en", "en")).isNull();
    }
}
