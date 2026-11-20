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
     * @return seconds allowed for one generation of llms-full.txt, all pages together
     */
    @AttributeDefinition(name = "Generation timeout", description = "Seconds allowed for one generation of llms-full.txt; pages not rendered in time are left out")
    int maxTotalSeconds() default 300;

    /**
     * @return node types of the pages left out of both files, separated by commas
     */
    @AttributeDefinition(name = "Excluded page types", description = "Node types (mixins) of the pages left out of llms.txt and llms-full.txt, separated by commas: as many as needed. Their sub-pages are still listed. Default: the No index option of the sitemap module, and the 'hidden in the menu' mark of the jahia.com and Academy templates. A type no installed module defines is ignored.")
    String excludedNodeTypes() default "jseomix:noIndex, jcmix:hidePage, jacademix:hidePage";

    /**
     * @return node types of the pages left out with everything below them, separated by commas
     */
    @AttributeDefinition(name = "Excluded section types", description = "Node types (mixins) of the pages left out of both files together with all their sub-pages, separated by commas. Empty by default.", required = false)
    String excludedTreeNodeTypes() default "";

    /**
     * @return whether the images of a page are written in llms-full.txt
     */
    @AttributeDefinition(name = "Keep images", description = "Write the images of a page that have a text alternative in llms-full.txt. Off: images are left out.")
    boolean keepImages() default false;

    /**
     * @return where this Jahia renders pages for llms-full.txt; blank for the port the request came in on, on 127.0.0.1
     */
    @AttributeDefinition(name = "Render base URL", description = "Scheme, host and port this Jahia answers on from inside its own container (http://127.0.0.1:8080). Blank: 127.0.0.1 on the port of the request.", required = false)
    String renderBaseUrl() default "";
}
