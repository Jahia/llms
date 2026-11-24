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
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.AccessDeniedException;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The llms mutations, under {@code llms} on the root Mutation: writes the files an editor reviewed and
 * publishes the site node alone. The caller needs {@code jcr:modifyProperties} and {@code publish} on the site.
 */
@GraphQLName("LlmsMutation")
@GraphQLDescription("llms.txt and llms-full.txt mutations")
public class GqlLlmsMutation {

    private static final String PUBLISH = "publish";

    private static final Logger LOGGER = LoggerFactory.getLogger(GqlLlmsMutation.class);
    /** llms.txt is an index: a file this large is a mistake, not a list of pages. */
    private static final int MAX_LLMS_BYTES = 1024 * 1024;
    private static final int MAX_SETTINGS = 32;
    private static final int MAX_SETTING_LENGTH = 2000;

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
            @GraphQLName("path") @GraphQLDescription("Path of a page of the site, for the files of that section; omitted for the files of the whole site") String path,
            @GraphQLName("llms") @GraphQLDescription("llms.txt; blank removes it, omitted leaves it unchanged") String llms,
            @GraphQLName("llmsFull") @GraphQLDescription("llms-full.txt; blank removes it, omitted leaves it unchanged") String llmsFull,
            @GraphQLName("baseUrl") @GraphQLDescription("Public base URL; blank removes it, omitted leaves it unchanged") String baseUrl) {
        JCRSiteNode site = SiteAccess.site(siteKey, "jcr:modifyProperties", PUBLISH);
        if (baseUrl != null && !baseUrl.isBlank() && !PublicUrls.BASE_URL.matcher(baseUrl).matches()) {
            throw new DataFetchingException("The public URL must be a scheme, a host and an optional port");
        }
        if (tooLarge(llms, MAX_LLMS_BYTES) || tooLarge(llmsFull, service.maxFullBytes())) {
            throw new DataFetchingException("The file is larger than the configured limit");
        }
        try {
            JCRNodeWrapper scope = SiteAccess.scope(site, path, "jcr:modifyProperties", PUBLISH);
            // The public URL belongs to the site: saving the files of a section leaves it alone.
            service.save(scope, llms, llmsFull, scope == site ? baseUrl : null);
            return true;
        } catch (AccessDeniedException e) {
            throw new DataFetchingException(SiteAccess.DENIED);
        } catch (RepositoryException e) {
            LOGGER.warn("Could not save the llms files of {}: {}", siteKey, e.getMessage());
            LOGGER.debug("Full exception", e);
            throw new DataFetchingException("The files could not be saved");
        }
    }

    /**
     * @param siteKey key of the site
     * @param keys    the keys of the values
     * @param values  the values, in the order of the keys; a blank one means "as the module configuration says"
     * @return the values as they now stand; one that carries an error means nothing was saved
     */
    @GraphQLField
    @GraphQLName("saveSettings")
    @GraphQLNonNull
    @GraphQLDescription("Writes what a site lists differently from the module configuration, in the configuration of that site. Nothing is written when a value is refused: the refused values come back with an error.")
    public List<GqlLlmsSetting> saveSettings(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("keys") @GraphQLNonNull @GraphQLDescription("Keys of the values") List<String> keys,
            @GraphQLName("values") @GraphQLNonNull @GraphQLDescription("The values, in the order of the keys; blank for the value of the module configuration") List<String> values) {
        SiteAccess.site(siteKey, SiteAccess.SETTINGS);
        if (keys.size() != values.size() || keys.size() > MAX_SETTINGS) {
            throw new DataFetchingException("As many values as keys are expected");
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String value = values.get(i);
            if (value != null && value.length() > MAX_SETTING_LENGTH) {
                throw new DataFetchingException("A value is too long");
            }
            byKey.put(keys.get(i), value);
        }
        try {
            return service.saveSiteSettings(siteKey, byKey).stream().map(GqlLlmsSetting::new).toList();
        } catch (IllegalArgumentException e) {
            throw new DataFetchingException("Unknown setting");
        } catch (IllegalStateException e) {
            throw new DataFetchingException(e.getMessage());
        } catch (IOException e) {
            LOGGER.warn("Could not write the llms settings of {}: {}", siteKey, e.getMessage());
            LOGGER.debug("Full exception", e);
            throw new DataFetchingException("The settings could not be saved");
        }
    }

    /**
     * @param siteKey     key of the site
     * @param language    language of the file, one of the languages the site serves
     * @param baseUrl     public base URL the links start with
     * @param environment the GraphQL environment, for the port this Jahia answers on
     * @return the identifier to read the progress with
     */
    @GraphQLField
    @GraphQLName("startGeneration")
    @GraphQLNonNull
    @GraphQLDescription("Starts a generation of llms-full.txt in the background; read it with the generationProgress query. Saves nothing.")
    public String startGeneration(
            @GraphQLName("siteKey") @GraphQLNonNull @GraphQLDescription("Key of the site") String siteKey,
            @GraphQLName("path") @GraphQLDescription("Path of a page of the site, for the files of that section; omitted for the files of the whole site") String path,
            @GraphQLName("language") @GraphQLNonNull @GraphQLDescription("Language of the file") String language,
            @GraphQLName("baseUrl") @GraphQLNonNull @GraphQLDescription("Public base URL of the site, scheme, host and port") String baseUrl,
            DataFetchingEnvironment environment) {
        JCRSiteNode site = SiteAccess.site(siteKey, PUBLISH);
        String lang = SiteAccess.language(site, language);
        if (!PublicUrls.BASE_URL.matcher(baseUrl).matches()) {
            throw new DataFetchingException("The public URL must be a scheme, a host and an optional port");
        }
        try {
            return service.startFullGeneration(siteKey, SiteAccess.sectionPath(site, path), lang, baseUrl,
                    SiteAccess.renderBase(ContextUtil.getHttpServletRequest(environment.getGraphQlContext())));
        } catch (IllegalStateException e) {
            throw new DataFetchingException(e.getMessage());
        }
    }

    private static boolean tooLarge(String text, int maxBytes) {
        return text != null && text.getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }
}
