package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;
import org.jahia.modules.graphql.provider.dxm.util.ContextUtil;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.PublicUrls;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;

/**
 * The llms queries, under {@code llms} on the root Query. Read-only: the generated text is returned,
 * never stored. The caller needs {@code publish} on the site: generating llms-full.txt renders every
 * listed page.
 */
@GraphQLName("LlmsQuery")
@GraphQLDescription("llms.txt and llms-full.txt queries")
public class GqlLlmsQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger(GqlLlmsQuery.class);

    @Inject
    @GraphQLOsgiService
    private LlmsService service;

    /**
     * @param siteKey     key of the site
     * @param language    language of the file, one of the site's languages
     * @param baseUrl     public base URL the links start with ({@code https://www.example.com})
     * @param full        true for llms-full.txt, false for llms.txt
     * @param environment the GraphQL environment, for the port this Jahia answers on
     * @return the generated file
     */
    @GraphQLField
    @GraphQLName("generate")
    @GraphQLNonNull
    @GraphQLDescription("Generates a site's llms.txt or llms-full.txt from its published pages, without saving it")
    public GqlLlmsGeneration generate(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Language of the file") String language,
            @GraphQLName("baseUrl") @GraphQLNonNull @GraphQLDescription("Public base URL of the site, scheme, host and port") String baseUrl,
            @GraphQLName("full") @GraphQLNonNull @GraphQLDescription("True for llms-full.txt") boolean full,
            DataFetchingEnvironment environment) {
        JCRSiteNode site = SiteAccess.site(siteKey, "publish");
        String lang = SiteAccess.language(site, language);
        if (!PublicUrls.BASE_URL.matcher(baseUrl).matches()) {
            throw new DataFetchingException("The public URL must be a scheme, a host and an optional port");
        }
        try {
            if (!full) {
                return new GqlLlmsGeneration(service.generateLlms(siteKey, lang, baseUrl));
            }
            HttpServletRequest request = ContextUtil.getHttpServletRequest(environment.getGraphQlContext());
            int port = request == null || request.getLocalPort() <= 0 ? 8080 : request.getLocalPort();
            return new GqlLlmsGeneration(service.generateLlmsFull(siteKey, lang, baseUrl, "http://127.0.0.1:" + port));
        } catch (IllegalStateException e) {
            throw new DataFetchingException(e.getMessage());
        } catch (RepositoryException | RuntimeException e) {
            LOGGER.warn("Could not generate {} for {}: {}", full ? "llms-full.txt" : "llms.txt", siteKey, e.getMessage());
            LOGGER.debug("Full exception", e);
            throw new DataFetchingException("The file could not be generated");
        }
    }
}
