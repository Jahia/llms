package org.jahia.modules.llms.servlet;

import org.jahia.bin.Jahia;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.DiscoveryLinks;
import org.jahia.services.content.JCRSessionFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * What every Markdown version of a page says in its headers, whichever way it was asked for: what it
 * is, the page it is another form of, the llms.txt that describes it, and a validator so an unchanged
 * page is not sent again.
 */
public final class MarkdownResponse {

    /** The content type of a Markdown version. */
    public static final String CONTENT_TYPE = "text/markdown;charset=UTF-8";

    private MarkdownResponse() {
    }

    /**
     * Writes the headers of a Markdown version.
     *
     * @param request   the request
     * @param response  the response
     * @param markdown  the Markdown
     * @param canonical the address of the page this is another form of
     * @param siteKey   the key of the site of the page
     * @return true when the visitor already holds this Markdown: the status is then 304 and no body is due
     */
    public static boolean notModified(HttpServletRequest request, HttpServletResponse response, String markdown, String canonical,
                                      String siteKey) {
        String etag = LlmsServlet.etag(markdown.getBytes(StandardCharsets.UTF_8));
        response.setHeader("ETag", etag);
        response.setHeader("X-Content-Type-Options", "nosniff");
        // The page itself is the reference: this is another form of it, not another page. And the
        // llms.txt of the site describes it, said in a header since Markdown has no <head> to say it in.
        response.setHeader("Link", DiscoveryLinks.header(canonical, llmsHref(siteKey)));
        if (LlmsServlet.matches(request.getHeader("If-None-Match"), etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return true;
        }
        return false;
    }

    /**
     * @param request the request
     * @param path    a path from the root of the server, as the visitor wrote it
     * @return that path as an address on the host the visitor asked
     */
    public static String onHost(HttpServletRequest request, String path) {
        int port = request.getServerPort();
        boolean standard = ("http".equals(request.getScheme()) && port == 80) || ("https".equals(request.getScheme()) && port == 443);
        return request.getScheme() + "://" + request.getServerName() + (standard ? "" : ":" + port) + path;
    }

    /** The llms.txt of the site of the page, when the visitor can read one. */
    private static String llmsHref(String siteKey) {
        try {
            String file = LlmsService.read(JCRSessionFactory.getInstance().getCurrentUserSession("live"), siteKey, LlmsService.LLMS);
            return file == null || file.isBlank() ? null : Jahia.getContextPath() + "/sites/" + siteKey + "/llms.txt";
        } catch (RepositoryException e) {
            return null;
        }
    }
}
