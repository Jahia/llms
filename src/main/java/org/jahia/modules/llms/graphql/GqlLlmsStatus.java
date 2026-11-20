package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.llms.service.LlmsService;

/**
 * Where the two files of a site stand in live.
 */
@GraphQLName("LlmsStatus")
@GraphQLDescription("Publication status of a site's llms.txt and llms-full.txt")
public class GqlLlmsStatus {

    private final LlmsService.Status status;

    /**
     * @param status what the service found
     */
    public GqlLlmsStatus(LlmsService.Status status) {
        this.status = status;
    }

    /**
     * @return the status of llms.txt
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Status of llms.txt")
    public GqlLlmsFileStatus getLlms() {
        return new GqlLlmsFileStatus(status.llms());
    }

    /**
     * @return the status of llms-full.txt
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Status of llms-full.txt")
    public GqlLlmsFileStatus getLlmsFull() {
        return new GqlLlmsFileStatus(status.llmsFull());
    }
}
