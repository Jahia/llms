package org.jahia.modules.llms.service.internal;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Markdown version of one page, served at the page's own URL with {@code .md} appended
 * ({@code page.html.md}), as the llms.txt proposal asks: what an llms.txt links to should be readable by
 * a language model, and that is the page as Markdown, not its HTML.
 *
 * <p>This class decides which paths are pages of a site and nothing else. A request for a Markdown
 * version makes this Jahia render a node of its repository: only a path that is plainly a page of one
 * site, and the one the visitor asked for, is ever rendered. Stateless and thread-safe.</p>
 */
public final class PageMarkdown {

    /**
     * A page of a site, in the forms Jahia's own rewrite rules produce: an optional {@code /cms} or
     * {@code /cms/render/live}, an optional language, then {@code /sites/<key>/...html}.
     */
    private static final Pattern PAGE = Pattern.compile(
            "^(?:/cms(?:/render/live)?)?(?:/[a-z]{2,3}(?:_[A-Z]{2})?)?/sites/([A-Za-z0-9_-]{1,64})/[^?#]*\\.html$");

    /**
     * A vanity URL as a visitor writes one: plain segments, none that starts with a dot, nothing encoded.
     * What a vanity URL of Jahia looks like, and nothing that could be read two ways.
     */
    private static final Pattern VANITY = Pattern.compile("^(?:/[A-Za-z0-9_~-][A-Za-z0-9._~-]*){1,12}$");
    private static final int MAX_VANITY_LENGTH = 255;
    /** First segments that are Jahia's own, or a module's: never a vanity URL. */
    private static final Set<String> RESERVED = Set.of("cms", "modules", "files", "repository", "engines", "gwt", "tools",
            "sites", "welcome", "start", "administration", "jahia", "css", "javascript", "icons", "flow", "validateTicket");
    private static final String MARKDOWN = ".md";

    private PageMarkdown() {
    }

    /**
     * The vanity URL a request for a Markdown version is about, when it is one: the address asked for
     * without its {@code .md}, as long as it is not a page address ({@code page.html.md}, which has its
     * own rule) and reads like a vanity URL.
     *
     * @param forwardedUri the URL the visitor asked for
     * @param contextPath  the context path of Jahia
     * @return the vanity URL, from the root of the context, or null
     */
    public static String vanityOf(String forwardedUri, String contextPath) {
        if (forwardedUri == null || !forwardedUri.endsWith(MARKDOWN) || forwardedUri.endsWith(".html" + MARKDOWN)) {
            return null;
        }
        String path = forwardedUri.substring(0, forwardedUri.length() - MARKDOWN.length());
        if (contextPath != null && !contextPath.isEmpty()) {
            if (!path.startsWith(contextPath + "/")) {
                return null;
            }
            path = path.substring(contextPath.length());
        }
        if (path.length() > MAX_VANITY_LENGTH || !VANITY.matcher(path).matches()) {
            return null;
        }
        int end = path.indexOf('/', 1);
        String first = end < 0 ? path.substring(1) : path.substring(1, end);
        return RESERVED.contains(first) ? null : path;
    }

    /**
     * @param uri         an address from the root of the server
     * @param contextPath the context path of Jahia, or nothing when Jahia is at the root
     * @return the address from the root of the context
     */
    public static String withoutContext(String uri, String contextPath) {
        boolean below = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath + '/');
        return below ? uri.substring(contextPath.length()) : uri;
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
        if (asked == null || fetched == null || asked.isEmpty() || !asked.startsWith("/")) {
            return false;
        }
        if (fetched.endsWith(asked)) {
            return true;
        }
        // The rules hand over the path decoded, where the address asked for is still encoded: a name
        // with a space or an accent (my%20page) reads "my page" there. Decoded once, and only an
        // address that reads one way.
        String decoded = isAmbiguous(asked) ? null : decoded(asked);
        return decoded != null && fetched.endsWith(decoded);
    }

    /**
     * @param rawPath a path, still encoded
     * @return the path with its percent escapes decoded, a plus sign staying a plus sign, or null when an
     *         escape is not one
     */
    public static String decoded(String rawPath) {
        try {
            return URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
