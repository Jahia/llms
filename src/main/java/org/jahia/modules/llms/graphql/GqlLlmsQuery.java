package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.jahia.modules.graphql.provider.dxm.DataFetchingException;
import org.jahia.modules.graphql.provider.dxm.osgi.annotations.GraphQLOsgiService;
import org.jahia.modules.graphql.provider.dxm.util.ContextUtil;
import org.jahia.modules.llms.service.GenerationJob;
import org.jahia.modules.llms.service.LlmsService;
import org.jahia.modules.llms.service.internal.PublicUrls;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * The llms queries, under {@code llms} on the root Query. Read-only: the generated text is returned,
 * never stored. The caller needs {@code publish} on the site: generating llms-full.txt renders every
 * listed page.
 */
@GraphQLName("LlmsQuery")
@GraphQLDescription("llms.txt and llms-full.txt queries")
public class GqlLlmsQuery {

    private static final String FULL_EXCEPTION = "Full exception";

    private static final Logger LOGGER = LoggerFactory.getLogger(GqlLlmsQuery.class);
    private static final String PUBLISH = "publish";

    @Inject
    @GraphQLOsgiService
    private LlmsService service;

    /**
     * @param siteKey key of the site
     * @return the pages that have, or can be given, their own llms.txt
     */
    @GraphQLField
    @GraphQLName("sections")
    @GraphQLNonNull
    @GraphQLDescription("The pages of a site that have their own llms.txt, and the pages below the home page that can be given one")
    public List<GqlLlmsSection> sections(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey) {
        JCRSiteNode site = SiteAccess.site(siteKey, PUBLISH);
        try {
            return service.sections(site).stream().map(GqlLlmsSection::new).toList();
        } catch (RepositoryException e) {
            LOGGER.warn("Could not list the sections of {}: {}", siteKey, e.getMessage());
            LOGGER.debug(FULL_EXCEPTION, e);
            throw new DataFetchingException("The sections could not be read");
        }
    }

    /**
     * @param siteKey key of a site the caller administers
     * @return the values that site may set for its generation
     */
    @GraphQLField
    @GraphQLName("settings")
    @GraphQLNonNull
    @GraphQLDescription("The values a site may set for the generation of its files, with what it inherits. For who may change them.")
    public List<GqlLlmsSetting> settings(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey) {
        SiteAccess.site(siteKey, SiteAccess.SETTINGS);
        return service.siteSettings(siteKey).stream().map(GqlLlmsSetting::new).toList();
    }

    /**
     * @param siteKey key of a site the caller may publish on: the rules in force for it, told only to who can
     *                generate
     * @return which pages generation leaves out and how many it lists
     */
    @GraphQLField
    @GraphQLName("rules")
    @GraphQLNonNull
    @GraphQLDescription("Which pages generation leaves out and how many it lists, as configured on this Jahia")
    public GqlLlmsRules rules(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey) {
        SiteAccess.site(siteKey, PUBLISH);
        return new GqlLlmsRules(service.rules(siteKey));
    }

    /**
     * @param siteKey key of the site
     * @param id      identifier returned by the startGeneration mutation
     * @return how far that generation is, and the file once it is finished
     */
    @GraphQLField
    @GraphQLName("generationProgress")
    @GraphQLNonNull
    @GraphQLDescription("How far a generation of llms-full.txt started with startGeneration is, and the file once finished")
    public GqlLlmsProgress generationProgress(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("id") @GraphQLNonNull @GraphQLDescription("Identifier of the generation") String id) {
        SiteAccess.site(siteKey, PUBLISH);
        GenerationJob job = service.job(siteKey, id);
        if (job == null) {
            // One answer for an unknown identifier and for a generation of another site.
            throw new DataFetchingException("Unknown generation");
        }
        return new GqlLlmsProgress(job.snapshot());
    }

    /**
     * @param siteKey key of the site
     * @return whether each file is served to visitors, and whether what is served is what is saved
     */
    @GraphQLField
    @GraphQLName("status")
    @GraphQLNonNull
    @GraphQLDescription("Whether a site's llms.txt and llms-full.txt are served, and are the saved ones")
    public GqlLlmsStatus status(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("path") @GraphQLDescription("Path of a page of the site, for the files of that section; omitted for the files of the whole site") String path) {
        JCRSiteNode site = SiteAccess.site(siteKey, PUBLISH);
        try {
            return new GqlLlmsStatus(service.status(SiteAccess.scope(site, path)));
        } catch (RepositoryException e) {
            LOGGER.warn("Could not read the status of the llms files of {}: {}", siteKey, e.getMessage());
            LOGGER.debug(FULL_EXCEPTION, e);
            throw new DataFetchingException("The status of the files could not be read");
        }
    }

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
            @GraphQLName("path") @GraphQLDescription("Path of a page of the site, for the files of that section; omitted for the files of the whole site") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Language of the file") String language,
            @GraphQLName("baseUrl") @GraphQLNonNull @GraphQLDescription("Public base URL of the site, scheme, host and port") String baseUrl,
            @GraphQLName("full") @GraphQLNonNull @GraphQLDescription("True for llms-full.txt") boolean full,
            DataFetchingEnvironment environment) {
        JCRSiteNode site = SiteAccess.site(siteKey, PUBLISH);
        String lang = SiteAccess.language(site, language);
        if (!PublicUrls.BASE_URL.matcher(baseUrl).matches()) {
            throw new DataFetchingException("The public URL must be a scheme, a host and an optional port");
        }
        String section = SiteAccess.sectionPath(site, path);
        try {
            if (!full) {
                return new GqlLlmsGeneration(service.generateLlms(siteKey, section, lang, baseUrl));
            }
            HttpServletRequest request = ContextUtil.getHttpServletRequest(environment.getGraphQlContext());
            return new GqlLlmsGeneration(service.generateLlmsFull(siteKey, section, lang, baseUrl, SiteAccess.renderBase(request)));
        } catch (IllegalStateException e) {
            throw new DataFetchingException(e.getMessage());
        } catch (RepositoryException | RuntimeException e) {
            LOGGER.warn("Could not generate {} for {}: {}", full ? "llms-full.txt" : "llms.txt", siteKey, e.getMessage());
            LOGGER.debug(FULL_EXCEPTION, e);
            throw new DataFetchingException("The file could not be generated");
        }
    }
}
