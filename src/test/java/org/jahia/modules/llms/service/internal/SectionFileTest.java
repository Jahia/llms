package org.jahia.modules.llms.service.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectionFileTest {

    @Test
    void aFileUnderThePathOfAPageBelongsToThatPage() {
        assertThat(SectionFile.of("/sites/a/home/docs/llms.txt")).isEqualTo(new SectionFile("/sites/a/home/docs", false));
        assertThat(SectionFile.of("/sites/a/home/docs/llms-full.txt")).isEqualTo(new SectionFile("/sites/a/home/docs", true));
        assertThat(SectionFile.of("/cms/render/live/en/sites/a/home/llms.txt")).isEqualTo(new SectionFile("/sites/a/home", false));
        assertThat(SectionFile.of("/cms/fr_FR/sites/a/home/user-guide_v2.1/llms.txt"))
                .isEqualTo(new SectionFile("/sites/a/home/user-guide_v2.1", false));
    }

    @Test
    void theFilesOfTheSiteItselfAreNotTheFilesOfASection() {
        assertThat(SectionFile.of("/sites/a/llms.txt")).isNull();
        assertThat(SectionFile.of("/llms.txt")).isNull();
    }

    @Test
    void aPathThatCouldBeReadTwoWaysNamesNoFile() {
        for (String path : new String[]{null, "/sites/a/home/../../b/home/llms.txt", "/sites/a/home/%2e%2e/llms.txt", "/sites/a/home//llms.txt",
                "/sites/a/home/x;y/llms.txt", "/sites/a/home/a%20b/llms.txt", "/sites/a/home/./llms.txt", "/sites/a/.hidden/llms.txt",
                "/files/live/sites/a/files/llms.txt", "/modules/x/sites/a/home/llms.txt", "/sites/a/home/llms.txt.bak", "/sites/a/home/llms.md"}) {
            assertThat(SectionFile.of(path)).as(String.valueOf(path)).isNull();
        }
    }

    @Test
    void theAddressOfTheFileOfAPage() {
        assertThat(SectionFile.hrefOf("/sites/a/home/docs")).isEqualTo("/sites/a/home/docs/llms.txt");
        // A page name an address would have to encode: no address is written rather than a wrong one.
        assertThat(SectionFile.hrefOf("/sites/a/home/my docs")).isNull();
        assertThat(SectionFile.hrefOf("/sites/a")).isNull();
        assertThat(SectionFile.hrefOf(null)).isNull();
    }

    /** A path of thousands of segments is read in one pass: it is a file of a section like another, not a crash. */
    @Test
    void readsAVeryLongPath() {
        String path = "/sites/a" + "/p".repeat(50_000);
        assertThat(SectionFile.of(path + "/llms.txt")).isEqualTo(new SectionFile(path, false));
        assertThat(SectionFile.hrefOf(path)).isEqualTo(path + "/llms.txt");
        assertThat(SectionFile.of("/sites/a" + "/p".repeat(50_000) + "//x/llms.txt")).isNull();
        assertThat(SectionFile.of("/sites/a/home/.hidden/llms.txt")).isNull();
        assertThat(SectionFile.of("/sites/a/home/llms.txt/")).isNull();
    }
}
