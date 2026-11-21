package org.jahia.modules.llms.render;

import org.jahia.modules.llms.service.LlmsService;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.render.Resource;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;

/**
 * What the Markdown views of the module ({@code jnt_page/md}, {@code jmix_mainResource/md}) write: the
 * page as Jahia renders it for a visitor, converted. The views hold no logic of their own.
 */
public final class MarkdownView {

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
        return NativeMarkdown.convert(html, base, keepImages);
    }
}
