package org.jahia.modules.llms.service.internal;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Turns the main content of a rendered page into Markdown for llms-full.txt.
 *
 * <p>Only what a reader needs survives: headings, paragraphs, lists, quotes, code, tables and links.
 * Images are left out unless asked for; kept, only those with a text alternative are written. Scripts, styles, form controls, dialogs and the page chrome
 * (header, navigation, footer) are dropped. Hidden elements are dropped too, except the panels a visitor
 * can open (tabs, accordions, slides). The content is the whole body without its chrome; a usual place
 * ({@code <main>}, a single {@code <article>}, {@code #content}) replaces it only when it holds nearly
 * all the text, so content beside {@code <main>} is kept. Stateless and thread-safe.</p>
 */
public final class HtmlToMarkdown {

    /**
     * Never content. A form is not in the list: sites wrap whole pages in one, so only its controls and
     * their labels go.
     * Hidden elements are dropped after {@link #revealDisclosed} kept the ones a visitor can open.
     */
    private static final String DROPPED = "script, style, noscript, template, svg, object, embed, canvas, "
            + "button, input, select, textarea, label, dialog, [role=dialog], [role=alertdialog], "
            + "[hidden], [aria-hidden=true], .sr-only, .visually-hidden";
    private static final String HIDDEN = "[hidden], [aria-hidden=true], [style~=(?i)display\\s*:\\s*none]";
    private static final String NEVER_REVEALED = "dialog, [role=dialog], [role=alertdialog], .modal";
    private static final String HEADINGS = "h1, h2, h3, h4, h5, h6, summary";
    /** Where the main content of a page usually is, most specific first (the order url2md settled on). */
    private static final String[] CONTENT_ROOTS = {"main, [role=main]", "article", "#content", "#main", ".content", ".main"};
    /**
     * A usual place is taken as the content only when it holds nearly all the text of the page. Sites put
     * content beside their {@code <main>} (an intro above it, sections after it): below this share, the
     * whole body is read, without its chrome, rather than drop what sits outside.
     */
    private static final double WHOLE_CONTENT_SHARE = 0.9;
    /**
     * Chrome a site marks with a class or an id instead of an element or a role, as templates written
     * before HTML5 do ({@code header-v8}, {@code blog-topbar}, {@code footer-v1}, {@code navbar}). The
     * name starts the class: {@code card-header} and {@code page-header} are parts of the content.
     */
    private static final Pattern CHROME_NAME = Pattern.compile(
            "^(?:site-|global-|blog-|main-|top-)?(?:header|footer|navbar|topbar|masthead)(?:[-_].*)?$");
    private static final Set<String> CHROME_NAMES = Set.of("nav", "menu", "copyright", "skip-link", "skip-links",
            "skiplinks", "cookies", "cookie-banner");
    /**
     * A block named like chrome that holds nearly all the text of the page is a wrapper around the page
     * ({@code <div class="header-fixed">}), not chrome. The bar is high on purpose: on a short page the
     * footer alone is most of the text.
     */
    private static final double WRAPPER_SHARE = 0.9;
    /** Breadcrumbs repeat what llms.txt lists. Other menus inside main stay: a site map page is one. */
    private static final String BREADCRUMB = "[class*=breadcrumb], [aria-label~=(?i)breadcrumb|ariane|brotkr]";
    private static final String CHROME = "header, footer, nav, aside, [role=navigation], [role=banner], "
            + "[role=contentinfo], [role=complementary], [role=search]";
    /** Compiled once: the converter runs these on every fragment of text of every page. */
    private static final Pattern BLANK_LINES = Pattern.compile("\n{3,}");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\r\\f]+");
    /**
     * A text that is nothing but a tag left open ({@code <ul}, {@code </div}): what a broken rich text leaves
     * on a page, where the visitor reads it too. No sentence is that, so it is not written. A text that
     * speaks of a tag ({@code use <ul> for a list}) is more than that, and stays.
     */
    private static final Pattern TAG_DEBRIS = Pattern.compile("\\s*</?[A-Za-z][A-Za-z0-9-]*\\s*");
    private static final String SENTENCE_END = ".:!?";
    private static final Set<String> LINK_SCHEMES = Set.of("http", "https", "mailto", "tel");
    private static final Set<String> IMAGE_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKS = Set.of("address", "article", "aside", "blockquote", "dd", "details",
            "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6",
            "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "summary", "table", "tbody", "td",
            "tfoot", "th", "thead", "tr", "ul");

    private HtmlToMarkdown() {
    }

    /**
     * @param page      the rendered page, parsed with its public URL as base URI
     * @param title     the page title, written by the caller: a first {@code h1} with the same text is left out
     * @param linkMap   maps an absolute link of the page to the URL to write (the public URL of a listed page)
     * @return the Markdown of the page's main content, without a trailing new line
     */
    public static String convert(Document page, String title, UnaryOperator<String> linkMap) {
        return convert(page, title, linkMap, false);
    }

    /**
     * @param page       the rendered page, parsed with its public URL as base URI
     * @param title      the page title, written by the caller: a first {@code h1} with the same text is left out
     * @param linkMap    maps an absolute link of the page to the URL to write (the public URL of a listed page)
     * @param keepImages false to leave images out: a reader of text gains nothing from an address it cannot
     *                   open, and logos, icons and decorations are most of the images of a page
     * @return the Markdown of the page's main content, without a trailing new line
     */
    public static String convert(Document page, String title, UnaryOperator<String> linkMap, boolean keepImages) {
        Element body = page.body();
        revealDisclosed(body);
        // The title of an accordion is a button inside a heading: its text is the heading.
        for (Element heading : body.select(HEADINGS)) {
            heading.select("button").unwrap();
        }
        page.select(DROPPED).remove();
        Element main = mainContent(body);
        main.select(BREADCRUMB).remove();
        Writer writer = new Writer(title, linkMap, false, keepImages);
        writer.blocks(main, 0);
        return writer.result();
    }

    /**
     * Keeps what a visitor can open: the panel of a tab, of an accordion or of a carousel is hidden in the
     * HTML until a control shows it, and it is content all the same. A hidden element is kept when it is
     * a tab panel or a slide, or when it, or an element around it, is the target of a control
     * ({@code aria-controls}, Bootstrap's {@code data-bs-target}). Everything else hidden is dropped:
     * decorative icons, templates, off-screen duplicates. Dialogs are never kept: a visitor reads a page,
     * and a dialog is mostly a form or a consent banner.
     */
    private static void revealDisclosed(Element body) {
        Map<String, Element> controls = new HashMap<>();
        for (Element control : body.select("[aria-controls], [data-bs-target], [data-target], a[href^=#][data-toggle], a[href^=#][data-bs-toggle]")) {
            for (String attribute : new String[]{"aria-controls", "data-bs-target", "data-target", "href"}) {
                for (String id : control.attr(attribute).trim().split("\\s+")) {
                    String target = id.startsWith("#") ? id.substring(1) : id;
                    if (!target.isEmpty()) {
                        controls.putIfAbsent(target, control);
                    }
                }
            }
        }
        for (Element hidden : body.select(HIDDEN)) {
            if (hidden.closest(NEVER_REVEALED) != null) {
                continue;
            }
            if (isDisclosed(hidden, controls)) {
                hidden.removeAttr("hidden").removeAttr("aria-hidden").removeAttr("style");
            } else if (hidden.hasAttr("style") && !hidden.hasAttr("hidden") && !"true".equals(hidden.attr("aria-hidden"))) {
                // display:none in a style attribute, opened by nothing: dropped here, DROPPED does not see it.
                hidden.remove();
            }
        }
        // The name of a tab is on its control, away from the panel: written at the top of the panel.
        for (Map.Entry<String, Element> entry : controls.entrySet()) {
            Element control = entry.getValue();
            Element panel = body.getElementById(entry.getKey());
            String label = control.text().trim();
            if (panel != null && !label.isEmpty() && control.closest(HEADINGS) == null && control.closest("nav, [role=navigation]") == null
                    && panel.closest(NEVER_REVEALED) == null && ("tab".equals(control.attr("role")) || "tabpanel".equals(panel.attr("role")))) {
                panel.prependElement("p").appendElement("strong").text(label);
            }
        }
    }

    private static boolean isDisclosed(Element hidden, Map<String, Element> controls) {
        if ("tabpanel".equals(hidden.attr("role")) || "slide".equals(hidden.attr("aria-roledescription"))) {
            return true;
        }
        for (Element el = hidden; el != null; el = el.parent()) {
            if (!el.id().isEmpty() && controls.containsKey(el.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The element that holds the content of the page. A usual place ({@code <main>}, a single article,
     * {@code #content}) is only a hint: it is taken when it holds nearly all the text, which spares the
     * reader what little surrounds it. As soon as a real share of the text sits outside it, the body is
     * read whole, without its chrome: content beside {@code <main>} is content.
     */
    private static Element mainContent(Element body) {
        Element withoutChrome = body.clone();
        removeChrome(withoutChrome);
        int all = withoutChrome.text().length();
        for (String selector : CONTENT_ROOTS) {
            List<Element> found = body.select(selector);
            // Several articles are a list of teasers, not the content.
            if (found.isEmpty() || ("article".equals(selector) && found.size() != 1)) {
                continue;
            }
            if (all > 0 && found.get(0).text().length() >= all * WHOLE_CONTENT_SHARE) {
                return found.get(0);
            }
        }
        removeChrome(body);
        return body;
    }

    /**
     * Drops the page chrome, and only outside the content: an article has its own header (its title and
     * lead), its own asides (notes), and a page can be a menu (a site map). Chrome is what HTML or ARIA
     * says is chrome, and what the template only names so.
     */
    private static void removeChrome(Element root) {
        int total = root.text().length();
        for (Element el : root.select(CHROME)) {
            String name = el.normalName();
            boolean sectioning = "header".equals(name) || "footer".equals(name) || "aside".equals(name);
            boolean ofContent = el.parent() != null && (sectioning && !el.hasAttr("role")
                    ? el.parent().closest("article, section, main, [role=main]") != null
                    : el.parent().closest("article, main, [role=main]") != null);
            if (!ofContent) {
                el.remove();
            }
        }
        for (Element el : root.select("[class], [id]")) {
            if (el != root && el.parent() != null && isNamedChrome(el) && el.closest("article, main, [role=main]") == null
                    && el.text().length() < total * WRAPPER_SHARE) {
                el.remove();
            }
        }
    }

    private static boolean isNamedChrome(Element el) {
        if (isChromeName(el.id())) {
            return true;
        }
        for (String name : el.classNames()) {
            if (isChromeName(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChromeName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return !n.isEmpty() && (CHROME_NAMES.contains(n) || CHROME_NAME.matcher(n).matches());
    }

    /** One conversion: the blocks written so far and whether the page title was already dropped. */
    private static final class Writer {
        private final String title;
        private final UnaryOperator<String> linkMap;
        /** Inside a list item or a table cell: everything ends up on one line, headings included. */
        private final boolean flat;
        private final boolean keepImages;
        private final List<String> out = new ArrayList<>();
        private boolean titleSeen;

        Writer(String title, UnaryOperator<String> linkMap, boolean flat, boolean keepImages) {
            this.keepImages = keepImages;
            this.title = title == null ? "" : title.trim();
            this.linkMap = linkMap;
            this.flat = flat;
            this.titleSeen = flat;
        }

        String result() {
            return BLANK_LINES.matcher(String.join("\n\n", out)).replaceAll("\n\n").trim();
        }

        /** Writes the children of a container, gathering consecutive inline content into paragraphs. */
        void blocks(Element container, int listDepth) {
            StringBuilder inline = new StringBuilder();
            for (Node child : container.childNodes()) {
                if (child instanceof Element el && BLOCKS.contains(el.normalName())) {
                    flush(inline);
                    block(el, listDepth);
                } else {
                    inline.append(inline(child));
                }
            }
            flush(inline);
        }

        private void flush(StringBuilder inline) {
            String text = tidy(inline.toString());
            if (!text.isEmpty()) {
                out.add(text);
            }
            inline.setLength(0);
        }

        private void block(Element el, int listDepth) {
            String name = el.normalName();
            switch (name) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> heading(el, name.charAt(1) - '0');
                case "p", "summary", "figcaption" -> add(tidy(inlineOf(el)));
                case "dt" -> add(wrap(tidy(inlineOf(el)), "**"));
                case "ul", "ol" -> add(list(el, listDepth));
                case "blockquote" -> add(quote(el));
                case "pre" -> add("```\n" + el.wholeText().stripTrailing() + "\n```");
                case "table" -> add(table(el));
                case "hr" -> add("---");
                default -> blocks(el, listDepth);
            }
        }

        private void add(String block) {
            if (block != null && !block.isBlank()) {
                out.add(block);
            }
        }

        /** The caller writes the title as the page's {@code #}: every heading of the content sits below it. */
        private void heading(Element el, int level) {
            String text = tidy(inlineOf(el));
            if (text.isEmpty()) {
                return;
            }
            if (level == 1 && !titleSeen && text.equalsIgnoreCase(title)) {
                titleSeen = true;
                return;
            }
            if (flat) {
                // A card title in a list item: "[Title](url): the text that follows".
                out.add(SENTENCE_END.indexOf(text.charAt(text.length() - 1)) >= 0 ? text : text + ":");
                return;
            }
            out.add("#".repeat(Math.max(2, level)) + " " + text);
        }

        private String list(Element list, int depth) {
            boolean ordered = "ol".equals(list.normalName());
            String indent = "  ".repeat(depth);
            StringBuilder sb = new StringBuilder();
            int index = 1;
            for (Element item : list.children()) {
                if (!"li".equals(item.normalName())) {
                    continue;
                }
                StringBuilder text = new StringBuilder();
                List<String> nested = new ArrayList<>();
                for (Node child : item.childNodes()) {
                    if (child instanceof Element el && ("ul".equals(el.normalName()) || "ol".equals(el.normalName()))) {
                        nested.add(list(el, depth + 1));
                    } else if (child instanceof Element el && BLOCKS.contains(el.normalName())) {
                        text.append(' ').append(textOfBlock(el));
                    } else {
                        text.append(inline(child));
                    }
                }
                String line = tidy(text.toString());
                if (line.isEmpty() && nested.isEmpty()) {
                    continue;
                }
                sb.append(indent).append(ordered ? index++ + ". " : "- ").append(line).append('\n');
                nested.forEach(n -> sb.append(n).append('\n'));
            }
            return sb.toString().stripTrailing();
        }

        /** A block inside a list item or a table cell, flattened to one line. */
        private String textOfBlock(Element el) {
            Writer inner = new Writer("", linkMap, true, keepImages);
            inner.blocks(el, 0);
            return oneLine(inner.result());
        }

        private String quote(Element el) {
            Writer inner = new Writer("", linkMap, false, keepImages);
            inner.titleSeen = true;
            inner.blocks(el, 0);
            String text = inner.result();
            return text.isEmpty() ? "" : "> " + text.replace("\n", "\n> ");
        }

        private String table(Element table) {
            List<List<String>> rows = new ArrayList<>();
            int columns = 0;
            for (Element row : table.select("tr")) {
                if (row.closest("table") != table) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (Element cell : row.children()) {
                    if ("td".equals(cell.normalName()) || "th".equals(cell.normalName())) {
                        cells.add(textOfBlock(cell).replace("|", "\\|"));
                    }
                }
                if (!cells.isEmpty()) {
                    rows.add(cells);
                    columns = Math.max(columns, cells.size());
                }
            }
            if (rows.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int r = 0; r < rows.size(); r++) {
                List<String> cells = rows.get(r);
                while (cells.size() < columns) {
                    cells.add("");
                }
                sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
                if (r == 0) {
                    sb.append("|").append(" --- |".repeat(columns)).append('\n');
                }
            }
            return sb.toString().trim();
        }

        private String inlineOf(Element el) {
            StringBuilder sb = new StringBuilder();
            for (Node child : el.childNodes()) {
                sb.append(child instanceof Element e && BLOCKS.contains(e.normalName()) ? " " + textOfBlock(e) + " " : inline(child));
            }
            return sb.toString();
        }

        private String inline(Node node) {
            if (node instanceof TextNode text) {
                return TAG_DEBRIS.matcher(text.text()).matches() ? "" : text.text();
            }
            if (!(node instanceof Element el)) {
                return "";
            }
            String name = el.normalName();
            return switch (name) {
                case "br" -> "\n";
                case "a" -> link(el);
                case "img" -> keepImages ? image(el) : "";
                case "iframe" -> embed(el);
                case "strong", "b" -> wrap(inlineOf(el), "**");
                case "em", "i" -> wrap(inlineOf(el), "*");
                case "code", "kbd", "samp" -> wrap(el.text(), "`");
                default -> inlineOf(el);
            };
        }

        private String link(Element a) {
            String text = tidy(inlineOf(a));
            String href = a.absUrl("href");
            if (!hasScheme(href, LINK_SCHEMES) || a.attr("href").startsWith("#")) {
                return text;
            }
            if (text.isEmpty()) {
                text = a.attr("aria-label").trim();
            }
            if (text.isEmpty()) {
                return "";
            }
            // The text of a link around an image is that image, already written as Markdown: its brackets stay.
            String label = keepImages && a.selectFirst("img") != null ? text : text.replace("[", "\\[").replace("]", "\\]");
            return "[" + label + "](" + target(linkMap.apply(href)) + ")";
        }

        private String image(Element img) {
            String alt = img.attr("alt").trim();
            String src = img.absUrl("src");
            if (!hasScheme(src, IMAGE_SCHEMES)) {
                // Lazy loading keeps the address aside until the image scrolls into view.
                src = img.hasAttr("data-src") ? img.absUrl("data-src") : img.absUrl("data-lazy-src");
            }
            if (alt.isEmpty() || !hasScheme(src, IMAGE_SCHEMES)) {
                return "";
            }
            return "![" + alt.replace("[", "\\[").replace("]", "\\]") + "](" + target(src) + ")";
        }

        /** An embedded video or map is content a text file cannot hold: its title and address are kept. */
        private String embed(Element iframe) {
            String label = iframe.attr("title").trim();
            String src = iframe.absUrl("src");
            if (label.isEmpty() || !hasScheme(src, IMAGE_SCHEMES)) {
                return "";
            }
            return "[" + label.replace("[", "\\[").replace("]", "\\]") + "](" + target(src) + ")";
        }

        /** Only the listed schemes are written as a link: {@code javascript:}, {@code data:} and the like never are. */
        private static boolean hasScheme(String url, Set<String> schemes) {
            int colon = url.indexOf(':');
            return colon > 0 && schemes.contains(url.substring(0, colon).toLowerCase(Locale.ROOT));
        }

        /** A URL as a Markdown link target: nothing in it may close the parenthesis or break the line. */
        private static String target(String url) {
            StringBuilder sb = new StringBuilder(url.length());
            for (int i = 0; i < url.length(); i++) {
                char c = url.charAt(i);
                switch (c) {
                    case '(' -> sb.append("%28");
                    case ')' -> sb.append("%29");
                    case '<' -> sb.append("%3C");
                    case '>' -> sb.append("%3E");
                    default -> {
                        if (Character.isWhitespace(c)) {
                            sb.append("%20");
                        } else if (!Character.isISOControl(c)) {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.toString();
        }

        /** Joins lines with one space, without a pattern that backtracks on long runs of white space. */
        private static String oneLine(String text) {
            StringBuilder sb = new StringBuilder(text.length());
            for (String line : text.split("\n", -1)) {
                String l = line.strip();
                if (!l.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(l);
                }
            }
            return sb.toString();
        }

        private static String wrap(String text, String mark) {
            String t = text.strip();
            if (t.isEmpty()) {
                return text;
            }
            String lead = text.startsWith(" ") ? " " : "";
            String trail = text.endsWith(" ") ? " " : "";
            return lead + mark + t + mark + trail;
        }

        /** Collapses white space, keeping the line breaks written for {@code <br>}. */
        private static String tidy(String text) {
            StringBuilder sb = new StringBuilder();
            for (String line : text.replace('\u00a0', ' ').split("\n", -1)) {
                String l = SPACES.matcher(line).replaceAll(" ").trim();
                if (!l.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(l);
                }
            }
            return sb.toString();
        }
    }

}
