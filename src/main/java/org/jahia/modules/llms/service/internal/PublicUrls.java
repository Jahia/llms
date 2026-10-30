package org.jahia.modules.llms.service.internal;

import org.jahia.bin.Jahia;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.seo.urlrewrite.UrlRewriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The public URL of a page as the site prints it on its public host: the page's default vanity URL when
 * it has one, else its render URL passed through Jahia's outbound rewriter as if the request came in on
 * that host. The host is never read from a request: it is the site's public base URL, set by an editor.
 * Thread-safe: each instance holds its own request stand-in, one instance per generation.
 */
public final class PublicUrls {

    /** Scheme, host and optional port, nothing else: the shape the CND constraint of {@code j:llmsBaseUrl} accepts. */
    public static final Pattern BASE_URL = Pattern.compile("^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$");

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicUrls.class);

    private final String base;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    /**
     * @param base the site's public base URL ({@code https://www.example.com}), checked against {@link #BASE_URL}
     */
    public PublicUrls(String base) {
        if (base == null || !BASE_URL.matcher(base).matches()) {
            throw new IllegalArgumentException("Invalid base URL");
        }
        this.base = base;
        URI uri = URI.create(base);
        int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
        this.request = request(uri.getScheme(), uri.getHost(), port, Jahia.getContextPath());
        this.response = response();
    }

    /**
     * @param page a page read from a live session in the language of the file
     * @return its absolute public URL
     * @throws RepositoryException when the page cannot be read
     */
    public String urlOf(JCRNodeWrapper page) throws RepositoryException {
        String vanity = defaultVanity(page);
        if (vanity != null) {
            return base + vanity;
        }
        String path = page.getUrl();
        try {
            UrlRewriteService rewriter = (UrlRewriteService) SpringContextSingleton.getBean("UrlRewriteService");
            String rewritten = rewriter.rewriteOutbound(path, request, response);
            if (rewritten != null && !rewritten.isEmpty()) {
                path = rewritten;
            }
        } catch (Exception e) {
            // The render URL is longer but always valid.
            LOGGER.debug("Outbound rewrite failed for {}", path, e);
        }
        return path.startsWith("http://") || path.startsWith("https://") ? path : base + path;
    }

    /** The page's active default vanity URL in the session's language, with the context path, or null. */
    private static String defaultVanity(JCRNodeWrapper page) throws RepositoryException {
        if (!page.hasNode("vanityUrlMapping")) {
            return null;
        }
        String language = page.getSession().getLocale() == null ? null : page.getSession().getLocale().toString();
        NodeIterator it = page.getNode("vanityUrlMapping").getNodes();
        while (it.hasNext()) {
            Node v = it.nextNode();
            if (v.isNodeType("jnt:vanityUrl") && flag(v, "j:default", false) && flag(v, "j:active", true)
                    && (language == null || !v.hasProperty("jcr:language") || language.equals(v.getProperty("jcr:language").getString()))
                    && v.hasProperty("j:url") && v.getProperty("j:url").getString().startsWith("/")) {
                return Jahia.getContextPath() + v.getProperty("j:url").getString();
            }
        }
        return null;
    }

    private static boolean flag(Node node, String name, boolean missing) throws RepositoryException {
        return node.hasProperty(name) ? node.getProperty(name).getBoolean() : missing;
    }

    /** What the rewriter reads of a request: scheme, host, port, context path and attributes. */
    private static HttpServletRequest request(String scheme, String host, int port, String contextPath) {
        Map<String, Object> attributes = new HashMap<>();
        String ctx = contextPath == null ? "" : contextPath;
        return (HttpServletRequest) Proxy.newProxyInstance(PublicUrls.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getScheme" -> scheme;
                    case "getServerName" -> host;
                    case "getServerPort", "getLocalPort" -> port;
                    case "getContextPath" -> ctx;
                    case "isSecure" -> "https".equals(scheme);
                    case "getMethod" -> "GET";
                    case "getCharacterEncoding" -> "UTF-8";
                    case "getRequestURI" -> ctx + "/";
                    case "getRequestURL" -> new StringBuffer(scheme + "://" + host + ":" + port + ctx + "/");
                    case "getAttribute" -> attributes.get(String.valueOf(args[0]));
                    case "setAttribute" -> {
                        attributes.put(String.valueOf(args[0]), args[1]);
                        yield null;
                    }
                    case "removeAttribute" -> {
                        attributes.remove(String.valueOf(args[0]));
                        yield null;
                    }
                    case "getAttributeNames" -> Collections.enumeration(new HashSet<>(attributes.keySet()));
                    case "getHeaderNames", "getHeaders", "getParameterNames" -> Collections.emptyEnumeration();
                    case "getParameterMap" -> Collections.emptyMap();
                    case "toString" -> "PublicUrls.request(" + scheme + "://" + host + ":" + port + ctx + ")";
                    default -> defaultFor(method.getReturnType());
                });
    }

    private static HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(PublicUrls.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "encodeURL", "encodeRedirectURL", "encodeUrl", "encodeRedirectUrl" -> args[0];
                    case "getCharacterEncoding" -> "UTF-8";
                    case "toString" -> "PublicUrls.response()";
                    default -> defaultFor(method.getReturnType());
                });
    }

    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }
}
