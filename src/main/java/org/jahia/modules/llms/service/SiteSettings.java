package org.jahia.modules.llms.service;

import org.jahia.modules.llms.service.internal.SiteWalker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
    /** Written in place of a list of types to empty it for one site. */
    static final String NONE = "none";

    /** What kind of value a setting holds. */
    enum Kind { NUMBER, TYPES, FLAG }

    /**
     * One value a site may set.
     *
     * @param key  its key in the configuration
     * @param kind what it holds
     * @param min  the smallest number allowed, for a number
     * @param max  the largest number allowed, for a number
     */
    record Definition(String key, Kind kind, int min, int max) {
    }

    /** The values a site may set, in the order the panel shows them. */
    static final List<Definition> DEFINITIONS = List.of(
            new Definition("maxPages", Kind.NUMBER, 1, MAX_LISTED),
            new Definition("maxDepth", Kind.NUMBER, 1, 10),
            new Definition("maxMainResources", Kind.NUMBER, 0, MAX_LISTED),
            new Definition("includedNodeTypes", Kind.TYPES, 0, 0),
            new Definition("excludedNodeTypes", Kind.TYPES, 0, 0),
            new Definition("excludedTreeNodeTypes", Kind.TYPES, 0, 0),
            new Definition("linkToMarkdown", Kind.FLAG, 0, 0),
            new Definition("keepImages", Kind.FLAG, 0, 0));

    /**
     * Why a value is refused.
     *
     * @param code   {@code number}, {@code range}, {@code types} or {@code flag}
     * @param detail the names that are not known types, for {@code types}
     */
    record Refusal(String code, String detail) {
    }

    /**
     * Checks what an administrator typed for a site, before it is written. Nothing is corrected silently
     * here: a value that would be changed or ignored when read is refused, so what is saved is what applies.
     *
     * @param values    the values by key; a blank one means "as the module configuration says"
     * @param knownType whether a name is a node type of this Jahia
     * @return the refused values by key; empty when all can be saved
     * @throws IllegalArgumentException when a key is not one a site may set
     */
    static Map<String, Refusal> check(Map<String, String> values, Predicate<String> knownType) {
        Map<String, Refusal> refused = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Definition definition = DEFINITIONS.stream().filter(d -> d.key().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown setting"));
            String text = entry.getValue() == null ? "" : entry.getValue().trim();
            if (text.isEmpty()) {
                continue;
            }
            Refusal refusal = switch (definition.kind()) {
                case NUMBER -> checkNumber(text, definition);
                case TYPES -> checkTypes(text, knownType);
                case FLAG -> "true".equals(text) || "false".equals(text) ? null : new Refusal("flag", null);
            };
            if (refusal != null) {
                refused.put(definition.key(), refusal);
            }
        }
        return refused;
    }

    private static Refusal checkNumber(String text, Definition definition) {
        if (!text.matches("[0-9]{1,6}")) {
            return new Refusal("number", null);
        }
        int number = Integer.parseInt(text);
        return number < definition.min() || number > definition.max() ? new Refusal("range", null) : null;
    }

    private static Refusal checkTypes(String text, Predicate<String> knownType) {
        if (NONE.equals(text)) {
            return null;
        }
        List<String> unknown = new ArrayList<>();
        for (String name : text.split("[,\\s]+")) {
            if (!name.isEmpty() && (SiteWalker.typeNames(name).isEmpty() || !knownType.test(name))) {
                unknown.add(name);
            }
        }
        return unknown.isEmpty() ? null : new Refusal("types", String.join(", ", unknown));
    }

    /**
     * @param key a key of {@link #DEFINITIONS}
     * @return the value of these settings for it, written as a configuration writes it
     */
    String textOf(String key) {
        return switch (key) {
            case "maxDepth" -> String.valueOf(maxDepth);
            case "maxPages" -> String.valueOf(maxPages);
            case "maxMainResources" -> String.valueOf(maxMainResources);
            case "excludedNodeTypes" -> String.join(", ", excluded);
            case "excludedTreeNodeTypes" -> String.join(", ", excludedTrees);
            case "includedNodeTypes" -> String.join(", ", included);
            case "linkToMarkdown" -> String.valueOf(linkToMarkdown);
            case "keepImages" -> String.valueOf(keepImages);
            default -> throw new IllegalArgumentException("Unknown setting");
        };
    }

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
