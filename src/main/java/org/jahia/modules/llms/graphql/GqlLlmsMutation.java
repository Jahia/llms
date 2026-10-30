package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.PublicUrls;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import java.nio.charset.StandardCharsets;

/**
 * The llms mutations, under {@code llms} on the root Mutation: writes the files an editor reviewed and
 * publishes the site node alone. The caller needs {@code jcr:modifyProperties} and {@code publish} on the site.
 */
@GraphQLName("LlmsMutation")
@GraphQLDescription("llms.txt and llms-full.txt mutations")
public class GqlLlmsMutation {

    private static final Logger LOGGER = LoggerFactory.getLogger(GqlLlmsMutation.class);
    /** llms.txt is an index: a file this large is a mistake, not a list of pages. */
    private static final int MAX_LLMS_BYTES = 1024 * 1024;

    @Inject
    @GraphQLOsgiService
    private LlmsService service;

    /**
     * @param siteKey  key of the site
     * @param llms     llms.txt to store; blank removes it, omitted leaves it unchanged
     * @param llmsFull llms-full.txt to store; blank removes it, omitted leaves it unchanged
     * @param baseUrl  public base URL to store; blank removes it, omitted leaves it unchanged
     * @return true once saved and published
     */
    @GraphQLField
    @GraphQLName("save")
    @GraphQLNonNull
    @GraphQLDescription("Saves a site's llms.txt and llms-full.txt and publishes the site node alone")
    public boolean save(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("llms") @GraphQLDescription("llms.txt; blank removes it, omitted leaves it unchanged") String llms,
            @GraphQLName("llmsFull") @GraphQLDescription("llms-full.txt; blank removes it, omitted leaves it unchanged") String llmsFull,
            @GraphQLName("baseUrl") @GraphQLDescription("Public base URL; blank removes it, omitted leaves it unchanged") String baseUrl) {
        JCRSiteNode site = SiteAccess.site(siteKey, "jcr:modifyProperties", "publish");
        if (baseUrl != null && !baseUrl.isBlank() && !PublicUrls.BASE_URL.matcher(baseUrl).matches()) {
            throw new DataFetchingException("The public URL must be a scheme, a host and an optional port");
        }
        if (tooLarge(llms, MAX_LLMS_BYTES) || tooLarge(llmsFull, service.maxFullBytes())) {
            throw new DataFetchingException("The file is larger than the configured limit");
        }
        try {
            service.save(site, llms, llmsFull, baseUrl);
            return true;
        } catch (AccessDeniedException e) {
            throw new DataFetchingException(SiteAccess.DENIED);
        } catch (RepositoryException e) {
            LOGGER.warn("Could not save the llms files of {}: {}", siteKey, e.getMessage());
            LOGGER.debug("Full exception", e);
            throw new DataFetchingException("The files could not be saved");
        }
    }

    private static boolean tooLarge(String text, int maxBytes) {
        return text != null && text.getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }
}
