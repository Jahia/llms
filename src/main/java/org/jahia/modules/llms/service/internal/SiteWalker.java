package org.jahia.modules.llms.service.internal;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lists the pages llms.txt and llms-full.txt describe, in the order both files present them.
 *
 * <p>The walk runs on a node read from a {@code guest} session in {@code live}: a page a visitor cannot
 * read, or one that was never published, is not there to walk. On top of that it skips pages hidden from
 * the navigation, pages marked {@code noindex} and pages with no published translation in the language,
 * but still walks their children.</p>
 *
 * <p>Sections: the home page and the level-1 pages without sub-pages come first, under the home page's
 * title; each level-1 page with sub-pages is a section of its own; pages and menu groups beside the home
 * page close the list. Not thread-safe: one instance per walk.</p>
 */
public final class SiteWalker {

    /** One listed page. {@code renderPath} is the page's render URL on this Jahia, used to fetch it. */
    public record Page(String title, String url, String description, String renderPath) {
    }

    /** A heading of llms.txt and the pages under it. */
    public record Section(String title, List<Page> pages) {
    }

    /** The walked site. */
    public record Walk(String siteTitle, String summary, List<Section> sections, boolean truncated) {
        /**
         * @return every page of every section, in order
         */
        public List<Page> pages() {
            List<Page> out = new ArrayList<>();
            sections.forEach(s -> out.addAll(s.pages()));
            return out;
        }
    }

    /** The public URL of a page, or null to leave the page out. */
    @FunctionalInterface
    public interface UrlResolver {
        String urlOf(JCRNodeWrapper page) throws RepositoryException;
    }

    private static final String PAGE = "jnt:page";
    private static final String MENU_GROUP = "jnt:navMenuText";
    private static final int MAX_DESCRIPTION = 300;
    /** A description is cut to MAX_DESCRIPTION: nothing past this many characters of the source is read. */
    private static final int MAX_DESCRIPTION_SOURCE = 4000;
    /** Menu groups nested deeper than this are not looked through. */
    private static final int MAX_GROUP_DEPTH = 5;
    private static final Pattern WHITE_SPACE = Pattern.compile("\\s+");
    private static final Pattern TAG = Pattern.compile("<[^<>]*>");

    /** A level-1 page with the pages below it, read once. */
    private record Group(JCRNodeWrapper page, List<JCRNodeWrapper> below) {
    }

    private final Locale locale;
    private final UrlResolver urls;
    private final int maxDepth;
    private final int maxPages;
    private int listed;
    private boolean truncated;

    /**
     * @param locale   the language of the titles, descriptions and URLs
     * @param urls     resolves the public URL of a page
     * @param maxDepth levels of pages below the home page that are walked (1 lists the level-1 pages)
     * @param maxPages most pages listed
     */
    public SiteWalker(Locale locale, UrlResolver urls, int maxDepth, int maxPages) {
        this.locale = locale;
        this.urls = urls;
        this.maxDepth = Math.max(1, maxDepth);
        this.maxPages = Math.max(1, maxPages);
    }

    /**
     * @param site the site node, from a guest session in live
     * @return the pages, by section
     * @throws RepositoryException when the site cannot be read
     */
    public Walk walk(JCRSiteNode site) throws RepositoryException {
        List<Section> sections = new ArrayList<>();
        // Every list of children is a read of the repository: each one is read once and kept.
        List<JCRNodeWrapper> top = new ArrayList<>();
        site.getNodes().forEach(top::add);
        JCRNodeWrapper home = home(top);
        if (home != null) {
            List<Page> main = new ArrayList<>();
            add(main, home);
            List<Group> grouped = new ArrayList<>();
            for (JCRNodeWrapper child : pagesBelow(home)) {
                if (truncated) {
                    break;
                }
                List<JCRNodeWrapper> below = pagesBelow(child);
                if (below.isEmpty()) {
                    add(main, child);
                } else {
                    grouped.add(new Group(child, below));
                }
            }
            section(sections, title(home), main);
            for (Group group : grouped) {
                if (truncated) {
                    break;
                }
                List<Page> pages = new ArrayList<>();
                add(pages, group.page());
                descend(pages, group.below(), 2);
                section(sections, title(group.page()), pages);
            }
        }
        for (JCRNodeWrapper other : top) {
            if (truncated) {
                break;
            }
            if ((home != null && other.getPath().equals(home.getPath())) || !(isPage(other) || isGroup(other))) {
                continue;
            }
            List<Page> pages = new ArrayList<>();
            if (isPage(other)) {
                add(pages, other);
            }
            descend(pages, pagesBelow(other), 1);
            section(sections, title(other), pages);
        }
        return new Walk(title(site), oneLine(property(site, "j:description")), sections, truncated);
    }

    /**
     * @param children the pages at this depth
     * @param depth    their level below the home page
     */
    private void descend(List<Page> pages, List<JCRNodeWrapper> children, int depth) throws RepositoryException {
        // Once the page limit is reached nothing more is listed: the rest of the tree is not read.
        if (depth > maxDepth || truncated) {
            return;
        }
        for (JCRNodeWrapper child : children) {
            if (truncated) {
                return;
            }
            add(pages, child);
            // The children of a page at the last level are not listed: they are not read either.
            if (depth < maxDepth) {
                descend(pages, pagesBelow(child), depth + 1);
            }
        }
    }

    /** Pages under a node, looking through menu groups, which are not pages themselves. */
    private static List<JCRNodeWrapper> pagesBelow(JCRNodeWrapper parent) throws RepositoryException {
        List<JCRNodeWrapper> out = new ArrayList<>();
        collect(out, parent, 0);
        return out;
    }

    private static void collect(List<JCRNodeWrapper> out, JCRNodeWrapper parent, int groupDepth) throws RepositoryException {
        for (JCRNodeWrapper child : parent.getNodes()) {
            if (isPage(child)) {
                out.add(child);
            } else if (isGroup(child) && groupDepth < MAX_GROUP_DEPTH) {
                collect(out, child, groupDepth + 1);
            }
        }
    }

    private void add(List<Page> pages, JCRNodeWrapper page) throws RepositoryException {
        if (!listable(page)) {
            return;
        }
        if (listed >= maxPages) {
            truncated = true;
            return;
        }
        String url = urls.urlOf(page);
        if (url == null || url.isEmpty()) {
            return;
        }
        listed++;
        pages.add(new Page(title(page), url, oneLine(property(page, "jcr:description")), page.getUrl()));
    }

    private boolean listable(JCRNodeWrapper page) throws RepositoryException {
        if (page.isNodeType("jmix:noindex") || bool(page, "j:hideFromNavigationMenu") || !page.hasI18N(locale)) {
            return false;
        }
        if (page.hasProperty("j:invalidLanguages")) {
            for (Value v : page.getProperty("j:invalidLanguages").getValues()) {
                if (locale.toString().equals(v.getString())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void section(List<Section> sections, String title, List<Page> pages) {
        if (!pages.isEmpty()) {
            sections.add(new Section(title, pages));
        }
    }

    /** The home page: the page flagged {@code j:isHomePage}, else the page named {@code home}. */
    private static JCRNodeWrapper home(List<JCRNodeWrapper> top) throws RepositoryException {
        JCRNodeWrapper named = null;
        for (JCRNodeWrapper child : top) {
            if (isPage(child)) {
                if (bool(child, "j:isHomePage")) {
                    return child;
                }
                if ("home".equals(child.getName())) {
                    named = child;
                }
            }
        }
        return named;
    }

    private static boolean isPage(JCRNodeWrapper node) throws RepositoryException {
        return node.isNodeType(PAGE);
    }

    private static boolean isGroup(JCRNodeWrapper node) throws RepositoryException {
        return node.isNodeType(MENU_GROUP);
    }

    private static boolean bool(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) && node.getProperty(name).getBoolean();
    }

    private static String property(JCRNodeWrapper node, String name) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getString() : null;
    }

    /**
     * The node's title in the language (a site keeps its own in {@code j:title}), else its name made
     * readable ({@code landing-pages}: Landing pages).
     */
    static String title(JCRNodeWrapper node) throws RepositoryException {
        for (String name : new String[]{"jcr:title", "j:title"}) {
            String title = property(node, name);
            if (title != null && !title.isBlank()) {
                // One line: a title is written after "# " and inside a link.
                return WHITE_SPACE.matcher(title).replaceAll(" ").trim();
            }
        }
        String name = node.getName().replace('-', ' ').replace('_', ' ').trim();
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /** A Markdown list item is one line: tags dropped, white space collapsed, long text cut. */
    static String oneLine(String text) {
        if (text == null) {
            return null;
        }
        // Cut first: the tag pattern below costs the square of the length on a text made of "<".
        String source = text.length() <= MAX_DESCRIPTION_SOURCE ? text : text.substring(0, MAX_DESCRIPTION_SOURCE);
        String out = WHITE_SPACE.matcher(TAG.matcher(source).replaceAll(" ")).replaceAll(" ").trim();
        if (out.isEmpty()) {
            return null;
        }
        return out.length() <= MAX_DESCRIPTION ? out : out.substring(0, MAX_DESCRIPTION).trim() + "…";
    }
}
