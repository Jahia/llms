package org.jahia.modules.llms.service.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a published page the way a visitor gets it: an anonymous GET of its render URL on this Jahia,
 * with no cookie, so the guest's rights, the live workspace and the output cache all apply.
 *
 * <p>The target is never chosen by a caller: the base is this Jahia's own address (configuration, else
 * 127.0.0.1 on the request's local port) and the path is a page's render URL read from the repository.
 * Redirects are followed on that origin only, and only to a path the caller allows. Thread-safe.</p>
 */
public final class PageFetcher {

    private static final Pattern CHARSET = Pattern.compile("charset=\"?([A-Za-z0-9._-]+)");

    /** One client for every generation: a client owns threads that are only freed when it is collected. */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final String base;
    private final Duration timeout;
    private final int maxBytes;
    private final Predicate<String> redirectPath;

    /**
     * @param base           scheme, host and port of this Jahia ({@code http://127.0.0.1:8080})
     * @param timeoutSeconds time allowed for one page
     * @param maxBytes       largest page read; a larger one is cut
     * @param redirectPath   accepts the raw (still encoded) path of a redirect; any other path is refused
     */
    public PageFetcher(String base, int timeoutSeconds, int maxBytes, Predicate<String> redirectPath) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.maxBytes = Math.max(1024, maxBytes);
        this.redirectPath = redirectPath;
    }

    /**
     * @param renderPath a page's render URL, from the root of this Jahia ({@code /cms/render/live/en/sites/x/home.html})
     * @return the page's HTML
     * @throws IOException when the page does not answer 200 with HTML
     * @throws InterruptedException when the generation is stopped
     */
    public String fetch(String renderPath) throws IOException, InterruptedException {
        if (renderPath == null || !renderPath.startsWith("/")) {
            throw new IOException("not a path");
        }
        URI uri = URI.create(base + renderPath);
        for (int hop = 0; hop < 4; hop++) {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Accept", "text/html")
                    .header("User-Agent", "Jahia-llms-generator")
                    .GET().build();
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                response.body().close();
                uri = redirect(uri, response.headers().firstValue("Location").orElse(null), redirectPath);
                continue;
            }
            String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (status != 200 || !type.contains("html")) {
                response.body().close();
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = response.body()) {
                return new String(read(in), charset(type));
            }
        }
        throw new IOException("too many redirects");
    }

    /**
     * A redirect stays on this Jahia and on the paths the caller allows: an absolute Location on another
     * origin is refused, and so is a path of another site or of a servlet.
     */
    static URI redirect(URI from, String location, Predicate<String> allowedPath) throws IOException {
        if (location == null || location.isEmpty()) {
            throw new IOException("redirect without location");
        }
        URI resolved;
        try {
            resolved = from.resolve(location);
        } catch (IllegalArgumentException e) {
            throw new IOException("redirect to an invalid location");
        }
        URI to = resolved.normalize();
        if (!from.getScheme().equals(to.getScheme()) || !from.getAuthority().equals(to.getAuthority())) {
            throw new IOException("redirect off this server");
        }
        // The raw path, as the server will receive it: a decoded path could hide "../" behind %2e%2e, and
        // the server may decode it after this check. Encoded separators and path parameters are refused
        // outright, since no page URL of Jahia needs them.
        // Tested before the dot segments are resolved, so nothing ambiguous is normalised away first.
        String path = to.getRawPath();
        if (path == null || resolved.getRawPath() == null || hasAmbiguousSegment(resolved.getRawPath()) || !allowedPath.test(path)) {
            throw new IOException("redirect off this site");
        }
        return to;
    }

    private static boolean hasAmbiguousSegment(String rawPath) {
        String p = rawPath.toLowerCase(Locale.ROOT);
        return p.contains("%2e") || p.contains("%2f") || p.contains("%5c") || p.contains("%25")
                || p.indexOf(';') >= 0 || p.indexOf('\\') >= 0 || p.contains("//");
    }

    private byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0 && out.size() < maxBytes) {
            out.write(buffer, 0, Math.min(n, maxBytes - out.size()));
        }
        return out.toByteArray();
    }

    private static Charset charset(String contentType) {
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (IllegalArgumentException e) {
                // unknown name: fall through
            }
        }
        return StandardCharsets.UTF_8;
    }
}
