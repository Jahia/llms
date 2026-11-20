package org.jahia.modules.llms.service.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two links the llms.txt proposal asks a page to carry, so that an agent holding the page finds the
 * rest without guessing: {@code rel="alternate" type="text/markdown"} to the Markdown version of the
 * page, and {@code rel="describedby"} to the llms.txt that covers it. Stateless and thread-safe.
 */
public final class DiscoveryLinks {

    private static final Pattern HEAD = Pattern.compile("<head(?:\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);

    private DiscoveryLinks() {
    }

    /**
     * @param html         a rendered page
     * @param markdownHref the address of its Markdown version, or null when it has none
     * @param llmsHref     the address of the llms.txt that covers it, or null when the site serves none
     * @return the page with the links written at the top of its {@code <head>}; unchanged when there is
     *         no link to write, no {@code <head>}, or when the page already carries them
     */
    public static String addTo(String html, String markdownHref, String llmsHref) {
        if (html == null || (markdownHref == null && llmsHref == null)) {
            return html;
        }
        Matcher head = HEAD.matcher(html);
        if (!head.find()) {
            return html;
        }
        StringBuilder links = new StringBuilder();
        if (markdownHref != null && !html.contains("type=\"text/markdown\"")) {
            links.append("<link rel=\"alternate\" type=\"text/markdown\" href=\"").append(attribute(markdownHref)).append("\"/>");
        }
        if (llmsHref != null && !html.contains("rel=\"describedby\"")) {
            links.append("<link rel=\"describedby\" href=\"").append(attribute(llmsHref)).append("\"/>");
        }
        return links.length() == 0 ? html : html.substring(0, head.end()) + links + html.substring(head.end());
    }

    /**
     * @param canonicalHref the address of the page a Markdown version is the other form of, or null
     * @param llmsHref      the address of the llms.txt, or null
     * @return the same links as the value of an HTTP {@code Link} header, for a resource that is not HTML;
     *         null when there is none
     */
    public static String header(String canonicalHref, String llmsHref) {
        StringBuilder value = new StringBuilder();
        if (canonicalHref != null) {
            value.append('<').append(uriReference(canonicalHref)).append(">; rel=\"canonical\"");
        }
        if (llmsHref != null) {
            value.append(value.length() == 0 ? "" : ", ").append('<').append(uriReference(llmsHref)).append(">; rel=\"describedby\"");
        }
        return value.length() == 0 ? null : value.toString();
    }

    /** An address as the value of an HTML attribute: nothing in it may end the attribute or the tag. */
    private static String attribute(String href) {
        return href.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** An address between the angle brackets of a Link header: nothing in it may close them or start a new line. */
    private static String uriReference(String href) {
        return href.replace("<", "%3C").replace(">", "%3E").replace("\r", "").replace("\n", "");
    }
}
