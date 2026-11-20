package org.jahia.modules.llms.servlet;

import org.jahia.exceptions.JahiaException;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.sites.JahiaSitesService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves a site's llms.txt and llms-full.txt from the live workspace, with the rights of the visitor.
 *
 * <p>The URL rewrite rules of the module send {@code /llms.txt} and {@code /llms-full.txt} here
 * ({@code file=full} for the second). {@code /sites/<key>/llms.txt} names the site ({@code site=<key>});
 * at the domain root the site is the one whose server name is the request's host. A file that is not
 * stored, or blank, answers 404.</p>
 *
 * <p>The URLs are public and a file can weigh megabytes: the answer carries an {@code ETag} and an
 * unchanged file answers 304, without being read again while the dates of the site node have not moved. A cache may keep the file but must revalidate it each time
 * ({@code no-cache}): what is published is what is served at once, which the panel and the modules that
 * write {@code j:llms} and then read the URL back (geo-readiness) rely on. Thread-safe; the only state is the validators of the files last served.</p>
 */
@Component(service = {HttpServlet.class, Servlet.class}, property = {"alias=/llms", "osgi.http.whiteboard.servlet.asyncSupported=true"})
public class LlmsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmsServlet.class);
    private static final Pattern SITE_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    /** A visitor without an account: a shared cache may store the file, and revalidates it before each use. */
    private static final String PUBLIC_CACHE = "public, no-cache";
    /** A signed-in user may read a file guests cannot: never stored by a shared cache, always revalidated. */
    private static final String PRIVATE_CACHE = "private, no-cache";

    static final long VALIDATOR_LIFETIME_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final int MAX_VALIDATORS = 2000;

    /** The ETag of each file last served, by site and file: no file content is kept in memory. */
    private final transient Map<String, Validator> validators = new ConcurrentHashMap<>();
    private transient JahiaSitesService sitesService;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Target target = Target.of((String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI),
                request.getContextPath(), request.getParameter("site"), request.getParameter("file"));
        if (target == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String property = target.full() ? LlmsService.LLMS_FULL : LlmsService.LLMS;
        String ifNoneMatch = request.getHeader("If-None-Match");
        String content = null;
        String cacheKey = null;
        String stamp = null;
        try {
            JCRSessionWrapper live = JCRSessionFactory.getInstance().getCurrentUserSession("live");
            String siteKey = target.siteKey();
            if (siteKey == null || siteKey.isEmpty()) {
                siteKey = siteOfHost(request.getServerName(), live);
            }
            // Read with the visitor's session, on every request: a site they may not read stays a 404.
            JCRNodeWrapper site = siteKey != null && SITE_KEY.matcher(siteKey).matches() ? LlmsService.siteNode(live, siteKey) : null;
            if (site != null) {
                cacheKey = siteKey + '/' + property;
                stamp = stamp(site);
                Validator known = validators.get(cacheKey);
                if (known != null && known.isFor(stamp, System.nanoTime()) && matches(ifNoneMatch, known.etag())) {
                    // The caller holds the file as it is served now: answered without reading or hashing it.
                    headers(response, known.etag());
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
                content = LlmsService.read(site, property);
            }
        } catch (Exception e) {
            // Debug only: the URL is public, a failing read must not write one line per request.
            LOGGER.debug("Could not read {}", property, e);
        }
        if (content == null || content.isBlank()) {
            if (cacheKey != null) {
                validators.remove(cacheKey);
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        String etag = etag(body);
        remember(cacheKey, stamp, etag);
        headers(response, etag);
        if (matches(ifNoneMatch, etag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        response.setContentType("text/plain;charset=UTF-8");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    /**
     * Which file of which site a request is for. A request the rewrite rules forwarded here is read from
     * the URL the visitor asked for, so a query string cannot name another site or file than the path
     * does; only a direct call to the servlet is read from its parameters.
     *
     * @param siteKey the site named by the URL, or null for the site of the request's host
     * @param full    true for llms-full.txt
     */
    record Target(String siteKey, boolean full) {

        /** The paths the rewrite rules of the module accept, written the same way. */
        private static final Pattern PUBLIC_URL = Pattern.compile(
                "^(?:/cms(?:/render/live)?(?:/[a-z]{2,3}(?:_[A-Z]{2})?)?)?(?:/sites/([A-Za-z0-9_-]{1,64}))?/llms(-full)?\\.txt$");

        /**
         * @param forwardedUri  the URL the visitor asked for when a rewrite rule forwarded the request, else null
         * @param contextPath   the context path of Jahia, which that URL starts with
         * @param siteParameter the {@code site} parameter
         * @param fileParameter the {@code file} parameter
         * @return the target, or null when the request was forwarded from a URL that is not one of ours:
         *         nothing is then guessed from parameters the visitor may have added
         */
        static Target of(String forwardedUri, String contextPath, String siteParameter, String fileParameter) {
            if (forwardedUri == null) {
                return new Target(siteParameter, "full".equals(fileParameter));
            }
            String path = contextPath != null && !contextPath.isEmpty() && forwardedUri.startsWith(contextPath + "/")
                    ? forwardedUri.substring(contextPath.length()) : forwardedUri;
            Matcher m = PUBLIC_URL.matcher(path);
            return m.matches() ? new Target(m.group(1), m.group(2) != null) : null;
        }
    }

    private static void headers(HttpServletResponse response, String etag) {
        response.setHeader("Cache-Control", JahiaUserManagerService.isGuest(JCRSessionFactory.getInstance().getCurrentUser()) ? PUBLIC_CACHE : PRIVATE_CACHE);
        response.setHeader("ETag", etag);
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private void remember(String cacheKey, String stamp, String etag) {
        if (cacheKey == null || stamp == null) {
            return;
        }
        if (validators.size() >= MAX_VALIDATORS) {
            validators.clear();
        }
        validators.put(cacheKey, new Validator(stamp, etag, System.nanoTime()));
    }

    /**
     * When the site node last changed and was last published, the two dates Jahia keeps on a site node.
     * Null when it carries neither: the file is then read on every request.
     */
    private static String stamp(JCRNodeWrapper site) throws RepositoryException {
        if (!site.hasProperty("jcr:lastModified")) {
            return null;
        }
        String modified = site.getProperty("jcr:lastModified").getString();
        return site.hasProperty("j:lastPublished") ? modified + '|' + site.getProperty("j:lastPublished").getString() : modified;
    }

    /**
     * The validator of a file as it was last read: good for the same dates on the site node, and for a
     * minute at most, so a change that moved neither date (an import that keeps them) is served late by
     * that much, never for good.
     */
    record Validator(String stamp, String etag, long readAt) {
        boolean isFor(String currentStamp, long now) {
            return currentStamp != null && currentStamp.equals(stamp) && now - readAt < VALIDATOR_LIFETIME_NANOS;
        }
    }

    /**
     * The site a host serves, from Jahia's cache of site keys by server name. A host no site claims, or
     * one only the system site claims (localhost on a local install), is served by the default site, as
     * Jahia serves its pages.
     */
    private String siteOfHost(String serverName, JCRSessionWrapper live) throws JahiaException, RepositoryException {
        String key = serverName == null ? null : sitesService.getSitenameByServerName(serverName.toLowerCase(Locale.ROOT));
        if (key == null || JahiaSitesService.SYSTEM_SITE_KEY.equals(key)) {
            JCRSiteNode site = (JCRSiteNode) sitesService.getDefaultSite(live);
            return site == null ? null : site.getSiteKey();
        }
        return key;
    }

    /** A strong validator of the bytes served. */
    static String etag(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            return '"' + HexFormat.of().formatHex(digest, 0, 16) + '"';
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** If-None-Match: a list of validators, each possibly weak ({@code W/}), or {@code *}. */
    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String c = candidate.trim();
            if ("*".equals(c) || etag.equals(c.startsWith("W/") ? c.substring(2) : c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param sitesService the sites service
     */
    @Reference
    public void setSitesService(JahiaSitesService sitesService) {
        this.sitesService = sitesService;
    }
}
