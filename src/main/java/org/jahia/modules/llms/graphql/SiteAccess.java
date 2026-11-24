package org.jahia.modules.llms.graphql;

import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.LocalAddress;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * Resolves the site a field is about with the caller's own session in {@code default}, and refuses a
 * guest, a malformed key, a site the caller cannot read and a caller without the permissions the field
 * needs on the site, with one answer.
 */
final class SiteAccess {

    static final String DENIED = "Permission denied";
    /** Lets its holder change what a site lists: a permission of the site administration. */
    static final String SETTINGS = "siteAdminLlmsSettings";
    private static final Pattern SITE_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2,3}(?:[_-][A-Za-z0-9]{2,8}){0,3}$");

    private SiteAccess() {
    }

    /**
     * @param siteKey     the key of the site
     * @param permissions the permissions the caller needs on the site node
     * @return the site node, read with the caller's session
     * @throws DataFetchingException when the caller is a guest, cannot read the site or lacks a permission
     */
    static JCRSiteNode site(String siteKey, String... permissions) {
        JahiaUser user = JCRSessionFactory.getInstance().getCurrentUser();
        if (user == null || JahiaUserManagerService.isGuest(user) || siteKey == null || !SITE_KEY.matcher(siteKey).matches()) {
            throw new DataFetchingException(DENIED);
        }
        try {
            JCRNodeWrapper node = JCRSessionFactory.getInstance().getCurrentUserSession("default").getNode("/sites/" + siteKey);
            if (node instanceof JCRSiteNode site) {
                for (String permission : permissions) {
                    if (!site.hasPermission(permission)) {
                        throw new DataFetchingException(DENIED);
                    }
                }
                return site;
            }
        } catch (PathNotFoundException e) {
            // answered below, the same way as a node that is not a site
        } catch (RepositoryException e) {
            throw new DataFetchingException("The site could not be read");
        }
        throw new DataFetchingException(DENIED);
    }

    /**
     * What the files are about: the site, or a page of it at the top of a section.
     *
     * @param site        the site, already checked with {@link #site}
     * @param path        the path of a page of the site, or null or blank for the site itself
     * @param permissions the permissions the caller needs on that page
     * @return the site, or the page read with the caller's session in {@code default}
     * @throws DataFetchingException when the path is not a page of this site, or the caller lacks a permission on it
     */
    static JCRNodeWrapper scope(JCRSiteNode site, String path, String... permissions) {
        if (path == null || path.isBlank()) {
            return site;
        }
        try {
            if (LlmsService.isSectionOf(site.getSiteKey(), path) && site.getSession().nodeExists(path)) {
                JCRNodeWrapper page = site.getSession().getNode(path);
                if (page.isNodeType("jnt:page")) {
                    for (String permission : permissions) {
                        if (!page.hasPermission(permission)) {
                            throw new DataFetchingException(DENIED);
                        }
                    }
                    return page;
                }
            }
        } catch (RepositoryException e) {
            // answered below, as a page that is not there
        }
        throw new DataFetchingException("Unknown section");
    }

    /**
     * @param site the site
     * @param path the path given by the caller
     * @return the path of the section, or null for the whole site
     */
    static String sectionPath(JCRSiteNode site, String path) {
        JCRNodeWrapper scope = scope(site, path);
        return scope == site ? null : scope.getPath();
    }

    /**
     * Where this Jahia renders its own pages for llms-full.txt when the configuration names no address:
     * the loopback, on the port the caller's request came in on. Never a host or a port taken from a
     * header of the request.
     *
     * @param request the caller's request, or null
     * @return scheme, host and port
     */
    static String renderBase(HttpServletRequest request) {
        return LocalAddress.of(request);
    }

    /**
     * @param site     the site
     * @param language a language code from the caller
     * @return the language, when it is one of the languages the site serves to visitors
     * @throws DataFetchingException otherwise
     */
    static String language(JCRSiteNode site, String language) {
        // The files describe the public site: a language switched off in live has nothing to list.
        if (language == null || !LANGUAGE.matcher(language).matches() || !site.getActiveLiveLanguages().contains(language)) {
            throw new DataFetchingException("Unknown language for this site");
        }
        return language;
    }
}
