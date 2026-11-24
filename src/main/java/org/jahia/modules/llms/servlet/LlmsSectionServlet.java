package org.jahia.modules.llms.servlet;

import org.jahia.bin.Jahia;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.PageMarkdown;
import org.jahia.modules.llms.service.internal.SectionFile;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the llms.txt and llms-full.txt of a section of a site: files placed under the path of a page,
 * which cover the pages below it ({@code /sites/a/home/docs/llms.txt}). The files of the whole site are
 * {@link LlmsServlet}'s.
 *
 * <p>Read from the live workspace with the rights of the visitor, like the files of the site: a section
 * whose page a visitor may not read answers 404. Only a request the rewrite rule of the module forwarded
 * is answered, for the path that was asked for. Stateless and thread-safe.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=/llmssection", "osgi.http.whiteboard.servlet.asyncSupported=true"})
public class LlmsSectionServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmsSectionServlet.class);
    private static final String PATH_PARAMETER = "path=";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            serve(request, response);
        } catch (IOException e) {
            // The visitor went away before the end: nothing is left to answer, and nothing to report.
            LOGGER.debug("Could not write the file of a section", e);
        }
    }

    private void serve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SectionFile file = fileOf((String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI),
                Jahia.getContextPath(), request.getQueryString());
        String content = null;
        if (file != null) {
            try {
                JCRSessionWrapper live = JCRSessionFactory.getInstance().getCurrentUserSession("live");
                if (live.nodeExists(file.nodePath())) {
                    JCRNodeWrapper page = live.getNode(file.nodePath());
                    if (page.isNodeType("jnt:page")) {
                        content = LlmsService.readAt(page, file.full() ? LlmsService.LLMS_FULL : LlmsService.LLMS);
                    }
                }
            } catch (Exception e) {
                // Debug only: the URL is public, a failing read must not write one line per request.
                LOGGER.debug("Could not read the files of {}", file.nodePath(), e);
            }
        }
        if (content == null || content.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        String etag = LlmsServlet.etag(body);
        LlmsServlet.headers(response, etag);
        if (LlmsServlet.matches(request.getHeader("If-None-Match"), etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        response.setContentType("text/plain;charset=UTF-8");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    /**
     * The file a request is for, or null when the request is not one to answer.
     *
     * @param forwardedUri the URL the visitor asked for when the rewrite rule forwarded the request, else null
     * @param contextPath  the context path of Jahia
     * @param queryString  the query string of the forward: {@code path=<what Jahia's rules made of the URL>},
     *                     then anything the visitor added, which is not read
     * @return the file of a section
     */
    static SectionFile fileOf(String forwardedUri, String contextPath, String queryString) {
        if (forwardedUri == null || queryString == null || !queryString.startsWith(PATH_PARAMETER)) {
            return null;
        }
        int end = queryString.indexOf('&');
        String path = queryString.substring(PATH_PARAMETER.length(), end < 0 ? queryString.length() : end);
        String asked = PageMarkdown.withoutContext(forwardedUri, contextPath);
        return PageMarkdown.isSamePage(asked, path) ? SectionFile.of(path) : null;
    }
}
