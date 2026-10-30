package org.jahia.modules.llms.service.internal;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        JCRNodeWrapper home = home(site);
        if (home != null) {
            List<Page> main = new ArrayList<>();
            add(main, home);
            List<JCRNodeWrapper> grouped = new ArrayList<>();
            for (JCRNodeWrapper child : pagesBelow(home)) {
                if (pagesBelow(child).isEmpty()) {
                    add(main, child);
                } else {
                    grouped.add(child);
                }
            }
            section(sections, title(home), main);
            for (JCRNodeWrapper group : grouped) {
                List<Page> pages = new ArrayList<>();
                add(pages, group);
                descend(pages, group, 2);
                section(sections, title(group), pages);
            }
        }
        for (JCRNodeWrapper other : site.getNodes()) {
            if ((home != null && other.getPath().equals(home.getPath())) || !(isPage(other) || isGroup(other))) {
                continue;
            }
            List<Page> pages = new ArrayList<>();
            if (isPage(other)) {
                add(pages, other);
            }
            descend(pages, other, 1);
            section(sections, title(other), pages);
        }
        return new Walk(title(site), oneLine(property(site, "j:description")), sections, truncated);
    }

    private void descend(List<Page> pages, JCRNodeWrapper parent, int depth) throws RepositoryException {
        if (depth > maxDepth) {
            return;
        }
        for (JCRNodeWrapper child : pagesBelow(parent)) {
            add(pages, child);
            descend(pages, child, depth + 1);
        }
    }

    /** Pages under a node, looking through menu groups, which are not pages themselves. */
    private static List<JCRNodeWrapper> pagesBelow(JCRNodeWrapper parent) throws RepositoryException {
        List<JCRNodeWrapper> out = new ArrayList<>();
        for (JCRNodeWrapper child : parent.getNodes()) {
            if (isPage(child)) {
                out.add(child);
            } else if (isGroup(child)) {
                out.addAll(pagesBelow(child));
            }
        }
        return out;
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
    private static JCRNodeWrapper home(JCRSiteNode site) throws RepositoryException {
        JCRNodeWrapper named = null;
        for (JCRNodeWrapper child : site.getNodes()) {
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
                return title.trim();
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
        String out = text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (out.isEmpty()) {
            return null;
        }
        return out.length() <= MAX_DESCRIPTION ? out : out.substring(0, MAX_DESCRIPTION).trim() + "…";
    }
}
