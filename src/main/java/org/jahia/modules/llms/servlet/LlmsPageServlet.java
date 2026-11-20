package org.jahia.modules.llms.servlet;

import org.jahia.bin.Jahia;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.DiscoveryLinks;
import org.jahia.modules.llms.service.internal.LocalAddress;
import org.jahia.modules.llms.service.internal.PageMarkdown;
import org.jahia.services.content.JCRSessionFactory;
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
import java.nio.charset.StandardCharsets;

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

    private transient LlmsService service;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = pagePath((String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI),
                Jahia.getContextPath(), request.getQueryString());
        String markdown = null;
        String asked = null;
        if (path != null) {
            asked = publicUrl(request);
            try {
                markdown = service.pageMarkdown(path, asked, LocalAddress.of(request));
            } catch (IllegalStateException e) {
                response.setHeader("Retry-After", "5");
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                // Debug only: the URL is public, a page that is not there must not write a line per request.
                LOGGER.debug("No Markdown version for {}", path, e);
            }
        }
        if (markdown == null || markdown.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        byte[] body = markdown.getBytes(StandardCharsets.UTF_8);
        String etag = LlmsServlet.etag(body);
        response.setHeader("Cache-Control", "public, no-cache");
        response.setHeader("ETag", etag);
        response.setHeader("X-Content-Type-Options", "nosniff");
        // The page itself is the reference: this is another form of it, not another page. And the
        // llms.txt of the site describes it, said in a header since Markdown has no <head> to say it in.
        response.setHeader("Link", DiscoveryLinks.header(asked, llmsHref(path)));
        if (LlmsServlet.matches(request.getHeader("If-None-Match"), etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        response.setContentType("text/markdown;charset=UTF-8");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
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

    /** The llms.txt of the site of the page, when the visitor can read one. */
    private static String llmsHref(String path) {
        try {
            String siteKey = PageMarkdown.siteKeyOf(path);
            String file = LlmsService.read(JCRSessionFactory.getInstance().getCurrentUserSession("live"), siteKey, LlmsService.LLMS);
            return file == null || file.isBlank() ? null : Jahia.getContextPath() + "/sites/" + siteKey + "/llms.txt";
        } catch (RepositoryException e) {
            return null;
        }
    }

    /** The address the visitor knows the page by: the URL they asked for, without its {@code .md}. */
    private static String publicUrl(HttpServletRequest request) {
        String uri = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        int port = request.getServerPort();
        boolean standard = ("http".equals(request.getScheme()) && port == 80) || ("https".equals(request.getScheme()) && port == 443);
        return request.getScheme() + "://" + request.getServerName() + (standard ? "" : ":" + port)
                + uri.substring(0, uri.length() - SUFFIX.length());
    }

    /**
     * @param service the llms service
     */
    @Reference
    public void setService(LlmsService service) {
        this.service = service;
    }
}
