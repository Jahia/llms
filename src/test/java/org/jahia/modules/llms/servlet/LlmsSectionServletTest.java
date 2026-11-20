package org.jahia.modules.llms.servlet;

import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.SectionFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmsSectionServletTest {

    private static final String RENDER = "/cms/render/live/en/sites/a/home/docs/llms.txt";

    @Test
    void servesTheFileOfThePageNamedByTheUrl() {
        assertThat(LlmsSectionServlet.fileOf("/sites/a/home/docs/llms.txt", "", "path=/sites/a/home/docs/llms.txt"))
                .isEqualTo(new SectionFile("/sites/a/home/docs", false));
        // On the site's own host the URL is short: Jahia's rules add the language and the site.
        assertThat(LlmsSectionServlet.fileOf("/home/docs/llms.txt", "", "path=" + RENDER)).isEqualTo(new SectionFile("/sites/a/home/docs", false));
        assertThat(LlmsSectionServlet.fileOf("/ctx/home/docs/llms-full.txt", "/ctx", "path=/cms/render/live/en/sites/a/home/docs/llms-full.txt"))
                .isEqualTo(new SectionFile("/sites/a/home/docs", true));
    }

    @Test
    void neverReadsWhatAVisitorAddedNorAnswersADirectCall() {
        assertThat(LlmsSectionServlet.fileOf("/home/docs/llms.txt", "", "path=" + RENDER + "&path=/sites/b/secret/llms.txt"))
                .isEqualTo(new SectionFile("/sites/a/home/docs", false));
        assertThat(LlmsSectionServlet.fileOf(null, "", "path=" + RENDER)).isNull();
        assertThat(LlmsSectionServlet.fileOf("/home/docs/llms.txt", "", "x=1&path=" + RENDER)).isNull();
        assertThat(LlmsSectionServlet.fileOf("/home/other/llms.txt", "", "path=" + RENDER)).isNull();
    }

    @Test
    void aSectionIsBelowItsSiteAndWrittenPlainly() {
        assertThat(LlmsService.isSectionOf("a", "/sites/a/home/docs")).isTrue();
        for (String path : new String[]{null, "/sites/a", "/sites/a/", "/sites/ab/home", "/sites/b/home", "/sites/a/home/../../b",
                "/sites/a//home", "/sites/a/home/", "/sites/a/home/.", "/sites/a/home\\x"}) {
            assertThat(LlmsService.isSectionOf("a", path)).as(String.valueOf(path)).isFalse();
        }
    }
}
