package org.jahia.modules.llms.service.internal;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>When the site has more pages than the file may list, the pages nearest to the home page are the ones
 * kept: every page of a level before any page of the next. The limit then cuts the deepest level reached,
 * not the last sections of the site.</p>
 *
 * <p>After the pages come the contents that are shown as a page of their own ({@code jmix:mainResource}:
 * a news item, an article), the most recent first, under the title of the page or folder that holds them.
 * They are not in the page tree: a {@link ContentFinder} finds them.</p>
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

    /** Finds the nodes of a type below a node, the most recently modified first, a batch at a time. */
    @FunctionalInterface
    public interface ContentFinder {
        /**
         * @param root   the site, or the page at the top of a section
         * @param type   the node type to look for
         * @param offset how many nodes to pass over
         * @param limit  the most nodes to return
         * @return the nodes, as the session of {@code root} reads them; fewer than the limit at the end
         * @throws RepositoryException when the repository cannot be searched
         */
        List<JCRNodeWrapper> find(JCRNodeWrapper root, String type, int offset, int limit) throws RepositoryException;
    }

    /** A content that is shown as a page of its own. */
    public static final String MAIN_RESOURCE = "jmix:mainResource";
    /** Batches read to fill the list of contents: what is found is filtered, so one batch may not do. */
    private static final int MAX_CONTENT_BATCHES = 10;

    private static final String PAGE = "jnt:page";
    private static final String CONTENT_FOLDER = "jnt:contentFolder";
    private static final String MENU_GROUP = "jnt:navMenuText";
    /**
     * The "No index" option of a page, from the sitemap module: the page asks search engines not to index
     * it, and is left out of the sitemap. It is left out of these files for the same reason. Without that
     * module the type does not exist and no page carries it. The default of the configuration.
     */
    public static final String NO_INDEX = "jseomix:noIndex";
    /**
     * What is left out when nothing is configured: No index, and the "hidden in the menu" mark of two
     * template sets (jahia.com, Academy), which say of a page what j:hideFromNavigationMenu says.
     */
    public static final String DEFAULT_EXCLUDED = NO_INDEX + ", jcmix:hidePage, jacademix:hidePage";
    private static final Pattern TYPE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*:[A-Za-z][A-Za-z0-9_.-]*$");
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
    /** The pages the limit lets in, chosen level by level before the walk; null before that choice. */
    private Set<String> admitted;
    /** Whether that choice left pages out. */
    private boolean cut;
    /** The deepest level that choice took a page from: the walk reads no deeper. */
    private int reach;
    private final Set<String> excluded;
    private final Set<String> excludedTrees;
    private final Set<String> included;
    private final int maxContents;
    private final ContentFinder finder;

    /**
     * @param locale   the language of the titles, descriptions and URLs
     * @param urls     resolves the public URL of a page
     * @param maxDepth levels of pages below the home page that are walked (1 lists the level-1 pages)
     * @param maxPages most pages listed
     */
    public SiteWalker(Locale locale, UrlResolver urls, int maxDepth, int maxPages) {
        this(locale, urls, maxDepth, maxPages, typeNames(DEFAULT_EXCLUDED), Set.of());
    }

    /**
     * @param locale        the language of the titles, descriptions and URLs
     * @param urls          resolves the public URL of a page
     * @param maxDepth      levels of pages below the home page that are walked (1 lists the level-1 pages)
     * @param maxPages      most pages listed
     * @param excluded      node types (mixins) of a page that is left out; its sub-pages are still listed
     * @param excludedTrees node types (mixins) of a page that is left out with everything below it
     */
    public SiteWalker(Locale locale, UrlResolver urls, int maxDepth, int maxPages, Set<String> excluded, Set<String> excludedTrees) {
        this(locale, urls, maxDepth, maxPages, excluded, excludedTrees, Set.of(), 0, null);
    }

    /**
     * @param locale        the language of the titles, descriptions and URLs
     * @param urls          resolves the public URL of a page
     * @param maxDepth      levels of pages below the home page that are walked (1 lists the level-1 pages)
     * @param maxPages      most pages listed
     * @param excluded      node types (mixins) of a page that is left out; its sub-pages are still listed
     * @param excludedTrees node types (mixins) of a page that is left out with everything below it
     * @param included      when not empty, only the pages and contents of one of these types are listed
     * @param maxContents   most contents shown as a page that are listed; 0 lists none
     * @param finder        finds those contents, or null to list none
     */
    @SuppressWarnings("java:S107")
    public SiteWalker(Locale locale, UrlResolver urls, int maxDepth, int maxPages, Set<String> excluded, Set<String> excludedTrees,
                      Set<String> included, int maxContents, ContentFinder finder) {
        this.included = Set.copyOf(included);
        this.maxContents = Math.max(0, maxContents);
        this.finder = finder;
        this.locale = locale;
        this.urls = urls;
        this.maxDepth = Math.max(1, maxDepth);
        this.maxPages = Math.max(1, maxPages);
        this.excluded = Set.copyOf(excluded);
        this.excludedTrees = Set.copyOf(excludedTrees);
    }

    /**
     * Reads a list of node type names from the configuration: separated by commas or spaces, anything
     * that is not a prefixed name ({@code jseomix:noIndex}) is dropped.
     *
     * @param configured the configured value, may be null
     * @return the names, in order, without duplicates
     */
    public static Set<String> typeNames(String configured) {
        Set<String> names = new LinkedHashSet<>();
        if (configured != null) {
            for (String name : configured.split("[,\\s]+")) {
                if (TYPE_NAME.matcher(name).matches()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /** Whether a node is of one of the types. A type the repository does not know is carried by no node. */
    private static boolean hasAny(JCRNodeWrapper node, Set<String> types) {
        for (String type : types) {
            try {
                if (node.isNodeType(type)) {
                    return true;
                }
            } catch (RepositoryException e) {
                // an unknown type: the module that defines it is not installed
            }
        }
        return false;
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
        for (JCRNodeWrapper child : site.getNodes()) {
            if (!hasAny(child, excludedTrees)) {
                top.add(child);
            }
        }
        JCRNodeWrapper home = home(top);
        List<JCRNodeWrapper> start = new ArrayList<>();
        for (JCRNodeWrapper node : top) {
            if (isPage(node) || isGroup(node)) {
                start.add(node);
            }
        }
        choose(start);
        if (home != null) {
            below(sections, home);
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
        contents(sections, site);
        return new Walk(title(site), oneLine(property(site, "j:description")), sections, truncated || cut);
    }

    /**
     * Lists a section of the site: one page and everything below it, laid out as the site is from its
     * home page. This is what an llms.txt placed under that page covers.
     *
     * @param sectionPage the page at the top of the section, from a guest session in live
     * @return the pages, by section, under the title of that page
     * @throws RepositoryException when the page cannot be read
     */
    public Walk walkSection(JCRNodeWrapper sectionPage) throws RepositoryException {
        List<Section> sections = new ArrayList<>();
        choose(List.of(sectionPage));
        below(sections, sectionPage);
        contents(sections, sectionPage);
        return new Walk(title(sectionPage), oneLine(property(sectionPage, "jcr:description")), sections, truncated || cut);
    }

    /**
     * Chooses the pages the limit lets in, one level at a time from the top: all the pages of a level are
     * in before the first page of the next. Reads no deeper than the level where the limit is reached.
     *
     * @param start the nodes at the top of the walk: their pages are at level 0
     */
    private void choose(List<JCRNodeWrapper> start) throws RepositoryException {
        Set<String> chosen = new HashSet<>();
        List<JCRNodeWrapper> level = start;
        for (int depth = 0; depth <= maxDepth && !level.isEmpty() && !cut; depth++) {
            for (JCRNodeWrapper node : level) {
                if (isPage(node) && listable(node)) {
                    if (chosen.size() >= maxPages) {
                        cut = true;
                        break;
                    }
                    chosen.add(node.getPath());
                    reach = depth;
                }
            }
            List<JCRNodeWrapper> next = new ArrayList<>();
            if (!cut && depth < maxDepth) {
                for (JCRNodeWrapper node : level) {
                    next.addAll(pagesBelow(node));
                }
            }
            level = next;
        }
        admitted = chosen;
    }

    /**
     * A page and what is below it: the page and its sub-pages without sub-pages come first, under its
     * title; each sub-page with sub-pages is a section of its own.
     */
    private void below(List<Section> sections, JCRNodeWrapper root) throws RepositoryException {
        List<Page> main = new ArrayList<>();
        add(main, root);
        List<Group> grouped = new ArrayList<>();
        for (JCRNodeWrapper child : pagesBelow(root)) {
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
        section(sections, title(root), main);
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

    /**
     * @param children the pages at this depth
     * @param depth    their level below the home page
     */
    private void descend(List<Page> pages, List<JCRNodeWrapper> children, int depth) throws RepositoryException {
        // No page was chosen below the level the limit reached: the rest of the tree is not read.
        if (depth > maxDepth || depth > reach || truncated) {
            return;
        }
        for (JCRNodeWrapper child : children) {
            if (truncated) {
                return;
            }
            add(pages, child);
            // The children of a page at the last level are not listed: they are not read either.
            if (depth < maxDepth && depth < reach) {
                descend(pages, pagesBelow(child), depth + 1);
            }
        }
    }

    /** Pages under a node, looking through menu groups, which are not pages themselves. */
    private List<JCRNodeWrapper> pagesBelow(JCRNodeWrapper parent) throws RepositoryException {
        List<JCRNodeWrapper> out = new ArrayList<>();
        collect(out, parent, 0);
        return out;
    }

    private void collect(List<JCRNodeWrapper> out, JCRNodeWrapper parent, int groupDepth) throws RepositoryException {
        for (JCRNodeWrapper child : parent.getNodes()) {
            if (hasAny(child, excludedTrees)) {
                // The page, or the menu group, and everything below it: not read at all.
                continue;
            }
            if (isPage(child)) {
                out.add(child);
            } else if (isGroup(child) && groupDepth < MAX_GROUP_DEPTH) {
                collect(out, child, groupDepth + 1);
            }
        }
    }

    private void add(List<Page> pages, JCRNodeWrapper page) throws RepositoryException {
        if (!listable(page) || (admitted != null && !admitted.contains(page.getPath()))) {
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

    /**
     * The contents shown as a page of their own below a node, the most recent first, each under the title
     * of the nearest page or content folder above it. A content has no place in the navigation: it is
     * left out by its type, by its language, or because a page above it is left out with all below it.
     */
    private void contents(List<Section> sections, JCRNodeWrapper root) throws RepositoryException {
        if (finder == null || maxContents == 0) {
            return;
        }
        Map<String, Section> byHolder = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        int batch = maxContents * 2 + 20;
        // When only marked contents are listed, the marks are what is looked for: marked contents are
        // few among many, and would not be among the most recent of all.
        for (String type : included.isEmpty() ? Set.of(MAIN_RESOURCE) : included) {
            for (int round = 0; round < MAX_CONTENT_BATCHES && seen.size() < maxContents; round++) {
                List<JCRNodeWrapper> found = finder.find(root, type, round * batch, batch);
                for (JCRNodeWrapper node : found) {
                    if (seen.size() >= maxContents) {
                        truncated = true;
                        break;
                    }
                    content(byHolder, seen, node, root);
                }
                if (found.size() < batch) {
                    break;
                }
            }
        }
        sections.addAll(byHolder.values());
    }

    /** Lists one content, when it is one to list. A content that cannot be read up to the root is passed over. */
    private void content(Map<String, Section> byHolder, Set<String> seen, JCRNodeWrapper node, JCRNodeWrapper root) {
        try {
            if (seen.contains(node.getPath()) || isPage(node) || !node.isNodeType(MAIN_RESOURCE) || !listableContent(node)
                    || isBelowExcludedTree(node, root)) {
                return;
            }
            String url = urls.urlOf(node);
            if (url == null || url.isEmpty()) {
                return;
            }
            JCRNodeWrapper holder = holder(node, root);
            Section section = byHolder.get(holder.getPath());
            if (section == null) {
                section = new Section(title(holder), new ArrayList<>());
                byHolder.put(holder.getPath(), section);
            }
            section.pages().add(new Page(title(node), url, oneLine(property(node, "jcr:description")), node.getUrl()));
            seen.add(node.getPath());
        } catch (RepositoryException e) {
            // A node above it that a visitor cannot read, or a node gone since it was found: not listed.
        }
    }

    /** The nearest page or content folder above a content, no higher than the root of the walk. */
    private static JCRNodeWrapper holder(JCRNodeWrapper node, JCRNodeWrapper root) throws RepositoryException {
        JCRNodeWrapper above = node.getParent();
        while (!above.getPath().equals(root.getPath()) && above.getDepth() > root.getDepth()
                && !above.isNodeType(PAGE) && !above.isNodeType(CONTENT_FOLDER)) {
            above = above.getParent();
        }
        return above;
    }

    private boolean isBelowExcludedTree(JCRNodeWrapper node, JCRNodeWrapper root) throws RepositoryException {
        if (excludedTrees.isEmpty()) {
            return false;
        }
        JCRNodeWrapper above = node.getParent();
        while (above.getDepth() > root.getDepth()) {
            if (hasAny(above, excludedTrees)) {
                return true;
            }
            above = above.getParent();
        }
        return false;
    }

    /** A content with no text in any language (a product with a reference and a price) has no translation to miss. */
    private boolean listableContent(JCRNodeWrapper node) throws RepositoryException {
        if (hasAny(node, excluded) || !marked(node) || (!node.getExistingLocales().isEmpty() && !node.hasI18N(locale))) {
            return false;
        }
        return validIn(node);
    }

    /** When only marked pages are listed: whether this one carries one of the marks. */
    private boolean marked(JCRNodeWrapper node) {
        return included.isEmpty() || hasAny(node, included);
    }

    private boolean listable(JCRNodeWrapper page) throws RepositoryException {
        if (hasAny(page, excluded) || !marked(page) || bool(page, "j:hideFromNavigationMenu") || !page.hasI18N(locale)) {
            return false;
        }
        return validIn(page);
    }

    private boolean validIn(JCRNodeWrapper page) throws RepositoryException {
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
    public static String oneLine(String text) {
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
