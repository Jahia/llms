package org.jahia.modules.llms;

import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * /llms servlet that return content of "j:llms" on site node
 */
@Component(service = {javax.servlet.http.HttpServlet.class, javax.servlet.Servlet.class}, property = {"alias=/llms", "osgi.http.whiteboard.servlet.asyncSupported=true"})
public class LlmsServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmsServlet.class);

    JahiaSitesService sitesService;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            // get the server name from the request
            final String serverName = request.getServerName();
            // aggregate all sites llms configurations
            JCRSiteNode site = sitesService.getSiteByServerName(serverName, JCRSessionFactory.getInstance().getCurrentUserSession("live"));
            if (site.isNodeType("jmix:llms")) {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Content-Type", "text/plain;charset=UTF-8");
                response.getWriter().write(site.getPropertyAsString("j:llms"));
                return;
            }
        } catch (Exception e) {
            LOGGER.error("Error while dispatching: {}", e.getMessage());
            LOGGER.debug("Full exception", e);
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Reference
    public void setSitesService(JahiaSitesService sitesService) {
        this.sitesService = sitesService;
    }
}
