package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.llms.service.GenerationJob;

/**
 * A page of a running generation and what became of it.
 */
@GraphQLName("LlmsPageProgress")
@GraphQLDescription("A page of a generation of llms-full.txt and what became of it")
public class GqlLlmsPageProgress {

    private final GenerationJob.PageProgress page;

    /**
     * @param page the page and its state
     */
    public GqlLlmsPageProgress(GenerationJob.PageProgress page) {
        this.page = page;
    }

    /**
     * @return the title of the page
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Title of the page")
    public String getTitle() {
        return page.title();
    }

    /**
     * @return the public URL of the page
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Public URL of the page")
    public String getUrl() {
        return page.url();
    }

    /**
     * @return PENDING, RENDERED, EMPTY or FAILED
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("PENDING, RENDERED (written in the file), EMPTY (nothing to read, left out) or FAILED (left out)")
    public String getState() {
        return page.state().name();
    }
}
