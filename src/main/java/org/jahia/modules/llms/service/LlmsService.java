package org.jahia.modules.llms.service;

import org.jahia.bin.Jahia;
import org.jahia.modules.llms.service.internal.HtmlToMarkdown;
import org.jahia.modules.llms.service.internal.PageFetcher;
import org.jahia.modules.llms.service.internal.PageMarkdown;
import org.jahia.modules.llms.service.internal.PublicUrls;
import org.jahia.modules.llms.service.internal.SectionFile;
import org.jahia.modules.llms.service.internal.SiteWalker;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.services.modulemanager.spi.Config;
import org.jahia.services.modulemanager.spi.ConfigService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
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
 * <p>Thread-safe. One generation of llms-full.txt runs per site at a time, and a few at most on this
 * Jahia: a generation renders every listed page.</p>
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

    /** The text the CND autocreates for llms.txt when the mixin is added: not a file an editor wrote. */
    static final String PLACEHOLDER = "# Title";
    private static final String BUSY = "Too many generations are running, try again in a moment";

    private final Set<String> running = ConcurrentHashMap.newKeySet();
    /** Generations of llms-full.txt running on this Jahia, all sites together. */
    private final Semaphore fullSlots = new Semaphore(2);
    /** Walks of a page tree running for llms.txt. */
    private final Semaphore walkSlots = new Semaphore(4);
    private volatile Limits limits;
    private volatile boolean servePageMarkdown = true;
    private volatile boolean discoveryLinks = true;
    /** Pages converted to Markdown at the same time for visitors: each one renders a page. */
    private final Semaphore pageSlots = new Semaphore(8);
    /** What a generation lists when the site has no configuration of its own. */
    private volatile SiteSettings defaults = new SiteSettings(10, 200, 100, SiteWalker.typeNames(SiteWalker.DEFAULT_EXCLUDED),
            Set.of(), Set.of(), false, false);
    /** The heading the files of the sections are listed under: the name of the file, the same in every language. */
    static final String SECTION_FILES_HEADING = "llms.txt";
    private static final String MENU_GROUP = "jnt:navMenuText";
    /** Menu groups nested deeper than this are not looked through. */
    private static final int MAX_GROUP_DEPTH = 5;
    private static final int OWN_CONFIG_ATTEMPTS = 5;
    private static final String SITE_PID = "org.jahia.modules.llms.site";
    private volatile ConfigService configService;
    /** The site configurations, one for each org.jahia.modules.llms.site-*.cfg. */
    private final List<SiteOverride> overrides = new CopyOnWriteArrayList<>();
    /** The generations started from the panel, by identifier, kept a while after they end. */
    private final Map<String, GenerationJob> jobs = new ConcurrentHashMap<>();
    private static final long JOB_LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(15);
    /** The most sections the panel is given to choose from. */
    private static final int MAX_SECTIONS = 100;
    private final ExecutorService jobRunner = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "llms-generation");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Where one file stands in live.
     *
     * @param served  whether its URL answers with a file
     * @param current whether the file served is the file saved
     */
    public record FileStatus(boolean served, boolean current) {
    }

    /**
     * Which pages generation leaves out and how many it lists, as configured.
     *
     * @param excludedTypes     node types of the pages left out, their sub-pages still listed
     * @param excludedTreeTypes node types of the pages left out with everything below them
     * @param maxPages          most pages one file lists
     * @param maxDepth          levels of pages below the home page that are listed
     * @param keepImages        whether images are written in llms-full.txt
     */
    public record Rules(List<String> excludedTypes, List<String> excludedTreeTypes, int maxPages, int maxDepth, boolean keepImages,
                        List<String> includedTypes, int maxMainResources) {
    }

    /**
     * A page that has, or can be given, its own files.
     *
     * @param path     the repository path of the page
     * @param title    its title
     * @param hasFiles whether it already carries files
     */
    public record SectionRef(String path, String title, boolean hasFiles) {
    }

    /** Where the two files of a site stand in live. */
    public record Status(FileStatus llms, FileStatus llmsFull) {
    }

    /** Told how a generation of llms-full.txt goes, page by page. Called from several threads. */
    public interface Progress {
        /**
         * @param pages the pages that will be rendered, in the order of the file
         */
        void started(List<SiteWalker.Page> pages);

        /**
         * @param index the place of a page in that list
         * @param state what became of it
         */
        void page(int index, GenerationJob.PageState state);
    }

    private static final Progress NO_PROGRESS = new Progress() {
        @Override
        public void started(List<SiteWalker.Page> pages) {
            // nobody follows a generation asked for in one request
        }

        @Override
        public void page(int index, GenerationJob.PageState state) {
            // nobody follows a generation asked for in one request
        }
    };

    /** What a generation produced. */
    public record Generation(String content, int pages, List<String> failedPages, boolean truncated, String cutBy, int skipped) {
        /** A file that no limit of time or size cut. */
        Generation(String content, int pages, List<String> failedPages, boolean truncated) {
            this(content, pages, failedPages, truncated, truncated ? CUT_BY_PAGES : null, 0);
        }
    }

    /** The site has more pages than one file lists. */
    public static final String CUT_BY_PAGES = "pages";
    /** The file reached its size limit before every page was written. */
    public static final String CUT_BY_SIZE = "size";
    /** The time allowed for one generation ran out. */
    public static final String CUT_BY_TIME = "time";

    /**
     * @param config the OSGi configuration
     */
    @Activate
    @Modified
    public void activate(LlmsConfig config) {
        if (Limits.isRejected(config.renderBaseUrl())) {
            LOGGER.warn("renderBaseUrl is not a scheme, a host and an optional port: ignored, pages are rendered on 127.0.0.1");
        }
        this.limits = Limits.of(config);
        this.servePageMarkdown = config.servePageMarkdown();
        this.discoveryLinks = config.discoveryLinks();
        this.defaults = SiteSettings.of(limits, config);
    }

    /**
     * @param override a site configuration that appeared
     */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    public void addSiteOverride(SiteOverride override) {
        overrides.add(override);
    }

    /**
     * @param override a site configuration that went away
     */
    public void removeSiteOverride(SiteOverride override) {
        overrides.remove(override);
    }

    /**
     * @param service Jahia's configuration service, which writes the files of karaf/etc
     */
    @Reference
    public void setConfigService(ConfigService service) {
        this.configService = service;
    }

    /**
     * One value a site may set, as the panel shows it.
     *
     * @param key       its key in the configuration
     * @param kind      NUMBER, TYPES or FLAG
     * @param value     what the site sets, or an empty text when it sets nothing
     * @param inherited what applies when the site sets nothing: the value of the module configuration
     * @param min       the smallest number allowed, for a number
     * @param max       the largest number allowed, for a number
     * @param error     why the value was refused ({@code number}, {@code range}, {@code types}, {@code flag}), or null
     * @param detail    the names that are not known types, when the error is {@code types}
     */
    public record Setting(String key, String kind, String value, String inherited, int min, int max, String error, String detail) {
    }

    /**
     * @param siteKey key of the site
     * @return the values the site may set, with what it sets and what it inherits
     */
    public List<Setting> siteSettings(String siteKey) {
        Map<String, Object> site = Map.of();
        for (SiteOverride override : overrides) {
            if (override.siteKey().equals(siteKey)) {
                site = override.properties();
                break;
            }
        }
        Map<String, String> values = new HashMap<>();
        for (SiteSettings.Definition definition : SiteSettings.DEFINITIONS) {
            Object value = site.get(definition.key());
            values.put(definition.key(), value == null ? "" : String.valueOf(value).trim());
        }
        return settingsOf(values, Map.of());
    }

    private List<Setting> settingsOf(Map<String, String> values, Map<String, SiteSettings.Refusal> refused) {
        SiteSettings module = defaults;
        List<Setting> out = new ArrayList<>();
        for (SiteSettings.Definition d : SiteSettings.DEFINITIONS) {
            SiteSettings.Refusal refusal = refused.get(d.key());
            String value = values.get(d.key());
            out.add(new Setting(d.key(), d.kind().name(), value == null ? "" : value.trim(), module.textOf(d.key()), d.min(), d.max(),
                    refusal == null ? null : refusal.code(), refusal == null ? null : refusal.detail()));
        }
        return out;
    }

    /**
     * Writes what a site lists differently from the module configuration, in its own configuration file
     * ({@code org.jahia.modules.llms.site-<name>.cfg}). Nothing is written when a value is refused. A site
     * that sets nothing any more has no file.
     *
     * @param siteKey key of the site
     * @param values  the values by key; a blank or missing one means "as the module configuration says"
     * @return the values as they now stand; one that carries an error means nothing was saved
     * @throws IOException              when the configuration cannot be written
     * @throws IllegalArgumentException when a key is not one a site may set
     * @throws IllegalStateException    when the configuration of the site cannot be found again by name
     */
    public List<Setting> saveSiteSettings(String siteKey, Map<String, String> values) throws IOException {
        NodeTypeRegistry registry = NodeTypeRegistry.getInstance();
        Map<String, SiteSettings.Refusal> refused = SiteSettings.check(values, registry::hasNodeType);
        if (!refused.isEmpty()) {
            return settingsOf(values, refused);
        }
        String identifier = siteKey;
        for (SiteOverride override : overrides) {
            if (override.siteKey().equals(siteKey)) {
                identifier = override.identifier();
                if (identifier == null) {
                    throw new IllegalStateException("The configuration of this site was not made from a named file");
                }
                break;
            }
        }
        Config config = ownConfig(siteKey, identifier);
        Map<String, String> stored = config.getRawProperties();
        boolean any = false;
        for (SiteSettings.Definition d : SiteSettings.DEFINITIONS) {
            String value = values.get(d.key());
            if (value == null || value.isBlank()) {
                stored.remove(d.key());
            } else {
                stored.put(d.key(), value.trim());
                any = true;
            }
        }
        if (any) {
            stored.put("siteKey", siteKey);
            configService.storeConfig(config);
        } else {
            configService.deleteConfig(config);
        }
        return settingsOf(values, Map.of());
    }

    /**
     * The configuration to write for a site: one that is this site's, or that is nobody's yet. A file may
     * be named after one site and be for another (an operator named it so): the administrator of the
     * first must never write, or remove, the configuration of the second. Such a name is passed over.
     */
    private Config ownConfig(String siteKey, String identifier) throws IOException {
        String candidate = identifier;
        for (int attempt = 0; attempt < OWN_CONFIG_ATTEMPTS; attempt++) {
            Config config = configService.getConfig(SITE_PID, candidate);
            String owner = config.getRawProperties().get("siteKey");
            if (owner == null || owner.isBlank() || owner.trim().equals(siteKey)) {
                return config;
            }
            candidate = siteKey + "-" + (attempt + 2);
        }
        throw new IllegalStateException("The configuration of this site could not be written: its file names are taken");
    }

    /** What a generation lists for a site: the module configuration, then what the site's own changes. */
    private SiteSettings settingsOf(String siteKey) {
        SiteSettings settings = defaults;
        for (SiteOverride override : overrides) {
            if (override.siteKey().equals(siteKey)) {
                settings = settings.with(override.properties());
            }
        }
        return settings;
    }

    /** The contents shown as a page of their own below a node, the most recently modified first. */
    private static List<JCRNodeWrapper> mainResources(JCRNodeWrapper root, int limit) throws RepositoryException {
        Query query = root.getSession().getWorkspace().getQueryManager().createQuery(
                "SELECT * FROM [jmix:mainResource] AS n WHERE ISDESCENDANTNODE(n, '" + root.getPath().replace("'", "''")
                        + "') ORDER BY n.[jcr:lastModified] DESC", Query.JCR_SQL2);
        query.setLimit(limit);
        List<JCRNodeWrapper> out = new ArrayList<>();
        NodeIterator found = query.execute().getNodes();
        while (found.hasNext()) {
            if (found.nextNode() instanceof JCRNodeWrapper node) {
                out.add(node);
            }
        }
        return out;
    }

    /**
     * Builds llms.txt from the published, guest-readable page tree.
     *
     * @param siteKey  key of the site
     * @param language language of titles, descriptions and links
     * @param baseUrl  public base URL of the site ({@code https://www.example.com})
     * @return the file and how many pages it lists
     * @throws RepositoryException   when the site cannot be read
     * @throws IllegalStateException when too many generations are running
     */
    public Generation generateLlms(String siteKey, String sectionPath, String language, String baseUrl) throws RepositoryException {
        if (!walkSlots.tryAcquire()) {
            throw new IllegalStateException(BUSY);
        }
        SiteWalker.Walk walk;
        try {
            walk = walk(siteKey, sectionPath, language, baseUrl);
        } finally {
            walkSlots.release();
        }
        boolean toMarkdown = settingsOf(siteKey).linkToMarkdown();
        StringBuilder sb = header(walk);
        for (SiteWalker.Section section : walk.sections()) {
            sb.append("\n## ").append(section.title()).append("\n\n");
            for (SiteWalker.Page page : section.pages()) {
                sb.append("- [").append(escape(page.title())).append("](").append(linkOf(page.url(), toMarkdown)).append(')');
                if (page.description() != null) {
                    sb.append(": ").append(page.description());
                }
                sb.append('\n');
            }
        }
        // The sections that have a file of their own are named last: a reader that wants more of one
        // follows the link, which is how the files of a large site chain, the proposal having no paging.
        List<SiteWalker.Page> files = sectionFiles(siteKey, sectionPath, language, baseUrl);
        if (!files.isEmpty()) {
            sb.append("\n## ").append(SECTION_FILES_HEADING).append("\n\n");
            for (SiteWalker.Page file : files) {
                sb.append("- [").append(escape(file.title())).append("](").append(file.url()).append(')');
                if (file.description() != null) {
                    sb.append(": ").append(file.description());
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
     * @throws IllegalStateException when a generation of the same site, or too many generations, are running
     */
    public Generation generateLlmsFull(String siteKey, String sectionPath, String language, String baseUrl, String renderBase)
            throws RepositoryException {
        String scope = scopeOf(siteKey, sectionPath);
        if (!running.add(scope)) {
            throw new IllegalStateException("A generation of llms-full.txt is already running for this site");
        }
        try {
            if (!fullSlots.tryAcquire()) {
                throw new IllegalStateException(BUSY);
            }
            try {
                return full(walk(siteKey, sectionPath, language, baseUrl), siteKey, renderBase, NO_PROGRESS);
            } finally {
                fullSlots.release();
            }
        } finally {
            running.remove(scope);
        }
    }

    /**
     * Starts a generation of llms-full.txt in the background and returns at once. The panel then reads
     * {@link #job} until it is finished: the pages rendered so far, then the file.
     *
     * @param siteKey    key of the site
     * @param language   language of the pages
     * @param baseUrl    public base URL of the site
     * @param renderBase scheme, host and port this Jahia answers on from inside its container
     * @return the identifier of the generation
     * @throws IllegalStateException when a generation of the same site, or too many generations, are running
     */
    public String startFullGeneration(String siteKey, String sectionPath, String language, String baseUrl, String renderBase) {
        jobs.values().removeIf(old -> old.ageNanos() > JOB_LIFETIME_NANOS);
        String scope = scopeOf(siteKey, sectionPath);
        if (!running.add(scope)) {
            throw new IllegalStateException("A generation of llms-full.txt is already running for this site");
        }
        if (!fullSlots.tryAcquire()) {
            running.remove(scope);
            throw new IllegalStateException(BUSY);
        }
        GenerationJob job = new GenerationJob(UUID.randomUUID().toString(), siteKey);
        jobs.put(job.id(), job);
        try {
            jobRunner.execute(() -> {
                // A thread of the module has no user: Jahia logs an error each time something asks for one.
                // The generation reads as a visitor does, so the visitor is who this thread works for.
                JCRSessionFactory sessions = JCRSessionFactory.getInstance();
                JahiaUser before = sessions.getCurrentUser();
                try {
                    JCRUserNode guest = JahiaUserManagerService.getInstance().lookupUser(JahiaUserManagerService.GUEST_USERNAME);
                    if (guest != null) {
                        sessions.setCurrentUser(guest.getJahiaUser());
                    }
                    job.finish(full(walk(siteKey, sectionPath, language, baseUrl), siteKey, renderBase, job));
                } catch (RepositoryException | RuntimeException e) {
                    LOGGER.warn("Could not generate llms-full.txt for {}: {}", siteKey, e.getMessage());
                    LOGGER.debug("Full exception", e);
                    job.fail("The file could not be generated");
                } finally {
                    sessions.setCurrentUser(before);
                    fullSlots.release();
                    running.remove(scope);
                }
            });
        } catch (RejectedExecutionException e) {
            // The module is stopping.
            jobs.remove(job.id());
            fullSlots.release();
            running.remove(scope);
            throw new IllegalStateException(BUSY);
        }
        return job.id();
    }

    /**
     * @param siteKey key of the site the caller may generate for
     * @param id      identifier of a generation
     * @return that generation when it is one of this site, else null
     */
    public GenerationJob job(String siteKey, String id) {
        GenerationJob job = id == null ? null : jobs.get(id);
        return job != null && job.siteKey().equals(siteKey) ? job : null;
    }

    /** Stops the generations that run when the module stops. */
    @Deactivate
    public void deactivate() {
        jobRunner.shutdownNow();
        jobs.clear();
    }

    private Generation full(SiteWalker.Walk walk, String siteKey, String renderBase, Progress progress) {
        Limits c = limits;
        boolean images = settingsOf(siteKey).keepImages();
        String base = c.renderBaseUrl() == null ? renderBase : c.renderBaseUrl();
        List<SiteWalker.Page> pages = walk.pages();
        progress.started(pages);
        // A page may redirect, but only to a render URL of the same site: the file describes this site
        // alone. The allowed paths start like the render URLs of the listed pages, up to the site key.
        Set<String> sitePrefixes = sitePrefixes(pages, siteKey);
        PageFetcher fetcher = new PageFetcher(base, c.fetchTimeoutSeconds(), c.maxPageBytes(),
                path -> sitePrefixes.stream().anyMatch(path::startsWith));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(c.maxTotalSeconds());

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

        ExecutorService pool = Executors.newFixedThreadPool(c.fetchThreads());
        try {
            List<Future<String>> markdown = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                SiteWalker.Page page = pages.get(i);
                int index = i;
                markdown.add(pool.submit(() -> {
                    try {
                        Document doc = Jsoup.parse(fetcher.fetch(page.renderPath()), page.url());
                        String md = HtmlToMarkdown.convert(doc, page.title(), href -> relink(href, page.url(), publicUrls), images);
                        // Said as soon as the page is rendered, not when its turn comes to be written.
                        progress.page(index, md.isBlank() ? GenerationJob.PageState.EMPTY : GenerationJob.PageState.RENDERED);
                        return md;
                    } catch (Exception e) {
                        progress.page(index, GenerationJob.PageState.FAILED);
                        throw e;
                    }
                }));
            }
            StringBuilder sb = header(walk);
            List<String> failed = new ArrayList<>();
            int maxBytes = c.maxFullBytes();
            int bytes = sb.toString().getBytes(StandardCharsets.UTF_8).length;
            int written = 0;
            boolean truncated = walk.truncated();
            String cutBy = truncated ? CUT_BY_PAGES : null;
            int skipped = 0;
            for (int i = 0; i < pages.size(); i++) {
                SiteWalker.Page page = pages.get(i);
                long left = TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime());
                if (left <= 0) {
                    // Out of time: the pages not rendered yet are named, not waited for.
                    truncated = true;
                    cutBy = CUT_BY_TIME;
                    for (int rest = i; rest < pages.size(); rest++) {
                        failed.add(pages.get(rest).url());
                        progress.page(rest, GenerationJob.PageState.FAILED);
                    }
                    break;
                }
                String body = body(markdown.get(i), page, Math.min(c.fetchTimeoutSeconds() * 2L, left), failed);
                if (body == null) {
                    // Also a page that did not answer in time, which its own thread has not reported.
                    progress.page(i, GenerationJob.PageState.FAILED);
                }
                // A page with nothing to read (an empty page, a page that is only a form) adds no block:
                // a title and an address with nothing under them tell a reader nothing.
                if (body == null || body.isBlank()) {
                    continue;
                }
                String block = "\n---\n\n# " + page.title() + "\n\nSource: " + page.url() + "\n\n" + body + "\n";
                int size = block.getBytes(StandardCharsets.UTF_8).length;
                if (bytes + size > maxBytes) {
                    // The file is full: this page and the ones after it are said to be left out, not left
                    // unsaid. The ones still being rendered are stopped below, and are not failures.
                    truncated = true;
                    cutBy = CUT_BY_SIZE;
                    skipped = pages.size() - i;
                    for (int rest = i; rest < pages.size(); rest++) {
                        progress.page(rest, GenerationJob.PageState.SKIPPED);
                    }
                    break;
                }
                sb.append(block);
                bytes += size;
                written++;
            }
            return new Generation(sb.toString(), written, failed, truncated, cutBy, skipped);
        } finally {
            pool.shutdownNow();
        }
    }

    /** {@code /cms/render/live/en/sites/key/} for each language and context the listed pages render under. */
    static Set<String> sitePrefixes(List<SiteWalker.Page> pages, String siteKey) {
        String sitePath = "/sites/" + siteKey + "/";
        Set<String> prefixes = new HashSet<>();
        for (SiteWalker.Page page : pages) {
            int at = page.renderPath().indexOf(sitePath);
            if (at >= 0) {
                prefixes.add(page.renderPath().substring(0, at + sitePath.length()));
            }
        }
        return prefixes;
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

    /**
     * The llms.txt of the sections below the site, or below a section: the pages that carry a published
     * llms.txt a visitor without an account can read, each with the address of that file. The address is
     * the one that answers on any host ({@code /sites/<key>/<path of the page>/llms.txt}).
     */
    private List<SiteWalker.Page> sectionFiles(String siteKey, String sectionPath, String language, String baseUrl)
            throws RepositoryException {
        Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
        String root = sectionPath == null ? "/sites/" + siteKey : sectionPath;
        JCRUserNode guest = JahiaUserManagerService.getInstance().lookupUser(JahiaUserManagerService.GUEST_USERNAME);
        if (guest == null) {
            throw new RepositoryException("guest user not found");
        }
        return JCRTemplate.getInstance().doExecute(guest.getJahiaUser(), "live", locale,
                (JCRCallback<List<SiteWalker.Page>>) session -> {
                    Query query = session.getWorkspace().getQueryManager().createQuery(
                            "select * from [" + MIXIN + "] as n where isdescendantnode(n, '" + root.replace("'", "''")
                                    + "') order by n.[jcr:path]", Query.JCR_SQL2);
                    query.setLimit(MAX_SECTIONS);
                    List<SiteWalker.Page> out = new ArrayList<>();
                    NodeIterator carrying = query.execute().getNodes();
                    while (carrying.hasNext()) {
                        JCRNodeWrapper page = (JCRNodeWrapper) carrying.nextNode();
                        String href = SectionFile.hrefOf(page.getPath());
                        if (href != null && page.isNodeType("jnt:page") && page.hasProperty(LLMS)
                                && !page.getProperty(LLMS).getString().isBlank()) {
                            out.add(new SiteWalker.Page(page.getDisplayableName(), baseUrl + Jahia.getContextPath() + href,
                                    SiteWalker.oneLine(page.hasProperty("jcr:description") ? page.getProperty("jcr:description").getString() : null),
                                    page.getUrl()));
                        }
                    }
                    return out;
                });
    }

    /** What a generation is about, for the one-at-a-time guard: the site, or one section of it. */
    private static String scopeOf(String siteKey, String sectionPath) {
        return sectionPath == null ? siteKey : sectionPath;
    }

    /**
     * Lists the pages of the site, or of one section of it, as a visitor without an account gets them.
     *
     * @param sectionPath the path of the page at the top of a section, or null for the whole site; a
     *                    section the guest cannot read in live has no page to list
     */
    private SiteWalker.Walk walk(String siteKey, String sectionPath, String language, String baseUrl) throws RepositoryException {
        PublicUrls urls = new PublicUrls(baseUrl);
        Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
        SiteSettings s = settingsOf(siteKey);
        JCRUserNode guest = JahiaUserManagerService.getInstance().lookupUser(JahiaUserManagerService.GUEST_USERNAME);
        if (guest == null) {
            throw new RepositoryException("guest user not found");
        }
        return JCRTemplate.getInstance().doExecute(guest.getJahiaUser(), "live", locale,
                (JCRCallback<SiteWalker.Walk>) session -> {
                    SiteWalker walker = new SiteWalker(locale, urls::urlOf, s.maxDepth(), s.maxPages(), s.excluded(),
                            s.excludedTrees(), s.included(), s.maxMainResources(), LlmsService::mainResources);
                    if (sectionPath != null) {
                        if (!isSectionOf(siteKey, sectionPath)) {
                            throw new RepositoryException("not a section of the site");
                        }
                        JCRNodeWrapper page = session.getNode(sectionPath);
                        if (!page.isNodeType("jnt:page")) {
                            throw new RepositoryException("not a page");
                        }
                        return walker.walkSection(page);
                    }
                    JCRNodeWrapper node = session.getNode("/sites/" + siteKey);
                    if (!(node instanceof JCRSiteNode site)) {
                        throw new RepositoryException("not a site");
                    }
                    return walker.walk(site);
                });
    }

    /**
     * @param siteKey key of a site
     * @param path    a repository path
     * @return whether the path is below that site, written plainly: no dot segment, no empty segment
     */
    public static boolean isSectionOf(String siteKey, String path) {
        String root = "/sites/" + siteKey + "/";
        return path != null && path.startsWith(root) && path.length() > root.length() && !path.endsWith("/")
                && !path.contains("//") && !path.contains("/../") && !path.contains("/./") && !path.endsWith("/..")
                && !path.endsWith("/.") && path.indexOf('\\') < 0;
    }

    private static StringBuilder header(SiteWalker.Walk walk) {
        StringBuilder sb = new StringBuilder("# ").append(walk.siteTitle()).append('\n');
        if (walk.summary() != null) {
            sb.append("\n> ").append(walk.summary()).append('\n');
        }
        return sb;
    }

    /**
     * @return whether the published pages carry the links to their Markdown version and to llms.txt
     */
    public boolean discoveryLinks() {
        return discoveryLinks;
    }

    /**
     * @return whether each page is also served as Markdown at its URL followed by .md
     */
    public boolean servesPageMarkdown() {
        return servePageMarkdown;
    }

    /**
     * The address a line of llms.txt links to: the page, or its Markdown version when configured so and
     * the page has one (an address that ends in {@code .html}; a vanity URL has none).
     */
    private String linkOf(String pageUrl, boolean linkToMarkdown) {
        return linkToMarkdown && servePageMarkdown && pageUrl.endsWith(".html") ? pageUrl + ".md" : pageUrl;
    }

    /**
     * The Markdown version of one page, as a visitor without an account gets the page: rendered by an
     * anonymous request to this Jahia, in live, then converted like a page of llms-full.txt.
     *
     * @param rawPath    the path of the page from the root of the context, still encoded; must be a page
     *                   of a site ({@link PageMarkdown#siteKeyOf})
     * @param publicUrl  the address the visitor knows the page by, which its relative links resolve against
     * @param renderBase scheme, host and port this Jahia answers on from inside its container
     * @return the Markdown, or null when Markdown versions are switched off or the path is not a page
     * @throws IOException           when the page does not answer 200 with HTML to a visitor
     * @throws InterruptedException  when the thread is stopped
     * @throws IllegalStateException when too many pages are being converted
     */
    public String pageMarkdown(String rawPath, String publicUrl, String renderBase) throws IOException, InterruptedException {
        String siteKey = PageMarkdown.siteKeyOf(rawPath);
        if (!servePageMarkdown || siteKey == null) {
            return null;
        }
        if (!pageSlots.tryAcquire()) {
            throw new IllegalStateException(BUSY);
        }
        try {
            Limits c = limits;
            String base = c.renderBaseUrl() == null ? renderBase : c.renderBaseUrl();
            // The path comes from the rewrite rules, which work from the root of the context: Jahia's
            // context path goes back in front of it. A redirect is followed only to another page of the site.
            String context = Jahia.getContextPath();
            PageFetcher fetcher = new PageFetcher(base, c.fetchTimeoutSeconds(), c.maxPageBytes(),
                    path -> siteKey.equals(PageMarkdown.siteKeyOf(path.startsWith(context) ? path.substring(context.length()) : path)));
            Document doc = Jsoup.parse(fetcher.fetch(context + rawPath), publicUrl);
            String title = PageMarkdown.titleOf(doc);
            String body = HtmlToMarkdown.convert(doc, title, href -> href, settingsOf(siteKey).keepImages());
            return (title.isEmpty() ? "" : "# " + title + "\n\n") + body + "\n";
        } finally {
            pageSlots.release();
        }
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
    public void save(JCRNodeWrapper site, String llms, String llmsFull, String baseUrl) throws RepositoryException {
        JCRSessionWrapper session = site.getSession();
        if (!site.isNodeType(MIXIN)) {
            site.addMixin(MIXIN);
            // The mixin autocreates a placeholder llms.txt: saving llms-full.txt alone must not publish it.
            if (llms == null && site.hasProperty(LLMS) && PLACEHOLDER.equals(site.getProperty(LLMS).getString().trim())) {
                site.getProperty(LLMS).remove();
            }
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
     * Compares what is saved with what visitors are served, here rather than in the panel: llms-full.txt
     * can weigh megabytes, and the panel only needs two booleans per file.
     *
     * @param site the site node, read with the caller's session in {@code default}
     * @return the status of both files
     * @throws RepositoryException when the site cannot be read
     */
    public Status status(JCRNodeWrapper site) throws RepositoryException {
        JCRSessionWrapper live = JCRSessionFactory.getInstance().getCurrentUserSession("live");
        return new Status(status(site, live, LLMS), status(site, live, LLMS_FULL));
    }

    private static FileStatus status(JCRNodeWrapper site, JCRSessionWrapper live, String property) throws RepositoryException {
        String served = live.nodeExists(site.getPath()) ? readAt(live.getNode(site.getPath()), property) : null;
        boolean isServed = served != null && !served.isBlank();
        String saved = site.hasProperty(property) ? site.getProperty(property).getString() : "";
        return new FileStatus(isServed, isServed && served.equals(saved));
    }

    /**
     * The pages an editor can pick as a section: those that already carry files, wherever they are, then
     * the pages below the home page that have sub-pages, which is where the sections of a site are.
     *
     * @param site the site, read with the caller's session in {@code default}
     * @return the pages, those with files first
     * @throws RepositoryException when the site cannot be read
     */
    public List<SectionRef> sections(JCRSiteNode site) throws RepositoryException {
        Map<String, SectionRef> out = new LinkedHashMap<>();
        // Titles are per language: the pages are read in the default language of the site, with the
        // caller's rights.
        JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession("default",
                Locale.forLanguageTag(site.getDefaultLanguage().replace('_', '-')));
        // The site key was checked by the caller: the path holds nothing to escape.
        Query query = session.getWorkspace().getQueryManager().createQuery(
                "select * from [" + MIXIN + "] as n where isdescendantnode(n, '" + site.getPath() + "')", Query.JCR_SQL2);
        query.setLimit(MAX_SECTIONS);
        NodeIterator carrying = query.execute().getNodes();
        while (carrying.hasNext()) {
            JCRNodeWrapper page = (JCRNodeWrapper) carrying.nextNode();
            if (page.isNodeType("jnt:page")) {
                out.put(page.getPath(), new SectionRef(page.getPath(), page.getDisplayableName(), true));
            }
        }
        for (JCRNodeWrapper home : session.getNode(site.getPath()).getNodes()) {
            if (home.isNodeType("jnt:page")) {
                candidates(out, home, "", 0);
            }
        }
        return new ArrayList<>(out.values());
    }

    /**
     * The pages with sub-pages right below a node, looking through menu groups: a group is a label of
     * the navigation, not a page, and the pages it gathers are sections of the site like the others.
     * Such a page is named after its group too, which is how an editor knows it.
     */
    private static void candidates(Map<String, SectionRef> out, JCRNodeWrapper parent, String group, int groupDepth)
            throws RepositoryException {
        for (JCRNodeWrapper child : parent.getNodes()) {
            if (out.size() >= MAX_SECTIONS) {
                return;
            }
            if (child.isNodeType("jnt:page")) {
                if (hasSubPage(child, 0)) {
                    out.putIfAbsent(child.getPath(), new SectionRef(child.getPath(), group + child.getDisplayableName(), false));
                }
            } else if (child.isNodeType(MENU_GROUP) && groupDepth < MAX_GROUP_DEPTH) {
                candidates(out, child, group + child.getDisplayableName() + " / ", groupDepth + 1);
            }
        }
    }

    /** Whether a page has a sub-page, right below it or below a menu group. */
    private static boolean hasSubPage(JCRNodeWrapper page, int groupDepth) throws RepositoryException {
        for (JCRNodeWrapper child : page.getNodes()) {
            if (child.isNodeType("jnt:page")
                    || (child.isNodeType(MENU_GROUP) && groupDepth < MAX_GROUP_DEPTH && hasSubPage(child, groupDepth + 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param siteKey key of the site
     * @return which pages generation leaves out and how many it lists for that site, as configured
     */
    public Rules rules(String siteKey) {
        SiteSettings s = settingsOf(siteKey);
        return new Rules(known(s.excluded()), known(s.excludedTrees()), s.maxPages(), s.maxDepth(), s.keepImages(),
                known(s.included()), s.maxMainResources());
    }

    /**
     * The configured types this Jahia knows. The defaults name the marks of several template sets: an
     * editor is told only of the ones a page of this Jahia can carry.
     */
    private static List<String> known(Set<String> types) {
        NodeTypeRegistry registry = NodeTypeRegistry.getInstance();
        return types.stream().filter(registry::hasNodeType).toList();
    }

    /**
     * @return the configured size limit of llms-full.txt, in bytes
     */
    public int maxFullBytes() {
        return limits.maxFullBytes();
    }

    /**
     * @param session a session on the site's workspace
     * @param siteKey key of the site
     * @param property {@link #LLMS} or {@link #LLMS_FULL}
     * @return the stored file, or null when there is none or only the autocreated placeholder
     * @throws RepositoryException when the site cannot be read
     */
    public static String read(JCRSessionWrapper session, String siteKey, String property) throws RepositoryException {
        JCRNodeWrapper site = siteNode(session, siteKey);
        return site == null ? null : read(site, property);
    }

    /**
     * @param session a session on the site's workspace
     * @param siteKey key of the site
     * @return the site node when the session can read it and it carries the files, else null
     * @throws RepositoryException when the site cannot be read
     */
    public static JCRNodeWrapper siteNode(JCRSessionWrapper session, String siteKey) throws RepositoryException {
        if (!session.nodeExists("/sites/" + siteKey)) {
            return null;
        }
        JCRNodeWrapper site = session.getNode("/sites/" + siteKey);
        return site.isNodeType(MIXIN) ? site : null;
    }

    /**
     * @param node     a site, or a page at the top of a section
     * @param property {@link #LLMS} or {@link #LLMS_FULL}
     * @return the file stored on that node, or null when it carries none or only the autocreated placeholder
     * @throws RepositoryException when the node cannot be read
     */
    public static String readAt(JCRNodeWrapper node, String property) throws RepositoryException {
        return node.isNodeType(MIXIN) ? read(node, property) : null;
    }

    /**
     * @param site     a site node that carries the files, from {@link #siteNode}
     * @param property {@link #LLMS} or {@link #LLMS_FULL}
     * @return the stored file, or null when there is none or only the autocreated placeholder
     * @throws RepositoryException when the property cannot be read
     */
    public static String read(JCRNodeWrapper site, String property) throws RepositoryException {
        if (!site.hasProperty(property)) {
            return null;
        }
        String content = site.getProperty(property).getString();
        return LLMS.equals(property) && PLACEHOLDER.equals(content.trim()) ? null : content;
    }

}
