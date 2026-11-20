package org.jahia.modules.llms.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What other modules rely on (geo-readiness writes {@code j:llms} on the site node and reads
 * {@code /llms.txt} back): these names and this routing are not ours alone to change.
 */
class StorageContractTest {

    private static String resource(String path) throws IOException {
        return Files.readString(Path.of("src/main/resources", path), StandardCharsets.UTF_8);
    }

    @Test
    void theMixinAndThePropertyKeepTheirNames() throws IOException {
        assertThat(LlmsService.MIXIN).isEqualTo("jmix:llms");
        assertThat(LlmsService.LLMS).isEqualTo("j:llms");
        assertThat(LlmsService.PLACEHOLDER).isEqualTo("# Title");
        assertThat(resource("META-INF/definitions.cnd"))
                .contains("[jmix:llms] mixin")
                .contains("- j:llms (string, textarea) = '# Title' autocreated");
    }

    @Test
    void theNewPropertiesAreOptional() throws IOException {
        // A module that adds the mixin and sets j:llms alone must still be able to save the site node.
        assertThat(resource("META-INF/definitions.cnd")).doesNotContain("mandatory");
    }

    @Test
    void llmsTxtIsStillRoutedToTheServlet() throws IOException {
        assertThat(resource("META-INF/last-urlrewrite-llms.xml"))
                .contains("llms\\.txt$</from>")
                .contains("<to last=\"true\">/modules/llms/llms</to>");
    }
}
