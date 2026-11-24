package org.jahia.modules.llms.render;

import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.TemplateNotFoundException;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Keeps the Markdown views behind the servlet of the module.
 *
 * <p>A view of a module has an address of its own in Jahia: the address of the node followed by the
 * template type ({@code about.md/}, {@code about.md.ajax}, or the same under {@code /cms/render}). Asked
 * for there, the Markdown would leave as {@code text/html}, with none of the headers the servlet
 * writes, and with the text of the page no longer escaped: a text a page shows as {@code &lt;script&gt;}
 * would be a script again. The rewrite rules only send the addresses that end in {@code .md} to the
 * servlet, and cannot name every other form.</p>
 *
 * <p>So the views answer only a rendering the module started. This runs before Jahia looks in its
 * cache: what the cache holds is not served to a visitor who asks for the view itself either.</p>
 */
@Component(service = RenderFilter.class)
public class MarkdownGate extends AbstractFilter {

    /** Registers where the filter applies: every rendering of the Markdown template type, first. */
    @Activate
    public MarkdownGate() {
        // Before every filter of Jahia, the one that reads the cache among them.
        setPriority(-10);
        setApplyOnTemplateTypes(NativeMarkdown.TEMPLATE_TYPE);
        setDescription("Answers the Markdown views of the llms module only to the module itself.");
    }

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        if (renderContext.getRequest().getAttribute(NativeMarkdown.STARTED_BY_MODULE) == null) {
            // What Jahia answers for a template type no view serves: 404.
            throw new TemplateNotFoundException("No view of type " + NativeMarkdown.TEMPLATE_TYPE + " at this address");
        }
        return null;
    }
}
