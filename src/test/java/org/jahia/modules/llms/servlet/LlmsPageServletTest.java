package org.jahia.modules.llms.servlet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmsPageServletTest {

    private static final String RENDER = "/cms/render/live/en/sites/a/home/about.html";

    @Test
    void rendersThePathTheRulesMadeOfTheUrlAskedFor() {
        assertThat(LlmsPageServlet.pagePath("/sites/a/home/about.html.md", "", "path=/sites/a/home/about.html"))
                .isEqualTo("/sites/a/home/about.html");
        // On the site's own host the URL is short: Jahia's rules add the language and the site.
        assertThat(LlmsPageServlet.pagePath("/home/about.html.md", "", "path=" + RENDER)).isEqualTo(RENDER);
        assertThat(LlmsPageServlet.pagePath("/ctx/home/about.html.md", "/ctx", "path=" + RENDER)).isEqualTo(RENDER);
    }

    @Test
    void neverReadsAPathTheVisitorAdded() {
        // What a visitor appends comes after the parameter of the rule, and is not read.
        assertThat(LlmsPageServlet.pagePath("/home/about.html.md", "", "path=" + RENDER + "&path=/sites/b/secret.html")).isEqualTo(RENDER);
        // A query string that does not start with the parameter of the rule is not a forward of the rule.
        assertThat(LlmsPageServlet.pagePath("/home/about.html.md", "", "x=1&path=" + RENDER)).isNull();
    }

    @Test
    void answersOnlyARequestTheRuleForwarded() {
        // Called directly: no forwarded URL.
        assertThat(LlmsPageServlet.pagePath(null, "", "path=" + RENDER)).isNull();
        assertThat(LlmsPageServlet.pagePath("/modules/llms/llmspage", "", "path=" + RENDER)).isNull();
        assertThat(LlmsPageServlet.pagePath("/home/about.html.md", "", null)).isNull();
    }

    @Test
    void refusesAPathThatIsNotThePageAskedFor() {
        assertThat(LlmsPageServlet.pagePath("/home/about.html.md", "", "path=/cms/render/live/en/sites/a/home/other.html")).isNull();
        assertThat(LlmsPageServlet.pagePath("/sites/a/home/about.html.md", "", "path=/sites/b/home/about.html")).isNull();
    }

    @Test
    void refusesWhatIsNotAPageOfASite() {
        for (String path : new String[]{"/modules/graphql.html", "/files/live/sites/a/files/doc.html", "/cms/render/default/en/sites/a/home.html",
                "/sites/a/home/../../b/home.html", "/sites/a/home/%2e%2e/x.html", "/sites/a/home;x=1/about.html", "/sites/a//about.html",
                "/tools/x.html", "/sites/a/home/about.html?x=1"}) {
            assertThat(LlmsPageServlet.pagePath(path + ".md", "", "path=" + path)).as(path).isNull();
        }
    }

    @Test
    void rendersTheNodeAnAddressFollowedByMdNames() {
        assertThat(LlmsPageServlet.nodePath("/sites/a/home/about.md", "", "node=/cms/render/live/en/sites/a/home/about"))
                .isEqualTo("/cms/render/live/en/sites/a/home/about.html");
        // The short address of the host of the site, which Jahia's rules completed.
        assertThat(LlmsPageServlet.nodePath("/home/about.md", "", "node=/cms/render/live/en/sites/a/home/about"))
                .isEqualTo("/cms/render/live/en/sites/a/home/about.html");
        assertThat(LlmsPageServlet.nodePath("/ctx/home/about.md", "/ctx", "node=/cms/render/live/en/sites/a/home/about"))
                .isEqualTo("/cms/render/live/en/sites/a/home/about.html");
    }

    @Test
    void neverRendersANodeTheVisitorNamedInTheQuery() {
        // What the rule wrote is not the address asked for: a visitor does not choose the node.
        assertThat(LlmsPageServlet.nodePath("/home/about.md", "", "node=/cms/render/live/en/sites/a/home/secret")).isNull();
        assertThat(LlmsPageServlet.nodePath("/home/about.md", "", "x=1&node=/cms/render/live/en/sites/a/home/about")).isNull();
        // Not a node of a site, called directly, a page address, or a path that reads two ways.
        assertThat(LlmsPageServlet.nodePath("/news/launch.md", "", "node=/news/launch")).isNull();
        assertThat(LlmsPageServlet.nodePath(null, "", "node=/cms/render/live/en/sites/a/home/about")).isNull();
        assertThat(LlmsPageServlet.nodePath("/home/about.html.md", "", "node=/cms/render/live/en/sites/a/home/about.html")).isNull();
        assertThat(LlmsPageServlet.nodePath("/home/../x.md", "", "node=/cms/render/live/en/sites/a/home/../x")).isNull();
    }

    @Test
    void readsTheNodeAndTheLanguageOfARenderPath() {
        LlmsPageServlet.Target target = LlmsPageServlet.Target.of("/cms/render/live/fr/sites/a/home/a%20propos.html");
        assertThat(target.nodePath()).isEqualTo("/sites/a/home/a propos");
        assertThat(target.locale().toLanguageTag()).isEqualTo("fr");
        assertThat(target.siteKey()).isEqualTo("a");
        assertThat(LlmsPageServlet.Target.of("/cms/render/live/en/sites/a/home/a+b.html").nodePath()).isEqualTo("/sites/a/home/a+b");
        assertThat(LlmsPageServlet.Target.of("/files/live/sites/a/files/x.html")).isNull();
        assertThat(LlmsPageServlet.Target.of("/cms/render/live/en/sites/a/home/%2e%2e/x.html")).isNull();
    }
}
