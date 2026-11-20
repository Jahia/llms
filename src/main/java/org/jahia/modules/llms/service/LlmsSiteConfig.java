package org.jahia.modules.llms.service;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * What one site lists differently from the module configuration: a factory configuration
 * {@code org.jahia.modules.llms.site} ({@code karaf/etc/org.jahia.modules.llms.site-<name>.cfg}), one per
 * site. Every value but the site key is optional: left blank, the value of the module configuration applies.
 */
@ObjectClassDefinition(name = "llms.txt generation of one site",
        description = "Values of the llms.txt generation that differ for one site. A blank value keeps the one of the module configuration.")
public @interface LlmsSiteConfig {

    /**
     * @return the key of the site these values are for
     */
    @AttributeDefinition(name = "Site key", description = "Key of the site these values are for")
    String siteKey();

    /**
     * @return levels of pages below the home page that are listed, or blank
     */
    @AttributeDefinition(name = "Page depth", description = "Levels of pages below the home page that are listed (1 to 10)", required = false)
    String maxDepth();

    /**
     * @return the most pages one file lists, or blank
     */
    @AttributeDefinition(name = "Pages", description = "Most pages one file lists", required = false)
    String maxPages();

    /**
     * @return the most contents shown as a page one file lists, or blank
     */
    @AttributeDefinition(name = "Contents shown as a page", description = "Most contents shown as a page of their own (jmix:mainResource) one file lists; 0 lists none", required = false)
    String maxMainResources();

    /**
     * @return node types of the pages left out, or blank
     */
    @AttributeDefinition(name = "Excluded page types", description = "Node types (mixins) of the pages left out, separated by commas; 'none' leaves none out", required = false)
    String excludedNodeTypes();

    /**
     * @return node types of the pages left out with everything below them, or blank
     */
    @AttributeDefinition(name = "Excluded section types", description = "Node types (mixins) of the pages left out with all their sub-pages, separated by commas; 'none' leaves none out", required = false)
    String excludedTreeNodeTypes();

    /**
     * @return node types a page must carry to be listed, or blank
     */
    @AttributeDefinition(name = "Listed page types", description = "When set, only the pages and contents that carry one of these node types (mixins) are listed, for example jmix:llmsListed; 'none' lists every page", required = false)
    String includedNodeTypes();

    /**
     * @return true or false, or blank
     */
    @AttributeDefinition(name = "Link llms.txt to Markdown", description = "true or false: link each page of a generated llms.txt to its Markdown version", required = false)
    String linkToMarkdown();

    /**
     * @return true or false, or blank
     */
    @AttributeDefinition(name = "Keep images", description = "true or false: write the images of a page in llms-full.txt", required = false)
    String keepImages();
}
