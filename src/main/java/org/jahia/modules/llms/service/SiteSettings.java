package org.jahia.modules.llms.service;

import org.jahia.modules.llms.service.internal.SiteWalker;

import java.util.Map;
import java.util.Set;

/**
 * What a generation lists for one site: the values of the module configuration, with the ones a site
 * configuration ({@code org.jahia.modules.llms.site-*.cfg}) changes for that site.
 *
 * @param maxDepth         levels of pages below the home page that are listed
 * @param maxPages         most pages one file lists
 * @param maxMainResources most contents shown as a page that one file lists; 0 lists none
 * @param excluded         node types of the pages left out, their sub-pages still listed
 * @param excludedTrees    node types of the pages left out with everything below them
 * @param included         when not empty, only pages and contents of one of these types are listed
 * @param linkToMarkdown   whether llms.txt links to the Markdown version of the pages
 * @param keepImages       whether images are written in llms-full.txt
 */
record SiteSettings(int maxDepth, int maxPages, int maxMainResources, Set<String> excluded, Set<String> excludedTrees,
                    Set<String> included, boolean linkToMarkdown, boolean keepImages) {

    static final int MAX_LISTED = 2000;

    static SiteSettings of(Limits limits, LlmsConfig c) {
        return new SiteSettings(limits.maxDepth(), limits.maxPages(), clamp(c.maxMainResources(), 0, MAX_LISTED),
                SiteWalker.typeNames(c.excludedNodeTypes()), SiteWalker.typeNames(c.excludedTreeNodeTypes()),
                SiteWalker.typeNames(c.includedNodeTypes()), c.linkToMarkdown(), c.keepImages());
    }

    /**
     * These settings with what a site configuration says. A key that is absent or blank changes nothing;
     * a number out of range is brought back into it, one that is not a number is ignored. A list of types
     * with no type name in it ({@code none}) is an empty list: that is how a site drops the exclusions.
     *
     * @param site the properties of the site configuration
     * @return the settings of that site
     */
    SiteSettings with(Map<String, ?> site) {
        return new SiteSettings(
                number(site, "maxDepth", maxDepth, 1, 10),
                number(site, "maxPages", maxPages, 1, MAX_LISTED),
                number(site, "maxMainResources", maxMainResources, 0, MAX_LISTED),
                types(site, "excludedNodeTypes", excluded),
                types(site, "excludedTreeNodeTypes", excludedTrees),
                types(site, "includedNodeTypes", included),
                flag(site, "linkToMarkdown", linkToMarkdown),
                flag(site, "keepImages", keepImages));
    }

    private static String text(Map<String, ?> site, String key) {
        Object value = site.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static int number(Map<String, ?> site, String key, int current, int min, int max) {
        String text = text(site, key);
        if (text == null) {
            return current;
        }
        try {
            return clamp(Integer.parseInt(text), min, max);
        } catch (NumberFormatException e) {
            return current;
        }
    }

    private static Set<String> types(Map<String, ?> site, String key, Set<String> current) {
        String text = text(site, key);
        return text == null ? current : SiteWalker.typeNames(text);
    }

    private static boolean flag(Map<String, ?> site, String key, boolean current) {
        String text = text(site, key);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        return !"false".equalsIgnoreCase(text) && current;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
