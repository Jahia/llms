package org.jahia.modules.llms.graphql;

import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.regex.Pattern;

/**
 * Resolves the site a field is about with the caller's own session in {@code default}, and refuses a
 * guest, a malformed key, a site the caller cannot read and a caller without the permissions the field
 * needs on the site, with one answer.
 */
final class SiteAccess {

    static final String DENIED = "Permission denied";
    private static final Pattern SITE_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2,3}([_-][A-Za-z0-9]{2,8})*$");

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
     * @param site     the site
     * @param language a language code from the caller
     * @return the language, when it is one of the site's languages
     * @throws DataFetchingException otherwise
     */
    static String language(JCRSiteNode site, String language) {
        if (language == null || !LANGUAGE.matcher(language).matches() || !site.getLanguages().contains(language)) {
            throw new DataFetchingException("Unknown language for this site");
        }
        return language;
    }
}
