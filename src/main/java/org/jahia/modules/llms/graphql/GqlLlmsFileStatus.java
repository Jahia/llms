package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.llms.service.LlmsService;

/**
 * Where one file stands in live, compared on the server so the panel never downloads the published text.
 */
@GraphQLName("LlmsFileStatus")
@GraphQLDescription("Whether a file is served to visitors and whether what is served is what is saved")
public class GqlLlmsFileStatus {

    private final LlmsService.FileStatus status;

    /**
     * @param status what the service found
     */
    public GqlLlmsFileStatus(LlmsService.FileStatus status) {
        this.status = status;
    }

    /**
     * @return whether the file's URL answers with a file
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when the file's URL serves a file")
    public boolean isServed() {
        return status.served();
    }

    /**
     * @return whether the served file is the saved one
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when the file served is the file saved")
    public boolean isCurrent() {
        return status.current();
    }
}
