package org.jahia.modules.llms.service;

import org.jahia.bin.Jahia;
import org.jahia.modules.llms.service.internal.HtmlToMarkdown;
import org.jahia.modules.llms.service.internal.PageFetcher;
import org.jahia.modules.llms.service.internal.PublicUrls;
import org.jahia.modules.llms.service.internal.SiteWalker;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Generates and stores a site's llms.txt and llms-full.txt.
 *
 * <p>Generation never writes: it returns text an editor reviews, edits and saves. Both files describe
 * the public site, so the pages are walked in {@code live} as {@code guest} (see {@link SiteWalker});
 * llms-full.txt then renders each listed page anonymously on this Jahia and keeps its main content as
 * Markdown. Saving writes the given text on the site node and publishes the site node alone.</p>
 *
 * <p>Thread-safe. One generation of llms-full.txt runs per site at a time.</p>
 */
@Component(service = LlmsService.class, configurationPid = "org.jahia.modules.llms", immediate = true)
@Designate(ocd = LlmsConfig.class)
public class LlmsService {

    /** Mixin that carries the files on the site node. */
    public static final String MIXIN = "jmix:llms";
    /** llms.txt. */
    public static final String LLMS = "j:llms";
    /** llms-full.txt. */
    public static final String LLMS_FULL = "j:llmsFull";
    /** Public base URL of the site, used in the generated links. */
    public static final String BASE_URL = "j:llmsBaseUrl";

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmsService.class);

    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private volatile LlmsConfig config;

    /** What a generation produced. */
    public record Generation(String content, int pages, List<String> failedPages, boolean truncated) {
    }

    /**
     * @param config the OSGi configuration
     */
    @Activate
    @Modified
    public void activate(LlmsConfig config) {
        this.config = config;
    }

    /**
     * Builds llms.txt from the published, guest-readable page tree.
     *
     * @param siteKey  key of the site
     * @param language language of titles, descriptions and links
     * @param baseUrl  public base URL of the site ({@code https://www.example.com})
     * @return the file and how many pages it lists
     * @throws RepositoryException when the site cannot be read
     */
    public Generation generateLlms(String siteKey, String language, String baseUrl) throws RepositoryException {
        SiteWalker.Walk walk = walk(siteKey, language, baseUrl);
        StringBuilder sb = header(walk);
        for (SiteWalker.Section section : walk.sections()) {
            sb.append("\n## ").append(section.title()).append("\n\n");
            for (SiteWalker.Page page : section.pages()) {
                sb.append("- [").append(escape(page.title())).append("](").append(page.url()).append(')');
                if (page.description() != null) {
                    sb.append(": ").append(page.description());
                }
                sb.append('\n');
            }
        }
        return new Generation(sb.toString(), walk.pages().size(), List.of(), walk.truncated());
    }

    /**
     * Builds llms-full.txt: the pages of llms.txt, each rendered as a visitor gets it and converted to
     * Markdown under its title and URL. Pages that fail to render are named in the result, not in the file.
     *
     * @param siteKey    key of the site
     * @param language   language of the pages
     * @param baseUrl    public base URL of the site
     * @param renderBase scheme, host and port this Jahia answers on from inside its container, used when
     *                   the configuration names none
     * @return the file, the pages it holds and the pages that failed
     * @throws RepositoryException   when the site cannot be read
     * @throws IllegalStateException when a generation of the same site is running
     */
    public Generation generateLlmsFull(String siteKey, String language, String baseUrl, String renderBase)
            throws RepositoryException {
        if (!running.add(siteKey)) {
            throw new IllegalStateException("A generation of llms-full.txt is already running for this site");
        }
        try {
            return full(walk(siteKey, language, baseUrl), renderBase);
        } finally {
            running.remove(siteKey);
        }
    }

    private Generation full(SiteWalker.Walk walk, String renderBase) {
        LlmsConfig c = config;
        String base = c.renderBaseUrl() == null || c.renderBaseUrl().isBlank() ? renderBase : c.renderBaseUrl();
        PageFetcher fetcher = new PageFetcher(base, c.fetchTimeoutSeconds(), c.maxPageKb() * 1024);
        List<SiteWalker.Page> pages = walk.pages();

        // A link of one page to another listed page is written with that page's public URL. Rendered
        // from inside the container, links come as the render URL, or without its /cms/render/live prefix.
        Map<String, String> publicUrls = new HashMap<>();
        String renderPrefix = Jahia.getContextPath() + "/cms/render/live";
        for (SiteWalker.Page p : pages) {
            publicUrls.put(p.renderPath(), p.url());
            if (p.renderPath().startsWith(renderPrefix + "/")) {
                publicUrls.put(Jahia.getContextPath() + p.renderPath().substring(renderPrefix.length()), p.url());
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(c.fetchThreads(), 8)));
        try {
            List<Future<String>> markdown = new ArrayList<>();
            for (SiteWalker.Page page : pages) {
                markdown.add(pool.submit(() -> {
                    Document doc = Jsoup.parse(fetcher.fetch(page.renderPath()), page.url());
                    return HtmlToMarkdown.convert(doc, page.title(), href -> relink(href, page.url(), publicUrls));
                }));
            }
            StringBuilder sb = header(walk);
            List<String> failed = new ArrayList<>();
            int maxBytes = c.maxFullKb() * 1024;
            int bytes = sb.toString().getBytes(StandardCharsets.UTF_8).length;
            int written = 0;
            boolean truncated = walk.truncated();
            for (int i = 0; i < pages.size(); i++) {
                SiteWalker.Page page = pages.get(i);
                String body = body(markdown.get(i), page, c.fetchTimeoutSeconds() * 2L, failed);
                if (body == null) {
                    continue;
                }
                String block = "\n---\n\n# " + page.title() + "\n\nSource: " + page.url() + "\n\n" + body + "\n";
                int size = block.getBytes(StandardCharsets.UTF_8).length;
                if (bytes + size > maxBytes) {
                    truncated = true;
                    break;
                }
                sb.append(block);
                bytes += size;
                written++;
            }
            return new Generation(sb.toString(), written, failed, truncated);
        } finally {
            pool.shutdownNow();
        }
    }

    private static String body(Future<String> future, SiteWalker.Page page, long waitSeconds, List<String> failed) {
        try {
            return future.get(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed.add(page.url());
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.debug("Could not render {} for llms-full.txt", page.renderPath(), e);
            failed.add(page.url());
        }
        return null;
    }

    /** Writes a link to another listed page with its public URL; any other link is left as resolved. */
    private static String relink(String href, String pageUrl, Map<String, String> publicUrls) {
        int origin = pageUrl.indexOf('/', pageUrl.indexOf("://") + 3);
        String host = origin < 0 ? pageUrl : pageUrl.substring(0, origin);
        if (href.startsWith(host + "/")) {
            String path = href.substring(host.length());
            int cut = path.indexOf('#');
            String mapped = publicUrls.get(cut < 0 ? path : path.substring(0, cut));
            if (mapped != null) {
                return cut < 0 ? mapped : mapped + path.substring(cut);
            }
        }
        return href;
    }

    private SiteWalker.Walk walk(String siteKey, String language, String baseUrl) throws RepositoryException {
        PublicUrls urls = new PublicUrls(baseUrl);
        Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
        LlmsConfig c = config;
        JCRUserNode guest = JahiaUserManagerService.getInstance().lookupUser(JahiaUserManagerService.GUEST_USERNAME);
        if (guest == null) {
            throw new RepositoryException("guest user not found");
        }
        return JCRTemplate.getInstance().doExecute(guest.getJahiaUser(), "live", locale,
                (JCRCallback<SiteWalker.Walk>) session -> {
                    JCRNodeWrapper node = session.getNode("/sites/" + siteKey);
                    if (!(node instanceof JCRSiteNode site)) {
                        throw new RepositoryException("not a site");
                    }
                    return new SiteWalker(locale, urls::urlOf, c.maxDepth(), c.maxPages()).walk(site);
                });
    }

    private static StringBuilder header(SiteWalker.Walk walk) {
        StringBuilder sb = new StringBuilder("# ").append(walk.siteTitle()).append('\n');
        if (walk.summary() != null) {
            sb.append("\n> ").append(walk.summary()).append('\n');
        }
        return sb;
    }

    private static String escape(String text) {
        return text.replace("[", "\\[").replace("]", "\\]");
    }

    /**
     * Writes the given files on the site node, then publishes the site node alone (not its pages or
     * content). A null argument leaves that property unchanged; a blank file removes it, so its URL
     * answers 404. Runs with the caller's session: the repository checks that they may modify and
     * publish the site.
     *
     * @param site     the site node, read with the caller's session in {@code default}
     * @param llms     llms.txt, or null
     * @param llmsFull llms-full.txt, or null
     * @param baseUrl  public base URL, or null
     * @throws RepositoryException when the caller may not write or publish the site
     */
    public void save(JCRSiteNode site, String llms, String llmsFull, String baseUrl) throws RepositoryException {
        JCRSessionWrapper session = site.getSession();
        if (!site.isNodeType(MIXIN)) {
            site.addMixin(MIXIN);
        }
        write(site, LLMS, llms);
        write(site, LLMS_FULL, llmsFull);
        write(site, BASE_URL, baseUrl);
        session.save();
        JCRPublicationService.getInstance().publish(Collections.singletonList(site.getIdentifier()),
                session.getWorkspace().getName(), "live", null);
    }

    private static void write(JCRNodeWrapper site, String property, String value) throws RepositoryException {
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            if (site.hasProperty(property)) {
                site.getProperty(property).remove();
            }
        } else {
            site.setProperty(property, value);
        }
    }

    /**
     * @return the configured size limit of llms-full.txt, in bytes
     */
    public int maxFullBytes() {
        return config.maxFullKb() * 1024;
    }

    /**
     * @param session a session on the site's workspace
     * @param siteKey key of the site
     * @param property {@link #LLMS} or {@link #LLMS_FULL}
     * @return the stored file, or null
     * @throws RepositoryException when the site cannot be read
     */
    public static String read(JCRSessionWrapper session, String siteKey, String property) throws RepositoryException {
        if (!session.nodeExists("/sites/" + siteKey)) {
            return null;
        }
        JCRNodeWrapper site = session.getNode("/sites/" + siteKey);
        return site.isNodeType(MIXIN) && site.hasProperty(property) ? site.getProperty(property).getString() : null;
    }

}
