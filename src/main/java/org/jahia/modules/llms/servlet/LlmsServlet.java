package org.jahia.modules.llms.servlet;

import org.jahia.modules.llms.service.LlmsService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Serves a site's llms.txt and llms-full.txt from the live workspace, with the rights of the visitor.
 *
 * <p>The URL rewrite rules of the module send {@code /llms.txt} and {@code /llms-full.txt} here
 * ({@code file=full} for the second). {@code /sites/<key>/llms.txt} names the site ({@code site=<key>});
 * at the domain root the site is the one whose server name is the request's host. A file that is not
 * stored, or blank, answers 404. Stateless and thread-safe.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=/llms", "osgi.http.whiteboard.servlet.asyncSupported=true"})
public class LlmsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmsServlet.class);
    private static final Pattern SITE_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private transient JahiaSitesService sitesService;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String property = "full".equals(request.getParameter("file")) ? LlmsService.LLMS_FULL : LlmsService.LLMS;
        String content = null;
        try {
            JCRSessionWrapper live = JCRSessionFactory.getInstance().getCurrentUserSession("live");
            String siteKey = request.getParameter("site");
            if (siteKey == null || siteKey.isEmpty()) {
                // A host no site claims, or one only the system site claims (localhost on a local
                // install), is served by the default site, as Jahia serves its pages.
                JCRSiteNode site = sitesService.getSiteByServerName(request.getServerName(), live);
                if (site == null || JahiaSitesService.SYSTEM_SITE_KEY.equals(site.getSiteKey())) {
                    site = (JCRSiteNode) sitesService.getDefaultSite(live);
                }
                siteKey = site == null ? null : site.getSiteKey();
            }
            if (siteKey != null && SITE_KEY.matcher(siteKey).matches()) {
                content = LlmsService.read(live, siteKey, property);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read {} for {}: {}", property, request.getServerName(), e.getMessage());
            LOGGER.debug("Full exception", e);
        }
        if (content == null || content.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType("text/plain;charset=UTF-8");
        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param sitesService the sites service
     */
    @Reference
    public void setSitesService(JahiaSitesService sitesService) {
        this.sitesService = sitesService;
    }
}
