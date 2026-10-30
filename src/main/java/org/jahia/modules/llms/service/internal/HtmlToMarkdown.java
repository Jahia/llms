package org.jahia.modules.llms.service.internal;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Turns the main content of a rendered page into Markdown for llms-full.txt.
 *
 * <p>Only what a reader needs survives: headings, paragraphs, lists, quotes, code, tables, links and
 * images that have a text alternative. Scripts, styles, forms, hidden elements and the page chrome
 * (header, navigation, footer) are dropped. The main content is {@code <main>} (or {@code role="main"}),
 * else the body without its chrome. Stateless and thread-safe.</p>
 */
public final class HtmlToMarkdown {

    private static final String DROPPED = "script, style, noscript, template, svg, iframe, object, embed, canvas, "
            + "form, button, input, select, textarea, dialog, [hidden], [aria-hidden=true], .sr-only, .visually-hidden";
    /** Breadcrumbs repeat what llms.txt lists. Other menus inside main stay: a site map page is one. */
    private static final String BREADCRUMB = "[class*=breadcrumb], [aria-label~=(?i)breadcrumb|ariane|brotkr]";
    private static final String CHROME = "header, footer, nav, aside, [role=navigation], [role=banner], "
            + "[role=contentinfo], [role=complementary], [role=search]";
    private static final Set<String> BLOCKS = Set.of("address", "article", "aside", "blockquote", "dd", "details",
            "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
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
        page.select(DROPPED).remove();
        Element main = page.selectFirst("main, [role=main]");
        if (main == null) {
            main = page.body();
            main.select(CHROME).remove();
        }
        main.select(BREADCRUMB).remove();
        Writer writer = new Writer(title, linkMap, false);
        writer.blocks(main, 0);
        return writer.result();
    }

    /** One conversion: the blocks written so far and whether the page title was already dropped. */
    private static final class Writer {
        private final String title;
        private final UnaryOperator<String> linkMap;
        /** Inside a list item or a table cell: everything ends up on one line, headings included. */
        private final boolean flat;
        private final List<String> out = new ArrayList<>();
        private boolean titleSeen;

        Writer(String title, UnaryOperator<String> linkMap, boolean flat) {
            this.title = title == null ? "" : title.trim();
            this.linkMap = linkMap;
            this.flat = flat;
            this.titleSeen = flat;
        }

        String result() {
            return String.join("\n\n", out).replaceAll("\n{3,}", "\n\n").trim();
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
                case "p", "summary", "dt", "figcaption" -> add(tidy(inlineOf(el)));
                case "ul", "ol" -> add(list(el, listDepth));
                case "blockquote" -> add(quote(el));
                case "pre" -> add("```\n" + el.wholeText().replaceAll("\\s+$", "") + "\n```");
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
                out.add(text.matches(".*[.:!?]$") ? text : text + ":");
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
            return sb.toString().replaceAll("\n+$", "");
        }

        /** A block inside a list item or a table cell, flattened to one line. */
        private String textOfBlock(Element el) {
            Writer inner = new Writer("", linkMap, true);
            inner.blocks(el, 0);
            return inner.result().replaceAll("\\s*\n+\\s*", " ");
        }

        private String quote(Element el) {
            Writer inner = new Writer("", linkMap, false);
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
                return text.text();
            }
            if (!(node instanceof Element el)) {
                return "";
            }
            String name = el.normalName();
            return switch (name) {
                case "br" -> "\n";
                case "a" -> link(el);
                case "img" -> image(el);
                case "strong", "b" -> wrap(inlineOf(el), "**");
                case "em", "i" -> wrap(inlineOf(el), "*");
                case "code", "kbd", "samp" -> wrap(el.text(), "`");
                default -> inlineOf(el);
            };
        }

        private String link(Element a) {
            String text = tidy(inlineOf(a));
            String href = a.absUrl("href");
            if (href.isEmpty() || href.startsWith("javascript:") || a.attr("href").startsWith("#")) {
                return text;
            }
            if (text.isEmpty()) {
                text = a.attr("aria-label").trim();
            }
            if (text.isEmpty()) {
                return "";
            }
            return "[" + text.replace("[", "\\[").replace("]", "\\]") + "](" + linkMap.apply(href).replace(" ", "%20") + ")";
        }

        private String image(Element img) {
            String alt = img.attr("alt").trim();
            String src = img.absUrl("src");
            if (alt.isEmpty() || src.isEmpty() || src.startsWith("data:")) {
                return "";
            }
            return "![" + alt.replace("[", "\\[").replace("]", "\\]") + "](" + src.replace(" ", "%20") + ")";
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
                String l = line.replaceAll("[ \\t\\r\\f]+", " ").trim();
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
