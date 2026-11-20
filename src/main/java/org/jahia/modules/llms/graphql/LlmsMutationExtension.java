package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

/**
 * Adds {@code llms { ... }} to the root Mutation. The returned object receives the OSGi services:
 * graphql-dxm-provider injects returned objects, never the static fields of an extension class.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
public final class LlmsMutationExtension {

    private LlmsMutationExtension() {
    }

    /**
     * @return the entry point of the llms mutations
     */
    @GraphQLField
    @GraphQLName("llms")
    @GraphQLNonNull
    @GraphQLDescription("llms.txt and llms-full.txt mutations")
    public static GqlLlmsMutation llms() {
        return new GqlLlmsMutation();
    }
}
