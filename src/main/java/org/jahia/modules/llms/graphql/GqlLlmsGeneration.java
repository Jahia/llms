package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.llms.service.LlmsService;

import java.util.List;

/**
 * A generated file, for the editor to review before saving it.
 */
@GraphQLName("LlmsGeneration")
@GraphQLDescription("A generated llms.txt or llms-full.txt, not saved")
public class GqlLlmsGeneration {

    private final LlmsService.Generation generation;

    /**
     * @param generation what the service produced
     */
    public GqlLlmsGeneration(LlmsService.Generation generation) {
        this.generation = generation;
    }

    /**
     * @return the file
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The generated file")
    public String getContent() {
        return generation.content();
    }

    /**
     * @return how many pages the file holds
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Pages the file lists or holds")
    public int getPages() {
        return generation.pages();
    }

    /**
     * @return public URLs of the pages that could not be rendered
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Public URLs of the pages that could not be rendered, left out of the file")
    public List<String> getFailedPages() {
        return generation.failedPages();
    }

    /**
     * @return whether a page or size limit cut the file
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when the page or size limit of the module configuration cut the file")
    public boolean isTruncated() {
        return generation.truncated();
    }
}
