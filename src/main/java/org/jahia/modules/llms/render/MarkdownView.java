package org.jahia.modules.llms.render;

import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.PageMarkdown;
import org.jahia.modules.llms.service.internal.SiteWalker;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.render.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.regex.Pattern;

/**
 * What the Markdown views of the module ({@code jnt_page/md}, {@code jmix_mainResource/md}) write: the
 * page as Jahia renders it for a visitor, converted. The views hold no logic of their own.
 */
public final class MarkdownView {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]+");

    private MarkdownView() {
    }

    /**
     * @param request  the request of the view
     * @param response its response
     * @return the Markdown of the node the view is rendered for
     * @throws RepositoryException when the page cannot be rendered for a visitor
     */
    public static String render(HttpServletRequest request, HttpServletResponse response) throws RepositoryException {
        Resource resource = (Resource) request.getAttribute("currentResource");
        JCRNodeWrapper node = resource.getNode();
        // Jahia keeps what this view returns. It lets go of it when the node, or anything below it,
        // changes: the Markdown is made of them.
        resource.getDependencies().add(node.getCanonicalPath());
        resource.getRegexpDependencies().add(Pattern.quote(node.getCanonicalPath()) + "/.*");
        LlmsService service = BundleUtils.getOsgiService(LlmsService.class, null);
        boolean keepImages = service != null && service.keepsImages(node.getResolveSite().getSiteKey());
        // Jahia keeps this Markdown for every visitor: the host one of them asked, which a request may
        // name as it likes, must not be written in it. The links get the host of each request when served.
        String base = NativeMarkdown.ORIGIN + node.getUrl();
        String html = NativeMarkdown.render(node.getPath(), resource.getLocale(), "html", request, response);
        Document page = Jsoup.parse(html == null ? "" : html, base);
        String title = PageMarkdown.titleOf(page);
        String description = node.hasProperty("jcr:description") ? node.getProperty("jcr:description").getString() : null;
        Calendar published = node.hasProperty("j:lastPublished") ? node.getProperty("j:lastPublished").getDate() : null;
        return frontMatter(title, SiteWalker.oneLine(description), published == null ? null : published.toInstant())
                + NativeMarkdown.convert(page, title, keepImages);
    }

    /**
     * What a reader that only has the file needs to name the page: its title, its address, what it is
     * about and when it was last published. The address is the one of the page as HTML, the one to quote;
     * it is written by whoever serves the Markdown, which knows the address that was asked for.
     *
     * @param title       the title of the page, may be empty
     * @param description its description on one line, or null
     * @param published   when the page was last published, or null
     * @return a YAML block, closed, followed by an empty line
     */
    static String frontMatter(String title, String description, Instant published) {
        StringBuilder yaml = new StringBuilder("---\n");
        if (title != null && !title.isBlank()) {
            yaml.append("title: ").append(quoted(title)).append('\n');
        }
        yaml.append("url: \"").append(NativeMarkdown.PAGE_URL).append("\"\n");
        if (description != null && !description.isBlank()) {
            yaml.append("description: ").append(quoted(description)).append('\n');
        }
        if (published != null) {
            yaml.append("last_updated: ").append(published.atZone(ZoneOffset.UTC).toLocalDate()).append('\n');
        }
        return yaml.append("---\n\n").toString();
    }

    /** A YAML string between double quotes, on one line: what could end it or start an escape is escaped. */
    private static String quoted(String text) {
        String line = CONTROL.matcher(text).replaceAll(" ").trim();
        return '"' + line.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
