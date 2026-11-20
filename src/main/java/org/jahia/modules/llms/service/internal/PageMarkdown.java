package org.jahia.modules.llms.service.internal;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Markdown version of one page, served at the page's own URL with {@code .md} appended
 * ({@code page.html.md}), as the llms.txt proposal asks: what an llms.txt links to should be readable by
 * a language model, and that is the page as Markdown, not its HTML.
 *
 * <p>This class decides which paths are pages of a site and nothing else. A request for a Markdown
 * version makes this Jahia fetch a path of itself: only a path that is plainly a page of one site is
 * ever fetched. Stateless and thread-safe.</p>
 */
public final class PageMarkdown {

    /**
     * A page of a site, in the forms Jahia's own rewrite rules produce: an optional {@code /cms} or
     * {@code /cms/render/live}, an optional language, then {@code /sites/<key>/...html}.
     */
    private static final Pattern PAGE = Pattern.compile(
            "^(?:/cms(?:/render/live)?)?(?:/[a-z]{2,3}(?:_[A-Z]{2})?)?/sites/([A-Za-z0-9_-]{1,64})/[^?#]*\\.html$");

    private PageMarkdown() {
    }

    /**
     * @param rawPath a path as it was asked for, still encoded, from the root of the context
     * @return the key of the site when the path is a page of a site and reads the same for every server
     *         (no encoded separator, no dot segment, no path parameter), else null
     */
    public static String siteKeyOf(String rawPath) {
        if (rawPath == null || isAmbiguous(rawPath)) {
            return null;
        }
        Matcher m = PAGE.matcher(rawPath);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * @param asked   the path a visitor asked for, without its {@code .md}
     * @param fetched the path the rewrite rules made of it
     * @return whether the second is the first with only a prefix added, which is all the rules do: the
     *         page fetched is then the page asked for
     */
    public static boolean isSamePage(String asked, String fetched) {
        return asked != null && fetched != null && !asked.isEmpty() && asked.startsWith("/") && fetched.endsWith(asked);
    }

    /**
     * @param page a rendered page
     * @return its title: its first {@code h1}, else the title of the document
     */
    public static String titleOf(Document page) {
        Element h1 = page.selectFirst("h1");
        String title = h1 == null ? "" : h1.text().trim();
        return (title.isEmpty() ? page.title() : title).replaceAll("\\s+", " ").trim();
    }

    private static boolean isAmbiguous(String rawPath) {
        String p = rawPath.toLowerCase(Locale.ROOT);
        return p.contains("%2e") || p.contains("%2f") || p.contains("%5c") || p.contains("%25")
                || p.indexOf(';') >= 0 || p.indexOf('\\') >= 0 || p.contains("//") || p.contains("/../") || p.contains("/./")
                || p.endsWith("/..") || p.endsWith("/.");
    }
}
