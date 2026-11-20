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
     * @return node types a page must carry to be listed, separated by commas; empty to list every page
     */
    @AttributeDefinition(name = "Listed page types", description = "When set, only the pages and contents that carry one of these node types (mixins) are listed in llms.txt and llms-full.txt; their sub-pages are still looked through. The module defines jmix:llmsListed for this, an option an editor sets on a page. Empty by default: every page is listed.", required = false)
    String includedNodeTypes() default "";

    /**
     * @return the most contents shown as a page of their own one file lists; 0 lists none
     */
    @AttributeDefinition(name = "Contents shown as a page", description = "Most contents shown as a page of their own (jmix:mainResource: a news item, an article) one file lists after the pages, the most recently modified first. 0 lists none.")
    int maxMainResources() default 100;

    /**
     * @return whether each page is also served as Markdown at its URL followed by .md
     */
    @AttributeDefinition(name = "Markdown version of pages", description = "Serve each published page as Markdown at its own URL followed by .md (page.html.md), as the llms.txt proposal asks.")
    boolean servePageMarkdown() default true;

    /**
     * @return whether the published pages carry the links to their Markdown version and to llms.txt
     */
    @AttributeDefinition(name = "Discovery links", description = "Write in the head of each published page a link to its Markdown version (rel=alternate, type=text/markdown) and to the llms.txt of the site (rel=describedby), as the llms.txt proposal asks.")
    boolean discoveryLinks() default true;

    /**
     * @return whether the links of a generated llms.txt point to the Markdown version of the pages
     */
    @AttributeDefinition(name = "Link llms.txt to Markdown", description = "In a generated llms.txt, link each page to its Markdown version (page.html.md) rather than to the page. Off by default: modules that compare the links of llms.txt with the pages of the site (geo-readiness, content-governance) expect the addresses of the pages.")
    boolean linkToMarkdown() default false;

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
