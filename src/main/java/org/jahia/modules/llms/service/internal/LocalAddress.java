package org.jahia.modules.llms.service.internal;

import javax.servlet.http.HttpServletRequest;

/**
 * Where this Jahia reaches itself to render one of its own pages when the configuration names no
 * address: the loopback, on the port the caller's request came in on. Never a host or a port taken from
 * a header of the request.
 */
public final class LocalAddress {

    private LocalAddress() {
    }

    /**
     * @param request the caller's request, or null
     * @return scheme, host and port
     */
    public static String of(HttpServletRequest request) {
        int port = request == null || request.getLocalPort() <= 0 ? 8080 : request.getLocalPort();
        return "http://127.0.0.1:" + port;
    }
}
