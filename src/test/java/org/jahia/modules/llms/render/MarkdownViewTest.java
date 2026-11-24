package org.jahia.modules.llms.render;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownViewTest {

    @Test
    void namesThePageBeforeItsMarkdown() {
        assertThat(MarkdownView.frontMatter("About us", "Who we are.", Instant.parse("2026-11-21T23:30:00Z"))).isEqualTo(
                "---\n"
                        + "title: \"About us\"\n"
                        + "url: \"" + NativeMarkdown.PAGE_URL + "\"\n"
                        + "description: \"Who we are.\"\n"
                        + "last_updated: 2026-11-21\n"
                        + "---\n\n");
    }

    @Test
    void leavesOutWhatThePageDoesNotSay() {
        assertThat(MarkdownView.frontMatter("", null, null)).isEqualTo("---\nurl: \"" + NativeMarkdown.PAGE_URL + "\"\n---\n\n");
        assertThat(MarkdownView.frontMatter("About", "  ", null)).doesNotContain("description").doesNotContain("last_updated");
    }

    /** A title is what an editor typed: it never ends the block, nor adds a key to it. */
    @Test
    void keepsATitleInsideItsString() {
        String yaml = MarkdownView.frontMatter("A \"quoted\" \\ title\n---\nurl: \"https://evil.example\"", null, null);
        assertThat(yaml).startsWith("---\ntitle: \"A \\\"quoted\\\" \\\\ title --- url: \\\"https://evil.example\\\"\"\nurl: \"" + NativeMarkdown.PAGE_URL);
        assertThat(yaml.lines().filter("---"::equals).count()).isEqualTo(2);
        assertThat(yaml.lines().filter(line -> line.startsWith("url:")).count()).isEqualTo(1);
    }
}
