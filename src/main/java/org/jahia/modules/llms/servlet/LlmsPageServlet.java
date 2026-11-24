package org.jahia.modules.llms.servlet;

import org.jahia.bin.Jahia;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.render.NativeMarkdown;
import org.jahia.modules.llms.service.internal.PageMarkdown;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.seo.VanityUrl;
import org.jahia.services.seo.jcr.VanityUrlService;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves the Markdown version of a page at the page's own URL followed by {@code .md}
 * ({@code /sites/a/home/about.html.md}), as the llms.txt proposal asks.
 *
 * <p>A rewrite rule of the module forwards those URLs here ({@code /modules/llmspage}: Jahia mounts a
 * servlet at {@code /modules/<alias>}) with the path Jahia's own rules made of them. The page is rendered by an anonymous request to this Jahia, so a visitor gets as Markdown
 * exactly what they may read as HTML: a page that is not published, or not open to visitors without an
 * account, answers 404 here as it does there.</p>
 *
 * <p>Only a request the rule forwarded is answered, for a path that is plainly a page of one site and
 * that is the page asked for (the context path is Jahia's own: inside a servlet of a module, the one of
 * the request is {@code /modules}): called directly, or with any other path, the servlet answers 404. It
 * never fetches a path a visitor chose freely. Stateless and thread-safe.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=/llmspage", "osgi.http.whiteboard.servlet.asyncSupported=true"})
public class LlmsPageServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmsPageServlet.class);
    private static final String SUFFIX = ".md";
    private static final String PATH_PARAMETER = "path=";
    private static final String NODE_PARAMETER = "node=";
    private static final String VANITY_NODE = "/vanityUrlMapping/";

    private transient LlmsService service;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = pagePath((String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI),
                Jahia.getContextPath(), request.getQueryString());
        // The address of the page this is another form of: about.html for about.html.md and about.md,
        // the vanity URL for a vanity URL.
        String forwarded = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        String page = forwarded == null ? null : forwarded.substring(0, Math.max(0, forwarded.length() - SUFFIX.length()));
        if (path == null) {
            // A vanity URL first, as Jahia looks for one before it reads an address as a path.
            path = vanityPagePath(request);
            if (path == null) {
                path = nodePath(forwarded, Jahia.getContextPath(), request.getQueryString());
                page = page + ".html";
            }
        }
        Target target = path == null || !service.servesPageMarkdown() ? null : Target.of(path);
        String markdown = null;
        if (target != null) {
            try {
                // Jahia renders the page, for a visitor without an account, and keeps the Markdown in its
                // cache: the same as for about.md. The headers are written here, where the address is known.
                markdown = NativeMarkdown.render(target.nodePath(), target.locale(), NativeMarkdown.TEMPLATE_TYPE, request, response);
            } catch (RepositoryException | RuntimeException e) {
                // Debug only: the URL is public, a page that is not there must not write a line per request.
                LOGGER.debug("No Markdown version for {}", path, e);
            }
        }
        if (markdown == null || markdown.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // Jahia trims what a view writes: a text file ends with a line break.
        markdown = markdown.strip().replace(NativeMarkdown.PAGE_URL, MarkdownResponse.onHost(request, page))
                .replace(NativeMarkdown.ORIGIN, MarkdownResponse.onHost(request, "")) + "\n";
        response.setHeader("Cache-Control", "public, no-cache");
        if (MarkdownResponse.notModified(request, response, markdown, MarkdownResponse.onHost(request, page), target.siteKey())) {
            return;
        }
        byte[] body = markdown.getBytes(StandardCharsets.UTF_8);
        response.setContentType(MarkdownResponse.CONTENT_TYPE);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    /**
     * The node a render path of Jahia names, and its language.
     *
     * @param nodePath the repository path of the page
     * @param locale   the language of the path, else the default language of the site
     * @param siteKey  the key of its site
     */
    record Target(String nodePath, Locale locale, String siteKey) {

        private static final Pattern RENDER_PATH = Pattern.compile(
                "^(?:/cms(?:/render/live)?)?(?:/([a-z]{2,3}(?:_[A-Z]{2})?))?(/sites/([A-Za-z0-9_-]{1,64})/[^?#]*)\\.html$");

        /**
         * @param renderPath a path already checked by {@link PageMarkdown#siteKeyOf}, still encoded
         * @return the node and the language it names, or null
         */
        static Target of(String renderPath) {
            Matcher m = RENDER_PATH.matcher(renderPath);
            if (PageMarkdown.siteKeyOf(renderPath) == null || !m.matches()) {
                return null;
            }
            try {
                // A plus sign in a path is a plus sign: only percent escapes are decoded.
                String nodePath = URLDecoder.decode(m.group(2).replace("+", "%2B"), StandardCharsets.UTF_8);
                String language = m.group(1);
                if (language == null) {
                    language = JahiaSitesService.getInstance().getSiteByKey(m.group(3)).getDefaultLanguage();
                }
                return language == null ? null : new Target(nodePath, Locale.forLanguageTag(language.replace('_', '-')), m.group(3));
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * The path to render for a request, or null when the request is not one to answer.
     *
     * @param forwardedUri the URL the visitor asked for when the rewrite rule forwarded the request, else null
     * @param contextPath  the context path of Jahia
     * @param queryString  the query string of the forward: {@code path=<what Jahia's rules made of the URL>},
     *                     then anything the visitor added, which is not read
     * @return the path of the page, still encoded, from the root of the context
     */
    static String pagePath(String forwardedUri, String contextPath, String queryString) {
        if (forwardedUri == null || !forwardedUri.endsWith(".html" + SUFFIX) || queryString == null || !queryString.startsWith(PATH_PARAMETER)) {
            return null;
        }
        // The first parameter is the one the rule wrote. A second "path" a visitor appended is never read.
        int end = queryString.indexOf('&');
        String path = queryString.substring(PATH_PARAMETER.length(), end < 0 ? queryString.length() : end);
        String asked = forwardedUri.substring(0, forwardedUri.length() - SUFFIX.length());
        if (contextPath != null && !contextPath.isEmpty() && asked.startsWith(contextPath + "/")) {
            asked = asked.substring(contextPath.length());
        }
        return PageMarkdown.siteKeyOf(path) != null && PageMarkdown.isSamePage(asked, path) ? path : null;
    }

    /**
     * The path to render for the address of a node followed by {@code .md} ({@code about.md}), or null.
     *
     * @param forwardedUri the URL the visitor asked for
     * @param contextPath  the context path of Jahia
     * @param queryString  the query string of the forward: {@code node=<what Jahia's rules made of the URL,
     *                     without its .md>}, then anything the visitor added, which is not read
     * @return the path of the page as Jahia renders it ({@code .../about.html}), still encoded
     */
    static String nodePath(String forwardedUri, String contextPath, String queryString) {
        if (forwardedUri == null || !forwardedUri.endsWith(SUFFIX) || forwardedUri.endsWith(".html" + SUFFIX)
                || queryString == null || !queryString.startsWith(NODE_PARAMETER)) {
            return null;
        }
        int end = queryString.indexOf('&');
        String path = queryString.substring(NODE_PARAMETER.length(), end < 0 ? queryString.length() : end) + ".html";
        String asked = forwardedUri.substring(0, forwardedUri.length() - SUFFIX.length()) + ".html";
        if (contextPath != null && !contextPath.isEmpty() && asked.startsWith(contextPath + "/")) {
            asked = asked.substring(contextPath.length());
        }
        return PageMarkdown.siteKeyOf(path) != null && PageMarkdown.isSamePage(asked, path) ? path : null;
    }

    /**
     * The page a vanity URL followed by {@code .md} is about, found as Jahia finds the page of a vanity
     * URL: among the vanity URLs of the site of the host first, then among those of every site, since
     * Jahia answers a vanity URL on a host that is not its site's (a host several sites share, as an
     * authoring or pre-production one). When the vanity URL names several contents across sites and the
     * host does not say which site is meant, none is chosen. The path fetched is the one Jahia gives
     * the content, never one written by the visitor.
     *
     * @return the path of the page from the root of the context, or null when the request is not for the
     *         Markdown version of an active vanity URL the visitor may read
     */
    private static String vanityPagePath(HttpServletRequest request) {
        String query = request.getQueryString();
        String vanity = PageMarkdown.vanityOf((String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI), Jahia.getContextPath());
        if (vanity == null || query == null || !query.startsWith(NODE_PARAMETER)) {
            return null;
        }
        try {
            String host = request.getServerName();
            String siteKey = host == null ? null : JahiaSitesService.getInstance().getSitenameByServerName(host.toLowerCase(Locale.ROOT));
            VanityUrlService vanities = (VanityUrlService) SpringContextSingleton.getBean("org.jahia.services.seo.jcr.VanityUrlService");
            Set<String> paths = siteKey == null ? Set.of() : pagePaths(vanities.findExistingVanityUrls(vanity, siteKey, "live"));
            if (paths.isEmpty()) {
                paths = pagePaths(vanities.findExistingVanityUrls(vanity, null, "live"));
            }
            return paths.size() == 1 ? paths.iterator().next() : null;
        } catch (Exception e) {
            // No such vanity URL, or a service that is not there: no page.
            LOGGER.debug("No Markdown version for the vanity URL {}", vanity, e);
            return null;
        }
    }

    /** The pages the active vanity URLs found name, each as the visitor may read it: the ones they cannot read are not there. */
    private static Set<String> pagePaths(List<VanityUrl> found) {
        Set<String> paths = new LinkedHashSet<>();
        String context = Jahia.getContextPath();
        for (VanityUrl vanityUrl : found) {
            int at = vanityUrl.getPath() == null ? -1 : vanityUrl.getPath().indexOf(VANITY_NODE);
            if (at < 0 || !vanityUrl.isActive() || vanityUrl.isFile() || vanityUrl.getLanguage() == null) {
                continue;
            }
            try {
                JCRNodeWrapper node = JCRSessionFactory.getInstance()
                        .getCurrentUserSession("live", Locale.forLanguageTag(vanityUrl.getLanguage().replace('_', '-')))
                        .getNode(vanityUrl.getPath().substring(0, at));
                String url = node.getUrl();
                String path = context != null && !context.isEmpty() && url.startsWith(context + "/") ? url.substring(context.length()) : url;
                if (PageMarkdown.siteKeyOf(path) != null) {
                    paths.add(path);
                }
            } catch (RepositoryException e) {
                // A content this visitor cannot read: it has no Markdown version for them.
            }
        }
        return paths;
    }


    /**
     * @param service the llms service
     */
    @Reference
    public void setService(LlmsService service) {
        this.service = service;
    }
}
