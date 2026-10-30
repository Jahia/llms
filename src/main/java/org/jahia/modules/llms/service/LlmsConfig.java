package org.jahia.modules.llms.service;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Limits of the llms.txt and llms-full.txt generation, read from the OSGi configuration
 * {@code org.jahia.modules.llms} ({@code karaf/etc/org.jahia.modules.llms.cfg}). The defaults below are
 * the only place the values are written; the module ships no .cfg, an operator adds one to change them.
 */
@ObjectClassDefinition(name = "llms.txt generation", description = "Limits of the llms.txt and llms-full.txt generation")
public @interface LlmsConfig {

    /**
     * @return how many levels of pages below the home page are walked
     */
    @AttributeDefinition(name = "Page depth", description = "Levels of pages below the home page that are listed")
    int maxDepth() default 3;

    /**
     * @return the most pages one file lists
     */
    @AttributeDefinition(name = "Pages", description = "Most pages one file lists")
    int maxPages() default 200;

    /**
     * @return seconds allowed to render one page for llms-full.txt
     */
    @AttributeDefinition(name = "Page timeout", description = "Seconds allowed to render one page for llms-full.txt")
    int fetchTimeoutSeconds() default 20;

    /**
     * @return pages rendered at the same time for llms-full.txt
     */
    @AttributeDefinition(name = "Parallel renders", description = "Pages rendered at the same time for llms-full.txt")
    int fetchThreads() default 4;

    /**
     * @return the largest rendered page read, in kilobytes
     */
    @AttributeDefinition(name = "Page size", description = "Largest rendered page read, in kilobytes")
    int maxPageKb() default 2048;

    /**
     * @return the largest llms-full.txt produced, in kilobytes
     */
    @AttributeDefinition(name = "llms-full.txt size", description = "Largest llms-full.txt produced, in kilobytes")
    int maxFullKb() default 4096;

    /**
     * @return where this Jahia renders pages for llms-full.txt; blank for the port the request came in on, on 127.0.0.1
     */
    @AttributeDefinition(name = "Render base URL", description = "Scheme, host and port this Jahia answers on from inside its own container (http://127.0.0.1:8080). Blank: 127.0.0.1 on the port of the request.", required = false)
    String renderBaseUrl() default "";
}
