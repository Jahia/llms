package org.jahia.modules.llms.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What other modules rely on: these names, this routing and this screen are not ours alone to change.
 * geo-readiness writes {@code j:llms} on the site node and reads {@code /llms.txt} back.
 * content-governance reads {@code j:llms} in live, fetches {@code /llms.txt} and {@code /llms-full.txt},
 * tells the placeholder from a file, asks whether the module {@code llms} is enabled on the site, and
 * links to the panel.
 */
class StorageContractTest {

    private static String resource(String path) throws IOException {
        return Files.readString(Path.of("src/main/resources", path), StandardCharsets.UTF_8);
    }

    @Test
    void thePanelKeepsItsRouteAndTheModuleItsKey() throws IOException {
        // content-governance links to /jahia/jcontent/<site>/<lang>/apps/siteSettingsSeo/llms.
        assertThat(Files.readString(Path.of("src/javascript/init.js"), StandardCharsets.UTF_8))
                .contains("registry.add('adminRoute', 'siteSettingsSeo/llms'");
        // The key other modules name in Jahia-Depends and look for in a site's installed modules.
        assertThat(Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8)).contains("<artifactId>llms</artifactId>");
        assertThat(Files.readString(Path.of("src/javascript/Llms/module.js"), StandardCharsets.UTF_8))
                .contains("export const MODULE = 'llms';");
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
                .contains("/llms\\.txt$</from>")
                .contains("/sites/([A-Za-z0-9_-]+)/llms\\.txt$</from>")
                .contains("<to last=\"true\">/modules/llms/llms</to>");
    }

    @Test
    void theMarkdownVersionOfAPageIsRoutedToItsOwnServlet() throws IOException {
        // Jahia mounts a servlet at /modules/<alias>, and everything under it: /modules/llms/<anything>
        // is the servlet of llms.txt. The page servlet has its own alias for that reason.
        assertThat(resource("META-INF/last-urlrewrite-llms.xml"))
                .contains("\\.html)\\.md$</from>")
                .contains("<to last=\"true\">/modules/llmspage?path=$1</to>");
    }
}
