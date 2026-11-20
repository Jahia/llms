package org.jahia.modules.llms.service;

import org.jahia.modules.llms.service.internal.PublicUrls;

/**
 * The generation limits, each brought back into a range the module can honour. The OSGi configuration is
 * free text for an operator: a value out of range must not disable a limit or overflow a size.
 *
 * @param maxDepth            levels of pages below the home page that are listed
 * @param maxPages            most pages one file lists
 * @param fetchTimeoutSeconds time allowed to render one page
 * @param fetchThreads        pages rendered at the same time
 * @param maxPageBytes        largest rendered page read
 * @param maxFullBytes        largest llms-full.txt produced or saved
 * @param maxTotalSeconds     time allowed for one generation of llms-full.txt
 * @param renderBaseUrl       where this Jahia renders pages, or null for the address of the request
 */
record Limits(int maxDepth, int maxPages, int fetchTimeoutSeconds, int fetchThreads, int maxPageBytes,
              int maxFullBytes, int maxTotalSeconds, String renderBaseUrl) {

    /** 64 MB: the largest size a limit in kilobytes may reach, far below an int overflow. */
    private static final int MAX_KB = 64 * 1024;

    static Limits of(LlmsConfig c) {
        return of(c.maxDepth(), c.maxPages(), c.fetchTimeoutSeconds(), c.fetchThreads(), c.maxPageKb(), c.maxFullKb(),
                c.maxTotalSeconds(), c.renderBaseUrl());
    }

    static Limits of(int maxDepth, int maxPages, int fetchTimeoutSeconds, int fetchThreads, int maxPageKb, int maxFullKb,
                     int maxTotalSeconds, String renderBaseUrl) {
        return new Limits(
                clamp(maxDepth, 1, 10),
                clamp(maxPages, 1, 2000),
                clamp(fetchTimeoutSeconds, 1, 120),
                clamp(fetchThreads, 1, 8),
                clamp(maxPageKb, 16, MAX_KB) * 1024,
                clamp(maxFullKb, 16, MAX_KB) * 1024,
                clamp(maxTotalSeconds, 10, 3600),
                validBase(renderBaseUrl));
    }

    /**
     * @param configured the configured render base URL
     * @return true when a value is configured and is not a scheme, a host and an optional port
     */
    static boolean isRejected(String configured) {
        return configured != null && !configured.isBlank() && validBase(configured) == null;
    }

    private static String validBase(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        return PublicUrls.BASE_URL.matcher(trimmed).matches() ? trimmed : null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
