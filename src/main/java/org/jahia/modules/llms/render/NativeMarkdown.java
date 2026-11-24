package org.jahia.modules.llms.render;

import org.jahia.modules.llms.service.internal.HtmlToMarkdown;
import org.jahia.modules.llms.service.internal.PageMarkdown;
import org.jahia.services.channels.ChannelService;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderException;
import org.jahia.services.render.RenderService;
import org.jahia.services.render.Resource;
import org.jahia.services.render.SiteInfo;
import org.jahia.services.render.URLGenerator;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jsoup.nodes.Document;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A page as Markdown, made by Jahia's own rendering rather than fetched over HTTP.
 *
 * <p>The page is rendered as HTML with its template, exactly as a visitor gets it, then converted. It is
 * always rendered for a visitor without an account, in live, whoever asks: a file meant for assistants
 * says the same to everyone, and nothing a signed-in user alone may read is written as Markdown.</p>
 *
 * <p>Two renderings nest. The outer one has the template type {@code md}: its view calls the inner one
 * and converts, and Jahia caches the Markdown it returns. The inner one is the page as HTML, which Jahia
 * caches as it does for visitors.</p>
 */
public final class NativeMarkdown {

    /** The template type of the Markdown views, which is the extension of their address. */
    public static final String TEMPLATE_TYPE = "md";

    /**
     * Stands for the host in the links of the Markdown Jahia keeps: a name no server has (RFC 2606), put
     * back to the host of the request by whoever serves the Markdown.
     */
    public static final String ORIGIN = "https://llms-origin.invalid";
    /**
     * Stands for the address of the page in the Markdown Jahia keeps: the same page is asked for at its
     * path and at its vanity URL, and on any host. Whoever serves the Markdown writes the address there.
     */
    public static final String PAGE_URL = "https://llms-page.invalid";
    private static final String SERVLET_PATH = "/cms/render";
    private static final String LIVE = "live";

    private NativeMarkdown() {
    }

    /**
     * Renders a node for a visitor without an account, in live.
     *
     * @param path         the repository path of a page or of a content shown as a page
     * @param locale       the language
     * @param templateType {@code md} for the Markdown version (cached as such), {@code html} for the page
     * @param request      the request
     * @param response     the response
     * @return what Jahia rendered
     * @throws PathNotFoundException when a visitor cannot read the node, or it is not published
     * @throws RepositoryException   when the node cannot be rendered
     */
    public static String render(String path, Locale locale, String templateType, HttpServletRequest request, HttpServletResponse response)
            throws RepositoryException {
        JCRUserNode guestNode = JahiaUserManagerService.getInstance().lookupUser(JahiaUserManagerService.GUEST_USERNAME);
        if (guestNode == null) {
            throw new RepositoryException("guest user not found");
        }
        JahiaUser guest = guestNode.getJahiaUser();
        JCRSessionFactory sessions = JCRSessionFactory.getInstance();
        JahiaUser asking = sessions.getCurrentUser();
        // The rendering writes its own attributes in the request: the caller gets its own back.
        Map<String, Object> attributes = new HashMap<>();
        for (String name : Collections.list(request.getAttributeNames())) {
            attributes.put(name, request.getAttribute(name));
        }
        try {
            sessions.setCurrentUser(guest);
            return JCRTemplate.getInstance().doExecute(guest, LIVE, locale, (JCRCallback<String>) session -> {
                JCRNodeWrapper node = session.getNode(path);
                JCRSiteNode site = node.getResolveSite();
                RenderContext context = new RenderContext(request, response, guest);
                context.setSite(site);
                context.setSiteInfo(new SiteInfo(site));
                context.setChannel(ChannelService.getInstance().getChannel("generic"));
                context.setServletPath(SERVLET_PATH);
                context.setWorkspace(LIVE);
                context.setEditMode(false);
                Resource resource = new Resource(node, templateType, null, Resource.CONFIGURATION_PAGE);
                context.setMainResource(resource);
                context.setURLGenerator(new URLGenerator(context, resource));
                try {
                    return RenderService.getInstance().render(resource, context);
                } catch (RenderException e) {
                    throw new RepositoryException(e);
                }
            });
        } finally {
            sessions.setCurrentUser(asking);
            for (String name : Collections.list(request.getAttributeNames())) {
                if (!attributes.containsKey(name)) {
                    request.removeAttribute(name);
                }
            }
            for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
                request.setAttribute(attribute.getKey(), attribute.getValue());
            }
        }
    }

    /**
     * @param page       a page as Jahia rendered it, parsed with the address its relative links resolve against
     * @param title      its title, as {@link PageMarkdown#titleOf} reads it
     * @param keepImages whether images are written
     * @return the page as Markdown, under its title
     */
    public static String convert(Document page, String title, boolean keepImages) {
        return (title.isEmpty() ? "" : "# " + title + "\n\n") + HtmlToMarkdown.convert(page, title, href -> href, keepImages) + "\n";
    }
}
