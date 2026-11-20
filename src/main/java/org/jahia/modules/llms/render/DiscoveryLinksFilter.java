package org.jahia.modules.llms.render;

import org.jahia.bin.Jahia;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.DiscoveryLinks;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

/**
 * Writes in the {@code <head>} of the published pages the two links the llms.txt proposal asks for: to
 * the Markdown version of the page, and to the llms.txt of the site. Only in live, only for the page
 * itself, and only on a site where the module is enabled.
 *
 * <p>The output is cached with the page, like the rest of its {@code <head>}: a site that gets its first
 * llms.txt shows the link on a page once the cache of that page is renewed.</p>
 */
@Component(service = RenderFilter.class)
public class DiscoveryLinksFilter extends AbstractFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryLinksFilter.class);
    private static final String MODULE = "llms";

    private LlmsService service;

    /** Registers where the filter applies. */
    @Activate
    public void activate() {
        // The same place in the chain as the filter of the sitemap module that writes the robots meta tag.
        setPriority(17);
        setApplyOnNodeTypes("jnt:page");
        setApplyOnConfigurations("page");
        setApplyOnModes("live");
        setDescription("Adds the llms.txt discovery links (rel=alternate text/markdown, rel=describedby) to the head of a page.");
    }

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        try {
            JCRSiteNode site = renderContext.getSite();
            if (!service.discoveryLinks() || site == null || !site.getInstalledModulesWithAllDependencies().contains(MODULE)) {
                return previousOut;
            }
            return DiscoveryLinks.addTo(previousOut, markdownHref(resource, site), llmsHref(resource.getNode(), site));
        } catch (Exception e) {
            // Two optional links never cost a page its rendering.
            LOGGER.debug("Could not add the llms.txt discovery links", e);
            return previousOut;
        }
    }

    /**
     * The Markdown version of the page, from the page itself. Never from the URL of the request: this
     * output is cached for every visitor, and one of them could ask for the page under an address of
     * their making.
     */
    private String markdownHref(Resource resource, JCRSiteNode site) {
        if (!service.servesPageMarkdown() || !"html".equals(resource.getTemplateType()) || resource.getLocale() == null) {
            return null;
        }
        return DiscoveryLinks.markdownHref(resource.getNode().getUrl(), Jahia.getContextPath(),
                resource.getLocale().toString(), site.getDefaultLanguage());
    }

    /** The llms.txt of the site, when it serves one: read in the session of the page, so as the visitor. */
    private static String llmsHref(JCRNodeWrapper page, JCRSiteNode site) throws RepositoryException {
        String file = LlmsService.read(page.getSession(), site.getSiteKey(), LlmsService.LLMS);
        return file == null || file.isBlank() ? null : Jahia.getContextPath() + "/sites/" + site.getSiteKey() + "/llms.txt";
    }

    /**
     * @param service the llms service
     */
    @Reference
    public void setService(LlmsService service) {
        this.service = service;
    }
}
